package com.dailio.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class FinanceDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                description TEXT NOT NULL,
                amount_minor INTEGER NOT NULL CHECK(amount_minor > 0),
                type TEXT NOT NULL CHECK(type IN ('INCOME', 'EXPENSE')),
                category TEXT NOT NULL,
                occurred_at INTEGER NOT NULL CHECK(occurred_at > 0)
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX idx_transactions_occurred_at ON transactions(occurred_at)")
        db.execSQL("CREATE INDEX idx_transactions_type_date ON transactions(type, occurred_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future migrations must be explicit and additive. Never drop financial records.
    }

    fun insert(transaction: FinanceTransaction): Long {
        val values = ContentValues().apply {
            put("description", transaction.description.trim())
            put("amount_minor", transaction.amountMinor)
            put("type", transaction.type.name)
            put("category", transaction.category.trim())
            put("occurred_at", transaction.occurredAt)
        }
        return writableDatabase.insertOrThrow("transactions", null, values)
    }

    fun delete(id: Long): Int =
        writableDatabase.delete("transactions", "id = ?", arrayOf(id.toString()))

    fun all(): List<FinanceTransaction> {
        val result = mutableListOf<FinanceTransaction>()
        readableDatabase.query(
            "transactions",
            arrayOf("id", "description", "amount_minor", "type", "category", "occurred_at"),
            null, null, null, null, "occurred_at DESC, id DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += FinanceTransaction(
                    id = cursor.getLong(0),
                    description = cursor.getString(1),
                    amountMinor = cursor.getLong(2),
                    type = TransactionType.valueOf(cursor.getString(3)),
                    category = cursor.getString(4),
                    occurredAt = cursor.getLong(5)
                )
            }
        }
        return result
    }

    companion object {
        const val DATABASE_NAME = "dailio_finance.db"
        private const val DATABASE_VERSION = 1
    }
}
