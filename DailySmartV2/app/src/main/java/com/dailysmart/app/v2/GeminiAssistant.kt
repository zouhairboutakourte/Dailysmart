package com.dailysmart.app.v2

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.ai
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AssistantInterpretation(
    val reply: String,
    val action: AssistantAction?
)

/**
 * Gemini access through Firebase AI Logic.
 * The Gemini API key is never stored in this Android project or APK.
 * The user's finance ledger, tasks, habits and stored reminders are never sent to Gemini.
 */
object GeminiAssistant {
    @Volatile
    private var initialized = false

    @Synchronized
    fun initialize(context: Context): Boolean {
        if (initialized) return true
        return runCatching {
            FirebaseApp.initializeApp(context.applicationContext)
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                if (com.dailysmart.app.v2.BuildConfig.DEBUG) {
                    DebugAppCheckProviderFactory.getInstance()
                } else {
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                }
            )
            initialized = true
            true
        }.getOrDefault(false)
    }

    suspend fun interpret(question: String, language: String): AssistantInterpretation {
        require(question.isNotBlank()) { "Please enter a message." }
        require(question.length <= 1500) { "Message is too long." }

        val localNow = ZonedDateTime.now()
        val localDateTime = localNow.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val localZone = localNow.zone.id

        val languageInstruction = when (language) {
            "ar" -> "Write the reply in clear Arabic. Understand Moroccan Darija and reply naturally in Darija when the user writes Darija."
            "fr" -> "Réponds en français clair. Comprends aussi le darija marocain."
            else -> "Reply in clear English. Understand Moroccan Darija, Arabic and French too."
        }

        val prompt = """
            You are DailySmart, a practical personal productivity and budgeting assistant for users in Morocco.
            $languageInstruction
            Use MAD / Moroccan dirham only when currency is omitted or clearly MAD.
            Current local date/time: $localDateTime. Local time zone: $localZone.
            Return ONLY one valid JSON object, without Markdown or code fences, using this schema:
            {"reply":"short user-facing message","action":"chat|add_task|add_expense|add_income|finance_report|add_reminder","title":"","amount":0,"description":"","trigger_at":""}

            Safety and accuracy rules:
            - You may propose exactly one new task, one expense, one income record, one one-time reminder, or a finance report.
            - Never claim that anything was saved or changed. The app will ask the user to confirm before adding a task, money record or reminder.
            - For add_task, require a clear task title.
            - For add_expense or add_income, require an explicit positive numeric amount AND a clear description. Do not invent missing amounts or descriptions.
            - If amount or description is missing, use action "chat" and ask the user for the missing detail.
            - If the user specifies a currency other than MAD, do not convert it; use action "chat" and ask for clarification.
            - Use finance_report only when the user asks about their own balance, spending, income or finance summary. The app calculates the report locally.
            - For add_reminder, require a clear reminder title and an unambiguous future date AND time. Do not guess missing dates, times, or whether an ambiguous time such as "8" means morning or evening.
            - For add_reminder, return trigger_at as an ISO 8601 date-time with the numeric UTC offset for the supplied local time zone. Use the current local date/time above to interpret relative dates. If the user gives no clear future time, use action "chat" and ask a short follow-up question.
            - Never claim that a reminder was scheduled; the app asks the user to confirm before scheduling it.
            - For deleting, editing, habits or any unsupported operation, use action "chat" and explain that it is not supported yet.
            - Do not invent or assume any financial records. You do not receive the user's stored data.
            - Keep reply concise and in the requested language. Use empty strings and amount 0 for fields that do not apply.

            User message:
            $question
        """.trimIndent()

        val model = Firebase.ai(
            backend = GenerativeBackend.googleAI(),
            useLimitedUseAppCheckTokens = true
        ).generativeModel("gemini-3.5-flash")
        val response = model.generateContent(prompt)
        val raw = response.text?.trim().orEmpty()
        if (raw.isEmpty()) return AssistantInterpretation("Gemini returned an empty response. Please try again.", null)

        val interpretation = parseInterpretation(raw)
        if (interpretation.action is AssistantAction.AddMoney && hasNonMadCurrency(question)) {
            return AssistantInterpretation(
                "I detected a currency other than MAD. Please clarify the amount in MAD before I prepare the record.",
                null
            )
        }
        return interpretation
    }

    internal fun hasNonMadCurrency(value: String): Boolean =
        Regex("""(?i)(?:\b(?:usd|eur|euro|euros|dollar|dollars|gbp|pound|pounds)\b|[$€£])""")
            .containsMatchIn(value)

    internal fun parseInterpretation(raw: String): AssistantInterpretation {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) {
            return AssistantInterpretation(raw.trim().take(1200).ifBlank { "Please try again." }, null)
        }
        val json = runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull()
            ?: return AssistantInterpretation("I couldn't safely interpret that. Please rephrase your request.", null)

        val reply = json.optString("reply").trim().take(1200)
            .ifBlank { "I understood your request. Please review the proposed action." }
        val actionType = json.optString("action").trim().lowercase()
        val action = when (actionType) {
            "add_task" -> {
                val title = json.optString("title").trim()
                if (title.isNotBlank() && title.length <= 500) AssistantAction.AddTask(title) else null
            }
            "add_expense", "add_income" -> {
                val amount = json.optDouble("amount", Double.NaN)
                val description = json.optString("description").trim()
                val validAmount = amount.isFinite() && amount > 0.0 && amount <= 1_000_000_000.0
                if (validAmount && description.isNotBlank() && description.length <= 500) {
                    AssistantAction.AddMoney(amount, description, actionType == "add_income")
                } else null
            }
            "add_reminder" -> {
                val title = json.optString("title").trim()
                val parsedTime = runCatching {
                    OffsetDateTime.parse(json.optString("trigger_at").trim())
                }.getOrNull()
                val triggerAt = parsedTime?.toInstant()?.toEpochMilli()
                val offsetMatchesDeviceZone = parsedTime != null &&
                    parsedTime.offset == ZoneId.systemDefault().rules.getOffset(parsedTime.toInstant())
                val now = System.currentTimeMillis()
                if (title.isNotBlank() && title.length <= 180 && triggerAt != null && offsetMatchesDeviceZone &&
                    triggerAt > now && triggerAt <= now + 366L * 24L * 60L * 60L * 1000L
                ) AssistantAction.AddReminder(title, triggerAt) else null
            }
            "finance_report" -> AssistantAction.FinanceReport
            else -> null
        }
        return AssistantInterpretation(reply, action)
    }
}
