package com.github.damontecres.wholphin.ui.playback.theater

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/** How long a top/bottom comment stays */
private const val FIXED_DURATION_MS = 4_000L

/** Comments more than this late (e.g. after a long frame) are dropped instead of shown mid-way */
private const val MAX_LATE_MS = 1_000L

/** How far ahead of playback comments are pre-rendered in the background */
private const val PRERENDER_AHEAD_MS = 1_500L

/** Upper bound of bitmaps waiting in the pre-render cache, whatever the data's density */
private const val PRERENDER_MAX_READY = 64

/** Gap kept between comments in a lane, in px */
private const val LANE_GAP_PX = 32f

/** A comment on screen, pre-rendered once into [bitmap] so each frame is a single drawImage */
private class ActiveDanmaku(
    val danmaku: Danmaku,
    val bitmap: ImageBitmap,
    /** Text width in px, without the bitmap's padding */
    val width: Float,
    val lane: Int,
)

/** A comment rendered into a bitmap, with its text width in px (without the bitmap's padding) */
private typealias RenderedDanmaku = Pair<ImageBitmap, Float>

/**
 * Renders upcoming comments into bitmaps on a background thread, [PRERENDER_AHEAD_MS] ahead of playback,
 * so spawning a comment on the main thread is a map lookup. Items are keyed by their index in the list.
 */
private class DanmakuPrerenderer(
    private val items: List<Danmaku>,
    private val render: (Danmaku) -> RenderedDanmaku,
) {
    private val ready = ConcurrentHashMap<Int, RenderedDanmaku>()

    /** Written by the main thread, read by [run] */
    @Volatile var positionMs = 0L

    @Volatile var densityPercent = 100

    /** Index the scheduler has consumed up to: nothing before it is rendered */
    @Volatile var consumedIndex = 0

    fun take(index: Int): RenderedDanmaku? = ready.remove(index)

    /** Drop what was rendered, e.g. after a seek */
    fun clear() = ready.clear()

    suspend fun run() {
        var cursor = 0
        while (true) {
            cursor = maxOf(cursor, consumedIndex)
            // After a seek backwards the cursor is far ahead of the new position: restart from the scheduler
            if (cursor < items.size && items[cursor].timeMs > positionMs + PRERENDER_AHEAD_MS * 4) cursor = consumedIndex
            val horizon = positionMs + PRERENDER_AHEAD_MS
            // Stop at the cap (e.g. thousands of comments in one second); the scheduler's takes make room
            while (cursor < items.size && items[cursor].timeMs <= horizon && ready.size < PRERENDER_MAX_READY) {
                val danmaku = items[cursor]
                if (keepAtDensity(danmaku, densityPercent) && !ready.containsKey(cursor)) ready[cursor] = render(danmaku)
                cursor++
            }
            delay(50)
        }
    }
}

/**
 * Assigns comments to lanes as playback advances. Each comment gets a lane once, when it starts,
 * and keeps it; when no lane is free it is dropped. Pure logic apart from [render]: unit-testable.
 *
 * Lanes count from the top for scrolling and top comments, from the bottom of the area for bottom ones.
 *
 * @param estimateWidth cheap upper-bound width, so comments that cannot fit are dropped before rendering
 * @param render renders a comment that got a lane, returning its bitmap and real text width
 */
