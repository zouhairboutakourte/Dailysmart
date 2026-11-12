package com.dailysmart.app.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReminderManagerTest {
    @Test
    fun convertsLocalDateAndTimeToExpectedInstant() {
        val date = LocalDate.of(2026, 11, 12)
        val zone = ZoneId.of("Africa/Casablanca")
        val expected = date.atTime(9, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, ReminderManager.epochMillis(date, 9, 30, zone))
    }

    @Test
    fun rejectsInvalidClockValues() {
        assertThrows(IllegalArgumentException::class.java) {
            ReminderManager.epochMillis(LocalDate.of(2026, 11, 12), 24, 0, ZoneId.of("UTC"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReminderManager.epochMillis(LocalDate.of(2026, 11, 12), 10, 60, ZoneId.of("UTC"))
        }
    }
}
