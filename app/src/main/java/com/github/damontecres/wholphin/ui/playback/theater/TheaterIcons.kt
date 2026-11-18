package com.github.damontecres.wholphin.ui.playback.theater

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Line icons for the theater UI, drawn on a 24x24 grid with a 1.8 stroke so they share one visual weight.
 * Tinted by the caller (e.g. tv-material Icon uses the content color).
 */
object TheaterIcons {
    val Subtitles =
        icon(
            stroke =
                listOf(
                    "M6 5h12a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3H6a3 3 0 0 1 -3 -3V8a3 3 0 0 1 3 -3z",
                    "M7 14h4M14 14h3M7 10.5h2M12 10.5h5",
                ),
        )
    val Audio =
        icon(
            stroke =
                listOf(
                    "M4 10v4h3l5 4V6L7 10H4z",
                    "M16 9a4 4 0 0 1 0 6M18.5 6.5a8 8 0 0 1 0 11",
                ),
        )
    val Chapters =
        icon(
            stroke =
                listOf(
                    "M6 4v16",
                    "M6 5h11l-2.5 3.5L17 12H6",
                ),
        )
    val Queue =
        icon(
            stroke = listOf("M4 6h11M4 12h11M4 18h7"),
            fill = listOf("M16 15v6l5 -3z"),
        )
    val More =
        icon(
            fill =
                listOf(
                    "M5 10.2a1.8 1.8 0 1 0 0 3.6a1.8 1.8 0 1 0 0 -3.6z",
                    "M12 10.2a1.8 1.8 0 1 0 0 3.6a1.8 1.8 0 1 0 0 -3.6z",
                    "M19 10.2a1.8 1.8 0 1 0 0 3.6a1.8 1.8 0 1 0 0 -3.6z",
                ),
        )
    val Danmaku =
        icon(
            stroke =
                listOf(
                    "M5 5h14a2 2 0 0 1 2 2v9a2 2 0 0 1 -2 2H10l-4 3v-3H5a2 2 0 0 1 -2 -2V7a2 2 0 0 1 2 -2z",
                    "M7 9.5h10M7 13h6",
                ),
        )
    val Skip =
        icon(fill = listOf("M4 5.5v13L13 12zM13 5.5v13L22 12z"))
    val Check =
        icon(stroke = listOf("M5 12.5l4.5 4.5L19 7.5"), strokeWidth = 2.4f)
    val Chevron =
        icon(stroke = listOf("M9 6l6 6-6 6"), strokeWidth = 2f)

    private fun icon(
        stroke: List<String> = emptyList(),
        fill: List<String> = emptyList(),
        strokeWidth: Float = 1.8f,
    ): ImageVector {
        val builder =
            ImageVector.Builder(
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
        stroke.forEach {
            builder.addPath(
                pathData = PathParser().parsePathString(it).toNodes(),
                stroke = SolidColor(androidx.compose.ui.graphics.Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        fill.forEach {
            builder.addPath(
                pathData = PathParser().parsePathString(it).toNodes(),
                fill = SolidColor(androidx.compose.ui.graphics.Color.Black),
            )
        }
        return builder.build()
    }
}
