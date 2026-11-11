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
        val normalizedInput = normalizeArabicDigits(input)
        val lower = normalizedInput.lowercase()

        val taskPrefixes = listOf(
            "add task", "create task", "new task", "ajouter tâche", "ajoute tâche",
            "ajouter une tâche", "ajoute une tâche", "زيد مهمة", "زيد ليا مهمة",
            "أضف مهمة", "مهمة جديدة", "zid mohimma", "zid task", "sajel mohimma", "sajel liya mohimma", "dir mohimma", "3tini mohimma"
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
            Triple("revenu", true, "revenu"), Triple("recette", true, "recette"),
            Triple("masrouf", false, "masrouf"), Triple("sajel masrouf", false, "sajel masrouf"),
            Triple("sraft", false, "sraft"), Triple("sreft", false, "sreft"), Triple("khrjt", false, "khrjt"),
            Triple("khrej", false, "khrej"), Triple("chrit", false, "chrit"), Triple("khlest", false, "khlest"),
            Triple("sarf", false, "sarf"), Triple("dkhl", true, "dkhl"), Triple("dakhl", true, "dakhl"),
            Triple("sajel dakhl", true, "sajel dakhl"), Triple("dkhol", true, "dkhol"),
            Triple("khdemt", true, "khdemt"), Triple("khedmt", true, "khedmt"),
            Triple("j'ai dépensé", false, "j'ai dépensé"), Triple("j'ai depense", false, "j'ai depense"),
            Triple("j'ai reçu", true, "j'ai reçu"), Triple("j'ai recu", true, "j'ai recu"),
            Triple("gagné", true, "gagné"), Triple("gagne", true, "gagne")
        )
        for ((prefix, isIncome, _) in moneyPrefixes.sortedByDescending { it.first.length }) {
            if (lower.startsWith(prefix)) {
                val rest = normalizedInput.drop(prefix.length).trim().trimStart(':', '-', ' ')
                val amountMatch = Regex("""(?:^|\s)(\d+(?:[.,]\d{1,2})?)(?=\s|$)""").find(rest)
                    ?: return AssistantAction.Help
                val amount = amountMatch.groupValues[1].replace(',', '.').toDoubleOrNull()
                    ?: return AssistantAction.Help
                if (!amount.isFinite() || amount <= 0.0) return AssistantAction.Help
                val description = (rest.removeRange(amountMatch.range).trim()).ifBlank {
                    if (isIncome) "Income" else "Expense"
                }
                return AssistantAction.AddMoney(amount, description, isIncome)
            }
        }

        if (listOf("report", "finance report", "bilan", "rapport", "تقرير", "تقرير مالي", "الحساب", "7sab", "7ssab", "7sab dyali", "ch7al sraft", "ch7al khrejt", "شحال صرفت", "شحال خرجت").any { lower == it }) {
            return AssistantAction.FinanceReport
        }
        return AssistantAction.Help
    }


    private fun normalizeArabicDigits(value: String): String = buildString(value.length) {
        value.forEach { char ->
            append(
                when (char) {
                    '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                    '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                    '٫' -> '.'; '٬' -> ','
                    else -> char
                }
            )
        }
    }

    fun helpText(): String =
        "Try: add task call the bank · sajel masrouf 50 qhwa · sajel dakhl 8000 salaire · ch7al sraft. " +
        "You can also use Arabic, French and Darija commands. This assistant works offline; it is not yet connected to an online AI model."
}
