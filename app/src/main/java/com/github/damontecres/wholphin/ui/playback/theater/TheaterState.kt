package com.github.damontecres.wholphin.ui.playback.theater

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.github.damontecres.wholphin.data.model.ServerUserConfig
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.PlaybackSettings
import com.github.damontecres.wholphin.ui.playback.PlaybackState
import kotlin.time.Duration

/**
 * Switch between the theater playback UI and the upstream one.
 *
 * Kept as a compile-time constant (not a preference) so the fork touches as few upstream files as possible.
 */
const val THEATER_UI_ENABLED = true

/** Width of the side panel */
val TheaterPanelWidth: Dp = 330.dp

/** Debounce before a d-pad seek is applied */
internal const val THEATER_SEEK_DEBOUNCE_MS = 750L

enum class TheaterPanel {
    SUBTITLES,
    AUDIO,
    CHAPTERS,
    QUEUE,
    DANMAKU,
    MORE,
}

/**
 * UI state of the theater overlay.
 *
 * - State 0: [visible] is false
 * - State 1: [visible], not [expanded]
 * - State 2: [visible] and [expanded] (button row)
 * - State 3: [panel] is not null (side panel)
 *
 * Visibility and auto-hide reuse [ControllerViewState], so the existing controller timeout preference applies.
 */
@Stable
class TheaterState(
    private val controller: ControllerViewState,
) {
    val visible: Boolean get() = controller.controlsVisible

    var expanded by mutableStateOf(false)
        private set

    var panel by mutableStateOf<TheaterPanel?>(null)
        private set

    /** The panel that was last opened, used to restore focus to its button */
    var lastPanel by mutableStateOf<TheaterPanel?>(null)
        private set

    /** Pending d-pad seek target, null when not seeking */
    var seekTargetMs by mutableStateOf<Long?>(null)
        private set

    /** Position when the current d-pad seek started */
    var seekStartMs by mutableLongStateOf(0L)
        private set

    /** Whether the floating skip-segment button may show (states 0 & 1) */
    val showsFloatingSkip: Boolean get() = !expanded && panel == null

    fun showInfo() {
        expanded = false
        panel = null
        controller.showControls()
    }

    fun showButtons() {
        expanded = true
        panel = null
        // Entering from state 0/1 focuses the first button, not the last opened panel's
        lastPanel = null
        controller.showControls(Long.MAX_VALUE)
    }

    fun openPanel(newPanel: TheaterPanel) {
        expanded = true
        panel = newPanel
        lastPanel = newPanel
        controller.showControls(Long.MAX_VALUE)
    }

    fun closePanel() {
        panel = null
        controller.showControls(Long.MAX_VALUE)
    }

    fun hide() {
        expanded = false
        panel = null
        controller.hideControls()
    }

    /** Called when the controller hid itself after the timeout */
    fun onHidden() {
        expanded = false
        panel = null
    }

    /** Restart the auto-hide timer, or keep the overlay visible when it should not auto-hide */
    fun pulse(isPlaying: Boolean) {
        if (!visible) return
        if (!isPlaying || expanded || panel != null || seekTargetMs != null) {
            controller.pulseControls(Long.MAX_VALUE)
        } else {
            controller.pulseControls()
        }
    }

    fun seekBy(
        player: Player,
        deltaMs: Long,
    ) {
        val current = seekTargetMs
        if (current == null) seekStartMs = player.currentPosition
        val target = ((current ?: seekStartMs) + deltaMs).coerceAtLeast(0L)
        // Duration is C.TIME_UNSET (negative) until known, then only the lower bound applies
        val duration = player.duration
        seekTargetMs = if (duration > 0) target.coerceAtMost(duration) else target
        if (!visible) controller.showControls()
    }

    fun clearSeek() {
        seekTargetMs = null
    }

    /** Fraction of the player height the subtitle view may use, so subtitles move above the overlay */
    val subtitleHeightFraction: Float
        get() =
            when {
                panel != null -> 1f
                expanded -> .56f
                visible -> .68f
                else -> 1f
            }

    /** Space to leave on the end side for subtitles, so they are not covered by the side panel */
    val subtitleEndPadding: Dp get() = if (panel != null) TheaterPanelWidth else 0.dp
}

/**
 * Same settings the upstream PlaybackDialog receives, built here so the page only needs one call.
 */
fun theaterPlaybackSettings(
    playback: PlaybackState,
    showDebugInfo: Boolean,
    playbackSpeed: Float,
    contentScale: ContentScale,
    userDto: ServerUserConfig?,
    playerBackend: PlayerBackend,
) = PlaybackSettings(
    showDebugInfo = showDebugInfo,
    audioIndex = playback.currentPlayback?.audioIndex,
    audioStreams = playback.currentMediaInfo.audioStreams,
    subtitleIndex = playback.currentPlayback?.subtitleIndex,
    subtitleStreams = playback.currentMediaInfo.subtitleStreams,
    playbackSpeed = playbackSpeed,
    contentScale = contentScale,
    subtitleDelay = playback.currentPlayback?.subtitleDelay ?: Duration.ZERO,
    hasSubtitleDownloadPermission = userDto?.policy?.let { it.isAdministrator || it.enableSubtitleManagement } == true,
    // Same rule as upstream: passing through audio prevents changing playback speed
    playbackSpeedEnabled = playerBackend == PlayerBackend.VLC || playback.currentPlayback?.audioDecoder != null,
    maxBitrate = playback.maxBitrate,
)
