package com.dailysmart.app.v2

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.ai
import com.google.firebase.appCheck
import com.google.firebase.initialize
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Gemini access through Firebase AI Logic.
 *
 * The Gemini API key is never stored in this Android project or APK.
 * Firebase AI Logic proxies requests and App Check helps reject unauthorized clients.
 */
object GeminiAssistant {
    @Volatile
    private var initialized = false

    @Synchronized
    fun initialize(context: Context): Boolean {
        if (initialized) return true
        return runCatching {
            Firebase.initialize(context = context.applicationContext)
            Firebase.appCheck.installAppCheckProviderFactory(
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

    suspend fun ask(question: String, language: String): String {
        require(question.isNotBlank()) { "Please enter a message." }
        require(question.length <= 1500) { "Message is too long." }

        val languageInstruction = when (language) {
            "ar" -> "Reply in clear Arabic. Understand Moroccan Darija and reply naturally in Darija when the user writes Darija."
            "fr" -> "Réponds en français clair. Comprends aussi le darija marocain."
            else -> "Reply in clear English. Understand Moroccan Darija, Arabic and French too."
        }

        val prompt = """
            You are DailySmart, a helpful personal productivity and budgeting assistant for users in Morocco.
            $languageInstruction
            Use MAD / Moroccan dirham when currency is relevant.
            Be concise, practical and respectful. Never invent the user's financial records or claim you changed app data.
            You are currently a conversational assistant only. Do not claim to have added, deleted, saved or modified anything.
            If the user asks for an action in the app, explain what they can do or ask for confirmation; do not pretend it was executed.
            User message:
            $question
        """.trimIndent()

        val model = Firebase.ai(
            backend = GenerativeBackend.googleAI(),
            useLimitedUseAppCheckTokens = true
        )
            .generativeModel("gemini-3.5-flash")
        val response = model.generateContent(prompt)
        return response.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Gemini returned an empty response. Please try again."
    }
}
