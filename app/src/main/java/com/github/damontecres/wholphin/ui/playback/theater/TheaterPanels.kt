package com.github.damontecres.wholphin.ui.playback.theater

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.formatMaxBitrate
import com.github.damontecres.wholphin.ui.playback.PlaybackSettings
import com.github.damontecres.wholphin.ui.playback.isDpadLeft
import com.github.damontecres.wholphin.ui.playback.isDpadRight
import com.github.damontecres.wholphin.ui.playback.overlay.PlaybackAction
import com.github.damontecres.wholphin.ui.playback.playbackScaleOptions
import com.github.damontecres.wholphin.ui.playback.playbackSpeedOptions
import com.github.damontecres.wholphin.ui.seasonEpisode
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val SubtitleDelayStep = 100.milliseconds

/**
 * Frame of a side panel: a title and a list that opens focused on [initialIndex].
 */
@Composable
private fun PanelFrame(
    title: String,
    initialIndex: Int,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState((initialIndex - 2).coerceAtLeast(0))
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .width(TheaterPanelWidth)
                .fillMaxHeight()
                .background(TheaterColors.PanelBackground)
                .padding(start = 18.dp, end = 18.dp, top = 28.dp),
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TheaterColors.Text,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            content = content,
        )
    }
}

@Composable
private fun PanelDivider() {
    Box(
        Modifier
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(TheaterColors.Text.copy(alpha = .1f)),
    )
}

private fun Modifier.focusIf(
    condition: Boolean,
    focusRequester: FocusRequester,
) = if (condition) this.focusRequester(focusRequester) else this

@Composable
fun SubtitlesPanel(
    settings: PlaybackSettings,
    enableSubtitleDelay: Boolean,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    onChangeSubtitleDelay: (Duration) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = settings.subtitleIndex
    // Fixed items before the streams: none, only forced
    val streamOffset = 2
    val initialIndex =
        when {
            current == TrackIndex.ONLY_FORCED -> {
                1
            }

            current == null || current < 0 -> {
                0
            }

            else -> {
                settings.subtitleStreams
                    .indexOfFirst { it.index == current }
                    .let { if (it < 0) 0 else it + streamOffset }
            }
        }
    val focusRequester = remember { FocusRequester() }
    var editingDelay by remember { mutableStateOf(false) }
    BackHandler(enabled = editingDelay) { editingDelay = false }

    PanelFrame(
        title = stringResource(R.string.subtitles),
        initialIndex = initialIndex,
        focusRequester = focusRequester,
        modifier = modifier,
    ) {
        item {
            PanelItem(
                headline = stringResource(R.string.none),
                selected = initialIndex == 0,
                onClick = { onSelect(TrackIndex.DISABLED) },
                modifier = Modifier.focusIf(initialIndex == 0, focusRequester),
            )
        }
        item {
            PanelItem(
                headline = stringResource(R.string.only_forced_subtitles),
                selected = initialIndex == 1,
                onClick = { onSelect(TrackIndex.ONLY_FORCED) },
                modifier = Modifier.focusIf(initialIndex == 1, focusRequester),
            )
        }
        itemsIndexed(settings.subtitleStreams) { index, stream ->
            val listIndex = index + streamOffset
            PanelItem(
                headline = stream.label,
                supporting = stream.displayTitle.takeIf { stream.streamTitle != null },
                selected = stream.index == current,
                onClick = { onSelect(stream.index) },
                modifier = Modifier.focusIf(listIndex == initialIndex, focusRequester),
            )
        }
        if (enableSubtitleDelay || settings.hasSubtitleDownloadPermission) {
            item { PanelDivider() }
        }
        if (enableSubtitleDelay) {
            item {
                val delay = settings.subtitleDelay
                PanelItem(
                    headline = stringResource(R.string.subtitle_delay),
                    trailingText =
                        if (editingDelay) {
                            "◀ $delay ▶"
                        } else {
                            delay.toString()
                        },
                    onClick = { editingDelay = !editingDelay },
                    modifier =
                        Modifier.onPreviewKeyEvent {
                            if (!editingDelay) return@onPreviewKeyEvent false
                            when {
                                isDpadLeft(it) || isDpadRight(it) -> {
                                    if (it.type == KeyEventType.KeyDown) {
                                        val step = if (isDpadLeft(it)) -SubtitleDelayStep else SubtitleDelayStep
                                        onChangeSubtitleDelay(delay + step)
                                    }
                                    true
                                }

                                else -> {
                                    false
                                }
                            }
                        },
                )
            }
        }
        if (settings.hasSubtitleDownloadPermission) {
            item {
                PanelItem(
                    headline = stringResource(R.string.search_and_download),
                    showChevron = true,
                    onClick = onSearch,
                )
            }
        }
    }
}

