package com.dailysmart.app.v2

import org.junit.Assert.assertEquals
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
    fun versionThreeBackupPreservesFinanceTargets() {
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
}
