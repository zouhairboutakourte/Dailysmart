package com.dailio.app.data

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

object FinanceCalculator {
    fun summarize(
        transactions: List<FinanceTransaction>,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): FinanceSummary {
        val nowDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val weekStart = nowDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val monthStart = nowDate.withDayOfMonth(1)
        val weekStartMillis = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val monthStartMillis = monthStart.atStartOfDay(zone).toInstant().toEpochMilli()

        fun total(rows: List<FinanceTransaction>, type: TransactionType, from: Long? = null): Long =
            rows.asSequence()
                .filter { it.type == type && (from == null || it.occurredAt >= from) && it.occurredAt <= nowMillis }
                .fold(0L) { sum, row -> Math.addExact(sum, row.amountMinor) }

        return FinanceSummary(
            allIncomeMinor = total(transactions, TransactionType.INCOME),
            allExpenseMinor = total(transactions, TransactionType.EXPENSE),
            weekIncomeMinor = total(transactions, TransactionType.INCOME, weekStartMillis),
            weekExpenseMinor = total(transactions, TransactionType.EXPENSE, weekStartMillis),
            monthIncomeMinor = total(transactions, TransactionType.INCOME, monthStartMillis),
            monthExpenseMinor = total(transactions, TransactionType.EXPENSE, monthStartMillis)
        )
    }
}
