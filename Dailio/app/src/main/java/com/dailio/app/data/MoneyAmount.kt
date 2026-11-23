package com.dailio.app.data

import java.math.BigDecimal
import java.math.RoundingMode

/** Converts user-entered MAD amounts into exact integer centimes. */
object MoneyAmount {
    private val validAmount = Regex("^\\d{1,12}(?:[.,]\\d{1,2})?$")

    fun parseMinorUnits(raw: String): Long? {
        val normalized = raw.trim().replace(" ", "").replace("\u00A0", "").replace(',', '.')
        if (!validAmount.matches(normalized)) return null
        return runCatching {
            val value = BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY)
            val minor = value.movePointRight(2).longValueExact()
            minor.takeIf { it > 0L }
        }.getOrNull()
    }

    fun formatMinorUnits(minor: Long): String =
        BigDecimal.valueOf(minor, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString()
}
