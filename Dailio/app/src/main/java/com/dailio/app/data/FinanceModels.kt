package com.dailio.app.data

enum class TransactionType { INCOME, EXPENSE }

data class FinanceTransaction(
    val id: Long = 0L,
    val description: String,
    val amountMinor: Long,
    val type: TransactionType,
    val category: String,
    val occurredAt: Long
) {
    init {
        require(description.isNotBlank()) { "Description is required." }
        require(amountMinor > 0L) { "Amount must be greater than zero." }
        require(category.isNotBlank()) { "Category is required." }
        require(occurredAt > 0L) { "Transaction date is required." }
    }
}

data class FinanceSummary(
    val allIncomeMinor: Long,
    val allExpenseMinor: Long,
    val weekIncomeMinor: Long,
    val weekExpenseMinor: Long,
    val monthIncomeMinor: Long,
    val monthExpenseMinor: Long
) {
    val balanceMinor: Long get() = allIncomeMinor - allExpenseMinor
    val monthNetMinor: Long get() = monthIncomeMinor - monthExpenseMinor
}
