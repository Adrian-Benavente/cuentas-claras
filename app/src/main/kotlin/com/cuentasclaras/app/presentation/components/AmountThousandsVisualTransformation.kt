package com.cuentasclaras.app.presentation.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.cuentasclaras.app.util.MoneyFormatter

/**
 * Shows thousand separators (`.`) on amount input without storing them.
 * Comma (cents) is unchanged.
 */
object AmountThousandsVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val formatted = MoneyFormatter.groupThousands(original)
        return TransformedText(
            AnnotatedString(formatted),
            AmountThousandsOffsetMapping(original, formatted),
        )
    }
}

private class AmountThousandsOffsetMapping(
    private val original: String,
    private val formatted: String,
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        val clamped = offset.coerceIn(0, original.length)
        val intLen = original.indexOf(',').let { if (it < 0) original.length else it }
        val totalDots = if (intLen == 0) 0 else (intLen - 1) / 3
        return if (clamped <= intLen) {
            val dotsBefore = totalDots - (intLen - clamped) / 3
            (clamped + dotsBefore).coerceIn(0, formatted.length)
        } else {
            (clamped + totalDots).coerceIn(0, formatted.length)
        }
    }

    override fun transformedToOriginal(offset: Int): Int {
        val clamped = offset.coerceIn(0, formatted.length)
        val dotsBefore = formatted.take(clamped).count { it == '.' }
        return (clamped - dotsBefore).coerceIn(0, original.length)
    }
}
