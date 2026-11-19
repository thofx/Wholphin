package com.github.damontecres.wholphin.ui.data

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.studioNames
import com.github.damontecres.wholphin.ui.components.ScrollableDialog
import com.github.damontecres.wholphin.ui.components.StreamLabel
import com.github.damontecres.wholphin.ui.formatBitrate
import com.github.damontecres.wholphin.ui.formatBytes
import com.github.damontecres.wholphin.ui.formatDate
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.main.nova.NOVA_HOME_ENABLED
import com.github.damontecres.wholphin.ui.main.nova.NovaInfoSection
import com.github.damontecres.wholphin.ui.main.nova.NovaSheet
import com.github.damontecres.wholphin.ui.main.nova.NovaStreamCard
import com.github.damontecres.wholphin.ui.main.nova.audioCard
import com.github.damontecres.wholphin.ui.main.nova.subtitleCard
import com.github.damontecres.wholphin.ui.main.nova.videoHeadline
import com.github.damontecres.wholphin.ui.util.StreamFormatting.formatAudioCodec
import com.github.damontecres.wholphin.ui.util.StreamFormatting.formatSubtitleCodec
import com.github.damontecres.wholphin.util.languageName
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRange
import org.jellyfin.sdk.model.api.VideoRangeType
import org.jellyfin.sdk.model.extensions.ticks
import java.time.LocalDateTime
import java.util.Locale

data class ItemDetailsDialogInfo(
    val title: String,
    val overview: String?,
    val genres: List<String>,
    val files: List<MediaSourceInfo>,
    val studios: List<String> = emptyList(),
    val lastPlayed: LocalDateTime? = null,
) {
    constructor(item: BaseItem) : this(
        title = item.name ?: "",
        overview = item.data.overview,
        genres = item.data.genres.orEmpty(),
        files = item.data.mediaSources.orEmpty(),
        studios = item.studioNames,
        lastPlayed = item.data.userData?.lastPlayedDate,
    )
}

/**
 * Dialog showing metadata about an item
 */