@Composable
fun AudioPanel(
    settings: PlaybackSettings,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialIndex =
        settings.audioStreams
            .indexOfFirst { it.index == settings.audioIndex }
            .coerceAtLeast(0)
    val focusRequester = remember { FocusRequester() }
    PanelFrame(
        title = stringResource(R.string.audio),
        initialIndex = initialIndex,
        focusRequester = focusRequester,
        modifier = modifier,
    ) {
        itemsIndexed(settings.audioStreams) { index, stream ->
            PanelItem(
                headline = stream.label,
                supporting = stream.displayTitle.takeIf { stream.streamTitle != null },
                selected = stream.index == settings.audioIndex,
                onClick = { onSelect(stream.index) },
                modifier = Modifier.focusIf(index == initialIndex, focusRequester),
            )
        }
    }
}

@Composable
private fun Thumbnail(
    url: String?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier =
            modifier
                .size(width = 88.dp, height = 50.dp)
                .clip(shape)
                .background(TheaterColors.Track, shape),
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun ChaptersPanel(
    chapters: List<Chapter>,
    currentIndex: Int,
    onSelect: (Chapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val imageUrlService = LocalImageUrlService.current
    val thumbHeightPx = with(LocalDensity.current) { 50.dp.roundToPx() }
    val nowPlaying = stringResource(R.string.now_playing)
    PanelFrame(
        title = stringResource(R.string.chapters),
        initialIndex = currentIndex,
        focusRequester = focusRequester,
        modifier = modifier,
    ) {
        itemsIndexed(chapters) { index, chapter ->
            val url =
                remember(chapter) {
                    imageUrlService.getItemImageUrl(
                        itemId = chapter.itemId,
                        imageType = ImageType.CHAPTER,
                        tag = chapter.tag,
                        imageIndex = chapter.index,
                        fillHeight = thumbHeightPx,
                    )
                }
            PanelItem(
                headline = chapter.name ?: "#${index + 1}",
                supporting = formatTimecode(chapter.position.inWholeMilliseconds),
                badge = nowPlaying.takeIf { index == currentIndex },
                leading = { Thumbnail(url) },
                onClick = { onSelect(chapter) },
                modifier = Modifier.focusIf(index == currentIndex, focusRequester),
            )
        }
    }
}

@Composable
fun QueuePanel(
    items: List<BaseItem>,
    currentIndex: Int,
    onSelect: (Int, BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val imageUrlService = LocalImageUrlService.current
    val nowPlaying = stringResource(R.string.now_playing)
    PanelFrame(
        title = stringResource(R.string.queue),
        initialIndex = currentIndex,
        focusRequester = focusRequester,
        modifier = modifier,
    ) {
        itemsIndexed(items) { index, item ->
            val url = remember(item) { imageUrlService.getItemImageUrl(item, ImageType.PRIMARY) }
            PanelItem(
                headline = listOfNotNull(item.data.seasonEpisode, item.name).joinToString(" · "),
                supporting =
                    item.data.runTimeTicks
                        ?.ticks
                        ?.let { formatTimecode(it.inWholeMilliseconds) },
                badge = nowPlaying.takeIf { index == currentIndex },
                leading = { Thumbnail(url) },
                onClick = { onSelect(index, item) },
                modifier = Modifier.focusIf(index == currentIndex, focusRequester),
            )
        }
    }
}

private enum class MoreChoice {
    SPEED,
    BITRATE,
    SCALE,
}

/**
 * Speed, max bitrate, video scale and debug info. Choosing a row shows its options in the same panel;
 * back returns to the rows.
 */
@Composable
fun MorePanel(
    settings: PlaybackSettings,
    enableVideoScale: Boolean,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var choice by remember { mutableStateOf<MoreChoice?>(null) }
    // Back from the options returns to the rows instead of closing the panel
    BackHandler(enabled = choice != null) { choice = null }
    val scaleName = playbackScaleOptions[settings.contentScale]?.let { stringResource(it) }
    when (choice) {
        null -> {
            val focusRequester = remember { FocusRequester() }
            PanelFrame(
                title = stringResource(R.string.more),
                initialIndex = 0,
                focusRequester = focusRequester,
                modifier = modifier,
            ) {
                item {
                    PanelItem(
                        headline = stringResource(R.string.playback_speed),
                        trailingText = "${settings.playbackSpeed}×",
                        showChevron = true,
                        enabled = settings.playbackSpeedEnabled,
                        onClick = { choice = MoreChoice.SPEED },
                        // Speed is disabled with audio passthrough, then focus starts on the next row
                        modifier = Modifier.focusIf(settings.playbackSpeedEnabled, focusRequester),
                    )
                }
                item {
                    PanelItem(
                        headline = stringResource(R.string.max_bitrate),
                        trailingText = formatMaxBitrate(settings.maxBitrate),
                        showChevron = true,
                        onClick = { choice = MoreChoice.BITRATE },
                        modifier = Modifier.focusIf(!settings.playbackSpeedEnabled, focusRequester),
                    )
                }
                if (enableVideoScale) {
                    item {
                        PanelItem(
                            headline = stringResource(R.string.video_scale),
                            trailingText = scaleName,
                            showChevron = true,
                            onClick = { choice = MoreChoice.SCALE },
                        )
                    }
                }
                item { PanelDivider() }
                item {
                    PanelItem(
                        headline = stringResource(R.string.theater_debug_info),
                        trailingText = stringResource(if (settings.showDebugInfo) R.string.theater_on else R.string.theater_off),
                        onClick = { onPlaybackActionClick(PlaybackAction.ShowDebug) },
                    )
                }
            }
        }

        MoreChoice.SPEED -> {
            val options = playbackSpeedOptions.map { it.toFloat() }
            OptionsList(
                title = stringResource(R.string.playback_speed),
                options = options,
                current = settings.playbackSpeed,
                label = { "$it×" },
                onSelect = {
                    onPlaybackActionClick(PlaybackAction.PlaybackSpeed(it))
                    choice = null
                },
                modifier = modifier,
            )
        }

        MoreChoice.BITRATE -> {
            OptionsList(
                title = stringResource(R.string.max_bitrate),
                options = AppPreference.bitrateValues,
                current = settings.maxBitrate,
                label = { formatMaxBitrate(it) },
                onSelect = {
                    onPlaybackActionClick(PlaybackAction.ChangeBitrate(it))
                    choice = null
                },
                modifier = modifier,
            )
        }

        MoreChoice.SCALE -> {
            val names = playbackScaleOptions.mapValues { stringResource(it.value) }
            OptionsList(
                title = stringResource(R.string.video_scale),
                options = playbackScaleOptions.keys.toList(),
                current = settings.contentScale,
                label = { names[it].orEmpty() },
                onSelect = {
                    onPlaybackActionClick(PlaybackAction.Scale(it))
                    choice = null
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun <T> OptionsList(
    title: String,
    options: List<T>,
    current: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialIndex = options.indexOf(current).coerceAtLeast(0)
    val focusRequester = remember { FocusRequester() }
    PanelFrame(
        title = title,
        initialIndex = initialIndex,
        focusRequester = focusRequester,
        modifier = modifier,
    ) {
        itemsIndexed(options) { index, option ->
            PanelItem(
                headline = label(option),
                selected = option == current,
                onClick = { onSelect(option) },
                modifier = Modifier.focusIf(index == initialIndex, focusRequester),
            )
        }
    }
}

/** A danmaku setting row: its title, option labels, the current option and how choosing one changes the settings */
private class DanmakuOption(
    val title: String,
    val labels: List<String>,
    val currentIndex: Int,
    val select: (Int) -> DanmakuSettings,
)

@Composable
private fun <T> danmakuOption(
    settings: DanmakuSettings,
    @StringRes title: Int,
    options: List<T>,
    current: T,
    label: @Composable (T) -> String,
    apply: DanmakuSettings.(T) -> DanmakuSettings,
) = DanmakuOption(
    title = stringResource(title),
    labels = options.map { label(it) },
    currentIndex = options.indexOf(current),
    select = { settings.apply(options[it]) },
)

/**
 * Danmaku on/off and display settings. Same interaction as [MorePanel]: a row opens its options in place.
 */
@Composable
fun DanmakuPanel(
    settings: DanmakuSettings,
    onChange: (DanmakuSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val percent: @Composable (Int) -> String = { "$it%" }
    val rows =
        listOf(
            danmakuOption(settings, R.string.theater_danmaku_density, DanmakuDensities, settings.densityPercent, percent) {
                copy(densityPercent = it)
            },
            danmakuOption(settings, R.string.theater_danmaku_opacity, DanmakuOpacities, settings.opacityPercent, percent) {
                copy(opacityPercent = it)
            },
            danmakuOption(settings, R.string.theater_danmaku_area, DanmakuArea.entries, settings.area, { stringResource(it.label) }) {
                copy(area = it)
            },
            danmakuOption(settings, R.string.theater_danmaku_speed, DanmakuSpeed.entries, settings.speed, { stringResource(it.label) }) {
                copy(speed = it)
            },
            danmakuOption(settings, R.string.theater_danmaku_size, DanmakuSize.entries, settings.size, { stringResource(it.label) }) {
                copy(size = it)
            },
        )
    // Index into rows of the open options list, null for the rows themselves
    var openRow by remember { mutableStateOf<Int?>(null) }
    BackHandler(enabled = openRow != null) { openRow = null }

    val row = openRow?.let { rows[it] }
    if (row != null) {
        OptionsList(
            title = row.title,
            options = row.labels.indices.toList(),
            current = row.currentIndex,
            label = { row.labels[it] },
            onSelect = {
                onChange(row.select(it))
                openRow = null
            },
            modifier = modifier,
        )
        return
    }
    val focusRequester = remember { FocusRequester() }
    PanelFrame(
        title = stringResource(R.string.theater_danmaku),
        initialIndex = 0,
        focusRequester = focusRequester,
        modifier = modifier,
    ) {
        item {
            PanelItem(
                headline = stringResource(R.string.theater_danmaku_show),
                trailingText = stringResource(if (settings.enabled) R.string.theater_on else R.string.theater_off),
                onClick = { onChange(settings.copy(enabled = !settings.enabled)) },
                modifier = Modifier.focusRequester(focusRequester),
            )
        }
        item { PanelDivider() }
        rows.forEachIndexed { index, option ->
            item {
                PanelItem(
                    headline = option.title,
                    trailingText = option.labels.getOrNull(option.currentIndex),
                    showChevron = true,
                    enabled = settings.enabled,
                    onClick = { openRow = index },
                )
            }
        }
    }
}
