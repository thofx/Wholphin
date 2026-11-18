package com.github.damontecres.wholphin.ui.playback.theater

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.github.damontecres.wholphin.ui.playback.HOLD_TO_SEEK_REPEAT_START_COUNT
import com.github.damontecres.wholphin.ui.playback.calculateSeekAccelerationMultiplier
import com.github.damontecres.wholphin.ui.playback.isDown
import com.github.damontecres.wholphin.ui.playback.isEnterKey
import com.github.damontecres.wholphin.ui.playback.isMedia
import com.github.damontecres.wholphin.ui.playback.isSkipBack
import com.github.damontecres.wholphin.ui.playback.isSkipForward
import com.github.damontecres.wholphin.ui.playback.isUp
import com.github.damontecres.wholphin.ui.seekBack
import com.github.damontecres.wholphin.ui.seekForward
import kotlin.time.Duration

/**
 * Remote key handling for the theater UI. Attach to a container that encloses the player and the overlay,
 * so keys not consumed by focused buttons/panels arrive here.
 *
 * Keys handled here are consumed on both key down & up, and acted on at key up (except hold-to-seek).
 */
@UnstableApi
class TheaterKeyHandler(
    private val player: Player,
    private val state: TheaterState,
    private val seekForward: Duration,
    private val seekBack: Duration,
    private val skipBackOnResume: Duration?,
    private val isEnabled: () -> Boolean,
    private val onInteraction: () -> Unit,
    private val onStop: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val focusPlayer: () -> Unit,
) {
    private var seekHandledByRepeat = false

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyUp) onInteraction.invoke()
        if (!isEnabled.invoke()) return false

        if (isMedia(event)) return handleMedia(event)

        // Back is handled with a BackHandler in TheaterOverlay: consuming the key event
        // does not stop the system back dispatch on newer Android versions

        // State 3: the panel handles navigation
        if (state.panel != null) return false

        // State 2: the button row handles left/right/enter
        if (state.expanded) {
            return onKeyUp(event, isUp(event)) {
                state.showInfo()
                focusPlayer.invoke()
            }
        }

        // States 0 & 1
        return when {
            isSkipBack(event) || isSkipForward(event) -> {
                handleSeek(event)
            }

            isEnterKey(event) -> {
                onKeyUp(event, true) { togglePlayPause() }
            }

            isDown(event) -> {
                onKeyUp(event, true) { state.showButtons() }
            }

            isUp(event) -> {
                onKeyUp(event, true) { state.showInfo() }
            }

            else -> {
                false
            }
        }
    }

    /** Consume [event] when [matches], invoking [action] on key up */
    private inline fun onKeyUp(
        event: KeyEvent,
        matches: Boolean,
        action: () -> Unit,
    ): Boolean {
        if (!matches) return false
        if (event.type == KeyEventType.KeyUp) action.invoke()
        return true
    }

    private fun togglePlayPause() {
        val wasPlaying = player.isPlaying
        Util.handlePlayPauseButtonAction(player)
        if (!wasPlaying) skipBackOnResume?.let { player.seekBack(it) }
    }

    private fun handleSeek(event: KeyEvent): Boolean {
        val isBack = isSkipBack(event)
        when (event.type) {
            KeyEventType.KeyDown -> {
                val repeatCount = event.nativeKeyEvent.repeatCount
                if (repeatCount == 0) {
                    seekHandledByRepeat = false
                } else if (repeatCount >= HOLD_TO_SEEK_REPEAT_START_COUNT) {
                    // Hold to seek, accelerating the longer the key is held
                    val multiplier =
                        calculateSeekAccelerationMultiplier(
                            repeatCount = repeatCount - HOLD_TO_SEEK_REPEAT_START_COUNT,
                            durationMs = player.duration.coerceAtLeast(0L),
                        )
                    seekHandledByRepeat = true
                    seek(isBack, multiplier)
                }
            }

            KeyEventType.KeyUp -> {
                if (!seekHandledByRepeat) seek(isBack, 1)
                seekHandledByRepeat = false
            }
        }
        return true
    }

    private fun seek(
        isBack: Boolean,
        multiplier: Int,
    ) {
        val delta = if (isBack) -(seekBack * multiplier) else seekForward * multiplier
        state.seekBy(player, delta.inWholeMilliseconds)
    }

    private fun handleMedia(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyUp) return false
        when (event.key) {
            // Same as upstream: seek right away and keep the play/pause state
            Key.MediaFastForward, Key.MediaSkipForward -> player.seekForward(seekForward)

            Key.MediaRewind, Key.MediaSkipBackward -> player.seekBack(seekBack)

            Key.MediaNext -> onNext.invoke()

            Key.MediaPrevious -> onPrevious.invoke()

            Key.Captions -> state.openPanel(TheaterPanel.SUBTITLES)

            Key.MediaAudioTrack -> state.openPanel(TheaterPanel.AUDIO)

            Key.MediaStop -> onStop.invoke()

            // Play/pause keys are handled by the MediaSession
            else -> return false
        }
        return true
    }
}
