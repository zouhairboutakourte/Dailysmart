package com.dailysmart.app.v2

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FinanceAnalyticsTest {
    @Test
    fun periodTotalsExcludeLegacyUndatedRecords() {
        val today = LocalDate.of(2026, 11, 12)
        val zone = ZoneId.of("UTC")
        fun at(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val records = listOf(
            MoneyItem("Groceries", 120.0, false, at(today)),
            MoneyItem("Salary", 8000.0, true, at(today)),
            MoneyItem("Taxi", 30.0, false, at(today.minusDays(2))),
            MoneyItem("Old expense", 500.0, false, 0L),
            MoneyItem("October rent", 2000.0, false, at(today.minusMonths(1)))
        )
        val result = FinanceAnalytics.summarize(records, today, zone)
        assertEquals(150.0, result.weekExpense, 0.001)
        assertEquals(150.0, result.monthExpense, 0.001)
        assertEquals(8000.0, result.monthIncome, 0.001)
        assertEquals(1, result.undatedRecords)
        assertEquals("Alimentation", result.topExpenseCategory?.first)
        assertEquals(listOf("Alimentation" to 120.0, "Transport" to 30.0), result.categoryExpenses)
        assertEquals(8000.0, result.weekIncome, 0.001)
    }

    @Test
    fun categoryBreakdownIsSortedAndIgnoresIncomeAndUndatedExpenses() {
        val today = LocalDate.of(2026, 11, 12)
        val zone = ZoneId.of("UTC")
        fun at(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val records = listOf(
            MoneyItem("Taxi", 50.0, false, at(today)),
            MoneyItem("Groceries", 90.0, false, at(today)),
            MoneyItem("Salary", 9000.0, true, at(today)),
            MoneyItem("Old coffee", 300.0, false, 0L)
        )
        val result = FinanceAnalytics.summarize(records, today, zone)
        assertEquals(listOf("Alimentation" to 90.0, "Transport" to 50.0), result.categoryExpenses)
    }

    @Test
    fun dateConversionKeepsUnknownDatesUnknown() {
        assertEquals(0L, FinanceAnalytics.timestampFromDate(""))
        val date = LocalDate.of(2026, 11, 12)
        val timestamp = FinanceAnalytics.timestampFromDate(date.toString())
        assertEquals(date.toString(), FinanceAnalytics.dateString(timestamp))
    }

    @Test
    fun categoryDetectionUnderstandsCommonDarijaTerms() {
        assertEquals("Alimentation", FinanceAnalytics.categoryFor("قهوة"))
        assertEquals("Transport", FinanceAnalytics.categoryFor("essence"))
        assertEquals("Santé", FinanceAnalytics.categoryFor("pharmacie"))
        assertEquals("Divers", FinanceAnalytics.categoryFor("something unknown"))
    }
}
