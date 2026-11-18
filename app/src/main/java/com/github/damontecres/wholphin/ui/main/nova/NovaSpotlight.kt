package com.github.damontecres.wholphin.ui.main.nova

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.components.rememberLogoUrl
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.main.HomePageContent
import com.github.damontecres.wholphin.ui.main.HomePageHeader
import com.github.damontecres.wholphin.ui.playback.isPlayKeyUp
import com.github.damontecres.wholphin.ui.playback.isUp
import com.github.damontecres.wholphin.ui.playback.playable
import com.github.damontecres.wholphin.ui.playback.theater.TheaterColors
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

/** How much of the first row shows under the spotlight, hinting that the page scrolls */
private val SpotlightPeek = 64.dp

/**
 * The home page in place of [HomePageContent]: a full screen spotlight (top bar above it) that collapses into the
 * upstream header and rows once a row takes focus.
 */
@Composable
fun NovaHomeContent(
    homeRows: List<HomeRowLoadingState>,
    position: RowColumn,
    onFocusPosition: (RowColumn) -> Unit,
    onClickItem: (RowColumn, BaseItem) -> Unit,
    onLongClickItem: (RowColumn, BaseItem) -> Unit,
    onClickPlay: (RowColumn, BaseItem) -> Unit,
    onUpdateBackdrop: (BaseItem) -> Unit,
    showLogo: Boolean,
    showBackdrop: Boolean,
    onClickViewMore: (RowColumn, HomeRowLoadingState.Success) -> Unit,
    modifier: Modifier = Modifier,
    loadingState: LoadingState? = null,
) {
    val topBar = LocalNovaTopBar.current
    val settings = rememberSpotlightSettings()
    val entries =
        remember(homeRows, settings) {
            if (settings.enabled) {
                buildSpotlight(
                    rows = homeRows,
                    maxEntries = settings.maxEntries,
                    maxResumed = if (settings.continueWatching) 2 else 0,
                    maxNextUp = if (settings.nextUp) 1 else 0,
                    recentlyAdded = settings.recentlyAdded,
                )
            } else {
                emptyList()
            }
        }
    val rotateMs = settings.autoRotateSeconds * 1000
    val hasSpotlight = entries.isNotEmpty()
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    // The list can shrink (refresh, removed item)
    val index = if (savedIndex in entries.indices) savedIndex else 0
    val entry = entries.getOrNull(index)

    // Whether the rows had focus last, so coming back to the page restores it
    var inRows by rememberSaveable { mutableStateOf(false) }
    var spotlightFocused by remember { mutableStateOf(false) }
    val spotlightShown = hasSpotlight && !inRows

    val spotlightFocus = remember { FocusRequester() }
    val topBarFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentPosition by rememberUpdatedState(position)
    val firstRow =
        remember(homeRows) {
            homeRows.indexOfFirst { it is HomeRowLoadingState.Success && it.items.isNotEmpty() }
        }

    fun focusSpotlight() {
        spotlightFocus.tryRequestFocus()
        scope.launch { listState.scrollToItem(0) }
    }

    LaunchedEffect(hasSpotlight) {
        if (hasSpotlight && !inRows) spotlightFocus.tryRequestFocus()
    }
    // Rotate while the spotlight has focus; flipping restarts the wait
    LaunchedEffect(index, spotlightFocused, entries.size, rotateMs) {
        if (spotlightFocused && entries.size > 1 && rotateMs > 0) {
            delay(rotateMs.toLong())
            savedIndex = (index + 1) % entries.size
        }
    }
    // The spotlight draws its own backdrop; the upstream one shows once it collapses, for the focused row item
    val focusedRowItem = (homeRows.getOrNull(position.row) as? HomeRowLoadingState.Success)?.items?.getOrNull(position.column)
    LaunchedEffect(spotlightShown, focusedRowItem?.id) {
        if (!spotlightShown) focusedRowItem?.let(onUpdateBackdrop)
    }
    // Back: rows -> spotlight -> top bar -> (not handled) leave the app
    BackHandler(enabled = inRows || spotlightFocused) {
        if (inRows && hasSpotlight) focusSpotlight() else topBarFocus.tryRequestFocus()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val spotlightHeight = maxHeight - TopBarHeight - SpotlightPeek
        val backdropAlpha by animateFloatAsState(if (spotlightShown) 1f else 0f, tween(600), label = "spotlight_backdrop")
        if (showBackdrop && entry != null && backdropAlpha > 0f) {
            SpotlightBackdrop(entry.item, Modifier.fillMaxSize().graphicsLayer { alpha = backdropAlpha })
        }
        Column(Modifier.fillMaxSize()) {
            if (topBar != null) {
                AnimatedVisibility(
                    visible = !inRows || !hasSpotlight,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    topBar(Modifier.onFocusChanged { if (it.hasFocus) inRows = false }, topBarFocus)
                }
            }
            if (entry != null) {
                val height by animateDpAsState(if (spotlightShown) spotlightHeight else 0.dp, tween(500), label = "spotlight_height")
                // Collapse by clipping: the pane keeps its full size (squeezing it breaks the focused button's border and
                // its focus), bottom aligned so it slides up and out
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clipToBounds(),
                ) {
                    SpotlightPane(
                        entries = entries,
                        index = index,
                        onIndexChange = { savedIndex = it },
                        focused = spotlightFocused,
                        rotateMs = rotateMs,
                        showLogo = showLogo,
                        focusRequester = spotlightFocus,
                        onFocusChanged = { focused ->
                            spotlightFocused = focused
                            if (focused) {
                                inRows = false
                                // Clicks in the spotlight move the page position to the item's row; going down from
                                // the spotlight should still land on the first row
                                if (firstRow >= 0 && currentPosition.row != firstRow) onFocusPosition(RowColumn(firstRow, 0))
                            }
                        },
                        // The item's own row position, so the context menu knows where it is from
                        onClick = { e ->
                            if (e.item.type.playable) onClickPlay(e.source, e.item) else onClickItem(e.source, e.item)
                        },
                        onLongClick = { e -> onLongClickItem(e.source, e.item) },
                        onPlay = { e -> if (e.item.type.playable) onClickPlay(e.source, e.item) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(Alignment.Bottom, unbounded = true)
                                .height(spotlightHeight),
                    )
                }
            }
            HomePageContent(
                homeRows = homeRows,
                position = position,
                onFocusPosition = onFocusPosition,
                onClickItem = onClickItem,
                onLongClickItem = onLongClickItem,
                onClickPlay = onClickPlay,
                showClock = false,
                onUpdateBackdrop = onUpdateBackdrop,
                showLogo = showLogo,
                showViewMore = true,
                loadingState = loadingState,
                listState = listState,
                // The spotlight takes the first focus, unless the rows had it before leaving the page
                takeFocus = !hasSpotlight || inRows,
                headerComposable = { item ->
                    if (!spotlightShown) HomePageHeader(item = item, showLogo = showLogo, modifier = HeaderUtils.modifier)
                },
                onClickViewMore = onClickViewMore,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 40.dp, end = 16.dp)
                        .onFocusChanged { if (it.hasFocus) inRows = true }
                        .onPreviewKeyEvent { event ->
                            // Up from the first row goes back to the spotlight (collapsed, so not found by focus search)
                            if (hasSpotlight && event.type == KeyEventType.KeyDown && isUp(event) &&
                                currentPosition.row == firstRow
                            ) {
                                focusSpotlight()
                                true
                            } else {
                                false
                            }
                        },
            )
        }
    }
}

