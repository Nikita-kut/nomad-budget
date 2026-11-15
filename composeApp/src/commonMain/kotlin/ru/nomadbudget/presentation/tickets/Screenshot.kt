package ru.nomadbudget.presentation.tickets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

expect fun encodePng(bitmap: ImageBitmap): ByteArray

const val MAX_SCREENSHOT_BYTES = 2 * 1024 * 1024
private const val PIXEL = 14
private const val KEEP_WIDTH = 0.36f
private const val KEEP_HEIGHT = 0.3f

fun keptArea(mark: Offset, width: Int, height: Int): Rect {
    val w = width * KEEP_WIDTH
    val h = height * KEEP_HEIGHT
    val left = (mark.x * width - w / 2).coerceIn(0f, max(0f, width - w))
    val top = (mark.y * height - h / 2).coerceIn(0f, max(0f, height - h))
    return Rect(left, top, left + w, top + h)
}

fun composeScreenshot(source: ImageBitmap, mark: Offset?, blurRest: Boolean): ImageBitmap {
    val width = source.width
    val height = source.height
    val out = ImageBitmap(width, height)
    val canvas = Canvas(out)
    val paint = Paint()
    val full = IntSize(width, height)
    if (blurRest) {
        val small = ImageBitmap(max(1, width / PIXEL), max(1, height / PIXEL))
        Canvas(small).drawImageRect(source, IntOffset.Zero, full, IntOffset.Zero, IntSize(small.width, small.height), Paint().apply { filterQuality = FilterQuality.Low })
        canvas.drawImageRect(small, IntOffset.Zero, IntSize(small.width, small.height), IntOffset.Zero, full, Paint().apply { filterQuality = FilterQuality.None })
        mark?.let {
            val keep = keptArea(it, width, height)
            val offset = IntOffset(keep.left.roundToInt(), keep.top.roundToInt())
            val size = IntSize(keep.width.roundToInt(), keep.height.roundToInt())
            canvas.drawImageRect(source, offset, size, offset, size, paint)
        }
    } else {
        canvas.drawImage(source, Offset.Zero, paint)
    }
    mark?.let {
        val center = Offset(it.x * width, it.y * height)
        val radius = max(width, height) * 0.02f
        canvas.drawCircle(center, radius, Paint().apply { color = Color(0xFFE53935); style = PaintingStyle.Stroke; strokeWidth = radius * 0.35f })
    }
    return out
}

fun scaled(source: ImageBitmap, factor: Float): ImageBitmap {
    val out = ImageBitmap(max(1, (source.width * factor).roundToInt()), max(1, (source.height * factor).roundToInt()))
    Canvas(out).drawImageRect(source, IntOffset.Zero, IntSize(source.width, source.height), IntOffset.Zero, IntSize(out.width, out.height), Paint().apply { filterQuality = FilterQuality.Medium })
    return out
}

fun pngWithinLimit(bitmap: ImageBitmap, limit: Int = MAX_SCREENSHOT_BYTES): ByteArray? {
    var current = bitmap
    repeat(4) {
        val png = encodePng(current)
        if (png.size <= limit) return png
        current = scaled(current, 0.7f)
    }
    return null
}
