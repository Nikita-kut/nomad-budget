package ru.nomadbudget.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object AppIcons {
    val Swap: ImageVector by lazy {
        icon("Swap") {
        path("M8 3 4 7l4 4")
        path("M4 7h16")
        path("m16 21 4-4-4-4")
        path("M20 17H4")
        }
    }

    val ArrowLeft: ImageVector by lazy {
        icon("ArrowLeft") {
        path("m12 19-7-7 7-7")
        path("M19 12H5")
        }
    }

    val ArrowRight: ImageVector by lazy {
        icon("ArrowRight") {
        path("M5 12h14")
        path("m12 5 7 7-7 7")
        }
    }

    val CalendarDays: ImageVector by lazy {
        icon("CalendarDays") {
        path("M8 2v4")
        path("M16 2v4")
        path("M5.0 4.0h14.0a2.0 2.0 0 0 1 2.0 2.0v14.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-14.0a2.0 2.0 0 0 1 2.0 -2.0Z")
        path("M3 10h18")
        path("M8 14h.01")
        path("M12 14h.01")
        path("M16 14h.01")
        path("M8 18h.01")
        path("M12 18h.01")
        path("M16 18h.01")
        }
    }

    val Calendar: ImageVector by lazy {
        icon("Calendar") {
        path("M8 2v4")
        path("M16 2v4")
        path("M5.0 4.0h14.0a2.0 2.0 0 0 1 2.0 2.0v14.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-14.0a2.0 2.0 0 0 1 2.0 -2.0Z")
        path("M3 10h18")
        }
    }

    val ChartColumn: ImageVector by lazy {
        icon("ChartColumn") {
        path("M3 3v16a2 2 0 0 0 2 2h16")
        path("M18 17V9")
        path("M13 17V5")
        path("M8 17v-3")
        }
    }

    val ChartLine: ImageVector by lazy {
        icon("ChartLine") {
        path("M3 3v16a2 2 0 0 0 2 2h16")
        path("M7 16c.5-2 1.5-7 4-7 2 0 2 3 4 3 2.5 0 4.5-5 5-7")
        }
    }

    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") {
        path("m6 9 6 6 6-6")
        }
    }

    val ChevronLeft: ImageVector by lazy {
        icon("ChevronLeft") {
        path("m15 18-6-6 6-6")
        }
    }

    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
        path("m9 18 6-6-6-6")
        }
    }

    val ChevronUp: ImageVector by lazy {
        icon("ChevronUp") {
        path("m18 15-6-6-6 6")
        }
    }

    val Plan: ImageVector by lazy {
        icon("Plan") {
        path("M9.0 2.0h6.0a1.0 1.0 0 0 1 1.0 1.0v2.0a1.0 1.0 0 0 1 -1.0 1.0h-6.0a1.0 1.0 0 0 1 -1.0 -1.0v-2.0a1.0 1.0 0 0 1 1.0 -1.0Z")
        path("M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2")
        path("M12 11h4")
        path("M12 16h4")
        path("M8 11h.01")
        path("M8 16h.01")
        }
    }

    val CreditCard: ImageVector by lazy {
        icon("CreditCard") {
        path("M4.0 5.0h16.0a2.0 2.0 0 0 1 2.0 2.0v10.0a2.0 2.0 0 0 1 -2.0 2.0h-16.0a2.0 2.0 0 0 1 -2.0 -2.0v-10.0a2.0 2.0 0 0 1 2.0 -2.0Z")
        path("M2 10L22 10")
        }
    }

    val MoreVertical: ImageVector by lazy {
        icon("MoreVertical") {
        path("M11.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0")
        path("M11.0 5.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0")
        path("M11.0 19.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0")
        }
    }

    val More: ImageVector by lazy {
        icon("More") {
        path("M11.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0")
        path("M18.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0")
        path("M4.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0")
        }
    }

    val Info: ImageVector by lazy {
        icon("Info") {
        path("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0")
        path("M12 16v-4")
        path("M12 8h.01")
        }
    }

    val Merge: ImageVector by lazy {
        icon("Merge") {
        path("m8 6 4-4 4 4")
        path("M12 2v10.3a4 4 0 0 1-1.172 2.872L4 22")
        path("m20 22-5-5")
        }
    }

    val Moon: ImageVector by lazy {
        icon("Moon") {
        path("M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z")
        }
    }

    val Edit: ImageVector by lazy {
        icon("Edit") {
        path("M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z")
        path("m15 5 4 4")
        }
    }

    val PiggyBank: ImageVector by lazy {
        icon("PiggyBank") {
        path("M19 5c-1.5 0-2.8 1.4-3 2-3.5-1.5-11-.3-11 5 0 1.8 0 3 2 4.5V20h4v-2h3v2h4v-4c1-.5 1.7-1 2-2h2v-4h-2c0-1-.5-1.5-1-2V5z")
        path("M2 9v1c0 1.1.9 2 2 2h1")
        path("M16 11h.01")
        }
    }

    val Add: ImageVector by lazy {
        icon("Add") {
        path("M5 12h14")
        path("M12 5v14")
        }
    }

    val Receipt: ImageVector by lazy {
        icon("Receipt") {
        path("M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z")
        path("M14 8H8")
        path("M16 12H8")
        path("M13 16H8")
        }
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
        path("M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8")
        path("M21 3v5h-5")
        path("M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16")
        path("M8 16H3v5")
        }
    }

    val Search: ImageVector by lazy {
        icon("Search") {
        path("M3.0 11.0a8.0 8.0 0 1 0 16.0 0a8.0 8.0 0 1 0 -16.0 0")
        path("m21 21-4.3-4.3")
        }
    }

    val Sun: ImageVector by lazy {
        icon("Sun") {
        path("M8.0 12.0a4.0 4.0 0 1 0 8.0 0a4.0 4.0 0 1 0 -8.0 0")
        path("M12 2v2")
        path("M12 20v2")
        path("m4.93 4.93 1.41 1.41")
        path("m17.66 17.66 1.41 1.41")
        path("M2 12h2")
        path("M20 12h2")
        path("m6.34 17.66-1.41 1.41")
        path("m19.07 4.93-1.41 1.41")
        }
    }

    val Trash: ImageVector by lazy {
        icon("Trash") {
        path("M3 6h18")
        path("M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6")
        path("M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2")
        path("M10 11L10 17")
        path("M14 11L14 17")
        }
    }

    val Wallet: ImageVector by lazy {
        icon("Wallet") {
        path("M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1")
        path("M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4")
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
        path("M18 6 6 18")
        path("m6 6 12 12")
        }
    }

    private class IconScope(private val builder: ImageVector.Builder) {
        fun path(data: String) {
            builder.addPath(
                pathData = addPathNodes(data),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }

    private fun icon(name: String, block: IconScope.() -> Unit): ImageVector {
        val builder = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        IconScope(builder).block()
        return builder.build()
    }

    private const val STROKE = 1.75f
}
