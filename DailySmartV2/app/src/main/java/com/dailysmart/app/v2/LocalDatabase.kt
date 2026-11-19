package com.dailysmart.app.v2

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Small offline-first SQLite store for user records.
 * Existing SharedPreferences records are imported once, in a single transaction.
 */
class LocalDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "dailysmart_v2.db",
    null,
    3
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, stable_id TEXT NOT NULL UNIQUE, title TEXT NOT NULL, done INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE money (id INTEGER PRIMARY KEY AUTOINCREMENT, stable_id TEXT NOT NULL UNIQUE, title TEXT NOT NULL, amount REAL NOT NULL, income INTEGER NOT NULL, created_at INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE habits (id INTEGER PRIMARY KEY AUTOINCREMENT, stable_id TEXT NOT NULL UNIQUE, title TEXT NOT NULL, completed_today INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Existing records have no reliable transaction date; zero means unknown.
            db.execSQL("ALTER TABLE money ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            // Add durable public IDs without discarding or reordering existing records.
            db.execSQL("ALTER TABLE tasks ADD COLUMN stable_id TEXT")
            db.execSQL("ALTER TABLE money ADD COLUMN stable_id TEXT")
            db.execSQL("ALTER TABLE habits ADD COLUMN stable_id TEXT")
            db.execSQL("UPDATE tasks SET stable_id = lower(hex(randomblob(16))) WHERE stable_id IS NULL OR stable_id = ''")
            db.execSQL("UPDATE money SET stable_id = lower(hex(randomblob(16))) WHERE stable_id IS NULL OR stable_id = ''")
            db.execSQL("UPDATE habits SET stable_id = lower(hex(randomblob(16))) WHERE stable_id IS NULL OR stable_id = ''")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_tasks_stable_id ON tasks(stable_id)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_money_stable_id ON money(stable_id)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_habits_stable_id ON habits(stable_id)")
        }
    }

    fun migrateLegacyOnce(tasks: List<TaskItem>, money: List<MoneyItem>, habits: List<HabitItem>, habitsDate: String) {
        val db = writableDatabase
        if (getMeta(db, "legacy_migration_complete") == "1") return
        db.beginTransaction()
        try {
            insertTasks(db, tasks)
            // SharedPreferences records predate reliable transaction timestamps.
            insertMoney(db, money.map { it.copy(createdAt = 0L) })
            insertHabits(db, habits)
            putMeta(db, "habits_date", habitsDate)
            putMeta(db, "legacy_migration_complete", "1")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadTasks(): List<TaskItem> {
        val result = mutableListOf<TaskItem>()
        readableDatabase.rawQuery("SELECT stable_id, title, done FROM tasks ORDER BY id", null).use { cursor ->
            while (cursor.moveToNext()) result += TaskItem(cursor.getString(1), cursor.getInt(2) == 1, cursor.getString(0))
        }
        return result
    }

    fun loadMoney(): List<MoneyItem> {
        val result = mutableListOf<MoneyItem>()
        readableDatabase.rawQuery("SELECT stable_id, title, amount, income, created_at FROM money ORDER BY id", null).use { cursor ->
            while (cursor.moveToNext()) result += MoneyItem(cursor.getString(1), cursor.getDouble(2), cursor.getInt(3) == 1, cursor.getLong(4), cursor.getString(0))
        }
        return result
    }

    fun loadHabitsForToday(today: String): List<HabitItem> {
        val db = writableDatabase
        val result = mutableListOf<HabitItem>()
        db.rawQuery("SELECT stable_id, title, completed_today FROM habits ORDER BY id", null).use { cursor ->
            while (cursor.moveToNext()) result += HabitItem(cursor.getString(1), cursor.getInt(2) == 1, cursor.getString(0))
        }
        if (getMeta(db, "habits_date") != today) {
            val reset = result.map { it.copy(completedToday = false) }
            replaceHabits(reset, today)
            return reset
        }
        return result
    }

    fun replaceTasks(items: List<TaskItem>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            insertTasks(db, items)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun replaceMoney(items: List<MoneyItem>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            insertMoney(db, items)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun replaceHabits(items: List<HabitItem>, today: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            insertHabits(db, items)
            putMeta(db, "habits_date", today)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun insertTasks(db: SQLiteDatabase, items: List<TaskItem>) {
        db.delete("tasks", null, null)
        items.forEach { item ->
            val values = ContentValues().apply {
                put("stable_id", item.stableId)
                put("title", item.title)
                put("done", if (item.done) 1 else 0)
            }
            db.insertOrThrow("tasks", null, values)
        }
    }

    private fun insertMoney(db: SQLiteDatabase, items: List<MoneyItem>) {
        db.delete("money", null, null)
        items.filter { it.amount.isFinite() && it.amount > 0.0 }.forEach { item ->
            val values = ContentValues().apply {
                put("stable_id", item.stableId)
                put("title", item.title)
                put("amount", item.amount)
                put("income", if (item.income) 1 else 0)
                put("created_at", item.createdAt)
            }
            db.insertOrThrow("money", null, values)
        }
    }

    private fun insertHabits(db: SQLiteDatabase, items: List<HabitItem>) {
        db.delete("habits", null, null)
        items.forEach { item ->
            val values = ContentValues().apply {
                put("stable_id", item.stableId)
                put("title", item.title)
                put("completed_today", if (item.completedToday) 1 else 0)
            }
            db.insertOrThrow("habits", null, values)
        }
    }

    private fun getMeta(db: SQLiteDatabase, key: String): String? =
        db.rawQuery("SELECT value FROM metadata WHERE key = ?", arrayOf(key)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun putMeta(db: SQLiteDatabase, key: String, value: String) {
        val values = ContentValues().apply { put("key", key); put("value", value) }
        db.insertWithOnConflict("metadata", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
