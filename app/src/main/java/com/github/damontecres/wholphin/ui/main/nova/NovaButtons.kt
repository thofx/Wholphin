package com.github.damontecres.wholphin.ui.main.nova

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.playback.theater.TheaterColors

/** Height of every action button; icon only buttons are circles of this size */
val NovaButtonHeight = 44.dp

/** Size icons are drawn at inside an action button */
val NovaButtonIconSize = 20.dp

/**
 * The one action button: a circle with the icon centered when the label is hidden, a pill with the icon, a gap and
 * the label otherwise. Used by the spotlight, the person page and (through upstream's expandable buttons) the
 * details, series, collection and library actions, so they all look the same.
 */
@Composable
fun NovaActionButton(
    label: String,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    icon: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = TheaterColors.Glass,
                contentColor = TheaterColors.Text,
                focusedContainerColor = TheaterColors.Focused,
                focusedContentColor = TheaterColors.OnFocused,
                disabledContainerColor = TheaterColors.Glass.copy(alpha = .06f),
                disabledContentColor = TheaterColors.Text.copy(alpha = .35f),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = modifier.height(NovaButtonHeight).widthIn(min = NovaButtonHeight),
    ) {
        // Circle: the icon centered in it; pill: more room on the ends, like the spotlight's button
        val circlePadding = (NovaButtonHeight - NovaButtonIconSize) / 2
        val startPadding by animateDpAsState(if (showLabel) 18.dp else circlePadding, label = "button_padding")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .height(NovaButtonHeight)
                    .padding(start = startPadding, end = circlePadding),
        ) {
            Box(Modifier.size(NovaButtonIconSize), contentAlignment = Alignment.Center, content = icon)
            AnimatedVisibility(
                visible = showLabel,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 18.dp - circlePadding),
                    )
                }
            }
        }
    }
}
