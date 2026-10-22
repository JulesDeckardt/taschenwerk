package de.taschenwerk.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun c(cx: Float, cy: Float, r: Float): String =
    "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0"

private fun stroke(name: String, vararg paths: String, w: Float = 1.8f): ImageVector {
    val b = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    )
    for (d in paths) {
        b.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = w,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
    return b.build()
}

private fun filled(name: String, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    )
    for (d in paths) {
        b.addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
    }
    return b.build()
}

object Ic {
    val home by lazy { stroke("home", "M4 10.5L12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1z") }
    val alarm by lazy { stroke("alarm", c(12f, 13f, 7.5f), "M12 9.5V13l2.5 2M4.5 4.5L2.8 6.2M19.5 4.5l1.7 1.7") }
    val calc by lazy {
        stroke(
            "calc", "M8 2.8h8a3 3 0 0 1 3 3v12.4a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3V5.8a3 3 0 0 1 3-3z",
            "M8.5 7h7M8.5 11.5h.01M12 11.5h.01M15.5 11.5h.01M8.5 15.5h.01M12 15.5h.01M15.5 15.5h.01"
        )
    }
    val cloud by lazy { stroke("cloud", "M7.5 19h9.5a4 4 0 0 0 .4-8 5.5 5.5 0 0 0-10.4 1.4A3.3 3.3 0 0 0 7.5 19z") }
    val mountain by lazy { stroke("mountain", "M2.5 19.5L9 8.5l4.2 6.4 2.3-3.2 6 8.8z") }
    val breath by lazy { stroke("breath", c(12f, 12f, 3.5f), c(12f, 12f, 8.5f)) }
    val more by lazy { filled("more", c(12f, 5f, 1.8f), c(12f, 12f, 1.8f), c(12f, 19f, 1.8f)) }
    val swapH by lazy { stroke("swapH", "M4 8h15M15.5 4.5L19 8l-3.5 3.5M20 16H5M8.5 12.5L5 16l3.5 3.5", w = 2f) }
    val money by lazy { stroke("money", "M3 6.5h18v11H3z", c(12f, 12f, 2.6f), "M6.5 10v4M17.5 10v4") }
    val grip by lazy { filled("grip", c(9f, 6f, 1.4f), c(15f, 6f, 1.4f), c(9f, 12f, 1.4f), c(15f, 12f, 1.4f), c(9f, 18f, 1.4f), c(15f, 18f, 1.4f)) }
    val dots by lazy { filled("dots", c(5.5f, 12f, 1.9f), c(12f, 12f, 1.9f), c(18.5f, 12f, 1.9f)) }
    val clock by lazy { stroke("clock", c(12f, 12f, 8.5f), "M12 7.8V12l3 2") }
    val note by lazy { stroke("note", "M9.5 17.5V6.2l9-2v11", c(7.2f, 17.5f, 2.3f), c(16.2f, 15.2f, 2.3f)) }
    val chevDown by lazy { stroke("chevDown", "M6 9l6 6 6-6", w = 2f) }
    val chevRight by lazy { stroke("chevRight", "M9 6l6 6-6 6", w = 2f) }
    val chevLeft by lazy { stroke("chevLeft", "M15 6l-6 6 6 6", w = 2f) }
    val chevUp by lazy { stroke("chevUp", "M6 15l6-6 6 6", w = 2f) }
    val plus by lazy { stroke("plus", "M12 5v14M5 12h14", w = 2f) }
    val minus by lazy { stroke("minus", "M5 12h14", w = 2f) }
    val close by lazy { stroke("close", "M6 6l12 12M18 6L6 18", w = 2f) }
    val check by lazy { stroke("check", "M5 12.5l4.5 4.5L19 7.5", w = 2.4f) }
    val play by lazy { filled("play", "M8 5.2v13.6L19 12z") }
    val pause by lazy {
        filled(
            "pause",
            "M7.7 5h1.4a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1-1.2 1.2H7.7a1.2 1.2 0 0 1-1.2-1.2V6.2A1.2 1.2 0 0 1 7.7 5z",
            "M14.9 5h1.4a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1-1.2 1.2h-1.4a1.2 1.2 0 0 1-1.2-1.2V6.2a1.2 1.2 0 0 1 1.2-1.2z"
        )
    }
    val flag by lazy { stroke("flag", "M6 21V4M6 4h11l-2.5 4 2.5 4H6", w = 2f) }
    val reset by lazy { stroke("reset", "M4 12a8 8 0 1 0 2.3-5.6M4 4v5h5", w = 2f) }
    val refresh by lazy { stroke("refresh", "M20 12a8 8 0 1 1-2.3-5.6M20 4v5h-5", w = 2f) }
    val skipNext by lazy { stroke("skipNext", "M6 5l9 7-9 7zM18 5v14", w = 2f) }
    val skipPrev by lazy { stroke("skipPrev", "M18 5l-9 7 9 7zM6 5v14", w = 2f) }
    val swap by lazy { stroke("swap", "M8 19V4M4.5 7.5L8 4l3.5 3.5M16 5v15M12.5 16.5L16 20l3.5-3.5", w = 2f) }
    val pin by lazy { stroke("pin", "M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z", c(12f, 9.5f, 2.5f), w = 2f) }
    val sun by lazy {
        stroke(
            "sun", c(12f, 12f, 4f),
            "M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"
        )
    }
    val moon by lazy { stroke("moon", "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z") }
    val rain by lazy {
        stroke(
            "rain", "M7 15h10a4 4 0 0 0 0-8 5 5 0 0 0-9.6 1.5A3.3 3.3 0 0 0 7 15z",
            "M9 18l-1 2.5M13 18l-1 2.5M17 18l-1 2.5"
        )
    }
    val storm by lazy {
        stroke(
            "storm", "M7 15h10a4 4 0 0 0 0-8 5 5 0 0 0-9.6 1.5A3.3 3.3 0 0 0 7 15z",
            "M12.5 15l-2 3.5h3l-2 3.5"
        )
    }
    val snow by lazy {
        stroke(
            "snow", "M7 14h10a4 4 0 0 0 0-8 5 5 0 0 0-9.6 1.5A3.3 3.3 0 0 0 7 14z",
            "M9 18h.01M12 20h.01M15 18h.01M12 17h.01"
        )
    }
    val fog by lazy { stroke("fog", "M4 9h16M3 13h18M5 17h14") }
    val cloudSun by lazy {
        stroke(
            "cloudSun", c(9f, 8.5f, 3.2f), "M9 2.3v1.4M2.8 8.5H4.2M4.6 4.1l1 1M13.4 4.1l-1 1",
            "M8.5 20h9a3.5 3.5 0 0 0 0-7 5 5 0 0 0-9.3 1.2A2.9 2.9 0 0 0 8.5 20z"
        )
    }
    /** Überwiegend sonnig: große Sonne, kleine Wolke unten rechts. */
    val sunCloudSmall by lazy {
        stroke(
            "sunCloudSmall", c(10f, 9.5f, 4f),
            "M10 2.3v1.4M2.8 9.5h1.4M4.9 4.4l1 1M15.1 4.4l-1 1M4.9 14.6l1-1M17.2 9.5h-1",
            "M14.8 20.5h4.7a2.4 2.4 0 0 0 0-4.8 3.4 3.4 0 0 0-6.4 1A1.9 1.9 0 0 0 14.8 20.5z"
        )
    }

