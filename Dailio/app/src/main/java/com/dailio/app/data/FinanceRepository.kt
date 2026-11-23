package com.dailio.app.data

class FinanceRepository(private val database: FinanceDatabase) {
    fun add(
        description: String,
        amountMinor: Long,
        type: TransactionType,
        category: String,
        occurredAt: Long = System.currentTimeMillis()
    ): Long = database.insert(
        FinanceTransaction(
            description = description.trim(),
            amountMinor = amountMinor,
            type = type,
            category = category.trim(),
            occurredAt = occurredAt
        )
    )

    fun delete(id: Long): Boolean = database.delete(id) == 1
    fun all(): List<FinanceTransaction> = database.all()
    fun close() = database.close()
}
