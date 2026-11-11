# DailySmart V2

Independent Android application built with Kotlin and Jetpack Compose.

## Implemented in version 2.3
- Separate package: `com.dailysmart.app.v2`
- Dark navy interface
- Offline assistant commands in English, French and Arabic: create tasks, record income/expenses, and request a finance report (not a connected LLM)
- Finance ledger: income, expenses and MAD balance
- Task list: add, complete and delete
- Habits: add, daily check-off, reset checkmarks and delete
- Finance import/export for DailySmart-format XLSX workbooks and CSV (XLSX imports are limited to 10 MB and bounded ZIP expansion for safety)
- Settings screen with transparent privacy and feature status
- Versioned JSON backup and confirmed restore for tasks, finance records and habits
- Local persistence via SharedPreferences, with delimiter-safe encoding for user-entered task, habit and transaction descriptions; legacy saved records remain readable
- No ads, no subscription, no account and no embedded AI API key

## Not yet implemented
- Room database migration and automated persistence tests
- Real conversational AI / secure backend
- Editing arbitrary Excel workbooks, formulas and charts; XLSX support currently focuses on DailySmart finance transaction sheets
- Full Arabic, Darija, French and English interface localization
- Calendar, reminders and goals

Data protection: JSON backup files are limited to 2 MB and validated before restore; restoring replaces existing records only after explicit confirmation. CSV imports are limited to 5 MB and validate finite positive amounts. XLSX imports enforce a 10 MB source-file limit, a 10 MB total expanded-content limit, a 5 MB per-entry limit and a 64-entry limit. Habit checkmarks reset when the local calendar date changes. The app labels unavailable capabilities honestly; it does not present local command shortcuts as an LLM.
