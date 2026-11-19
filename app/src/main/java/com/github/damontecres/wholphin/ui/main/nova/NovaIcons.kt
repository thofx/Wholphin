package com.github.damontecres.wholphin.ui.main.nova

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.vector.ImageVector
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.playback.theater.TheaterIcons

/**
 * The fork's line icons in place of upstream's FontAwesome and Material glyphs on the shared action buttons
 * (details, library toolbar…), so they share one weight. Glyphs without a line icon keep upstream's.
 */
object NovaIcons {
    fun forFontAwesome(
        @StringRes iconStringRes: Int,
        filled: Boolean = false,
    ): ImageVector? =
        if (!NOVA_HOME_ENABLED) {
            null
        } else {
            when (iconStringRes) {
                R.string.fa_eye -> TheaterIcons.Eye
                R.string.fa_eye_slash -> TheaterIcons.EyeOff
                R.string.fa_heart -> if (filled) TheaterIcons.HeartFilled else TheaterIcons.Heart
                R.string.fa_film -> TheaterIcons.Film
                R.string.fa_file_video -> TheaterIcons.Versions
                R.string.fa_shuffle -> TheaterIcons.Shuffle
                R.string.fa_dice -> TheaterIcons.Dice
                R.string.fa_sliders -> TheaterIcons.Sliders
                R.string.fa_filter -> TheaterIcons.Filter
                else -> null
            }
        }

    fun forMaterial(icon: ImageVector): ImageVector =
        if (!NOVA_HOME_ENABLED) {
            icon
        } else {
            when (icon) {
                Icons.Default.PlayArrow -> TheaterIcons.Play
                Icons.Default.MoreVert -> TheaterIcons.MoreVert
                Icons.Default.Delete -> TheaterIcons.Trash
                Icons.Default.Refresh -> TheaterIcons.Restart
                else -> icon
            }
        }

    /** The main action of a button group keeps its label; the others show it when focused */
    fun isPrimaryAction(
        @StringRes title: Int,
    ): Boolean = NOVA_HOME_ENABLED && (title == R.string.play || title == R.string.resume)
}
