# DailySmart V2

Independent Android application built with Kotlin and Jetpack Compose.

## Implemented in version 2.5
- Separate package: `com.dailysmart.app.v2`
- Refreshed dark navy interface with a blue/teal gradient hero, mint/lavender/gold accents, clearer finance colors, and daily progress feedback
- Branded launcher icon
- Arabic is the default UI language, with persistent Arabic/French/English selection and RTL layout
- Localized core navigation, task, finance, habit, backup and spreadsheet screens
- Empty-state guidance and improved list scrolling
- CSV parser supports quoted commas, escaped quotes and multiline descriptions; CSV, offline assistant commands and SQLite migration/reset have automated unit tests run before APK packaging
- Offline assistant commands in English, French, Arabic and Darija for common tasks and finance actions
- Gemini assistant through Firebase AI Logic; can propose one task, expense, income or finance report from natural language, with explicit confirmation before adding records; uses Firebase App Check and no embedded Gemini API key
- Finance ledger: income, expenses and MAD balance
- Task list: add, complete and delete
- Habits: add, daily check-off, reset checkmarks and delete
- Finance import/export for DailySmart-format XLSX workbooks and CSV (XLSX imports are limited to 10 MB and bounded ZIP expansion for safety)
- Settings screen with transparent privacy and feature status
- Versioned JSON backup and confirmed restore for tasks, finance records and habits
- Offline SQLite persistence for tasks, finance records and habits, with a one-time transactional import from existing SharedPreferences records
- No ads, no subscription, no account and no embedded AI API key

## Gemini setup required before live AI works
1. Create a Firebase project in the Firebase Console and register an Android app with package ID `com.dailysmart.app.v2`.
2. In **AI Services → AI Logic**, select **Get started** and choose **Gemini Developer API**.
3. Download the Android `google-services.json` file and place it at `DailySmartV2/app/google-services.json` in the repository. This is Firebase client configuration, not a Gemini API key; never add a Gemini API key to the app.
4. For debug testing, launch the app and register the App Check debug token shown in Android logs under **Firebase Console → App Check → Apps → Manage debug tokens**. Keep AI Logic App Check enforcement enabled.
5. For Play Store builds, register Play Integrity for App Check and use the Play signing certificate fingerprints.

The app build remains possible without `google-services.json`, but Gemini stays disabled until Firebase configuration is added. The assistant sends only the typed prompt to Gemini; it never uploads the user's finance ledger, tasks or habits. Gemini can propose one new task, one income/expense record, or a finance report. The app validates the proposed action and requires the user to confirm before adding a task or transaction; finance totals are calculated locally.

## Not yet implemented
- Real-device validation of backup/restore, upgrade from an older installed APK and performance is still required
- Multi-step Gemini workflows, editing/deleting records through Gemini, reminders and habit actions are not implemented; current AI proposals are limited to one new task, one income/expense record, or a local finance report
- Editing arbitrary Excel workbooks, formulas and charts; XLSX support currently focuses on DailySmart finance transaction sheets
- Full Arabic, Darija, French and English interface localization
- Calendar, reminders and goals

Data protection: JSON backup files are limited to 2 MB and validated before restore; restoring replaces existing records only after explicit confirmation. CSV imports are limited to 5 MB and validate finite positive amounts. XLSX imports enforce a 10 MB source-file limit, a 10 MB total expanded-content limit, a 5 MB per-entry limit and a 64-entry limit. Habit checkmarks reset when the local calendar date changes. The app labels unavailable capabilities honestly; it does not present local command shortcuts as an LLM.
