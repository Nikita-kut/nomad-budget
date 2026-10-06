package ru.nomadbudget.presentation.format

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class ThousandsVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val separatorIndex = raw.indexOfFirst { it == ',' || it == '.' }
        val integerLength = if (separatorIndex >= 0) separatorIndex else raw.length
        val out = StringBuilder()
        val originalToTransformed = IntArray(raw.length + 1)
        var inserted = 0
        raw.forEachIndexed { index, char ->
            val inIntegerPart = index < integerLength
            val remaining = integerLength - index
            if (inIntegerPart && index > 0 && remaining % GROUP == 0) {
                out.append(SEPARATOR)
                inserted++
            }
            originalToTransformed[index] = index + inserted
            out.append(char)
        }
        originalToTransformed[raw.length] = raw.length + inserted
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                originalToTransformed[offset.coerceIn(0, raw.length)]

            override fun transformedToOriginal(offset: Int): Int {
                var original = 0
                while (original < raw.length && originalToTransformed[original + 1] <= offset) original++
                return original
            }
        }
        return TransformedText(AnnotatedString(out.toString()), mapping)
    }

    companion object {
        private const val GROUP = 3
        private const val SEPARATOR = ' '

        fun sanitize(input: String, maxFraction: Int): String {
            val clean = sanitize(input)
            val comma = clean.indexOf(',')
            return when {
                comma < 0 -> clean
                maxFraction <= 0 -> clean.substring(0, comma)
                else -> clean.substring(0, (comma + 1 + maxFraction).coerceAtMost(clean.length))
            }
        }

        fun sanitize(input: String): String {
            val separators = input.withIndex().filter { it.value == ',' || it.value == '.' }
            val decimalIndex = when {
                separators.isEmpty() -> -1
                separators.size == 1 -> separators.single().index
                separators.map { it.value }.distinct().size == 2 -> separators.last().index
                else -> -1
            }
            val integerEnd = if (decimalIndex < 0) input.length else decimalIndex
            val integerDigits = input.substring(0, integerEnd).filter(Char::isDigit)
            if (decimalIndex < 0) return integerDigits
            val fractionDigits = input.substring(decimalIndex + 1).filter(Char::isDigit)
            return "$integerDigits,$fractionDigits"
        }
    }
}
