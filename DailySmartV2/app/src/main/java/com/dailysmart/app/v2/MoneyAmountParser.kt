package com.dailysmart.app.v2

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
        if (value.isBlank()) return null

        val comma = value.lastIndexOf(',')
        val dot = value.lastIndexOf('.')
        val decimalSeparator: Char?
        val groupingSeparator: Char?
        when {
            comma >= 0 && dot >= 0 -> {
                decimalSeparator = if (comma > dot) ',' else '.'
                groupingSeparator = if (decimalSeparator == ',') '.' else ','
                val integerPart = value.substringBefore(decimalSeparator)
                if (!validGroupedInteger(integerPart, groupingSeparator)) return null
                val fraction = value.substringAfterLast(decimalSeparator)
                if (fraction.length !in 1..2 || !fraction.all(Char::isDigit)) return null
            }
            comma >= 0 || dot >= 0 -> {
                val separator = if (comma >= 0) ',' else '.'
                val count = value.count { it == separator }
                val trailing = value.length - value.lastIndexOf(separator) - 1
                if (count == 1 && trailing in 1..2) {
                    decimalSeparator = separator
                    groupingSeparator = null
                } else if (trailing == 3 || count > 1) {
                    decimalSeparator = if (count > 1 && trailing in 1..2) separator else null
                    groupingSeparator = separator
                    val integerPart = if (decimalSeparator != null) value.substringBeforeLast(separator) else value
                    if (!validGroupedInteger(integerPart, separator)) return null
                    if (decimalSeparator != null) {
                        val fraction = value.substringAfterLast(separator)
                        if (fraction.length !in 1..2 || !fraction.all(Char::isDigit)) return null
                    }
                } else {
                    return null
                }
            }
            else -> {
                decimalSeparator = null
                groupingSeparator = null
            }
        }

        val normalized = buildString {
            value.forEach { ch ->
                when {
                    ch.isDigit() -> append(ch)
                    ch == decimalSeparator -> append('.')
                    ch == groupingSeparator || ch == ',' || ch == '.' -> Unit
                    else -> Unit
                }
            }
        }
        if (normalized.isBlank() || normalized == ".") return null
        val amount = normalized.toDoubleOrNull() ?: return null
        return amount.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun validGroupedInteger(value: String, separator: Char): Boolean {
        if (!value.contains(separator)) return value.isNotEmpty() && value.all(Char::isDigit)
        val groups = value.split(separator)
        return groups.isNotEmpty() && groups.first().length in 1..3 &&
            groups.first().all(Char::isDigit) &&
            groups.drop(1).all { it.length == 3 && it.all(Char::isDigit) }
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
