package com.dailysmart.app.v2

/**
 * Offline command interpreter for common DailySmart actions.
 * This is a deterministic local assistant, not a cloud LLM.
 */
sealed class AssistantAction {
    data class AddTask(val title: String) : AssistantAction()
    data class AddMoney(val amount: Double, val description: String, val income: Boolean) : AssistantAction()
    data class AddReminder(val title: String, val triggerAt: Long) : AssistantAction()
    data object FinanceReport : AssistantAction()
    data object Help : AssistantAction()
}

object AssistantEngine {
    fun parse(raw: String): AssistantAction {
        val input = raw.trim()
        if (input.isBlank() || input.length > 1500) return AssistantAction.Help
        val normalizedInput = normalizeArabicDigits(input)
        val lower = normalizedInput.lowercase()

        val taskPrefixes = listOf(
            "add task", "create task", "new task", "ajouter tâche", "ajoute tâche",
            "ajouter une tâche", "ajoute une tâche", "زيد مهمة", "زيد ليا مهمة",
            "أضف مهمة", "مهمة جديدة", "zid mohimma", "zid lia mohimma", "zid liya mohimma",
            "sajel mohimma", "sajel lia mohimma", "sajel liya mohimma", "dir mohimma",
            "bghit nzid mohimma", "bghit ndir task", "3tini mohimma", "zid task"
        )
        taskPrefixes.firstOrNull { lower.startsWith(it) }?.let { prefix ->
            val title = input.drop(prefix.length).trim().trimStart(':', '-', ' ')
            if (title.isNotBlank() && title.length <= 500) return AssistantAction.AddTask(title)
            return AssistantAction.Help
        }

        val moneyPrefixes = listOf(
            "j'ai dépensé" to false, "j'ai depense" to false, "i spent" to false,
            "j'ai payé" to false, "j'ai paye" to false, "i paid" to false,
            "spent" to false, "spend" to false, "paid" to false, "expense" to false,
            "sجل مصروف" to false, "سجل مصروف" to false, "مصروف" to false,
            "dépense" to false, "depense" to false,
            "income" to true, "salary" to true, "دخل" to true, "سجل دخل" to true,
            "revenu" to true, "recette" to true,
            "masrouf" to false, "sajel masrouf" to false, "sajel lia masrouf" to false,
            "ana sraft" to false, "ana sreft" to false, "lyom sraft" to false,
            "sraft lia" to false, "sraft" to false, "sreft" to false,
            "ana khrejt" to false, "khrjt" to false, "khrejt" to false,
            "khrej" to false, "chrit" to false, "ana khlest" to false, "khlest" to false,
            "sarf" to false, "dkhl" to true, "dakhl" to true,
            "sajel dakhl" to true, "sajel lia dakhl" to true, "dkhol" to true,
            "ana dkhelt" to true, "dkhelt" to true, "dkhalt" to true,
            "khdemt" to true, "khedmt" to true,
            "j'ai reçu" to true, "j'ai recu" to true,
            "gagné" to true, "gagne" to true,
            "صرفت" to false, "صرفـت" to false, "خلصت" to false,
            "شريت" to false, "دخلت" to true
        )
        for ((prefix, isIncome) in moneyPrefixes.sortedByDescending { it.first.length }) {
            if (lower.startsWith(prefix)) {
                val rest = normalizedInput.drop(prefix.length).trim().trimStart(':', '-', ' ')
                val amountMatch = Regex("""(?:^|\s)(\d+(?:[.,]\d{1,2})?)(?=\s|(?i:dh|dhs|mad|€)|$)""").find(rest)
                    ?: return AssistantAction.Help
                val amount = amountMatch.groupValues[1].replace(',', '.').toDoubleOrNull()
                    ?: return AssistantAction.Help
                if (!amount.isFinite() || amount <= 0.0) return AssistantAction.Help
                var description = rest.removeRange(amountMatch.range).trim()
                description = description
                    .replace(Regex("(?i)\\b(dh|dhs|mad|dirham|dirhams|درهم|دراهم)\\b|€"), " ")
                    .trim()
                    .replace(Regex("^(?i)(on|for|at|in|sur|pour|à|au|3la|f|في|على)\\s*"), "")
                    .trim(' ', ':', '-', '،', ',')
                if (description.isBlank()) description = if (isIncome) "Income" else "Expense"
                if (description.length > 500) return AssistantAction.Help
                return AssistantAction.AddMoney(amount, description, isIncome)
            }
        }

        if (listOf("report", "finance report", "bilan", "rapport", "تقرير", "تقرير مالي", "الحساب", "7sab", "7ssab", "7sab dyali", "ch7al sraft", "ch7al sreft", "ch7al khrejt", "ch7al khssert", "fin mchaw flosi", "ch7al ba9i", "شحال صرفت", "شحال خرجت", "فين مشاو فلوسي").any { lower == it }) {
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
                    '\u0640' -> ""
                    else -> char
                }
            )
        }
    }

    fun helpText(): String =
        "Try: add task call the bank · spent 45 DH on coffee · sajel masrouf 50 qhwa · sraft 50dh f l9hwa · khlest 120 dh internet · sajel dakhl 8000 salaire · ch7al sraft. " +
        "You can also use Arabic, French and Darija commands. Offline commands and local reminders work without internet; Gemini natural-language actions require Firebase setup and an internet connection, and proposed changes always need confirmation."
}
