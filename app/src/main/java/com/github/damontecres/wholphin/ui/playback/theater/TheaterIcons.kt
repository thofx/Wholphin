package com.github.damontecres.wholphin.ui.playback.theater

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Line icons for the theater UI and the fork's other pages, drawn on a 24x24 grid with a 1.8 stroke so they share one visual weight.
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

    // Details actions (watched, favorite, trailer, versions, delete, more, restart)
    val Eye =
        icon(
            stroke =
                listOf(
                    "M2.5 12s3.5-6.5 9.5-6.5S21.5 12 21.5 12s-3.5 6.5-9.5 6.5S2.5 12 2.5 12z",
                    "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
                ),
        )
    val EyeOff =
        icon(
            stroke =
                listOf(
                    "M10.6 5.6A10 10 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a17 17 0 0 1 -2.6 3.4M6.6 6.6C4 8.3 2.5 12 2.5 12s3.5 6.5 9.5 6.5a9.6 9.6 0 0 0 5.3-1.6",
                    "M9.9 9.9a3 3 0 0 0 4.2 4.2",
                    "M3.5 3.5l17 17",
                ),
        )
    val Heart =
        icon(stroke = listOf("M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0 1 12 7.6 4.3 4.3 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10z"))
    val HeartFilled =
        icon(fill = listOf("M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0 1 12 7.6 4.3 4.3 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10z"))
    val Film =
        icon(
            stroke =
                listOf(
                    "M5 4.5h14a1.5 1.5 0 0 1 1.5 1.5v12a1.5 1.5 0 0 1 -1.5 1.5H5A1.5 1.5 0 0 1 3.5 18V6A1.5 1.5 0 0 1 5 4.5z",
                    "M7.5 4.5v15M16.5 4.5v15M3.5 9.5h4M3.5 14.5h4M16.5 9.5h4M16.5 14.5h4",
                ),
        )
    val Versions =
        icon(stroke = listOf("M12 4l8.5 4.5L12 13L3.5 8.5z", "M3.5 12.5L12 17l8.5-4.5", "M3.5 16.5L12 21l8.5-4.5"))
    val Trash =
        icon(stroke = listOf("M4.5 7h15", "M9.5 7V4.5h5V7", "M6.5 7l1 12.5h9l1-12.5", "M10.3 11v5M13.7 11v5"))
    val MoreVert =
        icon(
            fill =
                listOf(
                    "M10.6 5.5a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0z",
                    "M10.6 12a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0z",
                    "M10.6 18.5a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0z",
                ),
        )
    val Restart =
        icon(stroke = listOf("M4.5 12a7.5 7.5 0 1 0 2.2-5.3", "M4.5 4.5v4h4"))

    // Library toolbar (sort, filter, view options, random, play, shuffle), same weight as above
    val ArrowUp =
        icon(stroke = listOf("M12 19V5", "M6.5 10.5L12 5l5.5 5.5"), strokeWidth = 2f)
    val ArrowDown =
        icon(stroke = listOf("M12 5v14", "M6.5 13.5L12 19l5.5-5.5"), strokeWidth = 2f)
    val Filter =
        icon(stroke = listOf("M4 5h16l-6.2 7.4V19l-3.6-2v-4.6z"))
    val Sliders =
        icon(
            stroke =
                listOf(
                    "M4 7h9M17 7h3M4 17h3M11 17h9",
                    "M13 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
                    "M7 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
                ),
        )
    val Dice =
        icon(
            stroke = listOf("M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2V6a2 2 0 0 1 2 -2z"),
            fill =
                listOf(
                    "M7.3 8.5a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0z",
                    "M10.8 12a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0z",
                    "M14.3 15.5a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0z",
                ),
        )
    val Play =
        icon(fill = listOf("M8 5.5v13l11-6.5z"))
    val Shuffle =
        icon(
            stroke =
                listOf(
                    "M4 7h3.5c3 0 4.5 10 8 10H20",
                    "M17 14l3 3-3 3",
                    "M4 17h3.5c1.2 0 2.1-1.6 2.9-3.5M13.6 10.5c.8-1.9 1.7-3.5 2.9-3.5H20",
                    "M17 4l3 3-3 3",
                ),
        )

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
