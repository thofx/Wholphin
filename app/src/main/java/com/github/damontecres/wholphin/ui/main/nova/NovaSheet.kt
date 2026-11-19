package com.github.damontecres.wholphin.ui.main.nova

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.damontecres.wholphin.ui.playback.theater.TheaterColors
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.launch

/**
 * A full height panel sliding in from the end, scrolled with up / down: long reading content (an item's overview and
 * media info) in the same shape as the player's side panels, instead of a small centered dialog.
 */
@Composable
fun NovaSheet(
    onDismissRequest: () -> Unit,
    width: Dp = 560.dp,
    itemSpacing: Dp = 12.dp,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            AnimatedVisibility(
                visibleState = visible,
                enter = slideInHorizontally { it / 3 } + fadeIn(),
            ) {
                LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing),
                    content = content,
                    modifier =
                        Modifier
                            .width(width)
                            .fillMaxHeight()
                            .background(TheaterColors.PanelBackground)
                            // Up / down move focus through the entries; the list follows it
                            .focusRequester(focusRequester),
                )
            }
        }
    }
}
