package com.github.damontecres.wholphin.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.playback.audioStreamCount
import com.github.damontecres.wholphin.ui.playback.embeddedSubtitleCount
import com.github.damontecres.wholphin.ui.playback.externalSubtitlesCount
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.util.StreamFormatting.concatWithSpace
import com.github.damontecres.wholphin.ui.util.StreamFormatting.formatAudioCodec
import com.github.damontecres.wholphin.ui.util.StreamFormatting.formatSubtitleCodec
import com.github.damontecres.wholphin.ui.util.StreamFormatting.formatVideoRange
import com.github.damontecres.wholphin.ui.util.StreamFormatting.resolutionString
import com.github.damontecres.wholphin.util.languageName
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.VideoRange

@Composable
@NonRestartableComposable
fun VideoStreamDetails(
    chosenStreams: ChosenStreams?,
    numberOfVersions: Int,
    modifier: Modifier = Modifier,
) = VideoStreamDetails(
    chosenStreams?.source,
    chosenStreams?.videoStream,
    chosenStreams?.audioStream?.stream,
    chosenStreams?.subtitleStream?.stream,
    numberOfVersions,
    modifier,
)

@Composable
fun VideoStreamDetails(
    source: MediaSourceInfo?,
    videoStream: MediaStream?,
    audioStream: MediaStream?,
    subtitleStream: MediaStream?,
    numberOfVersions: Int = 0,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        val video =
            remember(videoStream) {
                videoStream
                    ?.let {
                        val width = it.width
                        val height = it.height
                        val resName =
                            if (width != null && height != null) {
                                resolutionString(
                                    width,
                                    height,
                                    videoStream.isInterlaced,
                                )
                            } else {
                                null
                            }
                        val range =
                            formatVideoRange(
                                resources,
                                it.videoRange,
                                it.videoRangeType,
                                it.videoDoViTitle,
                            )
                        resName.concatWithSpace(range)
                    }
            }
        video?.let {
            StreamLabel(
                text = it,
                count = numberOfVersions,
                highlight = videoStream?.videoRange == VideoRange.HDR,
            )
        }
        videoStream?.codec?.uppercase()?.let {
            StreamLabel(it)
        }

        val audioCount = remember(source) { source?.audioStreamCount ?: 0 }
        val audio =
            remember(audioCount, audioStream) {
                if (audioCount == 0 || audioStream == null) {
                    resources.getString(R.string.none)
                } else {
                    listOfNotNull(
                        languageName(audioStream.language),
                        formatAudioCodec(resources, audioStream.codec, audioStream.profile),
                        audioStream.channelLayout,
                    ).joinToString(" ")
                }
            }
        val immersiveAudio =
            audioStream?.profile?.let { it.contains("Atmos", true) || it.contains("DTS:X", true) } == true
        // Nothing known about the audio (no language, codec or layout): no label rather than an empty one
        if (audio.isNotBlank()) {
            StreamLabel(
                text = audio,
                count = audioCount,
                icon = R.string.fa_volume_high,
                highlight = immersiveAudio,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }

        val subtitleCount =
            remember(source) {
                (source?.embeddedSubtitleCount ?: 0) + (source?.externalSubtitlesCount ?: 0)
            }
        var disabled by remember { mutableStateOf(false) }
        val subtitle =
            remember(subtitleCount, subtitleStream) {
                if (subtitleCount > 0 && subtitleStream == null) {
                    disabled = true
                    resources.getString(R.string.none)
                } else if (subtitleCount == 0 || subtitleStream == null) {
                    null
                } else {
                    disabled = false
                    listOfNotNull(
                        languageName(subtitleStream.language),
                        "SDH".takeIf { subtitleStream.isHearingImpaired },
                        formatSubtitleCodec(subtitleStream.codec),
                    ).joinToString(" ")
                }
            }
        subtitle?.let {
            StreamLabel(
                text = it,
                count = subtitleCount,
                icon = R.string.fa_closed_captioning,
                modifier = Modifier.widthIn(max = 160.dp),
                disabled = disabled,
            )
        }
    }
}

/** Gold for the specs worth showing off (HDR / Dolby Vision, Atmos / DTS:X), the same in every theme */
private val PremiumColor = Color(0xFFE5B45A)

@Composable
fun StreamLabel(
    text: String,
    modifier: Modifier = Modifier,
    @StringRes icon: Int? = null,
    count: Int = 0,
    disabled: Boolean = false,
    highlight: Boolean = false,
) {
    // Fixed height: icon and CJK glyphs have taller line heights and made some labels bigger than others
    val shape = RoundedCornerShape(6.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier =
            modifier
                .heightIn(min = 24.dp)
                .background(
                    if (highlight) PremiumColor.copy(alpha = .14f) else MaterialTheme.colorScheme.onSurface.copy(alpha = .08f),
                    shape,
                ).border(
                    1.dp,
                    if (highlight) PremiumColor.copy(alpha = .8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = .16f),
                    shape,
                ).padding(horizontal = 8.dp),
    ) {
        ProvideTextStyle(
            TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        ) {
            if (icon != null) {
                Text(
                    text = stringResource(icon),
                    fontFamily = FontAwesome,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f),
                    modifier = Modifier,
                )
            }
            val string =
                remember(text, disabled, count) {
                    val countToUse = if (disabled) count else count - 1
                    if (countToUse > 0) {
                        "$text (+$countToUse)"
                    } else {
                        text
                    }
                }
            Text(
                text = string,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier,
            )
        }
    }
}

@PreviewTvSpec
@Composable
private fun StreamLabelPreview() {
    WholphinTheme(appThemeColors = AppThemeColors.PURPLE) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(8.dp),
            ) {
                StreamLabel("1080p")
                StreamLabel("HDR")
                StreamLabel("H264")
                StreamLabel("AC3 5.1", icon = R.string.fa_volume_high, count = 2)

                StreamLabel("PGS", count = 1)
                StreamLabel("PGS", count = 1, disabled = true)
                StreamLabel("PGS", count = 2)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(8.dp)
                        .fillMaxWidth(.7f),
            ) {
                StreamLabel("1080p")
                StreamLabel("HDR")
                StreamLabel("English Dolby Atmos 5.1", icon = R.string.fa_volume_high, count = 2)
                StreamLabel("English SDH SRT", icon = R.string.fa_closed_captioning, count = 35)
            }
        }
    }
}
