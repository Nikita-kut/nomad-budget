package ru.nomadbudget.presentation.format

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

class ThousandsVisualTransformationTest {

    private val transformation = ThousandsVisualTransformation()

    @Test
    fun groups_integerPart() {
        assertEquals("5 000 000", transformation.filter(AnnotatedString("5000000")).text.text)
        assertEquals("50 000", transformation.filter(AnnotatedString("50000")).text.text)
        assertEquals("500", transformation.filter(AnnotatedString("500")).text.text)
        assertEquals("", transformation.filter(AnnotatedString("")).text.text)
    }

    @Test
    fun keeps_fractionUngrouped() {
        assertEquals("1 234,5678", transformation.filter(AnnotatedString("1234,5678")).text.text)
    }

    @Test
    fun offsetMapping_roundTrips() {
        val result = transformation.filter(AnnotatedString("1234567"))
        val mapping = result.offsetMapping
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(2, mapping.originalToTransformed(1))
        assertEquals(3, mapping.originalToTransformed(2))
        assertEquals(9, mapping.originalToTransformed(7))
        assertEquals(7, mapping.transformedToOriginal(9))
        assertEquals(2, mapping.transformedToOriginal(3))
        assertEquals(1, mapping.transformedToOriginal(2))
    }

    @Test
    fun sanitize_keepsDigitsAndOneSeparator() {
        assertEquals("1234,5", ThousandsVisualTransformation.sanitize("1 234.5"))
        assertEquals("1234,5", ThousandsVisualTransformation.sanitize("1,234.5"))
        assertEquals("12", ThousandsVisualTransformation.sanitize("1a2"))
    }
}
