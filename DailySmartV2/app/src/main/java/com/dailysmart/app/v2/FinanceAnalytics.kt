package com.dailysmart.app.v2

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.util.Locale

data class FinancePeriodSummary(
    val weekIncome: Double,
    val weekExpense: Double,
    val monthIncome: Double,
    val monthExpense: Double,
    val topExpenseCategory: Pair<String, Double>?,
    val undatedRecords: Int
)

/** Date-aware finance calculations. Legacy records with no known date are never assigned a guessed date. */
object FinanceAnalytics {
    fun summarize(items: List<MoneyItem>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): FinancePeriodSummary {
        val dated = items.filter { it.createdAt > 0L }.mapNotNull { item ->
            runCatching { item to Instant.ofEpochMilli(item.createdAt).atZone(zone).toLocalDate() }.getOrNull()
        }
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val monthStart = today.withDayOfMonth(1)
        fun total(records: List<Pair<MoneyItem, LocalDate>>, income: Boolean) =
            records.filter { it.first.income == income }.sumOf { it.first.amount }
        val thisWeek = dated.filter { !it.second.isBefore(weekStart) && !it.second.isAfter(today) }
        val thisMonth = dated.filter { !it.second.isBefore(monthStart) && !it.second.isAfter(today) }
        val top = thisMonth.asSequence()
            .filter { !it.first.income }
            .groupBy { categoryFor(it.first.title) }
            .mapValues { (_, values) -> values.sumOf { it.first.amount } }
            .maxByOrNull { it.value }
            ?.let { it.key to it.value }
        return FinancePeriodSummary(
            weekIncome = total(thisWeek, true),
            weekExpense = total(thisWeek, false),
            monthIncome = total(thisMonth, true),
            monthExpense = total(thisMonth, false),
            topExpenseCategory = top,
            undatedRecords = items.count { it.createdAt <= 0L }
        )
    }

    fun dateString(timestamp: Long): String = if (timestamp <= 0L) "" else runCatching {
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }.getOrDefault("")

    fun timestampFromDate(value: String): Long = runCatching {
        LocalDate.parse(value.trim()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrDefault(0L)

    fun categoryFor(description: String): String {
        val text = description.lowercase(Locale.ROOT)
        return when {
            listOf("rent", "loyer", "كراء", "الكرا", "logement").any(text::contains) -> "Logement"
            listOf("fuel", "essence", "gasoline", "transport", "taxi", "tram", "bus", "بنزين", "مازوط", "طاكسي", "طوبيس").any(text::contains) -> "Transport"
            listOf("doctor", "pharmacy", "pharmacie", "medicine", "santé", "health", "دواء", "طبيب", "صيدلية", "صحة").any(text::contains) -> "Santé"
            listOf("electricity", "water bill", "internet", "phone bill", "facture", "فاتورة", "ضو", "ماء", "انترنت").any(text::contains) -> "Factures"
            listOf("grocery", "groceries", "marjane", "market", "food", "restaurant", "café", "coffee", "qhwa", "مقاضي", "خضرة", "ماكلة", "قهوة", "مطعم").any(text::contains) -> "Alimentation"
            listOf("cinema", "game", "games", "outing", "loisir", "ترفيه", "سينما").any(text::contains) -> "Loisirs"
            listOf("saving", "savings", "épargne", "ادخار", "توفير").any(text::contains) -> "Épargne"
            else -> "Divers"
        }
    }
}
