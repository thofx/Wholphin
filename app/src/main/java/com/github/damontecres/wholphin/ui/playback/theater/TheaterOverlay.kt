package com.github.damontecres.wholphin.ui.playback.theater

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.ui.formatTime
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.playback.PlaybackSettings
import com.github.damontecres.wholphin.ui.playback.PlaybackState
import com.github.damontecres.wholphin.ui.playback.overlay.PlaybackAction
import com.github.damontecres.wholphin.ui.playback.overlay.PlaybackDebugOverlay
import com.github.damontecres.wholphin.ui.playback.overlay.SeekPreviewImage
import com.github.damontecres.wholphin.ui.playback.overlay.offsetByPercent
import com.github.damontecres.wholphin.ui.playback.overlay.rememberPauseState
import com.github.damontecres.wholphin.ui.seasonEpisode
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.PlayMethod
import java.time.LocalTime
import kotlin.time.Duration

/**
 * The theater playback overlay: info bar (state 1), button row (state 2) and side panels (state 3).
 *
 * Key handling lives in [TheaterKeyHandler]; this composable only renders [state] and owns focus inside the overlay.
 */
@UnstableApi
@Composable
fun TheaterOverlay(
    state: TheaterState,
    player: Player,
    playback: PlaybackState,
    settings: PlaybackSettings,
    showClock: Boolean,
    enableSubtitleDelay: Boolean,
    enableVideoScale: Boolean,
    trickplayUrlFor: (Int) -> String?,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onChangeSubtitleDelay: (Duration) -> Unit,
    onPlayItem: (BaseItem) -> Unit,
    onSkipSegment: () -> Unit,
    onRequestPlayerFocus: () -> Unit,
    serverBaseUrl: String?,
    modifier: Modifier = Modifier,
) {
    // State 3 -> 2, states 1 & 2 -> 0. Not enabled in state 0, so the page's handlers run there.
    // While the floating skip-segment button shows (states 0 & 1), back dismisses it first via the page's handler
    val floatingSkipShowing =
        state.showsFloatingSkip && playback.currentSegment?.interacted == false && playback.nextUp == null
    BackHandler(enabled = state.visible && !floatingSkipShowing) {
        if (state.panel != null) {
            state.closePanel()
        } else {
            state.hide()
            onRequestPlayerFocus()
        }
    }

    val item = playback.currentPlayback?.item
    val chapters = playback.currentMediaInfo.chapters
    val queue = remember(playback.playlist) { playback.playlist.items.map { it.item } }

    // Danmaku for the current item, empty when there are none (or the server has no danmu plugin)
    val danmakuSettings = rememberDanmakuSettings()
    val itemId = item?.id
    val context = LocalContext.current
    val httpClient = remember(context) { context.danmakuHttpClient() }
    val danmaku by produceState(emptyList<Danmaku>(), serverBaseUrl, itemId) {
        value = emptyList()
        if (serverBaseUrl != null && itemId != null) value = DanmakuLoader.load(httpClient, serverBaseUrl, itemId)
    }

    // Poll the position while visible, for the progress bar, chapter & end time
    var positionMs by remember { mutableLongStateOf(player.currentPosition) }
    LaunchedEffect(state.visible, player) {
        while (state.visible) {
            positionMs = player.currentPosition
            delay(500)
        }
    }

    // Apply a d-pad seek after a pause in key presses, then always resume playback
    LaunchedEffect(state.seekTargetMs) {
        val target = state.seekTargetMs ?: return@LaunchedEffect
        delay(THEATER_SEEK_DEBOUNCE_MS)
        player.seekTo(target)
        player.play()
        positionMs = target
        state.clearSeek()
    }

    // Auto-hide only in state 1 while playing
    val pauseState = rememberPauseState(player)
    LaunchedEffect(state.visible, pauseState.isPaused, state.expanded, state.panel, state.seekTargetMs) {
        state.pulse(isPlaying = !pauseState.isPaused)
    }
    LaunchedEffect(state.visible) {
        if (!state.visible) state.onHidden()
    }

    val durationMs = player.duration.coerceAtLeast(0L)
    val displayedMs = state.seekTargetMs ?: positionMs
    val currentChapterIndex =
        remember(chapters, displayedMs) {
            chapters.indexOfLast { it.position.inWholeMilliseconds <= displayedMs }.coerceAtLeast(0)
        }
    val panelOpen = state.panel != null

    Box(modifier = modifier) {
        if (danmakuSettings.value.enabled && danmaku.isNotEmpty()) {
            DanmakuLayer(
                items = danmaku,
                player = player,
                settings = danmakuSettings.value,
                dimmed = state.visible && state.panel == null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Scrims
        AnimatedVisibility(
            visible = state.visible && !panelOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = .45f),
                            .2f to Color.Transparent,
                            .4f to Color.Transparent,
                            .78f to Color.Black.copy(alpha = .72f),
                            1f to Color.Black.copy(alpha = .9f),
                        ),
                    ),
            )
        }
        AnimatedVisibility(
            visible = panelOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .3f)),
            )
        }

        // Debug info: states 0-2, display only
        if (playback.currentPlayback != null && settings.showDebugInfo && !panelOpen) {
            PlaybackDebugOverlay(
                analyticsState = playback.analyticsState,
                currentPlayback = playback.currentPlayback,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .widthIn(max = 520.dp)
                        .background(Color.Black.copy(alpha = .5f))
                        .padding(8.dp)
                        .focusProperties { canFocus = false },
            )
        }

        // Clock & end time
        AnimatedVisibility(
            visible = state.visible && showClock && !panelOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 28.dp, end = 48.dp),
        ) {
            ClockAndEndTime(
                remainingMs = (durationMs - displayedMs).coerceAtLeast(0L),
                speed = settings.playbackSpeed,
            )
        }

        // Info bar & button row
        AnimatedVisibility(
            visible = state.visible && !panelOpen,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 },
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 48.dp, bottom = 20.dp),
            ) {
                InfoBar(
                    state = state,
                    item = item,
                    playback = playback,
                    positionMs = displayedMs,
                    durationMs = durationMs,
                    chapterName = chapters.getOrNull(currentChapterIndex)?.name,
                    trickplayUrlFor = trickplayUrlFor,
                )
                if (state.expanded) {
                    ButtonRow(
                        state = state,
                        playback = playback,
                        settings = settings,
                        currentChapterIndex = currentChapterIndex,
                        hasQueue = queue.size > 1,
                        hasDanmaku = danmaku.isNotEmpty(),
                        danmakuEnabled = danmakuSettings.value.enabled,
                        onToggleDanmaku = {
                            danmakuSettings.update(danmakuSettings.value.copy(enabled = !danmakuSettings.value.enabled))
                        },
                        onSkipSegment = onSkipSegment,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                } else {
                    Text(
                        text = "↓  " + stringResource(R.string.theater_more_options),
                        fontSize = 14.sp,
                        color = TheaterColors.TextSecondary,
                        modifier =
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 10.dp),
                    )
                }
            }
        }

        // Side panel
        AnimatedVisibility(
            visible = panelOpen,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            // Keep the last panel while animating out
            val panel = state.panel ?: state.lastPanel
            when (panel) {
                TheaterPanel.SUBTITLES -> {
                    SubtitlesPanel(
                        settings = settings,
                        enableSubtitleDelay = enableSubtitleDelay,
                        onSelect = {
                            onPlaybackActionClick(PlaybackAction.ToggleCaptions(it))
                            state.closePanel()
                        },
                        onSearch = {
                            state.hide()
                            onPlaybackActionClick(PlaybackAction.SearchCaptions)
                        },
                        onChangeSubtitleDelay = onChangeSubtitleDelay,
                    )
                }

                TheaterPanel.AUDIO -> {
                    AudioPanel(
                        settings = settings,
                        onSelect = {
                            onPlaybackActionClick(PlaybackAction.ToggleAudio(it))
                            state.closePanel()
                        },
                    )
                }

                TheaterPanel.CHAPTERS -> {
                    ChaptersPanel(
                        chapters = chapters,
                        currentIndex = currentChapterIndex,
                        onSelect = {
                            player.seekTo(it.position.inWholeMilliseconds)
                            player.play()
                            state.hide()
                        },
                    )
                }

                TheaterPanel.QUEUE -> {
                    QueuePanel(
                        items = queue,
                        currentIndex = playback.playlistIndex,
                        onSelect = { index, selected ->
                            if (index == playback.playlistIndex) {
                                state.closePanel()
                            } else {
                                state.hide()
                                onPlayItem(selected)
                            }
                        },
                    )
                }

                TheaterPanel.DANMAKU -> {
                    DanmakuPanel(
                        settings = danmakuSettings.value,
                        onChange = danmakuSettings::update,
                    )
                }

                TheaterPanel.MORE -> {
                    MorePanel(
                        settings = settings,
                        enableVideoScale = enableVideoScale,
                        onPlaybackActionClick = onPlaybackActionClick,
                    )
                }

                null -> {}
            }
        }
    }
}

