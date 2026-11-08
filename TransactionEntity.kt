package com.dailysmart.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransactionType { INCOME, EXPENSE }

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val dateIso: String,
    val notes: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)