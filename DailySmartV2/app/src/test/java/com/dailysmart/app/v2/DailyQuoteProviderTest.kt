package com.dailysmart.app.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DailyQuoteProviderTest {
    @Test
    fun sameDayReturnsSameQuote() {
        assertEquals(
            DailyQuoteProvider.quoteFor(42, "en"),
            DailyQuoteProvider.quoteFor(42, "en")
        )
    }

    @Test
    fun quoteChangesByDayAndSupportsThreeLanguages() {
        assertNotEquals(
            DailyQuoteProvider.quoteFor(1, "en"),
            DailyQuoteProvider.quoteFor(2, "en")
        )
        assertEquals(
            "خطوات صغيرة تتكرر كل يوم تصنع تغييراً كبيراً.",
            DailyQuoteProvider.quoteFor(1, "ar")
        )
        assertEquals(
            "De petits pas répétés chaque jour créent de grands changements.",
            DailyQuoteProvider.quoteFor(1, "fr")
        )
    }

    @Test
    fun wrapsAfterLastQuoteAndDefaultsToEnglish() {
        assertEquals(
            DailyQuoteProvider.quoteFor(1, "en"),
            DailyQuoteProvider.quoteFor(11, "en")
        )
        assertEquals(
            DailyQuoteProvider.quoteFor(1, "en"),
            DailyQuoteProvider.quoteFor(1, "unknown")
        )
    }
}