@Composable
private fun ClockAndEndTime(
    remainingMs: Long,
    speed: Float,
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1000)
        }
    }
    val endsAt = now.plusNanos((remainingMs / speed.coerceAtLeast(.1f)).toLong() * 1_000_000)
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = formatTime(context, now),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = TheaterColors.Text,
        )
        Text(
            text = stringResource(R.string.ends_at, formatTime(context, endsAt)),
            fontSize = 14.sp,
            color = TheaterColors.TextSecondary,
        )
    }
}

@Composable
private fun InfoBar(
    state: TheaterState,
    item: BaseItem?,
    playback: PlaybackState,
    positionMs: Long,
    durationMs: Long,
    chapterName: String?,
    trickplayUrlFor: (Int) -> String?,
) {
    val seeking = state.seekTargetMs != null
    val percent = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    // Title block, with the seek preview layered above the progress position
    Box(modifier = Modifier.fillMaxWidth()) {
        // Fade the title out when the seek preview would cover it
        val titleAlpha by animateFloatAsState(if (seeking && percent < .5f) 0f else 1f)
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .widthIn(max = 460.dp)
                    .padding(bottom = 14.dp)
                    .alpha(titleAlpha),
        ) {
            item?.title?.let {
                Text(
                    text = it,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = TheaterColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item?.subtitle?.takeIf { it.isNotNullOrBlank() && item.title != it }?.let {
                Text(
                    text = it,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TheaterColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SpecTags(playback)
        }

        if (seeking) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                SeekPreview(
                    playback = playback,
                    positionMs = positionMs,
                    deltaMs = positionMs - state.seekStartMs,
                    chapterName = chapterName,
                    trickplayUrlFor = trickplayUrlFor,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .offsetByPercent(percent)
                            .padding(bottom = 10.dp),
                )
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        ProgressBar(
            percent = percent,
            seeking = seeking,
            chapterPercents =
                remember(playback.currentMediaInfo.chapters, durationMs) {
                    if (durationMs <= 0) {
                        emptyList()
                    } else {
                        playback.currentMediaInfo.chapters
                            .drop(1)
                            .map { it.position.inWholeMilliseconds.toFloat() / durationMs }
                    }
                },
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTimecode(positionMs),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TheaterColors.Text,
            )
            Text(
                text = formatTimecode(durationMs),
                fontSize = 14.sp,
                color = TheaterColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun SpecTags(playback: PlaybackState) {
    val video = playback.currentMediaInfo.videoStream
    val transcoding = playback.currentPlayback?.playMethod == PlayMethod.TRANSCODE
    if (video?.is4k != true && video?.hdr != true && !transcoding) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (video?.is4k == true) SpecTag("4K")
        if (video?.hdr == true) SpecTag("HDR", borderColor = TheaterColors.Premium)
        if (transcoding) SpecTag(stringResource(R.string.transcoding), filled = TheaterColors.Transcoding)
    }
}

@Composable
private fun SeekPreview(
    playback: PlaybackState,
    positionMs: Long,
    deltaMs: Long,
    chapterName: String?,
    trickplayUrlFor: (Int) -> String?,
    modifier: Modifier = Modifier,
) {
    val trickplayInfo = playback.currentMediaInfo.trickPlayInfo
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        if (trickplayInfo != null) {
            val tilesPerImage = trickplayInfo.tileWidth * trickplayInfo.tileHeight
            val index = (positionMs / trickplayInfo.interval).toInt() / tilesPerImage
            val url = remember(index) { trickplayUrlFor(index) }
            if (url != null) {
                Box(contentAlignment = Alignment.BottomStart) {
                    SeekPreviewImage(
                        previewImageUrl = url,
                        seekProgressMs = positionMs,
                        trickPlayInfo = trickplayInfo,
                    )
                    if (chapterName.isNotNullOrBlank()) {
                        Text(
                            text = chapterName,
                            fontSize = 14.sp,
                            color = TheaterColors.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .padding(6.dp)
                                    .background(Color.Black.copy(alpha = .7f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .background(Color.Black.copy(alpha = .6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = formatTimecode(positionMs),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TheaterColors.Text,
            )
            Text(
                text = formatOffset(deltaMs),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TheaterColors.accent,
            )
        }
    }
}

@Composable
private fun ProgressBar(
    percent: Float,
    seeking: Boolean,
    chapterPercents: List<Float>,
) {
    val accent = TheaterColors.accent
    val barHeight = 6.dp
    BoxWithConstraints(
        contentAlignment = Alignment.CenterStart,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(18.dp),
    ) {
        val width = maxWidth
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(TheaterColors.Track, RoundedCornerShape(3.dp)),
        )
        Box(
            Modifier
                .fillMaxWidth(percent)
                .height(barHeight)
                .background(accent, RoundedCornerShape(3.dp)),
        )
        chapterPercents.forEach {
            Box(
                Modifier
                    .offset(x = width * it)
                    .size(width = 2.dp, height = barHeight)
                    .background(Color.Black.copy(alpha = .8f)),
            )
        }
        if (seeking) {
            Box(
                Modifier
                    .offset(x = width * percent - 8.dp)
                    .size(16.dp)
                    .background(TheaterColors.Text, CircleShape),
            )
        }
    }
}

@Composable
private fun ButtonRow(
    state: TheaterState,
    playback: PlaybackState,
    settings: PlaybackSettings,
    currentChapterIndex: Int,
    hasQueue: Boolean,
    // The danmaku button only shows when the item has danmaku
    hasDanmaku: Boolean,
    danmakuEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
    onSkipSegment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val segment = playback.currentSegment?.segment
    val chapters = playback.currentMediaInfo.chapters
    val requesters = remember { TheaterPanel.entries.associateWith { FocusRequester() } }
    val skipRequester = remember { FocusRequester() }

    // Focus the button of the panel that just closed, otherwise the first visible button
    LaunchedEffect(state.expanded, state.panel) {
        if (state.expanded && state.panel == null) {
            val target =
                state.lastPanel?.let { requesters[it] }
                    ?: if (segment != null) skipRequester else requesters[TheaterPanel.SUBTITLES]
            target?.tryRequestFocus()
        }
    }

    val subtitleValue =
        when (val index = settings.subtitleIndex) {
            TrackIndex.ONLY_FORCED -> {
                stringResource(R.string.only_forced_subtitles)
            }

            null -> {
                stringResource(R.string.none)
            }

            else -> {
                settings.subtitleStreams.firstOrNull { it.index == index }?.label
                    ?: stringResource(R.string.none)
            }
        }
    val audioValue =
        settings.audioStreams
            .firstOrNull { it.index == settings.audioIndex }
            ?.label
    val currentItem = playback.currentPlayback?.item

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        // Buttons scroll when they do not fit; focus brings the focused one into view. More stays at the end.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    // Room for the focused button's scale-up
                    .padding(vertical = 4.dp, horizontal = 4.dp),
        ) {
            if (segment != null) {
                TheaterButton(
                    onClick = onSkipSegment,
                    modifier = Modifier.focusRequester(skipRequester),
                ) {
                    TheaterIcon(TheaterIcons.Skip, size = 20.dp)
                    Text(text = stringResource(R.string.skip), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            TheaterValueButton(
                icon = TheaterIcons.Subtitles,
                label = stringResource(R.string.subtitles),
                value = subtitleValue,
                onClick = { state.openPanel(TheaterPanel.SUBTITLES) },
                modifier =
                    Modifier
                        .focusRequester(requesters.getValue(TheaterPanel.SUBTITLES)),
            )
            if (settings.audioStreams.isNotEmpty()) {
                TheaterValueButton(
                    icon = TheaterIcons.Audio,
                    label = stringResource(R.string.audio),
                    value = audioValue,
                    onClick = { state.openPanel(TheaterPanel.AUDIO) },
                    modifier =
                        Modifier
                            .focusRequester(requesters.getValue(TheaterPanel.AUDIO)),
                )
            }
            if (chapters.isNotEmpty()) {
                TheaterValueButton(
                    icon = TheaterIcons.Chapters,
                    label = stringResource(R.string.chapters),
                    value = chapters.getOrNull(currentChapterIndex)?.name ?: "#${currentChapterIndex + 1}",
                    onClick = { state.openPanel(TheaterPanel.CHAPTERS) },
                    modifier =
                        Modifier
                            .focusRequester(requesters.getValue(TheaterPanel.CHAPTERS)),
                )
            }
            if (hasQueue) {
                TheaterValueButton(
                    icon = TheaterIcons.Queue,
                    label = stringResource(R.string.queue),
                    value = currentItem?.let { it.data.seasonEpisode ?: it.name },
                    onClick = { state.openPanel(TheaterPanel.QUEUE) },
                    modifier =
                        Modifier
                            .focusRequester(requesters.getValue(TheaterPanel.QUEUE)),
                )
            }
            if (hasDanmaku) {
                TheaterValueButton(
                    icon = TheaterIcons.Danmaku,
                    label = stringResource(R.string.theater_danmaku),
                    value = stringResource(if (danmakuEnabled) R.string.theater_on else R.string.theater_off),
                    // Click toggles, the most common action; long press opens the settings
                    onClick = onToggleDanmaku,
                    onLongClick = { state.openPanel(TheaterPanel.DANMAKU) },
                    width = 128.dp,
                    modifier = Modifier.focusRequester(requesters.getValue(TheaterPanel.DANMAKU)),
                )
            }
        }
        TheaterButton(
            onClick = { state.openPanel(TheaterPanel.MORE) },
            modifier = Modifier.focusRequester(requesters.getValue(TheaterPanel.MORE)),
        ) {
            TheaterIcon(TheaterIcons.More)
            Text(text = stringResource(R.string.more), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
