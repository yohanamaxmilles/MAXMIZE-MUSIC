package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Maxmize's own icon family: consistent 24x24 grid, rounded line caps and joins,
 * balanced proportions. Colors are applied via Icon(tint = ...), so the ink color
 * below is only a placeholder.
 */
object MaxmizeIcons {

    private class P(val d: String, val width: Float = 1.8f, val filled: Boolean = false)

    private fun icon(name: String, vararg paths: P): ImageVector {
        val ink = SolidColor(Color.Black)
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { p ->
                addPath(
                    pathData = addPathNodes(p.d),
                    fill = if (p.filled) ink else null,
                    stroke = ink,
                    strokeLineWidth = p.width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
    }

    val Shuffle: ImageVector by lazy {
        icon(
            "MaxShuffle",
            P("M3 7h3.2a4 4 0 0 1 3.3 1.8l5 7.4a4 4 0 0 0 3.3 1.8H21"),
            P("M3 17h3.2a4 4 0 0 0 3.3-1.8l.6-.9"),
            P("M13.9 8.8A4 4 0 0 1 17.2 7H21"),
            P("M18.5 4.5L21 7l-2.5 2.5"),
            P("M18.5 14.5L21 17l-2.5 2.5"),
        )
    }

    val Previous: ImageVector by lazy {
        icon(
            "MaxPrevious",
            P("M18 6.2L9.4 12l8.6 5.8z", width = 2f, filled = true),
            P("M6 6v12", width = 2.4f),
        )
    }

    val Next: ImageVector by lazy {
        icon(
            "MaxNext",
            P("M6 6.2L14.6 12 6 17.8z", width = 2f, filled = true),
            P("M18 6v12", width = 2.4f),
        )
    }

    val Play: ImageVector by lazy {
        icon("MaxPlay", P("M8.5 5.6v12.8L18.6 12z", width = 2f, filled = true))
    }

    val Pause: ImageVector by lazy {
        icon(
            "MaxPause",
            P("M8.6 5.8v12.4", width = 3.4f),
            P("M15.4 5.8v12.4", width = 3.4f),
        )
    }

    val Repeat: ImageVector by lazy {
        icon(
            "MaxRepeat",
            P("M17 3.5l3 3-3 3"),
            P("M20 6.5H8a5 5 0 0 0-5 5V12"),
            P("M7 20.5l-3-3 3-3"),
            P("M4 17.5h12a5 5 0 0 0 5-5V12"),
        )
    }

    val RepeatOne: ImageVector by lazy {
        icon(
            "MaxRepeatOne",
            P("M17 3.5l3 3-3 3"),
            P("M20 6.5H8a5 5 0 0 0-5 5V12"),
            P("M7 20.5l-3-3 3-3"),
            P("M4 17.5h12a5 5 0 0 0 5-5V12"),
            P("M11 10.6l1.4-1v5.8", width = 1.5f),
        )
    }

    val Heart: ImageVector by lazy {
        icon(
            "MaxHeart",
            P("M12 20.4s-7.6-4.6-7.6-10.2A4.3 4.3 0 0 1 12 7.7a4.3 4.3 0 0 1 7.6 2.5c0 5.6-7.6 10.2-7.6 10.2z", width = 1.8f),
        )
    }

    val HeartFilled: ImageVector by lazy {
        icon(
            "MaxHeartFilled",
            P("M12 20.4s-7.6-4.6-7.6-10.2A4.3 4.3 0 0 1 12 7.7a4.3 4.3 0 0 1 7.6 2.5c0 5.6-7.6 10.2-7.6 10.2z", width = 1.8f, filled = true),
        )
    }

    val Queue: ImageVector by lazy {
        icon(
            "MaxQueue",
            P("M4 7h10"),
            P("M4 12h10"),
            P("M4 17h5"),
            P("M14.8 17a2 2 0 1 0 4 0a2 2 0 1 0-4 0"),
            P("M18.8 17V8.2l2.7 1.1"),
        )
    }

    val Lyrics: ImageVector by lazy {
        icon(
            "MaxLyrics",
            P("M6 4.5h12a2.5 2.5 0 0 1 2.5 2.5v8a2.5 2.5 0 0 1-2.5 2.5h-6.2L7.5 20.5v-3H6A2.5 2.5 0 0 1 3.5 15V7A2.5 2.5 0 0 1 6 4.5z"),
            P("M8 9.2h8"),
            P("M8 12.6h5"),
        )
    }

    val Audio: ImageVector by lazy {
        icon(
            "MaxAudio",
            P("M6.5 10.5v7", width = 2.2f),
            P("M12 5.5v12", width = 2.2f),
            P("M17.5 12v5.5", width = 2.2f),
        )
    }

    val ChevronDown: ImageVector by lazy {
        icon("MaxChevronDown", P("M6 9.5l6 6 6-6", width = 2f))
    }

    val ChevronUp: ImageVector by lazy {
        icon("MaxChevronUp", P("M6 14.5l6-6 6 6", width = 2f))
    }

    val Moon: ImageVector by lazy {
        icon("MaxMoon", P("M20 14.4A8 8 0 1 1 9.6 4a6.4 6.4 0 0 0 10.4 10.4z"))
    }

    val More: ImageVector by lazy {
        icon(
            "MaxMore",
            P("M12 5.5v.01", width = 2.8f),
            P("M12 12v.01", width = 2.8f),
            P("M12 18.5v.01", width = 2.8f),
        )
    }

    // ---- Navigation & header icons ----
    val Home: ImageVector by lazy {
        icon(
            "MaxHome",
            P("M4.2 10.6L12 4.2l7.8 6.4v8.1a1.6 1.6 0 0 1-1.6 1.6H15v-5.3a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v5.3H5.8a1.6 1.6 0 0 1-1.6-1.6z", width = 1.8f),
        )
    }

    val HomeFilled: ImageVector by lazy {
        icon(
            "MaxHomeFilled",
            P("M4.2 10.6L12 4.2l7.8 6.4v8.1a1.6 1.6 0 0 1-1.6 1.6H15v-5.3a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v5.3H5.8a1.6 1.6 0 0 1-1.6-1.6z", width = 1.8f, filled = true),
        )
    }

    val Library: ImageVector by lazy {
        icon(
            "MaxLibrary",
            P("M6 5v14", width = 2f),
            P("M10.6 5v14", width = 2f),
            P("M14.6 6.4l4 12.8", width = 2f),
        )
    }

    val LibraryFilled: ImageVector by lazy {
        icon(
            "MaxLibraryFilled",
            P("M6 5v14", width = 2.9f),
            P("M10.6 5v14", width = 2.9f),
            P("M14.6 6.4l4 12.8", width = 2.9f),
        )
    }

    val Pulse: ImageVector by lazy {
        icon("MaxPulse", P("M2.8 12h3l2.2-6.2 3.6 12.4 3-9.2 1.8 3h5.8", width = 1.9f))
    }

    val PulseFilled: ImageVector by lazy {
        icon("MaxPulseFilled", P("M2.8 12h3l2.2-6.2 3.6 12.4 3-9.2 1.8 3h5.8", width = 2.7f))
    }

    val QueueFilled: ImageVector by lazy {
        icon(
            "MaxQueueFilled",
            P("M4 7h10", width = 2.6f),
            P("M4 12h10", width = 2.6f),
            P("M4 17h5", width = 2.6f),
            P("M14.8 17a2 2 0 1 0 4 0a2 2 0 1 0-4 0", width = 2f, filled = true),
            P("M18.8 17V8.2l2.7 1.1", width = 2.2f),
        )
    }

    val Search: ImageVector by lazy {
        icon(
            "MaxSearch",
            P("M10.8 17.6a6.8 6.8 0 1 0 0-13.6a6.8 6.8 0 0 0 0 13.6z", width = 1.9f),
            P("M16 16l4.2 4.2", width = 1.9f),
        )
    }

    val SearchFilled: ImageVector by lazy {
        icon(
            "MaxSearchFilled",
            P("M10.8 17.6a6.8 6.8 0 1 0 0-13.6a6.8 6.8 0 0 0 0 13.6z", width = 2.6f),
            P("M16 16l4.2 4.2", width = 2.6f),
        )
    }

    val Sliders: ImageVector by lazy {
        icon(
            "MaxSliders",
            P("M4 7h8.2"),
            P("M17.8 7H20"),
            P("M12.2 7a2.8 2.8 0 1 0 5.6 0a2.8 2.8 0 1 0-5.6 0"),
            P("M4 17h2.2"),
            P("M11.8 17H20"),
            P("M6.2 17a2.8 2.8 0 1 0 5.6 0a2.8 2.8 0 1 0-5.6 0"),
        )
    }

    val ChevronRight: ImageVector by lazy {
        icon("MaxChevronRight", P("M9.5 6l6 6-6 6", width = 2f))
    }

    val Disc: ImageVector by lazy {
        icon(
            "MaxDisc",
            P("M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0-17 0"),
            P("M9.2 12a2.8 2.8 0 1 0 5.6 0a2.8 2.8 0 1 0-5.6 0"),
        )
    }
}
