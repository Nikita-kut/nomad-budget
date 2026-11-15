package ru.nomadbudget.presentation.tickets

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

actual fun encodePng(bitmap: ImageBitmap): ByteArray {
    val out = ByteArrayOutputStream()
    bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}

actual fun softwareCopy(bitmap: ImageBitmap): ImageBitmap =
    bitmap.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false).asImageBitmap()
