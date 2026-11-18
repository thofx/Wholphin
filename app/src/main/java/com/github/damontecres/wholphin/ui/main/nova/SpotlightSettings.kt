package com.github.damontecres.wholphin.ui.main.nova

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogItemDivider
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.main.settings.HomeSettingsListItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SpotlightSettings(
    val enabled: Boolean = true,
    val continueWatching: Boolean = true,
    val nextUp: Boolean = true,
    val recentlyAdded: Boolean = true,
    val maxEntries: Int = 7,
    /** 0 is off */
    val autoRotateSeconds: Int = 8,
)

private val SpotlightCounts = listOf(5, 7, 10)
private val SpotlightRotations = listOf(0, 8, 15)

/**
 * Spotlight settings, kept on the device in their own SharedPreferences so the fork leaves the upstream home settings
 * (which can be saved to the server) untouched. Shared between the home page and its settings page.
 */
object SpotlightSettingsStore {
    private const val PREFS_NAME = "nova_spotlight"
    private var flow: MutableStateFlow<SpotlightSettings>? = null

    fun settings(context: Context): StateFlow<SpotlightSettings> = flow ?: MutableStateFlow(load(context)).also { flow = it }

    fun update(
        context: Context,
        settings: SpotlightSettings,
    ) {
        (settings(context) as MutableStateFlow).value = settings
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("enabled", settings.enabled)
            .putBoolean("continue_watching", settings.continueWatching)
            .putBoolean("next_up", settings.nextUp)
            .putBoolean("recently_added", settings.recentlyAdded)
            .putInt("max_entries", settings.maxEntries)
            .putInt("auto_rotate_seconds", settings.autoRotateSeconds)
            .apply()
    }

    private fun load(context: Context): SpotlightSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaults = SpotlightSettings()
        return SpotlightSettings(
            enabled = prefs.getBoolean("enabled", defaults.enabled),
            continueWatching = prefs.getBoolean("continue_watching", defaults.continueWatching),
            nextUp = prefs.getBoolean("next_up", defaults.nextUp),
            recentlyAdded = prefs.getBoolean("recently_added", defaults.recentlyAdded),
            maxEntries = prefs.getInt("max_entries", defaults.maxEntries),
            autoRotateSeconds = prefs.getInt("auto_rotate_seconds", defaults.autoRotateSeconds),
        )
    }
}

@Composable
fun rememberSpotlightSettings(): SpotlightSettings {
    val context = LocalContext.current
    val settings by remember(context) { SpotlightSettingsStore.settings(context.applicationContext) }.collectAsState()
    return settings
}

private fun <T> List<T>.next(current: T): T = get((indexOf(current) + 1).mod(size))

/**
 * The spotlight entry at the top of the home settings' row list, with a dialog for its options
 */
@Composable
fun SpotlightSettingsItem(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val settings = rememberSpotlightSettings()
    var showDialog by remember { mutableStateOf(false) }
    val update = { s: SpotlightSettings -> SpotlightSettingsStore.update(context, s) }

    HomeSettingsListItem(
        selected = false,
        headlineText = stringResource(R.string.nova_spotlight),
        supportingContent = {
            Text(
                if (settings.enabled) {
                    stringResource(R.string.nova_spotlight_summary, settings.maxEntries)
                } else {
                    stringResource(R.string.nova_spotlight_off)
                },
            )
        },
        leadingContent = { Icon(Icons.Default.Star, contentDescription = null) },
        onClick = { showDialog = true },
        modifier = modifier,
    )

    val check: @Composable (Boolean) -> Unit = { on -> if (on) Icon(Icons.Default.Check, contentDescription = null) }
    val value: @Composable (String) -> Unit = { Text(it) }
    val rotation =
        if (settings.autoRotateSeconds == 0) {
            stringResource(R.string.nova_spotlight_off)
        } else {
            stringResource(R.string.nova_spotlight_seconds, settings.autoRotateSeconds)
        }
    DialogPopup(
        showDialog = showDialog,
        title = stringResource(R.string.nova_spotlight),
        dismissOnClick = false,
        waitToLoad = false,
        onDismissRequest = { showDialog = false },
        dialogItems =
            buildList {
                add(
                    DialogItem(
                        headlineContent = { Text(stringResource(R.string.nova_spotlight_show)) },
                        trailingContent = { check(settings.enabled) },
                        onClick = { update(settings.copy(enabled = !settings.enabled)) },
                    ),
                )
                add(DialogItemDivider)
                // Sources; at least one stays on
                val sources = listOf(settings.continueWatching, settings.nextUp, settings.recentlyAdded)
                listOf(
                    R.string.continue_watching to { s: SpotlightSettings, on: Boolean -> s.copy(continueWatching = on) },
                    R.string.next_up to { s: SpotlightSettings, on: Boolean -> s.copy(nextUp = on) },
                    R.string.recently_added to { s: SpotlightSettings, on: Boolean -> s.copy(recentlyAdded = on) },
                ).forEachIndexed { i, (label, set) ->
                    add(
                        DialogItem(
                            headlineContent = { Text(stringResource(label)) },
                            trailingContent = { check(sources[i]) },
                            enabled = settings.enabled && (!sources[i] || sources.count { it } > 1),
                            onClick = { update(set(settings, !sources[i])) },
                        ),
                    )
                }
                add(DialogItemDivider)
                add(
                    DialogItem(
                        headlineContent = { Text(stringResource(R.string.nova_spotlight_count)) },
                        trailingContent = { value(settings.maxEntries.toString()) },
                        enabled = settings.enabled,
                        onClick = { update(settings.copy(maxEntries = SpotlightCounts.next(settings.maxEntries))) },
                    ),
                )
                add(
                    DialogItem(
                        headlineContent = { Text(stringResource(R.string.nova_spotlight_auto_rotate)) },
                        trailingContent = { value(rotation) },
                        enabled = settings.enabled,
                        onClick = {
                            update(settings.copy(autoRotateSeconds = SpotlightRotations.next(settings.autoRotateSeconds)))
                        },
                    ),
                )
            },
    )
}
