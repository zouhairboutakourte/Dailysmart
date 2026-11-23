package com.dailio.app.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FinanceDatabaseTest {
    private lateinit var database: FinanceDatabase
    private lateinit var repository: FinanceRepository

    @Before fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.deleteDatabase(FinanceDatabase.DATABASE_NAME)
        database = FinanceDatabase(app)
        repository = FinanceRepository(database)
    }

    @After fun tearDown() { repository.close() }

    @Test fun recordsPersistAndDeleteByStableId() {
        val id = repository.add("Groceries", 12550L, TransactionType.EXPENSE, "Food", 1_800_000_000_000L)
        assertEquals(1, repository.all().size)
        assertEquals(id, repository.all().first().id)
        assertEquals(12550L, repository.all().first().amountMinor)
        assertTrue(repository.delete(id))
        assertTrue(repository.all().isEmpty())
    }

    @Test fun invalidTransactionDoesNotReplaceOrDeleteExistingRecords() {
        repository.add("Salary", 880000L, TransactionType.INCOME, "Work", 1_800_000_000_000L)
        val failure = runCatching {
            repository.add("Invalid", 0L, TransactionType.EXPENSE, "Other", 1_800_000_000_000L)
        }
        assertTrue(failure.isFailure)
        assertEquals(1, repository.all().size)
        assertEquals("Salary", repository.all().single().description)
    }
}
