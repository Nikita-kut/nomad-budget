package ru.nomadbudget.presentation.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

object CaptureMask {
    var active: Boolean by mutableStateOf(false)
}

private const val BULLETS = "•••"

object MaskedInput : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val length = text.length
        return TransformedText(
            AnnotatedString(BULLETS),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = offset.coerceIn(0, BULLETS.length)
                override fun transformedToOriginal(offset: Int): Int = offset.coerceIn(0, length)
            },
        )
    }
}

@Composable
fun captureMask(): VisualTransformation = if (CaptureMask.active) MaskedInput else VisualTransformation.None

@Composable
fun rememberAmountTransformation(): VisualTransformation {
    val thousands = remember { ThousandsVisualTransformation() }
    return if (CaptureMask.active) MaskedInput else thousands
}
