# DailySmart V2

A clean, independent Android application built with Kotlin and Jetpack Compose.

## Current first-build scope
- Independent package: `com.dailysmart.app.v2`
- Dark navy mobile interface
- Local task list with completion and deletion
- Personal finance income/expense ledger in MAD
- Local persistence using Android SharedPreferences
- Assistant command interpreter for task, income and expense entry
- CSV import/export for finance records
- No ads, no subscription, no embedded AI API key

## Planned next milestones
1. Room database + data migration and automated tests.
2. Real XLSX workbook import/export, formulas, charts and spreadsheet analysis.
3. Secure AI backend/proxy with provider configuration outside the APK.
4. Arabic/Darija/French/English localization and full RTL.
5. Habits, goals, budgets, reports, reminders and backup.

The assistant is deliberately transparent: the first build executes a small set of local commands. It does not claim to be a connected large-language-model assistant until a secure backend is implemented.
