package com.dailysmart.app.v2

import org.json.JSONArray
import org.json.JSONObject

/** Versioned, validated JSON backup for all user-owned DailySmart records. */
object BackupCodec {
    const val MAX_BACKUP_BYTES = 2 * 1024 * 1024
    private const val VERSION = 1
    private const val MAX_RECORDS_PER_SECTION = 10_000
    private const val MAX_TITLE_LENGTH = 500

    data class Snapshot(
        val tasks: List<TaskItem>,
        val money: List<MoneyItem>,
        val habits: List<HabitItem>
    )

    fun encode(tasks: List<TaskItem>, money: List<MoneyItem>, habits: List<HabitItem>): ByteArray {
        require(tasks.size <= MAX_RECORDS_PER_SECTION && money.size <= MAX_RECORDS_PER_SECTION &&
            habits.size <= MAX_RECORDS_PER_SECTION) { "Too many records to back up." }
        val root = JSONObject().put("app", "DailySmart").put("version", VERSION)
            .put("createdAt", System.currentTimeMillis())
        root.put("tasks", JSONArray().apply {
            tasks.forEach { put(JSONObject().put("title", clean(it.title)).put("done", it.done)) }
        })
        root.put("money", JSONArray().apply {
            money.forEach {
                require(it.amount.isFinite() && it.amount > 0.0) { "Invalid finance amount in current data." }
                put(JSONObject().put("title", clean(it.title)).put("amount", it.amount).put("income", it.income))
            }
        })
        root.put("habits", JSONArray().apply {
            habits.forEach { put(JSONObject().put("title", clean(it.title)).put("completedToday", it.completedToday)) }
        })
        return root.toString(2).toByteArray(Charsets.UTF_8).also {
            require(it.size <= MAX_BACKUP_BYTES) { "Backup exceeds the 2 MB safety limit." }
        }
    }

    fun decode(bytes: ByteArray): Snapshot {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BACKUP_BYTES) {
            "Backup must be between 1 byte and 2 MB."
        }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.optString("app") == "DailySmart") { "This file is not a DailySmart backup." }
        require(root.optInt("version", -1) == VERSION) { "Unsupported backup version." }
        val tasksArray = root.optJSONArray("tasks") ?: throw IllegalArgumentException("Backup is missing tasks.")
        val moneyArray = root.optJSONArray("money") ?: throw IllegalArgumentException("Backup is missing finance records.")
        val habitsArray = root.optJSONArray("habits") ?: throw IllegalArgumentException("Backup is missing habits.")
        require(tasksArray.length() <= MAX_RECORDS_PER_SECTION &&
            moneyArray.length() <= MAX_RECORDS_PER_SECTION &&
            habitsArray.length() <= MAX_RECORDS_PER_SECTION) { "Backup contains too many records." }

        val tasks = (0 until tasksArray.length()).map { i ->
            val item = tasksArray.getJSONObject(i)
            TaskItem(clean(item.getString("title")), item.optBoolean("done", false))
        }
        val money = (0 until moneyArray.length()).map { i ->
            val item = moneyArray.getJSONObject(i)
            val amount = item.getDouble("amount")
            require(amount.isFinite() && amount > 0.0) { "Backup contains an invalid amount." }
            MoneyItem(clean(item.getString("title")), amount, item.getBoolean("income"))
        }
        val habits = (0 until habitsArray.length()).map { i ->
            val item = habitsArray.getJSONObject(i)
            HabitItem(clean(item.getString("title")), item.optBoolean("completedToday", false))
        }
        return Snapshot(tasks, money, habits)
    }

    private fun clean(value: String): String {
        val result = value.trim()
        require(result.isNotEmpty() && result.length <= MAX_TITLE_LENGTH) {
            "Record titles must contain 1 to $MAX_TITLE_LENGTH characters."
        }
        return result
    }
}
