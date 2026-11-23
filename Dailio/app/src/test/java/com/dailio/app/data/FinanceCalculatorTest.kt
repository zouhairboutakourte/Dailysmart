package com.dailio.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FinanceCalculatorTest {
    @Test fun computesAllTimeWeekAndMonthFromTheSameTransactionSet() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 11, 23)
        val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        fun at(date: LocalDate) = date.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val rows = listOf(
            FinanceTransaction(1, "Salary", 880000, TransactionType.INCOME, "Work", at(today)),
            FinanceTransaction(2, "Groceries", 12550, TransactionType.EXPENSE, "Food", at(today)),
            FinanceTransaction(3, "October rent", 200000, TransactionType.EXPENSE, "Housing", at(today.minusMonths(1))),
            FinanceTransaction(4, "Taxi", 3000, TransactionType.EXPENSE, "Transport", at(today.minusDays(2))),
            FinanceTransaction(5, "Future", 99900, TransactionType.INCOME, "Other", at(today.plusDays(1)))
        )
        val result = FinanceCalculator.summarize(rows, now, zone)
        assertEquals(880000L, result.allIncomeMinor)
        assertEquals(215550L, result.allExpenseMinor)
        assertEquals(880000L, result.weekIncomeMinor)
        assertEquals(12550L, result.weekExpenseMinor)
        assertEquals(880000L, result.monthIncomeMinor)
        assertEquals(12550L, result.monthExpenseMinor)
        assertEquals(664450L, result.balanceMinor)
    }
}
