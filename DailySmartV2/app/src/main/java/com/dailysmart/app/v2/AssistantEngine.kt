package com.dailysmart.app.v2

/**
 * Offline command interpreter for common DailySmart actions.
 * This is a deterministic local assistant, not a cloud LLM.
 */
sealed class AssistantAction {
    data class AddTask(val title: String) : AssistantAction()
    data class AddMoney(val amount: Double, val description: String, val income: Boolean) : AssistantAction()
    data object FinanceReport : AssistantAction()
    data object Help : AssistantAction()
}

object AssistantEngine {
    fun parse(raw: String): AssistantAction {
        val input = raw.trim()
        val lower = input.lowercase()

        val taskPrefixes = listOf(
            "add task", "create task", "new task", "ajouter tâche", "ajoute tâche",
            "ajouter une tâche", "ajoute une tâche", "زيد مهمة", "زيد ليا مهمة",
            "أضف مهمة", "مهمة جديدة"
        )
        taskPrefixes.firstOrNull { lower.startsWith(it) }?.let { prefix ->
            val title = input.drop(prefix.length).trim().trimStart(':', '-', ' ')
            if (title.isNotBlank()) return AssistantAction.AddTask(title)
            return AssistantAction.Help
        }

        val moneyPrefixes = listOf(
            Triple("expense", false, "expense"), Triple("spend", false, "spend"),
            Triple("مصروف", false, "مصروف"), Triple("سجل مصروف", false, "سجل مصروف"),
            Triple("dépense", false, "dépense"), Triple("depense", false, "depense"),
            Triple("income", true, "income"), Triple("salary", true, "salary"),
            Triple("دخل", true, "دخل"), Triple("سجل دخل", true, "سجل دخل"),
            Triple("revenu", true, "revenu"), Triple("recette", true, "recette")
        )
        for ((prefix, isIncome, _) in moneyPrefixes.sortedByDescending { it.first.length }) {
            if (lower.startsWith(prefix)) {
                val rest = input.drop(prefix.length).trim().trimStart(':', '-', ' ')
                val amountMatch = Regex("""(?:^|\\s)(\\d+(?:[.,]\\d{1,2})?)(?=\\s|$)""").find(rest)
                    ?: return AssistantAction.Help
                val amount = amountMatch.groupValues[1].replace(',', '.').toDoubleOrNull()
                    ?: return AssistantAction.Help
                if (amount <= 0.0) return AssistantAction.Help
                val description = (rest.removeRange(amountMatch.range).trim()).ifBlank {
                    if (isIncome) "Income" else "Expense"
                }
                return AssistantAction.AddMoney(amount, description, isIncome)
            }
        }

        if (listOf("report", "finance report", "bilan", "rapport", "تقرير", "تقرير مالي", "الحساب").any { lower == it }) {
            return AssistantAction.FinanceReport
        }
        return AssistantAction.Help
    }

    fun helpText(): String =
        "Try: add task call the bank · expense 50 lunch · income 8000 salary · report. " +
        "You can also use French or Arabic commands. This assistant works offline; it is not yet connected to an online AI model."
}
