package com.github.damontecres.wholphin.vlc

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceView
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCVout
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration

/**
 * A [Player] backed by libVLC, for media ExoPlayer cannot handle on this device.
 *
 * libVLC drives MediaCodec itself and renders straight into the [SurfaceView] the UI hands over.
 * Only what the playback UI uses is supported: a single media item, play/pause, seek, speed,
 * volume, audio/subtitle track selection and external subtitles.
 *
 * libVLC calls can block for seconds (stop() waits for the network input to shut down), so every
 * call into it runs on [vlcHandler]; the main thread only keeps the state it reports to media3.
 */
@OptIn(UnstableApi::class)
class VlcPlayer(
    context: Context,
    verboseLogs: Boolean = false,
) : SimpleBasePlayer(Looper.getMainLooper()),
    MediaPlayer.EventListener,
    IVLCVout.OnNewVideoLayoutListener {
    // -vv: libVLC's own logs (decoder and output chosen, buffering) in logcat, with the app's verbose logging
    private val libVlc =
        LibVLC(
            context.applicationContext,
            arrayListOf("--http-reconnect", "--audio-time-stretch") + if (verboseLogs) listOf("-vv") else emptyList(),
        )
    private val mediaPlayer = MediaPlayer(libVlc).also { it.setEventListener(this) }
    private val vlcThread = HandlerThread("VlcPlayer").also { it.start() }
    private val vlcHandler = Handler(vlcThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    var isReleased = false
        private set

    private var mediaItem: MediaItem? = null

    // A new uid per loaded media, so a stream switch for the same item is still a new playlist entry
    private var mediaUid = Any()

    // Bumped per load so results computed for a previous media are dropped
    private var loadGeneration = 0
    private var prepared = false
    private var started = false
    private var playWhenReady = false
    private var playbackState = Player.STATE_IDLE
    private var vlcPlaying = false
    private var error: PlaybackException? = null
    private var durationMs = C.TIME_UNSET
    private var seekable = true
    private var tracks = Tracks.EMPTY

    // libVLC announces streams one by one while opening; tracks are only published once playing,
    // otherwise the first update has video but no audio yet and track selection gives up
    private var tracksReady = false
    private var trackSelectionParameters = TrackSelectionParameters.DEFAULT
    private var videoSize = VideoSize.UNKNOWN
    private var speed = 1f
    private var volume = 1f
    private var firstFrameRendered = false

    // Position comes from TimeChanged events, extrapolated while playing, so the main thread never
    // asks libVLC (which takes the same lock a blocking stop() holds)
    private var timeMs = 0L
    private var timeAtMs = 0L
    private var pendingSeekMs: Long? = null
    private var seekAtMs = 0L

    // A seek libVLC cannot take yet (not started, or stopped at the end); sent once it is playing
    private var deferredSeekMs: Long? = null

    // After a load, libVLC still delivers events of the previous media until it reports MediaChanged
    private var awaitingMedia = false

    private var surfaceView: SurfaceView? = null

    /**
     * A transparent surface stacked above the video one. libVLC only renders straight to the video
     * surface (hardware path, HDR passed through) when it also has a surface for subtitles;
     * otherwise it falls back to drawing every frame with OpenGL.
     */
    var subtitlesView: SurfaceView? = null
        set(value) {
            if (field === value) return
            field = value
            attachViews()
        }

    private val layoutListener =
        View.OnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            mediaPlayer.vlcVout.setWindowSize(right - left, bottom - top)
        }

    var subtitleDelay: Duration = Duration.ZERO
        set(value) {
            field = value
            vlc { mediaPlayer.setSpuDelay(value.inWholeMicroseconds) }
        }

    private fun vlc(block: () -> Unit) {
        vlcHandler.post(block)
    }

    override fun getState(): State {
        val renderedFirstFrame = firstFrameRendered
        firstFrameRendered = false
        // media3 only accepts an error in STATE_IDLE, and no media only when idle or ended
        val state =
            when {
                error != null -> STATE_IDLE
                mediaItem == null && playbackState != STATE_ENDED -> STATE_IDLE
                else -> playbackState
            }
        val builder =
            State
                .Builder()
                .setAvailableCommands(COMMANDS)
                .setPlayWhenReady(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaybackState(state)
                .setIsLoading(state == STATE_BUFFERING)
                .setPlayerError(error)
                .setPlaybackParameters(PlaybackParameters(speed))
                .setVolume(volume)
                .setVideoSize(videoSize)
                .setTrackSelectionParameters(trackSelectionParameters)
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(30_000)
                .setNewlyRenderedFirstFrame(renderedFirstFrame)
        mediaItem?.let { item ->
            builder
                .setPlaylist(
                    listOf(
                        MediaItemData
                            .Builder(mediaUid)
                            .setMediaItem(item)
                            .setTracks(tracks)
                            .setDurationUs(if (durationMs > 0) durationMs * 1000 else C.TIME_UNSET)
                            .setIsSeekable(seekable)
                            .build(),
                    ),
                ).setCurrentMediaItemIndex(0)
                .setContentPositionMs { positionMs() }
                .setContentBufferedPositionMs { positionMs() }
        }
        return builder.build()
    }

    private fun positionMs(): Long {
        pendingSeekMs?.let { return it }
        if (!vlcPlaying || playbackState != STATE_READY) return timeMs
        val elapsed = ((SystemClock.elapsedRealtime() - timeAtMs) * speed).toLong()
        val position = timeMs + elapsed
        return if (durationMs > 0) position.coerceAtMost(durationMs) else position
    }

    private fun setTime(ms: Long) {
        timeMs = ms
        timeAtMs = SystemClock.elapsedRealtime()
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        val item = mediaItems.getOrNull(if (startIndex == C.INDEX_UNSET) 0 else startIndex)
        load(item, if (startPositionMs == C.TIME_UNSET) 0 else startPositionMs)
        return Futures.immediateVoidFuture()
    }

    private fun load(
        item: MediaItem?,
        startMs: Long,
    ) {
        val wasStarted = started
        started = false
        vlcPlaying = false
        loadGeneration++
        mediaItem = item
        mediaUid = Any()
        tracks = Tracks.EMPTY
        tracksReady = false
        error = null
        durationMs = C.TIME_UNSET
        videoSize = VideoSize.UNKNOWN
        pendingSeekMs = startMs.takeIf { it > 0 }
        seekAtMs = SystemClock.elapsedRealtime()
        deferredSeekMs = null
        setTime(startMs)
        val uri = item?.localConfiguration?.uri
        if (wasStarted) vlc { mediaPlayer.stop() }
        if (uri == null) {
            playbackState = STATE_IDLE
            return
        }
        Timber.i("VLC loading %s at %sms", item.mediaId, startMs)
        val subtitles = item.localConfiguration?.subtitleConfigurations.orEmpty().map { it.uri.toString() }
        awaitingMedia = true
        vlc {
            val media = Media(libVlc, uri)
            media.setHWDecoderEnabled(true, false)
            if (startMs > 0) {
                media.addOption(":start-time=${startMs / 1000.0}")
                // Resume from the keyframe before the position: an exact seek decodes and drops everything from
                // that keyframe on first (seconds of 4K video), which made resuming much slower than ExoPlayer
                media.addOption(":input-fast-seek")
            }
            subtitles.forEach { media.addSlave(IMedia.Slave(IMedia.Slave.Type.Subtitle, 4, it)) }
            mediaPlayer.media = media
            media.release()
        }
        playbackState = if (prepared) STATE_BUFFERING else STATE_IDLE
        startIfReady()
    }

    // libVLC needs both surfaces attached before it starts, see subtitlesView
    private fun startIfReady() {
        if (prepared && !started && mediaItem != null && mediaPlayer.vlcVout.areViewsAttached()) {
            started = true
            vlc { mediaPlayer.play() }
        }
    }

    override fun handlePrepare(): ListenableFuture<*> {
        prepared = true
        error = null
        if (mediaItem != null) {
            playbackState = STATE_BUFFERING
            startIfReady()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        if (started) {
            setTime(positionMs())
            // Stop extrapolating right away; libVLC confirms the pause a moment later
            if (!playWhenReady) vlcPlaying = false
            vlc { if (playWhenReady) mediaPlayer.play() else mediaPlayer.pause() }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        started = false
        prepared = false
        vlcPlaying = false
        playbackState = STATE_IDLE
        vlc { mediaPlayer.stop() }
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        if (!isReleased) {
            Timber.i("VLC release")
            isReleased = true
            surfaceView?.removeOnLayoutChangeListener(layoutListener)
            surfaceView = null
            mediaPlayer.setEventListener(null)
            // detachViews() also takes libVLC's input lock, which a stop() in progress holds
            vlc {
                mediaPlayer.stop()
                val vout = mediaPlayer.vlcVout
                if (vout.areViewsAttached()) vout.detachViews()
                mediaPlayer.release()
                libVlc.release()
            }
            vlcThread.quitSafely()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        if (!seekable) return Futures.immediateVoidFuture()
        val target = if (positionMs == C.TIME_UNSET) 0 else positionMs
        pendingSeekMs = target
        seekAtMs = SystemClock.elapsedRealtime()
        setTime(target)
        when {
            !started -> deferredSeekMs = target

            playbackState == STATE_ENDED -> {
                // libVLC stops at the end; play again and seek once it is back
                deferredSeekMs = target
                playbackState = STATE_BUFFERING
                vlc {
                    mediaPlayer.stop()
                    mediaPlayer.play()
                }
            }

            else -> vlc { mediaPlayer.setTime(target, false) }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        setTime(positionMs())
        speed = playbackParameters.speed
        vlc { mediaPlayer.setRate(playbackParameters.speed) }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(
        volume: Float,
        volumeOperationType: Int,
    ): ListenableFuture<*> {
        this.volume = volume
        vlc { mediaPlayer.setVolume((volume * 100).roundToInt()) }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetTrackSelectionParameters(trackSelectionParameters: TrackSelectionParameters): ListenableFuture<*> {
        this.trackSelectionParameters = trackSelectionParameters
        val disableText = C.TRACK_TYPE_TEXT in trackSelectionParameters.disabledTrackTypes
        val disableAudio = C.TRACK_TYPE_AUDIO in trackSelectionParameters.disabledTrackTypes
        // Format ids are "<index>:<es id>" or "<index>:e:<es id>", see buildTracks
        val selections =
            trackSelectionParameters.overrides.keys.mapNotNull { trackGroup ->
                val esId =
                    tracks.groups
                        .firstOrNull { it.mediaTrackGroup == trackGroup }
                        ?.getTrackFormat(0)
                        ?.id
                        ?.substringAfterLast(':')
                        ?.toIntOrNull()
                if (esId == null) Timber.w("VLC: no track for override %s", trackGroup.getFormat(0).id)
                esId?.let { trackGroup.type to it }
            }
        vlc {
            if (disableText) mediaPlayer.setSpuTrack(-1)
            if (disableAudio) mediaPlayer.setAudioTrack(-1)
            selections.forEach { (type, esId) ->
                when (type) {
                    C.TRACK_TYPE_AUDIO -> mediaPlayer.setAudioTrack(esId)
                    C.TRACK_TYPE_TEXT -> mediaPlayer.setSpuTrack(esId)
                    C.TRACK_TYPE_VIDEO -> mediaPlayer.setVideoTrack(esId)
                }
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVideoOutput(videoOutput: Any): ListenableFuture<*> {
        if (videoOutput is SurfaceView) {
            if (videoOutput !== surfaceView) {
                detachSurface()
                surfaceView = videoOutput
                videoOutput.addOnLayoutChangeListener(layoutListener)
                attachViews()
            }
        } else {
            Timber.w("VLC: unsupported video output %s", videoOutput::class.java)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleClearVideoOutput(videoOutput: Any?): ListenableFuture<*> {
        if (videoOutput == null || videoOutput === surfaceView) detachSurface()
        return Futures.immediateVoidFuture()
    }

    private fun attachViews() {
        val video = surfaceView ?: return
        val subtitles = subtitlesView ?: return
        val vout = mediaPlayer.vlcVout
        if (vout.areViewsAttached()) vout.detachViews()
        vout.setVideoView(video)
        vout.setSubtitlesView(subtitles)
        if (video.width > 0) vout.setWindowSize(video.width, video.height)
        vout.attachViews(this)
        startIfReady()
    }

    private fun detachSurface() {
        surfaceView?.removeOnLayoutChangeListener(layoutListener)
        surfaceView = null
        val vout = mediaPlayer.vlcVout
        if (vout.areViewsAttached()) vout.detachViews()
    }

    override fun onNewVideoLayout(
        vlcVout: IVLCVout,
        width: Int,
        height: Int,
        visibleWidth: Int,
        visibleHeight: Int,
        sarNum: Int,
        sarDen: Int,
    ) {
        if (visibleWidth <= 0 || visibleHeight <= 0) return
        val sar = if (sarNum > 0 && sarDen > 0) sarNum.toFloat() / sarDen else 1f
        videoSize = VideoSize(visibleWidth, visibleHeight, sar)
        invalidateState()
    }

    override fun onEvent(event: MediaPlayer.Event) {
        if (isReleased) return
        if (awaitingMedia) {
            if (event.type == MediaPlayer.Event.MediaChanged) awaitingMedia = false
            return
        }
        when (event.type) {
            MediaPlayer.Event.Opening -> playbackState = STATE_BUFFERING

            MediaPlayer.Event.Buffering -> {
                if (event.buffering < 100f) {
                    playbackState = STATE_BUFFERING
                } else if (playbackState == STATE_BUFFERING) {
                    playbackState = STATE_READY
                }
            }

            MediaPlayer.Event.Playing -> {
                playbackState = STATE_READY
                vlcPlaying = true
                setTime(timeMs)
                if (!playWhenReady) vlc { mediaPlayer.pause() }
                deferredSeekMs?.let { target ->
                    deferredSeekMs = null
                    pendingSeekMs = target
                    seekAtMs = SystemClock.elapsedRealtime()
                    vlc { mediaPlayer.setTime(target, false) }
                }
                // libVLC keeps the delay per input, so every new media needs it again
                val delayUs = subtitleDelay.inWholeMicroseconds
                if (delayUs != 0L) vlc { mediaPlayer.setSpuDelay(delayUs) }
                if (!tracksReady) {
                    tracksReady = true
                    updateTracks()
                }
            }

            MediaPlayer.Event.Paused -> {
                setTime(positionMs())
                vlcPlaying = false
                playbackState = STATE_READY
            }

            MediaPlayer.Event.Vout -> if (event.voutCount > 0) firstFrameRendered = true

            MediaPlayer.Event.LengthChanged -> durationMs = event.lengthChanged

            MediaPlayer.Event.SeekableChanged -> seekable = event.seekable

            MediaPlayer.Event.TimeChanged -> {
                val pending = pendingSeekMs
                // Until libVLC reaches the seek target it still reports the old position; a seek that
                // lands further away (keyframes, segments) is accepted after a second
                if (pending != null &&
                    abs(event.timeChanged - pending) > 1500 &&
                    SystemClock.elapsedRealtime() - seekAtMs < 1000
                ) {
                    return
                }
                pendingSeekMs = null
                setTime(event.timeChanged)
                if (pending == null) return
            }

            MediaPlayer.Event.ESAdded,
            MediaPlayer.Event.ESDeleted,
            MediaPlayer.Event.ESSelected,
            -> {
                if (tracksReady) updateTracks()
                return
            }

            MediaPlayer.Event.EndReached -> {
                vlcPlaying = false
                playbackState = STATE_ENDED
            }

            MediaPlayer.Event.EncounteredError -> {
                Timber.e("VLC playback error")
                error = PlaybackException("libVLC playback error", null, PlaybackException.ERROR_CODE_DECODING_FAILED)
                playbackState = STATE_IDLE
                prepared = false
                started = false
                vlcPlaying = false
            }

            else -> return
        }
        invalidateState()
    }

    /** Reads the track list from libVLC on its thread, then publishes it on the main thread */
    private fun updateTracks() {
        val generation = loadGeneration
        vlc {
            val newTracks = buildTracks() ?: return@vlc
            mainHandler.post {
                if (isReleased || generation != loadGeneration) return@post
                tracks = newTracks
                invalidateState()
            }
        }
    }

    private fun buildTracks(): Tracks? {
        val media = mediaPlayer.media ?: return null
        val mediaTracks =
            try {
                (0 until media.trackCount).mapNotNull { media.getTrack(it) }
            } finally {
                media.release()
            }
        val selected =
            mapOf(
                IMedia.Track.Type.Audio to mediaPlayer.audioTrack,
                IMedia.Track.Type.Video to mediaPlayer.videoTrack,
                IMedia.Track.Type.Text to mediaPlayer.spuTrack,
            )
        val groups =
            mediaTracks
                .mapIndexedNotNull { index, track ->
                    val type =
                        when (track.type) {
                            IMedia.Track.Type.Audio -> C.TRACK_TYPE_AUDIO
                            IMedia.Track.Type.Video -> C.TRACK_TYPE_VIDEO
                            IMedia.Track.Type.Text -> C.TRACK_TYPE_TEXT
                            else -> return@mapIndexedNotNull null
                        }
                    group(track.toFormat("$index:${track.id}", type), selected[track.type] == track.id)
                }.toMutableList()
        // External subtitles were added as slaves; they show up as subtitle tracks the media does not list
        val knownIds = mediaTracks.filter { it.type == IMedia.Track.Type.Text }.map { it.id }.toSet()
        val selectedSpu = mediaPlayer.spuTrack
        mediaPlayer.spuTracks
            ?.filter { it.id >= 0 && it.id !in knownIds }
            ?.forEach {
                val format =
                    Format
                        .Builder()
                        .setId("${groups.size}:e:${it.id}")
                        .setSampleMimeType(MimeTypes.TEXT_UNKNOWN)
                        .setLabel(it.name)
                        .build()
                groups.add(group(format, selectedSpu == it.id))
            }
        return Tracks(groups)
    }

    private fun group(
        format: Format,
        selected: Boolean,
    ) = Tracks.Group(TrackGroup(format), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(selected))

    private fun IMedia.Track.toFormat(
        id: String,
        type: Int,
    ): Format {
        val codecName = codec?.trim()?.lowercase().orEmpty()
        val builder =
            Format
                .Builder()
                .setId(id)
                .setCodecs(codecName)
                .setLanguage(language)
                .setLabel(description)
                .setAverageBitrate(if (bitrate > 0) bitrate else Format.NO_VALUE)
        when (type) {
            C.TRACK_TYPE_AUDIO -> {
                builder.setSampleMimeType("${MimeTypes.BASE_TYPE_AUDIO}/$codecName")
                (this as? IMedia.AudioTrack)?.let {
                    builder.setChannelCount(it.channels).setSampleRate(it.rate)
                }
            }

            C.TRACK_TYPE_VIDEO -> {
                builder.setSampleMimeType("${MimeTypes.BASE_TYPE_VIDEO}/$codecName")
                (this as? IMedia.VideoTrack)?.let {
                    builder.setWidth(it.width).setHeight(it.height)
                    if (it.frameRateDen > 0) builder.setFrameRate(it.frameRateNum.toFloat() / it.frameRateDen)
                }
            }

            else -> builder.setSampleMimeType("${MimeTypes.BASE_TYPE_TEXT}/$codecName")
        }
        return builder.build()
    }

    companion object {
        private val COMMANDS =
            Player.Commands
                .Builder()
                .addAll(
                    COMMAND_PLAY_PAUSE,
                    COMMAND_PREPARE,
                    COMMAND_STOP,
                    COMMAND_RELEASE,
                    COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    COMMAND_SEEK_TO_DEFAULT_POSITION,
                    COMMAND_SEEK_TO_PREVIOUS,
                    COMMAND_SEEK_BACK,
                    COMMAND_SEEK_FORWARD,
                    COMMAND_SET_SPEED_AND_PITCH,
                    COMMAND_GET_CURRENT_MEDIA_ITEM,
                    COMMAND_GET_TIMELINE,
                    COMMAND_SET_MEDIA_ITEM,
                    COMMAND_GET_TRACKS,
                    COMMAND_SET_TRACK_SELECTION_PARAMETERS,
                    COMMAND_GET_VOLUME,
                    COMMAND_SET_VOLUME,
                    COMMAND_SET_VIDEO_SURFACE,
                ).build()
    }
}
