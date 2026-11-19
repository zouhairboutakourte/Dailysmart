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
        val money = listOf(MoneyItem("Salary", 8500.0, true), MoneyItem("Invalid", Double.NaN, false))
        val habits = listOf(HabitItem("Water", true), HabitItem("Read", false))

        database.migrateLegacyOnce(tasks, money, habits, "2026-11-11")

        assertEquals(tasks, database.loadTasks())
        val loadedMoney = database.loadMoney()
        assertEquals(1, loadedMoney.size)
        assertEquals("Salary", loadedMoney.single().title)
        assertEquals(8500.0, loadedMoney.single().amount, 0.001)
        assertEquals(money.first().stableId, loadedMoney.single().stableId)
        assertEquals(habits, database.loadHabitsForToday("2026-11-11"))

        database.migrateLegacyOnce(listOf(TaskItem("Replacement")), emptyList(), emptyList(), "2026-11-12")

        assertEquals(tasks, database.loadTasks())
        assertEquals(loadedMoney.single().stableId, database.loadMoney().single().stableId)
        assertEquals(tasks.map { it.stableId }, database.loadTasks().map { it.stableId })
    }

    @Test
    fun stableIdsSurviveReplaceAndReload() {
        val task = TaskItem("Keep ID")
        val transaction = MoneyItem("Coffee", 18.0, false, 1_794_456_000_000L)
        val habit = HabitItem("Walk")
        database.replaceTasks(listOf(task))
        database.replaceMoney(listOf(transaction))
        database.replaceHabits(listOf(habit), "2026-11-19")

        database.replaceTasks(database.loadTasks().map { it.copy(done = true) })
        database.replaceMoney(database.loadMoney().map { it.copy(title = "Coffee updated") })
        database.replaceHabits(database.loadHabitsForToday("2026-11-19").map { it.copy(completedToday = true) }, "2026-11-19")

        assertEquals(task.stableId, database.loadTasks().single().stableId)
        assertEquals(transaction.stableId, database.loadMoney().single().stableId)
        assertEquals(habit.stableId, database.loadHabitsForToday("2026-11-19").single().stableId)
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
