package com.github.damontecres.wholphin.ui.main.nova

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import com.github.damontecres.wholphin.ui.formatDate
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.nav.ApplicationContentViewModel
import com.github.damontecres.wholphin.ui.playback.theater.TheaterColors
import com.github.damontecres.wholphin.ui.playback.theater.TheaterIcons
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.time.Duration

private val PortraitHeight = 267.dp

/**
 * The person page's header: portrait, name, one line of facts, the overview and two buttons (favorite, the full
 * overview). The backdrop shows [backdropItem], one of their works, so the page is not on a flat background.
 */
@Composable
fun NovaPersonHeader(
    name: String,
    overview: String?,
    imageUrl: String?,
    birthdate: LocalDate?,
    deathdate: LocalDate?,
    birthPlace: String?,
    favorite: Boolean,
    backdropItem: BaseItem?,
    overviewOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    modifier: Modifier = Modifier,
    appViewModel: ApplicationContentViewModel = hiltViewModel(),
) {
    LaunchedEffect(backdropItem?.id) {
        backdropItem?.let { appViewModel.backdropService.submit(it) }
    }
    // Focus on the buttons brings the whole header into view, not just the buttons (the name would scroll away)
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Row(
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalAlignment = Alignment.Top,
        // Clear of the clock
        modifier =
            modifier
                .bringIntoViewRequester(bringIntoView)
                .onFocusChanged { if (it.hasFocus) scope.launch { bringIntoView.bringIntoView() } }
                .padding(top = 32.dp),
    ) {
        // The name's first letter behind the photo, shown when there is none
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(width = 200.dp, height = PortraitHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TheaterColors.accent.copy(alpha = .28f)),
        ) {
            Text(
                text = name.take(1),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .8f),
            )
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // As tall as the portrait, the buttons at its bottom: they stay put whatever the overview's length
            modifier =
                Modifier
                    .widthIn(max = 620.dp)
                    .height(PortraitHeight),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val facts =
                listOfNotNull(
                    birthdate?.let {
                        val age = if (deathdate == null) it.until(LocalDate.now()).years else null
                        stringResource(R.string.born) + " " + formatDate(it) +
                            (age?.let { a -> " (" + pluralStringResource(R.plurals.years_old, a, a) + ")" } ?: "")
                    },
                    birthPlace?.takeIf { it.isNotNullOrBlank() },
                    deathdate?.let { stringResource(R.string.died) + " " + formatDate(it) },
                )
            if (facts.isNotEmpty()) {
                Text(
                    text = facts.joinToString("  ·  "),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (overview.isNotNullOrBlank()) {
                Text(
                    // Paragraph breaks would spend the preview's few lines on blank space
                    text = remember(overview) { overview.trim().replace(Regex("""\s*\n\s*"""), " ") },
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .82f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).wrapContentHeight(Alignment.Bottom),
            ) {
                // The page's main action keeps its label
                NovaActionButton(
                    label = stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite),
                    showLabel = true,
                    onClick = favoriteOnClick,
                ) {
                    Icon(
                        imageVector = if (favorite) TheaterIcons.HeartFilled else TheaterIcons.Heart,
                        contentDescription = null,
                        tint = if (favorite) Color.Red else LocalContentColor.current,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (overview.isNotNullOrBlank()) {
                    ExpandablePlayButton(
                        title = R.string.nova_overview,
                        resume = Duration.ZERO,
                        icon = TheaterIcons.Info,
                        onClick = { overviewOnClick() },
                    )
                }
            }
        }
    }
}
