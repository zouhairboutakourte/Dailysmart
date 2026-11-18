package com.dailysmart.app.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyAmountParserTest {
    @Test fun parsesFrenchAndArabicDecimalSeparators() {
        assertEquals(12.5, MoneyAmountParser.parse("12,5")!!, 0.000001)
        assertEquals(12.5, MoneyAmountParser.parse("12٫5")!!, 0.000001)
        assertEquals(12.5, MoneyAmountParser.parse("١٢٫٥")!!, 0.000001)
    }

    @Test fun parsesThousandsAndMixedLocaleFormats() {
        assertEquals(1500.0, MoneyAmountParser.parse("1 500 DH")!!, 0.000001)
        assertEquals(1500.0, MoneyAmountParser.parse("1,500 MAD")!!, 0.000001)
        assertEquals(1234.5, MoneyAmountParser.parse("1,234.50")!!, 0.000001)
        assertEquals(1234.5, MoneyAmountParser.parse("1.234,50")!!, 0.000001)
        assertEquals(500.0, MoneyAmountParser.parse("٥٠٠ درهم")!!, 0.000001)
    }

    @Test fun rejectsInvalidOrUnsafeAmounts() {
        listOf("", "0", "-5", "NaN", "Infinity", "12,34,56", "abc", "1.2.3").forEach {
            assertNull("Expected invalid amount: $it", MoneyAmountParser.parse(it))
        }
    }
}
