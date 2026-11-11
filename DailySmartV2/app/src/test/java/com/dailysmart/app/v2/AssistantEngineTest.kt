package com.dailysmart.app.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantEngineTest {
    @Test
    fun parsesDarijaExpenseCommand() {
        val action = AssistantEngine.parse("sajel masrouf 45 qhwa")
        assertTrue(action is AssistantAction.AddMoney)
        val money = action as AssistantAction.AddMoney
        assertEquals(45.0, money.amount, 0.001)
        assertEquals("qhwa", money.description)
        assertEquals(false, money.income)
    }

    @Test
    fun parsesArabicIndicDigitsForIncome() {
        val action = AssistantEngine.parse("سجل دخل ١٢٠٠ راتب")
        assertTrue(action is AssistantAction.AddMoney)
        val money = action as AssistantAction.AddMoney
        assertEquals(1200.0, money.amount, 0.001)
        assertEquals("راتب", money.description)
        assertEquals(true, money.income)
    }

    @Test
    fun parsesArabicTaskCommand() {
        val action = AssistantEngine.parse("زيد مهمة غسل السيارة")
        assertTrue(action is AssistantAction.AddTask)
        assertEquals("غسل السيارة", (action as AssistantAction.AddTask).title)
    }

    @Test
    fun parsesFrenchExpenseCommand() {
        val action = AssistantEngine.parse("j'ai dépensé 25 café")
        assertTrue(action is AssistantAction.AddMoney)
        val money = action as AssistantAction.AddMoney
        assertEquals(25.0, money.amount, 0.001)
        assertEquals("café", money.description)
        assertEquals(false, money.income)
    }

    @Test
    fun recognizesDarijaFinanceReport() {
        assertTrue(AssistantEngine.parse("ch7al sraft") is AssistantAction.FinanceReport)
        assertTrue(AssistantEngine.parse("شحال صرفت") is AssistantAction.FinanceReport)
    }
}
