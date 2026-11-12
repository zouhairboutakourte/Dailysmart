package com.dailysmart.app.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceTargetsTest {
    @Test fun budgetProgressAndRemainingAreCorrect() {
        assertEquals(0.5f, FinanceTargets.progress(500.0, 1000.0), 0.001f)
        assertEquals(500.0, FinanceTargets.remaining(1000.0, 500.0), 0.001)
        assertFalse(FinanceTargets.isOverTarget(500.0, 1000.0))
    }
    @Test fun overBudgetCapsProgressAndShowsOverage() {
        assertEquals(1f, FinanceTargets.progress(1200.0, 1000.0), 0.001f)
        assertEquals(0.0, FinanceTargets.remaining(1000.0, 1200.0), 0.001)
        assertTrue(FinanceTargets.isOverTarget(1200.0, 1000.0))
    }
    @Test fun invalidTargetsDoNotCreateFalseProgress() {
        assertEquals(0f, FinanceTargets.progress(100.0, 0.0), 0.001f)
        assertEquals(0.0, FinanceTargets.remaining(Double.NaN, 10.0), 0.001)
        assertFalse(FinanceTargets.isOverTarget(20.0, Double.POSITIVE_INFINITY))
    }
}