@Composable
fun ItemDetailsDialog(
    info: ItemDetailsDialogInfo,
    showFilePath: Boolean,
    onDismissRequest: () -> Unit,
) {
    val resources = LocalResources.current
    // Extract stringResource calls outside of ScrollableDialog's non-composable lambda
    val pathLabel = stringResource(R.string.path)
    val fileSizeLabel = stringResource(R.string.file_size)
    val videoLabel = stringResource(R.string.video)
    val audioLabel = stringResource(R.string.audio)
    val subtitleLabel = stringResource(R.string.subtitle)
    val bitrateLabel = stringResource(R.string.bitrate)
    val unknown = stringResource(R.string.unknown)
    val runtimeLabel = stringResource(R.string.runtime_sort)
    val lastPlayedLabel = stringResource(R.string.last_played)

    // Nova: a side panel with a readable overview; upstream's centered dialog otherwise
    val container: @Composable (LazyListScope.() -> Unit) -> Unit =
        if (NOVA_HOME_ENABLED) {
            { content -> NovaSheet(onDismissRequest = onDismissRequest, content = content) }
        } else {
            { content ->
                ScrollableDialog(
                    onDismissRequest = onDismissRequest,
                    width = 680.dp,
                    maxHeight = 440.dp,
                    itemSpacing = 8.dp,
                    content = content,
                )
            }
        }
    container {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(if (NOVA_HOME_ENABLED) 18.dp else 16.dp)) {
                Text(
                    text = info.title,
                    style = if (NOVA_HOME_ENABLED) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                )
                if (NOVA_HOME_ENABLED) {
                    // Studios and genres as tags
                    val tags = info.studios + info.genres
                    if (tags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            tags.forEach { StreamLabel(it) }
                        }
                    }
                } else {
                    if (info.studios.isNotEmpty()) {
                        Text(
                            text = info.studios.joinToString(", "),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (info.genres.isNotEmpty()) {
                        Text(
                            text = info.genres.joinToString(", "),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
                if (info.overview.isNotNullOrBlank()) {
                    Text(
                        text = info.overview,
                        style =
                            if (NOVA_HOME_ENABLED) {
                                MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp)
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                    )
                }
                val lastPlayed =
                    remember(info.lastPlayed) { info.lastPlayed?.let { formatDate(it) } }
                if (lastPlayed != null) {
                    Text(
                        text = "$lastPlayedLabel: $lastPlayed",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Show detailed media information for the selected source (first one if multiple)
        info.files.forEachIndexed { index, source ->
            source.mediaStreams?.letNotEmpty { mediaStreams ->
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                }

                // General file information
                item {
                    val containerLabel = stringResource(R.string.container)
                    MediaInfoSection(
                        title =
                            titleIndex(
                                stringResource(R.string.general),
                                index,
                                info.files.size,
                            ),
                        items =
                            buildList {
                                source.container?.let { add(containerLabel to it) }
                                if (showFilePath) {
                                    source.path?.let { add(pathLabel to it) }
                                    add("ID" to (source.id ?: unknown))
                                }
                                source.size?.let {
                                    add(fileSizeLabel to formatBytes(it))
                                }
                                source.bitrate?.let {
                                    add(
                                        bitrateLabel to formatBitrate(it),
                                    )
                                }
                                source.runTimeTicks?.let {
                                    add(runtimeLabel to resources.formatDuration(it.ticks))
                                }
                            },
                    )
                }

                val videoStreams = mediaStreams.filter { it.type == MediaStreamType.VIDEO }
                val audioStreams = mediaStreams.filter { it.type == MediaStreamType.AUDIO }
                val subtitleStreams = mediaStreams.filter { it.type == MediaStreamType.SUBTITLE }
                if (NOVA_HOME_ENABLED) {
                    // Nova: video as a summary line and a two column grid, one card per audio / subtitle track
                    itemsIndexed(videoStreams) { index, stream ->
                        NovaInfoSection(
                            title = titleIndex(videoLabel, index, videoStreams.size),
                            headline = remember { videoHeadline(stream) },
                            pairs =
                                remember {
                                    buildVideoStreamInfo(resources, stream) + buildVideoStreamInfoAdditional(resources, stream)
                                },
                        )
                    }
                    listOf(audioLabel to audioStreams, subtitleLabel to subtitleStreams).forEach { (label, streams) ->
                        if (streams.isNotEmpty()) {
                            item {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            itemsIndexed(streams) { index, stream ->
                                val (primary, flags, secondary) =
                                    remember {
                                        if (label == audioLabel) audioCard(resources, stream) else subtitleCard(stream)
                                    }
                                NovaStreamCard(title = "${index + 1}", primary = primary, flags = flags, secondary = secondary)
                            }
                        }
                    }
                } else {
                    // Video streams
                    itemsIndexed(videoStreams) { index, stream ->
                        MediaInfoSection(
                            title = titleIndex(videoLabel, index, videoStreams.size),
                            items = remember { buildVideoStreamInfo(resources, stream) },
                            additional = remember { buildVideoStreamInfoAdditional(resources, stream) },
                        )
                    }

                    // Audio streams - display multiple per row
                    itemsIndexed(audioStreams.chunked(3)) { groupIndex, streamGroup ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            streamGroup.forEachIndexed { index, stream ->
                                MediaInfoSection(
                                    title =
                                        titleIndex(
                                            audioLabel,
                                            groupIndex * 3 + index,
                                            audioStreams.size,
                                        ),
                                    items = buildAudioStreamInfo(resources, stream),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // Fill remaining space if less than 3 items
                            repeat(3 - streamGroup.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    // Subtitle streams - display multiple per row
                    itemsIndexed(subtitleStreams.chunked(3)) { groupIndex, streamGroup ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            streamGroup.forEachIndexed { index, stream ->
                                MediaInfoSection(
                                    title =
                                        titleIndex(
                                            subtitleLabel,
                                            groupIndex * 3 + index,
                                            subtitleStreams.size,
                                        ),
                                    items = buildSubtitleStreamInfo(resources, stream, showFilePath),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // Fill remaining space if less than 3 items
                            repeat(3 - streamGroup.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (index != info.files.lastIndex) {
                    item {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaInfoSection(
    title: String,
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    additional: List<Pair<String, String>> = listOf(),
) {
    if (NOVA_HOME_ENABLED) {
        NovaInfoSection(title = title, pairs = items + additional, modifier = modifier)
        return
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.padding(vertical = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                items.forEach { (label, value) ->
                    Row(
                        modifier = Modifier,
                    ) {
                        Text(
                            text = "$label: ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (additional.isNotEmpty()) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    additional.forEach { (label, value) ->
                        Row(
                            modifier = Modifier,
                        ) {
                            Text(
                                text = "$label: ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

fun titleIndex(
    title: String,
    index: Int,
    total: Int,
) = if (total > 1) {
    "$title (${index + 1})"
} else {
    title
}

private fun buildVideoStreamInfo(
    resources: Resources,
    stream: MediaStream,
): List<Pair<String, String>> =
    buildList {
        val yesStr = resources.getString(R.string.yes)
        val noStr = resources.getString(R.string.no)

        val titleLabel = resources.getString(R.string.title)
        val codecLabel = resources.getString(R.string.codec)
        val resolutionLabel = resources.getString(R.string.resolution)
        val aspectRatioLabel = resources.getString(R.string.aspect_ratio)
        val framerateLabel = resources.getString(R.string.framerate)
        val bitrateLabel = resources.getString(R.string.bitrate)
        val profileLabel = resources.getString(R.string.profile)
        val levelLabel = resources.getString(R.string.level)
        val interlacedLabel = resources.getString(R.string.interlaced)
        val videoRangeLabel = resources.getString(R.string.video_range)
        val sdrStr = resources.getString(R.string.sdr)
        val hdrStr = resources.getString(R.string.hdr)

        stream.title?.let { add(titleLabel to it) }
        stream.codec?.let { add(codecLabel to it.uppercase()) }
        if (stream.width != null && stream.height != null) {
            add(resolutionLabel to "${stream.width}x${stream.height}")
        }
        if (stream.width != null && stream.height != null) {
            val aspectRatio = calculateAspectRatio(stream.width!!, stream.height!!)
            add(aspectRatioLabel to aspectRatio)
        }
        stream.bitRate?.let { add(bitrateLabel to formatBitrate(it)) }
        stream.averageFrameRate?.let {
            add(framerateLabel to String.format(Locale.getDefault(), "%.3f", it))
        }

        stream.videoRange.let {
            val rangeStr =
                when (it) {
                    VideoRange.SDR -> sdrStr
                    VideoRange.HDR -> hdrStr
                    VideoRange.UNKNOWN -> null
                }
            rangeStr?.let { add(videoRangeLabel to it) }
        }
        stream.profile?.let { add(profileLabel to it) }
        stream.level?.let { add(levelLabel to it.toString()) }
        stream.isInterlaced.let { add(interlacedLabel to if (it) yesStr else noStr) }
    }

private fun buildVideoStreamInfoAdditional(
    resources: Resources,
    stream: MediaStream,
): List<Pair<String, String>> =
    buildList {
        val yesStr = resources.getString(R.string.yes)
        val noStr = resources.getString(R.string.no)

        val avcLabel = resources.getString(R.string.avc)
        val anamorphicLabel = resources.getString(R.string.anamorphic)
        val bitDepthLabel = resources.getString(R.string.bit_depth)

        val videoRangeTypeLabel = resources.getString(R.string.video_range_type)
        val colorSpaceLabel = resources.getString(R.string.color_space)
        val colorTransferLabel = resources.getString(R.string.color_transfer)
        val colorPrimariesLabel = resources.getString(R.string.color_primaries)
        val pixelFormatLabel = resources.getString(R.string.pixel_format)
        val refFramesLabel = resources.getString(R.string.ref_frames)
        val nalLabel = resources.getString(R.string.nal)
        val dolbyVisionLabel = resources.getString(R.string.dolby_vision)

        val sdrStr = resources.getString(R.string.sdr)
        val hdr10Str = resources.getString(R.string.hdr10)
        val hdr10PlusStr = resources.getString(R.string.hdr10_plus)
        val hlgStr = resources.getString(R.string.hlg)
        val bitUnit = resources.getString(R.string.bit_unit)

        stream.isAvc?.let { add(avcLabel to if (it) yesStr else noStr) }
        stream.isAnamorphic?.let { add(anamorphicLabel to if (it) yesStr else noStr) }
        stream.bitDepth?.let { add(bitDepthLabel to "$it $bitUnit") }
        stream.videoRangeType?.let {
            val rangeTypeStr =
                when (it) {
                    VideoRangeType.SDR -> sdrStr

                    VideoRangeType.HDR10 -> hdr10Str

                    VideoRangeType.HDR10_PLUS -> hdr10PlusStr

                    VideoRangeType.HLG -> hlgStr

                    VideoRangeType.DOVI,
                    VideoRangeType.DOVI_WITH_HDR10,
                    VideoRangeType.DOVI_WITH_HLG,
                    VideoRangeType.DOVI_WITH_SDR,
                    -> resources.getString(R.string.dolby_vision)

                    VideoRangeType.UNKNOWN -> null

                    else -> null
                }
            rangeTypeStr?.let { add(videoRangeTypeLabel to it) }
        }
        stream.colorSpace?.let { add(colorSpaceLabel to it) }
        stream.colorTransfer?.let { add(colorTransferLabel to it) }
        stream.colorPrimaries?.let { add(colorPrimariesLabel to it) }
        stream.pixelFormat?.let { add(pixelFormatLabel to it) }
        stream.refFrames?.let { add(refFramesLabel to it.toString()) }
        stream.nalLengthSize?.let { add(nalLabel to it) }
        stream.videoDoViTitle?.let { add(dolbyVisionLabel to it) }
    }

private fun buildAudioStreamInfo(
    resources: android.content.res.Resources,
    stream: MediaStream,
): List<Pair<String, String>> =
    buildList {
        val titleLabel = resources.getString(R.string.title)
        val languageLabel = resources.getString(R.string.language)
        val codecLabel = resources.getString(R.string.codec)
        val layoutLabel = resources.getString(R.string.layout)
        val channelsLabel = resources.getString(R.string.channels)
        val bitrateLabel = resources.getString(R.string.bitrate)
        val sampleRateLabel = resources.getString(R.string.sample_rate)
        val defaultLabel = resources.getString(R.string.default_track)
        val profileLabel = resources.getString(R.string.profile)
        val yesStr = resources.getString(R.string.yes)
        val noStr = resources.getString(R.string.no)
        val sampleRateUnit = resources.getString(R.string.sample_rate_unit)

        stream.title?.let { add(titleLabel to it) }
        languageName(stream.language)?.let { add(languageLabel to it) }
        stream.codec?.let {
            val formattedCodec = formatAudioCodec(resources, it, stream.profile) + " ($it)"
            add(codecLabel to formattedCodec)
        }
        stream.channelLayout?.let { add(layoutLabel to it) }
        stream.channels?.let { add(channelsLabel to it.toString()) }
        stream.profile?.let { add(profileLabel to it) }
        stream.bitRate?.let { add(bitrateLabel to formatBitrate(it)) }
        stream.sampleRate?.let { add(sampleRateLabel to "$it $sampleRateUnit") }
        stream.isDefault?.let { add(defaultLabel to if (it) yesStr else noStr) }
    }

private fun buildSubtitleStreamInfo(
    resources: Resources,
    stream: MediaStream,
    showPath: Boolean,
): List<Pair<String, String>> =
    buildList {
        val titleLabel = resources.getString(R.string.title)
        val languageLabel = resources.getString(R.string.language)
        val codecLabel = resources.getString(R.string.codec)
        val defaultLabel = resources.getString(R.string.default_track)
        val forcedLabel = resources.getString(R.string.forced_track)
        val externalLabel = resources.getString(R.string.external_track)
        val yesStr = resources.getString(R.string.yes)
        val noStr = resources.getString(R.string.no)
        val pathLabel = resources.getString(R.string.path)

        stream.title?.let { add(titleLabel to it) }
        languageName(stream.language)?.let { add(languageLabel to it) }
        stream.codec?.let {
            val formattedCodec = formatSubtitleCodec(it) + " ($it)"
            add(codecLabel to formattedCodec)
        }
        stream.isDefault?.let { add(defaultLabel to if (it) yesStr else noStr) }
        stream.isForced?.let { add(forcedLabel to if (it) yesStr else noStr) }
        stream.isExternal?.let { add(externalLabel to if (it) yesStr else noStr) }
        stream.isHearingImpaired?.let {
            add((stream.localizedHearingImpaired ?: "SDH") to if (it) yesStr else noStr)
        }
        if (showPath) {
            stream.path?.let { add(pathLabel to it) }
        }
    }

private fun calculateAspectRatio(
    width: Int,
    height: Int,
): String {
    val gcd = gcd(width, height)
    val w = width / gcd
    val h = height / gcd
    return "$w:$h"
}

private fun gcd(
    a: Int,
    b: Int,
): Int = if (b == 0) a else gcd(b, a % b)