/** Full screen backdrop with a slow zoom, and scrims for the text and the top bar */
@Composable
private fun SpotlightBackdrop(
    item: BaseItem,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val url = remember(item.id) { imageUrlService.getItemImageUrl(item, ImageType.BACKDROP, fillWidth = 1920) }
    val background = MaterialTheme.colorScheme.background
    Box(modifier) {
        Crossfade(targetState = url, animationSpec = tween(800), label = "spotlight_image") { imageUrl ->
            val zoom = remember { Animatable(1.08f) }
            LaunchedEffect(Unit) { zoom.animateTo(1f, tween(16_000, easing = LinearOutSlowInEasing)) }
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom.value
                            scaleY = zoom.value
                        },
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to background.copy(alpha = .9f),
                        .38f to background.copy(alpha = .55f),
                        .66f to Color.Transparent,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = .5f),
                        .18f to Color.Transparent,
                        .5f to Color.Transparent,
                        .76f to background.copy(alpha = .55f),
                        1f to background,
                    ),
                ),
        )
    }
}

@Composable
private fun SpotlightPane(
    entries: List<SpotlightEntry>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    focused: Boolean,
    rotateMs: Int,
    showLogo: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onClick: (SpotlightEntry) -> Unit,
    onLongClick: (SpotlightEntry) -> Unit,
    onPlay: (SpotlightEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = entries[index]
    // The button's click handlers may outlive a recomposition; always act on the current item
    val current by rememberUpdatedState(entry)
    // Progress of the auto advance, shown on the current thumbnail
    val progress = remember(index, focused) { Animatable(0f) }
    LaunchedEffect(index, focused, rotateMs) {
        if (focused && entries.size > 1 && rotateMs > 0) progress.animateTo(1f, tween(rotateMs, easing = LinearEasing))
    }
    Box(modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 56.dp, bottom = 8.dp)
                    .width(480.dp),
        ) {
            AnimatedContent(
                targetState = entry,
                transitionSpec = {
                    (fadeIn(tween(500)) + slideInHorizontally(tween(500)) { it / 12 }) togetherWith fadeOut(tween(200))
                },
                label = "spotlight_info",
            ) { e ->
                SpotlightInfo(e, showLogo)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PrimaryButton(
                    entry = entry,
                    modifier =
                        Modifier
                            .focusRequester(focusRequester)
                            .onFocusChanged { onFocusChanged(it.isFocused) }
                            .onPreviewKeyEvent { event ->
                                when {
                                    event.key == Key.DirectionLeft || event.key == Key.DirectionRight -> {
                                        if (event.type == KeyEventType.KeyDown && entries.size > 1) {
                                            val step = if (event.key == Key.DirectionRight) 1 else -1
                                            onIndexChange((index + step).mod(entries.size))
                                        }
                                        true
                                    }

                                    isPlayKeyUp(event) -> {
                                        onPlay(entry)
                                        true
                                    }

                                    else -> {
                                        false
                                    }
                                }
                            },
                    onClick = { onClick(current) },
                    onLongClick = { onLongClick(current) },
                )
                val percent =
                    entry.item.data.userData
                        ?.playedPercentage
                if (entry.kind == SpotlightKind.CONTINUE && percent != null) {
                    Box(
                        Modifier
                            .size(width = 110.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(TheaterColors.Track),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((percent / 100).toFloat().coerceIn(0f, 1f))
                                .background(TheaterColors.accent),
                        )
                    }
                }
            }
        }
        if (entries.size > 1) {
            Thumbnails(
                entries = entries,
                index = index,
                progress = { progress.value },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 32.dp, bottom = 8.dp),
            )
        }
    }
}