private class DanmakuScheduler(
    private val items: List<Danmaku>,
    private val scrollMs: Long,
    private val estimateWidth: (Danmaku) -> Float,
    private val render: (Danmaku) -> RenderedDanmaku,
    private val prerendered: DanmakuPrerenderer,
) {
    val active = mutableListOf<ActiveDanmaku>()
    private var nextIndex = 0

    /** Start over from [positionMs], e.g. after a seek: comments that were skipped are not replayed */
    fun reset(positionMs: Long) {
        active.clear()
        nextIndex = firstIndexAtOrAfter(positionMs)
        prerendered.clear()
        prerendered.consumedIndex = nextIndex
        prerendered.positionMs = positionMs
    }

    fun update(
        positionMs: Long,
        width: Float,
        laneCount: Int,
        densityPercent: Int,
    ) {
        active.removeAll { positionMs - it.danmaku.timeMs > durationOf(it.danmaku) }
        while (nextIndex < items.size && items[nextIndex].timeMs <= positionMs) {
            val index = nextIndex++
            val danmaku = items[index]
            // Always take it out, so skipped comments do not stay in the cache
            val cached = prerendered.take(index)
            if (positionMs - danmaku.timeMs > MAX_LATE_MS) continue
            if (!keepAtDensity(danmaku, densityPercent)) continue
            // Not pre-rendered yet (e.g. just after a seek): check it can fit before rendering on this thread
            if (cached == null) findLane(danmaku, estimateWidth(danmaku), positionMs, width, laneCount) ?: continue
            val (bitmap, textWidth) = cached ?: render(danmaku)
            // The estimate is an upper bound, but check again with the real width
            val lane = findLane(danmaku, textWidth, positionMs, width, laneCount) ?: continue
            active.add(ActiveDanmaku(danmaku, bitmap, textWidth, lane))
        }
        prerendered.consumedIndex = nextIndex
        prerendered.positionMs = positionMs
    }

    /** X of a comment's text at [positionMs] */
    fun x(
        item: ActiveDanmaku,
        positionMs: Long,
        width: Float,
    ): Float {
        val progress = (positionMs - item.danmaku.timeMs).toFloat() / scrollMs
        return when (item.danmaku.type) {
            DanmakuType.SCROLL -> width - (width + item.width) * progress
            DanmakuType.SCROLL_REVERSE -> (width + item.width) * progress - item.width
            DanmakuType.TOP, DanmakuType.BOTTOM -> (width - item.width) / 2
        }
    }

    private fun durationOf(danmaku: Danmaku) = if (danmaku.type.isScrolling) scrollMs else FIXED_DURATION_MS

    private fun findLane(
        danmaku: Danmaku,
        textWidth: Float,
        nowMs: Long,
        width: Float,
        laneCount: Int,
    ): Int? =
        (0 until laneCount).firstOrNull { lane ->
            val inLane =
                active.filter {
                    it.lane == lane && (it.danmaku.type == DanmakuType.BOTTOM) == (danmaku.type == DanmakuType.BOTTOM)
                }
            when (danmaku.type) {
                // Fixed comments only stack with their own kind
                DanmakuType.TOP, DanmakuType.BOTTOM -> {
                    inLane.none { it.danmaku.type == danmaku.type }
                }

                // Scrolling: no top comment or opposite-direction comment in the lane, and room behind the last one
                else -> {
                    inLane.none {
                        it.danmaku.type == DanmakuType.TOP || (it.danmaku.type.isScrolling && it.danmaku.type != danmaku.type)
                    } &&
                        inLane.lastOrNull { it.danmaku.type == danmaku.type }?.let { canFollow(it, textWidth, nowMs, width) } != false
                }
            }
        }

    /**
     * Whether a comment of [textWidth] starting now can follow [last] in the same direction: [last] must have fully
     * entered with a gap, and the new one (faster when longer) must not close that gap before [last] leaves.
     */
    private fun canFollow(
        last: ActiveDanmaku,
        textWidth: Float,
        nowMs: Long,
        width: Float,
    ): Boolean {
        val lastSpeed = (width + last.width) / scrollMs
        val newSpeed = (width + textWidth) / scrollMs
        val elapsed = (nowMs - last.danmaku.timeMs).toFloat()
        val gapNow = lastSpeed * elapsed - last.width
        if (gapNow < LANE_GAP_PX) return false
        val remaining = scrollMs - elapsed
        return newSpeed <= lastSpeed || gapNow - (newSpeed - lastSpeed) * remaining >= LANE_GAP_PX
    }

    private fun firstIndexAtOrAfter(positionMs: Long): Int {
        var low = 0
        var high = items.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (items[mid].timeMs < positionMs) low = mid + 1 else high = mid
        }
        return low
    }
}

/**
 * Renders a comment once: soft shadow + thin outline + fill, so the frame loop only blits a bitmap.
 * Returns the bitmap (with [pad] px on each side) and the text width.
 */
private fun renderDanmaku(
    danmaku: Danmaku,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    density: Density,
    pad: Int,
): RenderedDanmaku {
    val fill = Color(0xFF000000.toInt() or danmaku.color)
    val layout = textMeasurer.measure(danmaku.text, style.copy(color = fill), maxLines = 1)
    val bitmap = ImageBitmap(layout.size.width + pad * 2, layout.size.height + pad * 2)
    val topLeft = Offset(pad.toFloat(), pad.toFloat())
    CanvasDrawScope().draw(
        density,
        LayoutDirection.Ltr,
        Canvas(bitmap),
        Size(bitmap.width.toFloat(), bitmap.height.toFloat()),
    ) {
        // Thin outline: a thick one is wider than CJK strokes and makes white text look hollow
        drawText(
            layout,
            color = if (danmaku.color == 0) Color.White.copy(alpha = .6f) else Color.Black.copy(alpha = .6f),
            topLeft = topLeft,
            drawStyle = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round),
            shadow = Shadow.None,
        )
        // Style and color must be given again: the layout's paint keeps the previous call's Stroke
        drawText(layout, color = fill, topLeft = topLeft, drawStyle = Fill)
    }
    // Upload the texture now rather than on the frame the comment first shows
    bitmap.prepareToDraw()
    return bitmap to layout.size.width.toFloat()
}

