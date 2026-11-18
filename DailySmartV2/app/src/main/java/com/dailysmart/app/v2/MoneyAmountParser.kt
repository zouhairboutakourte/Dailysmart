package com.dailysmart.app.v2

import java.util.Locale

/**
 * Shared positive MAD amount parser for manual entry, imports and assistant commands.
 * Accepts Arabic-Indic digits, decimal commas, grouping spaces and common currency labels.
 */
object MoneyAmountParser {
    fun parse(raw: String): Double? {
        if (raw.isBlank() || raw.length > 80) return null
        var value = normalizeDigits(raw).trim()
            .replace("\u00A0", "")
            .replace("\u202F", "")
            .replace(Regex("(?i)\\s+"), "")
            .replace(Regex("(?i)(mad|dhs?|dirhams?|درهم|دراهم|€)"), "")
            .replace("٫", ".")
            .replace("٬", ",")
        if (value.isBlank() || value.any { !(it.isDigit() || it == ',' || it == '.' || it == '+' || it == '-') }) return null
        if (value.startsWith("-") || value.count { it == '-' } > 0 || value.count { it == '+' } > 1) return null
        value = value.removePrefix("+")
        val comma = value.lastIndexOf(',')
        val dot = value.lastIndexOf('.')
        val decimalSeparator: Char? = when {
            comma >= 0 && dot >= 0 -> if (comma > dot) ',' else '.'
            comma >= 0 -> if (value.length - comma - 1 in 1..2) ',' else null
            dot >= 0 -> if (value.length - dot - 1 in 1..2) '.' else null
            else -> null
        }
        val normalized = buildString {
            value.forEachIndexed { index, ch ->
                when {
                    ch.isDigit() -> append(ch)
                    ch == decimalSeparator -> append('.')
                    ch == ',' || ch == '.' -> Unit
                    else -> return null
                }
            }
        }
        if (normalized.isBlank() || normalized == ".") return null
        val amount = normalized.toDoubleOrNull() ?: return null
        return amount.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun normalizeDigits(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            append(
                when (ch) {
                    in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
                    in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
                    else -> ch
                }
            )
        }
    }
}