private val TextShadow = Shadow(Color.Black.copy(alpha = .6f), blurRadius = 12f)

@Composable
private fun SpotlightInfo(
    entry: SpotlightEntry,
    showLogo: Boolean,
) {
    val item = entry.item
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(width = 20.dp, height = 2.dp).background(TheaterColors.accent))
            Text(
                text = stringResource(entry.kind.label),
                color = TheaterColors.Text.copy(alpha = .85f),
                style = MaterialTheme.typography.labelLarge.copy(shadow = TextShadow),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
        }
        // Logo when there is one, else the title: the layout adapts to either
        val logoUrl = rememberLogoUrl(item)
        var logoFailed by remember(logoUrl) { mutableStateOf(false) }
        if (showLogo && logoUrl != null && !logoFailed) {
            AsyncImage(
                model = logoUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onError = { logoFailed = true },
                modifier = Modifier.heightIn(max = 90.dp).widthIn(max = 320.dp),
            )
        } else {
            Text(
                text = item.title.orEmpty(),
                color = TheaterColors.Text,
                fontSize = 40.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.displaySmall.copy(shadow = TextShadow),
            )
        }
        // The episode number is in the quick details already
        if (item.type == BaseItemKind.EPISODE) {
            item.name?.let {
                Text(
                    text = it,
                    color = TheaterColors.Text,
                    style = MaterialTheme.typography.titleLarge.copy(shadow = TextShadow),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        QuickDetails(item.ui.quickDetails, item.timeRemainingOrRuntime)
        val overview = item.data.overview
        if (overview.isNotNullOrBlank()) {
            Text(
                text = overview,
                color = TheaterColors.Text.copy(alpha = .82f),
                style = MaterialTheme.typography.bodyMedium.copy(shadow = TextShadow),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PrimaryButton(
    entry: SpotlightEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Play what can be played; a series opens its details (it has no single video to start)
    val (icon, label) =
        when {
            !entry.item.type.playable -> Icons.Default.Info to R.string.nova_home_details
            entry.kind == SpotlightKind.CONTINUE -> Icons.Default.PlayArrow to R.string.resume
            else -> Icons.Default.PlayArrow to R.string.play
        }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = novaPillColors(containerColor = Color.White.copy(alpha = .16f)),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, TheaterColors.accent), inset = (-3).dp, shape = CircleShape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text = stringResource(label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Thumbnails(
    entries: List<SpotlightEntry>,
    index: Int,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val accent = TheaterColors.accent
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier,
    ) {
        entries.forEachIndexed { i, e ->
            val current = i == index
            val width by animateDpAsState(if (current) 104.dp else 64.dp, tween(350), label = "thumb_width")
            val url = remember(e.item.id) { imageUrlService.getItemImageUrl(e.item, ImageType.BACKDROP, fillWidth = 320) }
            val shape = RoundedCornerShape(6.dp)
            Box(
                Modifier
                    .size(width = width, height = width * 9 / 16)
                    .graphicsLayer { alpha = if (current) 1f else .45f }
                    .clip(shape)
                    .then(if (current) Modifier.border(BorderStroke(2.dp, Color.White.copy(alpha = .9f)), shape) else Modifier),
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (current) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .height(3.dp)
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = progress()
                                transformOrigin = TransformOrigin(0f, .5f)
                            }.background(accent),
                    )
                }
            }
        }
    }
}
