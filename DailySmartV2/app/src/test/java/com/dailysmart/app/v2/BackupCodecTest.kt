package com.dailysmart.app.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupCodecTest {
    @Test
    fun readsVersionOneBackupsWithoutInventingTransactionDates() {
        val json = """{"app":"DailySmart","version":1,"tasks":[],"money":[{"title":"Old expense","amount":45.0,"income":false}],"habits":[]}"""
        val snapshot = BackupCodec.decode(json.toByteArray())
        assertEquals(1, snapshot.money.size)
        assertEquals(0L, snapshot.money.single().createdAt)
    }

    @Test
    fun versionFourBackupPreservesFinanceTargets() {
        val bytes = BackupCodec.encode(emptyList(), emptyList(), emptyList(), 5000.0, 1200.0)
        val snapshot = BackupCodec.decode(bytes)
        assertEquals(5000.0, snapshot.monthlyBudget!!, 0.001)
        assertEquals(1200.0, snapshot.monthlySavingsGoal!!, 0.001)
    }

    @Test
    fun versionTwoBackupPreservesTransactionDates() {
        val timestamp = 1_794_456_000_000L
        val bytes = BackupCodec.encode(
            emptyList(),
            listOf(MoneyItem("Coffee", 20.0, false, timestamp)),
            emptyList()
        )
        val snapshot = BackupCodec.decode(bytes)
        assertEquals(timestamp, snapshot.money.single().createdAt)
    }
    @Test
    fun versionFourBackupPreservesStableRecordIds() {
        val task = TaskItem("Pay rent", false, "task-stable-1")
        val money = MoneyItem("Salary", 9000.0, true, 1_794_456_000_000L, "money-stable-1")
        val habit = HabitItem("Read", true, "habit-stable-1")

        val snapshot = BackupCodec.decode(BackupCodec.encode(listOf(task), listOf(money), listOf(habit)))

        assertEquals(task.stableId, snapshot.tasks.single().stableId)
        assertEquals(money.stableId, snapshot.money.single().stableId)
        assertEquals(habit.stableId, snapshot.habits.single().stableId)
    }

    @Test
    fun olderBackupsReceiveStableIdsWhenDecoded() {
        val json = """{"app":"DailySmart","version":3,"tasks":[{"title":"Task","done":false}],"money":[],"habits":[{"title":"Walk","completedToday":false}]}"""
        val snapshot = BackupCodec.decode(json.toByteArray())

        assertTrue(snapshot.tasks.single().stableId.isNotBlank())
        assertTrue(snapshot.habits.single().stableId.isNotBlank())
    }

}
