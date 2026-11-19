package com.github.damontecres.wholphin.ui.main.nova

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.playback.theater.TheaterColors
import com.github.damontecres.wholphin.ui.tryRequestFocus

/**
 * The settings pages' layout: the screen's title and its groups on the left (focusing one shows it), the group's
 * settings next to it. Over a dimmed backdrop.
 */
@Composable
fun NovaSettingsLayout(
    title: String,
    groups: List<String>,
    selectedGroup: Int,
    onSelectGroup: (Int) -> Unit,
    listFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    list: @Composable (Modifier) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = .86f))
                .padding(start = NovaContentStart, end = 32.dp, top = 40.dp),
    ) {
        Column(modifier = Modifier.width(200.dp).fillMaxHeight()) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                modifier = Modifier.padding(start = 16.dp, bottom = 24.dp),
            )
            // Back from the settings, focus returns to the group they belong to, not the nearest one
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.focusGroup().focusRestorer(),
            ) {
                itemsIndexed(groups) { index, group ->
                    GroupItem(
                        text = group,
                        selected = index == selectedGroup,
                        onFocus = { onSelectGroup(index) },
                    )
                }
            }
        }
        list(
            Modifier
                // Entering from the groups lands on the group's first setting, not the one nearest the group's row
                .focusGroup()
                .focusProperties { onEnter = { listFocusRequester.tryRequestFocus() } }
                .width(600.dp)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun GroupItem(
    text: String,
    selected: Boolean,
    onFocus: () -> Unit,
) {
    val accent = TheaterColors.accent
    Surface(
        // Focus selects the group, like a TV's settings; there is nothing more to do on click
        onClick = onFocus,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors =
            novaPillColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.onSurface.copy(
                            alpha = .08f,
                        )
                    } else {
                        Color.Transparent
                    },
                contentColor = if (selected) TheaterColors.Text else TheaterColors.Text.copy(alpha = .7f),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 6.dp),
            )
            if (selected) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(accent, CircleShape),
                )
            }
        }
    }
}
