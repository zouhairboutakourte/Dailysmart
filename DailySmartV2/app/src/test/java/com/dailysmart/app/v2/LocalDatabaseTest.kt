package com.dailysmart.app.v2

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LocalDatabaseTest {
    private lateinit var database: LocalDatabase

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        application.deleteDatabase("dailysmart_v2.db")
        database = LocalDatabase(application)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun migratesLegacyRecordsOnlyOnce() {
        val tasks = listOf(TaskItem("Pay bill", true), TaskItem("Buy groceries", false))
        val money = listOf(
            MoneyItem("Salary", 8500.0, true),
            MoneyItem("Legacy sub-cent", 0.0049, false),
            MoneyItem("Legacy zero", 0.0, false),
            MoneyItem("Legacy negative", -12.5, false)
        )
        val habits = listOf(HabitItem("Water", true), HabitItem("Read", false))

        database.migrateLegacyOnce(tasks, money, habits, "2026-11-11")

        assertEquals(tasks, database.loadTasks())
        assertEquals(money.map { it.copy(createdAt = 0L) }, database.loadMoney())
        assertEquals(habits, database.loadHabitsForToday("2026-11-11"))

        database.migrateLegacyOnce(listOf(TaskItem("Replacement")), emptyList(), emptyList(), "2026-11-12")

        assertEquals(tasks, database.loadTasks())
        assertEquals(money.map { it.copy(createdAt = 0L) }, database.loadMoney())
    }


    @Test
    fun nonFiniteReplacementIsRejectedWithoutDeletingExistingMoney() {
        val original = listOf(MoneyItem("Salary", 8500.0, true))
        database.replaceMoney(original)

        val failure = runCatching {
            database.replaceMoney(listOf(MoneyItem("Corrupt", Double.NaN, false)))
        }

        assertTrue(failure.isFailure)
        assertEquals(original, database.loadMoney())
    }

    @Test
    fun resetsHabitCheckmarksWhenDayChanges() {
        database.migrateLegacyOnce(
            emptyList(),
            emptyList(),
            listOf(HabitItem("Walk", true), HabitItem("Read", false)),
            "2026-11-10"
        )

        val nextDay = database.loadHabitsForToday("2026-11-11")

        assertEquals(2, nextDay.size)
        assertFalse(nextDay[0].completedToday)
        assertFalse(nextDay[1].completedToday)
        assertTrue(nextDay.all { !it.completedToday })
    }
}
