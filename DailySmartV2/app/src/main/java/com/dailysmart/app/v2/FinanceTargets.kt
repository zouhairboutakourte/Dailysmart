package com.dailysmart.app.v2

/** Safe calculations for monthly budget and savings goal progress. */
object FinanceTargets {
    fun progress(current: Double, target: Double): Float {
        if (!current.isFinite() || !target.isFinite() || current < 0.0 || target <= 0.0) return 0f
        return (current / target).toFloat().coerceIn(0f, 1f)
    }
    fun remaining(target: Double, current: Double): Double {
        if (!target.isFinite() || !current.isFinite() || target <= 0.0 || current < 0.0) return 0.0
        return (target - current).coerceAtLeast(0.0)
    }
    fun isOverTarget(current: Double, target: Double): Boolean =
        current.isFinite() && target.isFinite() && current > target && target > 0.0
}
