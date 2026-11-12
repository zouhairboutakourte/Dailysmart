package com.dailysmart.app.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeminiAssistantParsingTest {
    @Test
    fun parsesExpenseAsPendingAction() {
        val result = GeminiAssistant.parseInterpretation(
            """{"reply":"I can add this expense. Please confirm.","action":"add_expense","amount":45,"description":"coffee","title":""}"""
        )
        assertEquals("I can add this expense. Please confirm.", result.reply)
        val action = result.action as AssistantAction.AddMoney
        assertEquals(45.0, action.amount, 0.001)
        assertEquals("coffee", action.description)
        assertEquals(false, action.income)
    }

    @Test
    fun parsesIncomeAsPendingAction() {
        val result = GeminiAssistant.parseInterpretation(
            """{"reply":"Confirm this income.","action":"add_income","amount":8500,"description":"salary","title":""}"""
        )
        val action = result.action as AssistantAction.AddMoney
        assertEquals(true, action.income)
        assertEquals(8500.0, action.amount, 0.001)
    }

    @Test
    fun refusesMoneyActionWhenAmountIsMissing() {
        val result = GeminiAssistant.parseInterpretation(
            """{"reply":"How much did it cost?","action":"add_expense","amount":0,"description":"groceries","title":""}"""
        )
        assertNull(result.action)
        assertEquals("How much did it cost?", result.reply)
    }

    @Test
    fun parsesTaskAndFinanceReport() {
        val task = GeminiAssistant.parseInterpretation(
            """{"reply":"I can add this task.","action":"add_task","title":"Call the bank","amount":0,"description":""}"""
        )
        assertEquals(AssistantAction.AddTask("Call the bank"), task.action)
        val report = GeminiAssistant.parseInterpretation(
            """{"reply":"Here is your report.","action":"finance_report","title":"","amount":0,"description":""}"""
        )
        assertTrue(report.action is AssistantAction.FinanceReport)
    }

    @Test
    fun detectsForeignCurrencyWithoutTreatingDirhamsAsForeign() {
        assertTrue(GeminiAssistant.hasNonMadCurrency("I spent 20 euros"))
        assertTrue(GeminiAssistant.hasNonMadCurrency("paid \\$15"))
        assertFalse(GeminiAssistant.hasNonMadCurrency("صرف 45 DH"))
        assertFalse(GeminiAssistant.hasNonMadCurrency("spent 45 MAD"))
    }

    @Test
    fun unsupportedOrMalformedActionsNeverExecute() {
        val unsupported = GeminiAssistant.parseInterpretation(
            """{"reply":"I cannot delete records yet.","action":"delete_all","title":"","amount":0,"description":""}"""
        )
        assertNull(unsupported.action)
        val malformed = GeminiAssistant.parseInterpretation("not json")
        assertNull(malformed.action)
    }
}
