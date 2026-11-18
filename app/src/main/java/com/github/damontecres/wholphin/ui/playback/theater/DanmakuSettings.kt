package com.github.damontecres.wholphin.ui.playback.theater

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.github.damontecres.wholphin.R

enum class DanmakuArea(
    val fraction: Float,
    @param:StringRes val label: Int,
) {
    QUARTER(.25f, R.string.theater_danmaku_area_quarter),
    HALF(.5f, R.string.theater_danmaku_area_half),
    FULL(1f, R.string.theater_danmaku_area_full),
}

enum class DanmakuSpeed(
    /** How long a scrolling comment takes to cross the screen */
    val scrollMs: Long,
    @param:StringRes val label: Int,
) {
    SLOW(11_000, R.string.theater_danmaku_slow),
    NORMAL(8_000, R.string.theater_danmaku_normal),
    FAST(5_500, R.string.theater_danmaku_fast),
}

enum class DanmakuSize(
    val sp: Int,
    @param:StringRes val label: Int,
) {
    SMALL(18, R.string.theater_danmaku_small),
    MEDIUM(22, R.string.theater_danmaku_medium),
    LARGE(27, R.string.theater_danmaku_large),
}

val DanmakuDensities = listOf(100, 75, 50, 25)
val DanmakuOpacities = listOf(100, 75, 50)

data class DanmakuSettings(
    val enabled: Boolean = true,
    val densityPercent: Int = 100,
    val opacityPercent: Int = 75,
    val area: DanmakuArea = DanmakuArea.HALF,
    val speed: DanmakuSpeed = DanmakuSpeed.NORMAL,
    val size: DanmakuSize = DanmakuSize.MEDIUM,
)

/**
 * Danmaku settings, kept in their own SharedPreferences so the fork does not touch the upstream preference definitions.
 */
private const val PREFS_NAME = "theater_danmaku"

private fun Context.loadDanmakuSettings(): DanmakuSettings {
    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val defaults = DanmakuSettings()
    return DanmakuSettings(
        enabled = prefs.getBoolean("enabled", defaults.enabled),
        densityPercent = prefs.getInt("density", defaults.densityPercent),
        opacityPercent = prefs.getInt("opacity", defaults.opacityPercent),
        area = enumValueOrDefault(prefs.getString("area", null), defaults.area),
        speed = enumValueOrDefault(prefs.getString("speed", null), defaults.speed),
        size = enumValueOrDefault(prefs.getString("size", null), defaults.size),
    )
}

private fun Context.saveDanmakuSettings(settings: DanmakuSettings) {
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean("enabled", settings.enabled)
        .putInt("density", settings.densityPercent)
        .putInt("opacity", settings.opacityPercent)
        .putString("area", settings.area.name)
        .putString("speed", settings.speed.name)
        .putString("size", settings.size.name)
        .apply()
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(
    name: String?,
    default: T,
): T = enumValues<T>().firstOrNull { it.name == name } ?: default

/** Current danmaku settings and a setter that also persists them */
class DanmakuSettingsHolder(
    private val context: Context,
    private val state: MutableState<DanmakuSettings>,
) {
    val value: DanmakuSettings get() = state.value

    fun update(settings: DanmakuSettings) {
        state.value = settings
        context.saveDanmakuSettings(settings)
    }
}

@Composable
fun rememberDanmakuSettings(): DanmakuSettingsHolder {
    val context = LocalContext.current
    return remember(context) {
        DanmakuSettingsHolder(context, mutableStateOf(context.loadDanmakuSettings()))
    }
}
