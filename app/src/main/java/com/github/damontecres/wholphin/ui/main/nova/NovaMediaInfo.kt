package com.github.damontecres.wholphin.ui.main.nova

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.StreamLabel
import com.github.damontecres.wholphin.ui.formatBitrate
import com.github.damontecres.wholphin.ui.util.StreamFormatting.formatAudioCodec
import com.github.damontecres.wholphin.ui.util.StreamFormatting.formatSubtitleCodec
import com.github.damontecres.wholphin.util.languageName
import org.jellyfin.sdk.model.api.MediaStream

/** Values longer than this take a whole row of the grid (paths, ids) */
private const val LONG_VALUE = 22

/**
 * A media info section of the details panel: an optional one line summary, then label / value pairs in two columns
 */
@Composable
fun NovaInfoSection(
    title: String,
    pairs: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    headline: String? = null,
) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.padding(vertical = 6.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = muted)
        if (headline != null) {
            Text(text = headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        // Short values two per row, long ones on their own
        val rows = mutableListOf<List<Pair<String, String>>>()
        pairs.forEach { pair ->
            val last = rows.lastOrNull()
            if (pair.second.length > LONG_VALUE) {
                rows.add(listOf(pair))
            } else if (last != null && last.size == 1 && last[0].second.length <= LONG_VALUE) {
                rows[rows.lastIndex] = last + pair
            } else {
                rows.add(listOf(pair))
            }
        }
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    Row(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = muted,
                            modifier = Modifier.width(88.dp),
                        )
                        Text(text = value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (row.size == 1 && row[0].second.length <= LONG_VALUE) {
                    androidx.compose.foundation.layout
                        .Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** "HEVC · 3840×1608 · HDR10 · 24 fps · 3.88 Mbps" */
fun videoHeadline(stream: MediaStream): String =
    listOfNotNull(
        stream.codec?.uppercase(),
        if (stream.width != null && stream.height != null) "${stream.width}×${stream.height}" else null,
        stream.videoRangeType?.serialName ?: stream.videoRange?.serialName,
        stream.realFrameRate?.let { "%.3g fps".format(it) },
        stream.bitRate?.let(::formatBitrate),
    ).joinToString(" · ")

/** One audio or subtitle track: a summary line with its flags, then the rest in a muted line */
@Composable
fun NovaStreamCard(
    title: String,
    primary: String,
    flags: List<String>,
    secondary: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
            )
            Text(text = primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            flags.forEach { StreamLabel(it) }
        }
        if (!secondary.isNullOrBlank()) {
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
            )
        }
    }
}

private fun MediaStream.flags(): List<String> =
    listOfNotNull(
        localizedDefault.takeIf { isDefault },
        localizedForced.takeIf { isForced },
        localizedExternal.takeIf { isExternal },
        "SDH".takeIf { isHearingImpaired == true },
    )

/** Language, codec, layout on the first line; the file's title, bitrate and sample rate below */
fun audioCard(
    resources: Resources,
    stream: MediaStream,
): Triple<String, List<String>, String?> {
    val primary =
        listOfNotNull(
            stream.language?.takeIf { it.isNotBlank() && it != "und" }?.let(::languageName),
            formatAudioCodec(resources, stream.codec, stream.profile),
            stream.channelLayout,
        ).joinToString(" · ").ifBlank { stream.codec.orEmpty() }
    val secondary =
        listOfNotNull(
            stream.title,
            stream.bitRate?.let(::formatBitrate),
            stream.sampleRate?.let { "$it Hz" },
        ).joinToString(" · ")
    return Triple(primary, stream.flags(), secondary)
}

/** Language and format on the first line, the file's title below */
fun subtitleCard(stream: MediaStream): Triple<String, List<String>, String?> {
    val primary =
        listOfNotNull(
            stream.language?.takeIf { it.isNotBlank() && it != "und" }?.let(::languageName),
            formatSubtitleCodec(stream.codec),
        ).joinToString(" · ").ifBlank { stream.codec.orEmpty() }
    return Triple(primary, stream.flags(), stream.title)
}