/**
 * Draws danmaku over the video, synced to the player position, so pause/speed/seek follow playback.
 *
 * Per frame (only while playing): one scheduler update and one drawImage per comment on screen.
 */
@Composable
fun DanmakuLayer(
    items: List<Danmaku>,
    player: Player,
    settings: DanmakuSettings,
    modifier: Modifier = Modifier,
    /** Fade comments while the overlay UI shows, so they do not fight with the clock and info bar */
    dimmed: Boolean = false,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 0)
    // A separate measurer for the background thread: TextMeasurer is not meant to be shared across threads
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val backgroundMeasurer =
        remember(fontFamilyResolver, density) { TextMeasurer(fontFamilyResolver, density, LayoutDirection.Ltr, cacheSize = 0) }
    val style =
        remember(settings.size) {
            TextStyle(
                fontSize = settings.size.sp.sp,
                fontWeight = FontWeight.Medium,
                // Soft shadow keeps text readable on bright frames without a heavy outline
                shadow = Shadow(Color.Black.copy(alpha = .5f), Offset(0f, 1f), blurRadius = 4f),
            )
        }
    val fontPx = with(density) { style.fontSize.toPx() }
    // Room in the bitmap for the outline and the shadow blur
    val pad = ceil(with(density) { 1.dp.toPx() } + 5f).toInt()

    // Speed or size changes would move or resize comments already on screen: start a new schedule instead
    val prerenderer =
        remember(items, style, density) {
            DanmakuPrerenderer(items) { renderDanmaku(it, backgroundMeasurer, style, density, pad) }
        }
    LaunchedEffect(prerenderer) { withContext(WholphinDispatchers.Default) { prerenderer.run() } }
    LaunchedEffect(prerenderer, settings.densityPercent) { prerenderer.densityPercent = settings.densityPercent }
    val scheduler =
        remember(items, settings.speed, style, density, prerenderer) {
            DanmakuScheduler(
                items = items,
                scrollMs = settings.speed.scrollMs,
                // CJK glyphs are about 1em wide, others narrower: an upper bound
                estimateWidth = { it.text.length * fontPx },
                render = { renderDanmaku(it, textMeasurer, style, density, pad) },
                prerendered = prerenderer,
            ).also { it.reset(player.currentPosition) }
        }

    var size by remember { mutableStateOf(IntSize.Zero) }
    var positionMs by remember { mutableLongStateOf(player.currentPosition) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    DisposableEffect(player, scheduler) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    // Seek, or a new item: comments that were skipped are not replayed
                    scheduler.reset(newPosition.positionMs)
                    positionMs = newPosition.positionMs
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val topInset = with(density) { 12.dp.toPx() }
    val areaHeight = size.height * settings.area.fraction - topInset
    val laneHeight = fontPx * 1.4f
    val laneCount = (areaHeight / laneHeight).toInt().coerceAtLeast(1)

    // Advance the schedule each frame while playing; when paused, update once and stop waking up
    LaunchedEffect(scheduler, isPlaying, size, laneCount, settings.densityPercent) {
        if (size == IntSize.Zero) return@LaunchedEffect
        do {
            withFrameNanos { }
            val now = player.currentPosition
            scheduler.update(now, size.width.toFloat(), laneCount, settings.densityPercent)
            positionMs = now
        } while (isPlaying)
    }

    val alpha = settings.opacityPercent / 100f * (if (dimmed) .35f else 1f)
    Canvas(modifier = modifier.onSizeChanged { size = it }) {
        val now = positionMs
        scheduler.active.forEach { item ->
            val y =
                when (item.danmaku.type) {
                    DanmakuType.BOTTOM -> topInset + areaHeight - laneHeight * (item.lane + 1)
                    else -> topInset + laneHeight * item.lane
                }
            drawImage(
                item.bitmap,
                topLeft = Offset(scheduler.x(item, now, this.size.width) - pad, y - pad),
                alpha = alpha,
            )
        }
    }
}
