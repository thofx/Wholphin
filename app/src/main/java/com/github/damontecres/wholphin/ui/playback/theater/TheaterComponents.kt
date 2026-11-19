package com.github.damontecres.wholphin.ui.playback.theater

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.playback.SimpleMediaStream

/** Colors of the theater UI. The accent follows the app theme, the rest is fixed for readability over video. */
object TheaterColors {
    val Text = Color(0xFFF2F3F5)
    val TextSecondary = Color(0xFFC3C8D0)
    val Dark = Color(0xFF0B0D10)
    val PanelBackground = Color(0xF20E1014)
    val Track = Color(0x38FFFFFF)

    /** Fixed status color for the transcoding tag, independent of the theme */
    val Transcoding = Color(0xFFF5B544)

    /** Premium specs (HDR), the same gold as the details page's stream labels */
    val Premium = Color(0xFFE5B45A)

    /** Theme accent, same color the upstream seek bar uses */
    val accent: Color
        @Composable get() = MaterialTheme.colorScheme.border

    /** One button style everywhere: focused is the theme's light color with dark content, like upstream buttons */
    val Focused: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface
    val OnFocused: Color
        @Composable get() = MaterialTheme.colorScheme.inverseOnSurface

    /** Unfocused buttons: a light glass that reads on pictures and flat backgrounds alike */
    val Glass: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = .14f)
}

/** Height shared by every button in the button row, so buttons with one or two lines line up */
private val ButtonHeight = 56.dp

/** Width of the buttons that show a current value, so they line up regardless of the value's length */
val ValueButtonWidth = 168.dp

@Composable
fun TheaterIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 22.dp,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/**
 * A focusable button in the button row: inverted (light background, dark text) when focused.
 */
@Composable
fun TheaterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.height(ButtonHeight),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = TheaterColors.Glass,
                contentColor = TheaterColors.Text,
                focusedContainerColor = TheaterColors.Focused,
                focusedContentColor = TheaterColors.OnFocused,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp),
            content = content,
        )
    }
}

/** A button-row button with an icon, a label and its current value */
@Composable
fun TheaterValueButton(
    icon: ImageVector,
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = ValueButtonWidth,
    onLongClick: (() -> Unit)? = null,
) {
    TheaterButton(onClick = onClick, onLongClick = onLongClick, modifier = modifier.width(width)) {
        TheaterIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = LocalContentColor.current.copy(alpha = .72f),
                maxLines = 1,
            )
            if (value != null) {
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 2.dp),
                )
            }
        }
    }
}

/**
 * A row in a side panel.
 *
 * @param selected marks the item in use (accent check), independent of focus
 * @param trailingText current value shown at the end, e.g. for settings rows
 */
@Composable
fun PanelItem(
    headline: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    selected: Boolean = false,
    trailingText: String? = null,
    showChevron: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    badge: String? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val accent = TheaterColors.accent
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = TheaterColors.Text,
                focusedContainerColor = TheaterColors.Focused,
                focusedContentColor = TheaterColors.OnFocused,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = TheaterColors.TextSecondary.copy(alpha = .5f),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .heightIn(min = 46.dp)
                    .padding(PaddingValues(horizontal = 14.dp, vertical = 8.dp)),
        ) {
            leading?.invoke()
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = headline,
                    fontSize = 16.sp,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Second line: supporting text, then the badge, so the badge never squeezes the headline
                if (supporting != null || badge != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (supporting != null) {
                            Text(
                                text = supporting,
                                fontSize = 14.sp,
                                color = LocalContentColor.current.copy(alpha = .7f),
                                maxLines = 1,
                            )
                        }
                        if (badge != null) {
                            Text(
                                text = badge,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (focused) TheaterColors.Dark else accent,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            if (trailingText != null) {
                Text(
                    text = trailingText,
                    fontSize = 15.sp,
                    color = LocalContentColor.current.copy(alpha = .75f),
                    maxLines = 1,
                )
            }
            if (selected) {
                TheaterIcon(
                    TheaterIcons.Check,
                    tint = if (focused) TheaterColors.Dark else accent,
                    size = 20.dp,
                )
            }
            if (showChevron) {
                TheaterIcon(TheaterIcons.Chevron, size = 18.dp)
            }
        }
    }
}

/** A small outlined spec tag, e.g. 4K or HDR */
@Composable
fun SpecTag(
    text: String,
    modifier: Modifier = Modifier,
    filled: Color? = null,
    borderColor: Color = TheaterColors.Text.copy(alpha = .5f),
) {
    val shape = RoundedCornerShape(4.dp)
    // Fixed height: CJK text (e.g. the transcoding tag) has a taller line height and made its tag bigger
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .height(22.dp)
                .then(
                    if (filled != null) {
                        Modifier.background(filled, shape)
                    } else {
                        Modifier.border(1.dp, borderColor, shape)
                    },
                ).padding(horizontal = 6.dp),
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (filled != null) TheaterColors.Dark else TheaterColors.Text,
        )
    }
}

/** Name shown for an audio/subtitle stream: its own title, else the generated display title */
val SimpleMediaStream.label: String get() = streamTitle ?: displayTitle

/** Format milliseconds as h:mm:ss or m:ss */
fun formatTimecode(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Format a signed offset, e.g. +1:30 or -0:10 */
fun formatOffset(ms: Long): String = (if (ms < 0) "-" else "+") + formatTimecode(kotlin.math.abs(ms))
