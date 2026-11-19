package com.dailysmart.app.v2

import android.database.sqlite.SQLiteDatabase
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
    fun upgradesVersionTwoDatabaseWithoutLosingExistingRecords() {
        val application = RuntimeEnvironment.getApplication()
        database.close()
        application.deleteDatabase("dailysmart_v2.db")
        val legacy = SQLiteDatabase.openOrCreateDatabase(application.getDatabasePath("dailysmart_v2.db"), null)
        legacy.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, done INTEGER NOT NULL)")
        legacy.execSQL("CREATE TABLE money (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, amount REAL NOT NULL, income INTEGER NOT NULL, created_at INTEGER NOT NULL DEFAULT 0)")
        legacy.execSQL("CREATE TABLE habits (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, completed_today INTEGER NOT NULL)")
        legacy.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        legacy.execSQL("INSERT INTO tasks(title, done) VALUES('Existing task', 1)")
        legacy.execSQL("INSERT INTO money(title, amount, income, created_at) VALUES('Existing expense', 42.5, 0, 1234)")
        legacy.execSQL("INSERT INTO habits(title, completed_today) VALUES('Existing habit', 1)")
        legacy.version = 2
        legacy.close()

        database = LocalDatabase(application)
        val task = database.loadTasks().single()
        val money = database.loadMoney().single()
        val habit = database.loadHabitsForToday("2026-11-19").single()

        assertEquals("Existing task", task.title)
        assertEquals(42.5, money.amount, 0.001)
        assertEquals(1234L, money.createdAt)
        assertEquals("Existing habit", habit.title)
        assertTrue(task.stableId.isNotBlank())
        assertTrue(money.stableId.isNotBlank())
        assertTrue(habit.stableId.isNotBlank())
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