    /** Stark bewölkt: große Wolke, nur ein Rand der Sonne schaut hervor. */
    val cloudSunMost by lazy {
        stroke(
            "cloudSunMost", "M5.2 10.6a3.6 3.6 0 0 1 6.3-3.4",
            "M8.4 2.6v1.3M3 5.4l.9.9M2.3 9.6h1.3",
            "M7 20.5h10.5a4 4 0 0 0 .4-8 5.5 5.5 0 0 0-10.4 1.4A3.3 3.3 0 0 0 7 20.5z"
        )
    }

    /** Nacht, teils bewölkt: Mond mit Wolke. */
    val moonCloud by lazy {
        stroke(
            "moonCloud", "M11.5 8.6A4.2 4.2 0 0 1 6 3a4.6 4.6 0 1 0 5.5 5.6z",
            "M8.5 20h9a3.5 3.5 0 0 0 0-7 5 5 0 0 0-9.3 1.2A2.9 2.9 0 0 0 8.5 20z"
        )
    }

    /** Schauer: Sonne, daneben Wolke mit Regen. */
    val showerSun by lazy {
        stroke(
            "showerSun", "M4.4 7a2.6 2.6 0 1 0 5.2 0a2.6 2.6 0 1 0 -5.2 0",
            "M7 1.8v1.3M1.8 7h1.3M3.3 3.3l.9.9M10.7 3.3l-.9.9M3.3 10.7l.9-.9",
            "M9.5 17.5h8.5a3.3 3.3 0 0 0 0-6.6 4.6 4.6 0 0 0-8.6 1.1A2.8 2.8 0 0 0 9.5 17.5z",
            "M11.6 19.8l-.8 2.2M14.6 19.8l-.8 2.2M17.6 19.8l-.8 2.2"
        )
    }

    /** Gewitter mit Sonne. */
    val stormSun by lazy {
        stroke(
            "stormSun", "M4.4 7a2.6 2.6 0 1 0 5.2 0a2.6 2.6 0 1 0 -5.2 0",
            "M7 1.8v1.3M1.8 7h1.3M3.3 3.3l.9.9M10.7 3.3l-.9.9M3.3 10.7l.9-.9",
            "M9.5 17.5h8.5a3.3 3.3 0 0 0 0-6.6 4.6 4.6 0 0 0-8.6 1.1A2.8 2.8 0 0 0 9.5 17.5z",
            "M14.6 18.6l-1.9 2.9h2.7l-1.7 2.5"
        )
    }

