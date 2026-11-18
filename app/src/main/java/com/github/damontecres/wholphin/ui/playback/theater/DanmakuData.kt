package com.github.damontecres.wholphin.ui.playback.theater

import android.content.Context
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.UUID

enum class DanmakuType {
    SCROLL,

    /** Left to right (mode 6) */
    SCROLL_REVERSE,
    TOP,
    BOTTOM,
    ;

    val isScrolling: Boolean get() = this == SCROLL || this == SCROLL_REVERSE
}

/**
 * One danmaku comment.
 *
 * @param id stable key for density filtering: the comment id from the XML when present, else its position in the file
 * @param color RGB, without alpha
 */
data class Danmaku(
    val id: Int,
    val timeMs: Long,
    val type: DanmakuType,
    val color: Int,
    val text: String,
)

/** Gives the theater UI the app's HTTP client (the fork keeps upstream DI modules untouched) */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DanmakuEntryPoint {
    @AuthOkHttpClient
    fun okHttpClient(): OkHttpClient
}

fun Context.danmakuHttpClient(): OkHttpClient =
    EntryPointAccessors.fromApplication(applicationContext, DanmakuEntryPoint::class.java).okHttpClient()

/**
 * Loads danmaku from jellyfin-plugin-danmu (https://github.com/cxfksword/jellyfin-plugin-danmu).
 *
 * `/api/danmu/{id}/raw` returns the Bilibili style XML, or 404 when there is none (or the plugin is missing).
 */
object DanmakuLoader {
    /** Tolerates extra attributes and whitespace around `p` */
    private val entryRegex = Regex("""<d\s+p="([^"]*)"[^>]*>([^<]*)</d>""")

    suspend fun load(
        client: OkHttpClient,
        serverBaseUrl: String,
        itemId: UUID,
    ): List<Danmaku> {
        val xml =
            withContext(WholphinDispatchers.IO) {
                val call = client.newCall(Request.Builder().url("${serverBaseUrl.trimEnd('/')}/api/danmu/$itemId/raw").build())
                // Leaving the item cancels the coroutine; also cancel the blocking HTTP call
                val cancelHandle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
                try {
                    call.execute().use { response -> if (response.isSuccessful) response.body.string() else null }
                } catch (ex: Exception) {
                    // Missing plugin, network error: no danmaku
                    Timber.w(ex, "Failed to load danmaku for %s", itemId)
                    null
                } finally {
                    cancelHandle.dispose()
                }
            } ?: return emptyList()
        return withContext(WholphinDispatchers.Default) { parse(xml) }
    }

    private class Entry(
        val key: Int,
        val timeMs: Long,
        val type: DanmakuType,
        val color: Int,
        val text: String,
    ) {
        /** Same time, mode, color and text: a duplicate */
        val dedupKey get() = listOf(timeMs, type, color, text)
    }

    /**
     * Parse `<d p="time,mode,size,color,timestamp,pool,user,id,...">text</d>` entries, sorted by time.
     *
     * Mode 4 is bottom, 5 is top, 6 scrolls left to right, 7 (advanced) and 8 (code) are skipped;
     * everything else (1-3, and non-standard values from some sources) scrolls right to left.
     * Exact duplicates (same time, mode, color and text) are dropped.
     */
    fun parse(xml: String): List<Danmaku> {
        val entries =
            entryRegex
                .findAll(xml)
                .mapIndexedNotNull { index, match -> parseEntry(index, match.groupValues[1], match.groupValues[2]) }
                .distinctBy { it.dedupKey }
                .toList()
        // Some sources only have whole seconds; comments of the same second would move as one column,
        // so spread each over its second (deterministic, by its key)
        val wholeSecondsOnly = entries.isNotEmpty() && entries.all { it.timeMs % 1000 == 0L }
        return entries
            .map {
                val timeMs = if (wholeSecondsOnly) it.timeMs + mix(it.key) % 1000 else it.timeMs
                Danmaku(it.key, timeMs, it.type, it.color, it.text)
            }.sortedBy { it.timeMs }
    }

    private fun parseEntry(
        index: Int,
        p: String,
        rawText: String,
    ): Entry? {
        val params = p.split(',')
        val timeSec = params.getOrNull(0)?.toDoubleOrNull() ?: return null
        val text = unescape(rawText).trim()
        if (text.isEmpty()) return null
        val type =
            when (params.getOrNull(1)?.toIntOrNull()) {
                4 -> DanmakuType.BOTTOM

                5 -> DanmakuType.TOP

                6 -> DanmakuType.SCROLL_REVERSE

                // Advanced (7) and code (8) comments carry JSON/script as text: not supported, skip
                7, 8 -> return null

                else -> DanmakuType.SCROLL
            }
        val color =
            params
                .getOrNull(3)
                ?.toLongOrNull()
                ?.toInt()
                ?.and(0xFFFFFF) ?: 0xFFFFFF
        // Bilibili style comment id (8th field); some sources leave it 0, then fall back to the position
        val key =
            params
                .getOrNull(7)
                ?.toLongOrNull()
                ?.takeIf { it != 0L }
                ?.hashCode() ?: index
        return Entry(key, (timeSec * 1000).toLong(), type, color, text)
    }

    private fun unescape(text: String): String =
        text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
}

/**
 * Whether [danmaku] is kept at [densityPercent]. A pseudo-random choice seeded by the id,
 * so the same comments show every time (e.g. after seeking back).
 */
fun keepAtDensity(
    danmaku: Danmaku,
    densityPercent: Int,
): Boolean {
    if (densityPercent >= 100) return true
    return mix(danmaku.id) % 100 < densityPercent
}

/**
 * murmur3 32-bit finalizer: consecutive inputs give well spread, non-negative outputs.
 * (A plain multiplicative hash kept and dropped runs of consecutive ids, leaving seconds-long gaps.)
 */
private fun mix(value: Int): Int {
    var h = value
    h = h xor (h ushr 16)
    h *= -0x7a143595
    h = h xor (h ushr 13)
    h *= -0x3d4d51cb
    h = h xor (h ushr 16)
    return h and Int.MAX_VALUE
}
