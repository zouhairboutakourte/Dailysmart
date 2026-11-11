# DailySmart V2

Independent Android application built with Kotlin and Jetpack Compose.

## Implemented in version 2.1
- Separate package: `com.dailysmart.app.v2`
- Dark navy interface
- Offline assistant commands in English, French and Arabic: create tasks, record income/expenses, and request a finance report (not a connected LLM)
- Finance ledger: income, expenses and MAD balance
- Task list: add, complete and delete
- Habits: add, daily check-off, reset checkmarks and delete
- CSV finance import/export
- Settings screen with transparent privacy and feature status
- Local persistence via SharedPreferences
- No ads, no subscription, no account and no embedded AI API key

## Not yet implemented
- Room database and automated persistence tests
- Real conversational AI / secure backend
- XLSX workbook import/export, formulas and charts (CSV only currently)
- Full Arabic, Darija, French and English interface localization
- Calendar, reminders, goals and backup/restore

The app labels unavailable capabilities honestly; it does not present local command shortcuts as an LLM.