    val droplet by lazy { stroke("droplet", "M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z", w = 2f) }
    val wind by lazy { stroke("wind", "M3 9h11a3 3 0 1 0-3-3M3 15h15a3 3 0 1 1-3 3", w = 2f) }
    val thermo by lazy { stroke("thermo", "M10 14.5V5a2 2 0 0 1 4 0v9.5a4 4 0 1 1-4 0z", w = 2f) }
    val crosshair by lazy { stroke("crosshair", c(12f, 12f, 7f), c(12f, 12f, 2f), "M12 2v3M12 19v3M2 12h3M19 12h3") }
    val map by lazy { stroke("map", "M9 4L3 6v14l6-2 6 2 6-2V4l-6 2zM9 4v14M15 6v14") }
    val gauge by lazy { stroke("gauge", "M4 16a8 8 0 1 1 16 0", "M12 16l3.5-4.5") }
    val hourglass by lazy { stroke("hourglass", "M6 3h12M6 21h12M7 3c0 5 10 5 10 9s-10 4-10 9M17 3c0 5-10 5-10 9") }
    val pulse by lazy { stroke("pulse", "M3 12h3l2.5-6 4 12 3-9 2 3H21") }
    val stopwatch by lazy { stroke("stopwatch", c(12f, 13.5f, 7.5f), "M12 13.5l2.5-3M10 2.5h4") }
    val percent by lazy { stroke("percent", "M19 5L5 19", c(7f, 7f, 2.5f), c(17f, 17f, 2.5f)) }
    val backspace by lazy { stroke("backspace", "M9 5h11v14H9l-6-7z", "M12 9.5l5 5M17 9.5l-5 5", w = 1.9f) }
    val history by lazy { stroke("history", "M4 12a8 8 0 1 0 2.3-5.6M4 4v5h5", "M12 8v4l3 2") }
    val info by lazy { stroke("info", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M12 11v5.5", "M12 7.6v.4", w = 1.8f) }
    val speaker by lazy { stroke("speaker", "M4 10v4h4l5 4V6L8 10z", "M16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12", w = 1.9f) }
    val speakerOff by lazy { stroke("speakerOff", "M4 10v4h4l5 4V6L8 10z", "M17 9.5l5 5M22 9.5l-5 5", w = 1.9f) }
    val ruler by lazy {
        stroke(
            "ruler", "M4 8h16a1.5 1.5 0 0 1 1.5 1.5v5A1.5 1.5 0 0 1 20 16H4a1.5 1.5 0 0 1-1.5-1.5v-5A1.5 1.5 0 0 1 4 8z",
            "M6.5 8v3M10 8v4M13.5 8v3M17 8v4"
        )
    }
    val area by lazy { stroke("area", "M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M4 14L14 4M10 20L20 10") }
    val cube by lazy { stroke("cube", "M12 3l8 4.5v9L12 21l-8-4.5v-9z", "M4 7.5l8 4.5 8-4.5M12 12v9") }
    val weight by lazy { stroke("weight", "M6.5 9h11l2.5 11H4z", c(12f, 5.5f, 2.5f)) }
    val coin by lazy {
        stroke(
            "coin", c(12f, 12f, 8.5f),
            "M14.8 9.2c-.5-.9-1.6-1.4-2.8-1.4-1.6 0-2.8.8-2.8 2s1.2 1.7 2.8 2.1 2.8.9 2.8 2.1-1.2 2-2.8 2c-1.3 0-2.4-.6-2.9-1.5M12 6.2v1.6M12 16.2v1.6"
        )
    }
    val speed by lazy { stroke("speed", "M4 17a8 8 0 1 1 16 0", "M12 17l4-5") }
    val pressure by lazy { stroke("pressure", c(12f, 13f, 7.5f), "M12 13V9M10 3h4") }
    val bolt by lazy { stroke("bolt", "M13 2L5 13.5h6L10 22l9-12h-6z") }
    val binary by lazy { stroke("binary", "M7 5v6M5 5h2M5 11h4", c(16f, 8f, 2.5f), c(8f, 16f, 2.5f), "M17 13v6M15 13h2M15 19h4") }
    val trash by lazy { stroke("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3") }
    val edit by lazy { stroke("edit", "M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4") }
    val globe by lazy { stroke("globe", c(12f, 12f, 9f), "M3 12h18M12 3c3 3.5 3 14.5 0 18M12 3c-3 3.5-3 14.5 0 18") }
    val bell by lazy { stroke("bell", "M6 16V11a6 6 0 0 1 12 0v5l2 2H4z", "M10 20a2 2 0 0 0 4 0") }
    val battery by lazy { stroke("battery", "M5 7h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z", "M21 10.5v3") }
    val search by lazy { stroke("search", c(11f, 11f, 6.5f), "M16 16l4.5 4.5", w = 2f) }
}
