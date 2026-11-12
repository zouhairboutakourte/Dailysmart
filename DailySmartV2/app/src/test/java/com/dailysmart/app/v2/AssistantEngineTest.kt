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
    fun parsesArabiziExpenseWithAttachedCurrencyAndPreposition() {
        val action = AssistantEngine.parse("sraft 50dh f l9hwa")
        assertTrue(action is AssistantAction.AddMoney)
        val money = action as AssistantAction.AddMoney
        assertEquals(50.0, money.amount, 0.001)
        assertEquals("l9hwa", money.description)
        assertEquals(false, money.income)
    }

    @Test
    fun parsesAlternativeArabiziPaymentPhrase() {
        val action = AssistantEngine.parse("ana khlest 120 dh internet")
        assertTrue(action is AssistantAction.AddMoney)
        val money = action as AssistantAction.AddMoney
        assertEquals(120.0, money.amount, 0.001)
        assertEquals("internet", money.description)
        assertEquals(false, money.income)
    }

    @Test
    fun parsesArabiziTaskAndReportPhrases() {
        val task = AssistantEngine.parse("zid lia mohimma n3ayet l'assurance")
        assertTrue(task is AssistantAction.AddTask)
        assertEquals("n3ayet l'assurance", (task as AssistantAction.AddTask).title)
        assertTrue(AssistantEngine.parse("fin mchaw flosi") is AssistantAction.FinanceReport)
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
