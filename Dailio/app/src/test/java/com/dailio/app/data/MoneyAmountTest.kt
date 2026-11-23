package com.dailio.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyAmountTest {
    @Test fun parsesDotCommaAndGroupedAmountsExactly() {
        assertEquals(123450L, MoneyAmount.parseMinorUnits("1234.50"))
        assertEquals(123450L, MoneyAmount.parseMinorUnits("1234,50"))
        assertEquals(100L, MoneyAmount.parseMinorUnits("1"))
        assertEquals(100000L, MoneyAmount.parseMinorUnits("1 000,00"))
    }

    @Test fun rejectsInvalidOrAmbiguousAmounts() {
        listOf("", "0", "-10", "NaN", "Infinity", "1.234", "1,2,3", "abc").forEach {
            assertNull("Should reject $it", MoneyAmount.parseMinorUnits(it))
        }
    }
}
