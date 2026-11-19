# DailySmart V2

Independent Android application built with Kotlin and Jetpack Compose.

## Current version: 2.13.0 (versionCode 14)
- Separate package: `com.dailysmart.app.v2`
- Dark navy and blue interface with complementary teal, lavender and gold accents
- Branded launcher icon
- Arabic is the default UI language, with persistent Arabic/French/English selection and RTL layout
- App-authored interface labels, dialogs, finance categories, status messages and reminder notification channels are localized in Arabic, French and English; changing the selected language also applies the locale to Compose and Material date/time pickers. User-entered task, habit and transaction names are intentionally preserved as entered.
- CSV parser supports quoted commas, escaped quotes and multiline descriptions
- Offline assistant commands in English, French, Arabic and Darija, including Arabizi / Darija written with Latin letters and numerals (3/7/9), for common tasks and finance actions
- Gemini assistant through Firebase AI Logic when Firebase configuration is supplied; no embedded Gemini API key
- Finance ledger: income, expenses and MAD balance
- Finance insights: week/month income and expense totals, month-to-date savings/overspending signal, ranked category breakdown, transaction dates, monthly spending budget and savings target progress
- Task list: add, complete and delete
- Habits: add, daily check-off, reset checkmarks and delete
- Device-local one-time reminders with date/time selection, notifications and rescheduling after device reboot
- Finance CSV import/export and DailySmart-format XLSX import
- Workspace XLSX export with Dashboard, Finance, Monthly, Categories, Tasks and Habits sheets according to the selected export scope; dashboard totals use Excel formulas and category/monthly charts are included when matching data exists
- Settings screen with privacy and feature status
- Version 4 JSON backup and confirmed restore for tasks, finance records, habits, monthly budget and monthly savings target; stable record IDs are preserved, and older backups receive IDs when restored
- Offline SQLite persistence (schema version 3) for tasks, finance records and habits, with stable UUID-style record IDs, an in-place migration from schema version 2, and a one-time transactional import from existing SharedPreferences records
- No ads, no subscription and no account

## Gemini setup required before live AI works
1. Create a Firebase project in the Firebase Console and register an Android app with package ID `com.dailysmart.app.v2`.
2. In **AI Services → AI Logic**, select **Get started** and choose **Gemini Developer API**.
3. Download the Android `google-services.json` file. For local testing, place it at `DailySmartV2/app/google-services.json`. For GitHub Actions, store its Base64 contents as the protected Actions secret `FIREBASE_GOOGLE_SERVICES_JSON_BASE64`; do not commit the file. This is Firebase client configuration, not a Gemini API key; never add a Gemini API key to the app.
4. For debug testing, launch the app and register the App Check debug token shown in Android logs under **Firebase Console → App Check → Apps → Manage debug tokens**. Keep AI Logic App Check enforcement enabled.
5. For Play Store builds, register Play Integrity for App Check and use the Play signing certificate fingerprints.

The app build remains possible without `google-services.json`, but Gemini stays disabled until Firebase configuration is added. The assistant sends only the typed prompt to Gemini; it never uploads the user's finance ledger, tasks or habits. Gemini can propose one new task, one income/expense record, one-time reminder or a finance report. The app validates each proposed action and requires the user to confirm before adding a task, transaction or reminder; finance totals are calculated locally. Reminder schedules remain on-device and are not sent to Gemini.

## Secure signed release build
The normal pull-request workflow builds and tests a debug APK. A signed release APK and Play Store AAB are produced only when a repository maintainer manually runs the workflow with the `release` input enabled and all required Actions secrets configured.

Required GitHub repository secrets (Settings → Secrets and variables → Actions):
- `FIREBASE_GOOGLE_SERVICES_JSON_BASE64`: Base64-encoded contents of the Firebase Android `google-services.json` for package `com.dailysmart.app.v2`.
- `ANDROID_KEYSTORE_BASE64`: Base64-encoded upload keystore file.
- `ANDROID_KEYSTORE_PASSWORD`: upload keystore password.
- `ANDROID_KEY_ALIAS`: upload key alias.
- `ANDROID_KEY_PASSWORD`: upload key password.

Generate a dedicated upload key locally and keep a secure offline backup. Never send the keystore or its passwords in chat, commit them to Git, or reuse a personal certificate. On Windows PowerShell, generate the key with:

```powershell
keytool -genkeypair -v -keystore dailysmart-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias dailysmart-upload
[Convert]::ToBase64String([IO.File]::ReadAllBytes("dailysmart-upload.jks")) | Set-Clipboard
```

Paste the clipboard value into `ANDROID_KEYSTORE_BASE64`. Convert Firebase config similarly after downloading it:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("google-services.json")) | Set-Clipboard
```

Paste that value into `FIREBASE_GOOGLE_SERVICES_JSON_BASE64`. Add the three remaining keystore values as separate Actions secrets. Then open **Actions → Build DailySmart V2 Native → Run workflow**, select this feature branch, enable the `release` input, and run it. The `DailySmart-V2-Signed-Release` artifact contains both the signed APK for direct device testing and the AAB for Play Console. Keep the same upload key for all future updates; changing it can prevent normal updates unless the Play Console upload-key reset process is used.

## Real-device acceptance checklist
Run this checklist on the actual Android phone before considering the build a beta candidate:
- Install the debug APK alongside the previous app; verify the package remains `com.dailysmart.app.v2`.
- Add, edit, complete and delete tasks, habits, income and expenses; force-close and reopen; confirm records remain.
- Export CSV and XLSX; open both in Excel or Google Sheets; verify formulas, totals, chart data and Arabic/French text; import a DailySmart workbook and confirm no duplicate or lost records.
- Create a reminder with notifications allowed; test delivery while app is backgrounded, after force-stop if supported by Android, and after device reboot. Android battery optimization may delay inexact alarms.
- Switch Arabic / French / English; verify labels, dialogs, date/time controls, RTL layout, and app restart persistence.
- Measure scrolling and startup on the phone; check crash-free behavior and battery use.
- Test backup and restore using a disposable test dataset before restoring any real personal data.
- Test a signed release APK installed from the build artifact and verify Gemini through Firebase App Check. For Play Integrity production testing, use a Play internal-testing installation and register the Play signing certificate in Firebase App Check.

## Not yet implemented / validation still required
- Real-device validation of backup/restore, upgrade from an older installed APK, performance and reminder delivery
- Multi-step Gemini workflows, editing/deleting records through Gemini, recurring reminders and habit actions; current AI proposals are limited to one new task, one income/expense record, one-time reminder with a clear future date/time, or a local finance report
- Editing arbitrary Excel workbooks and importing arbitrary Excel sheets; XLSX import currently focuses on DailySmart finance workbooks and the first worksheet
- Final visual proofreading of every translated screen and system-owned Android file-picker/permission dialog on real devices; system-owned dialogs may follow the Android system language rather than the in-app language
- Calendar and goals; local one-time reminders are supported

## Data protection and limits
Reminders are stored locally and are not included in JSON backups yet. Reminder alarms are inexact and Android may delay delivery to save battery. JSON backup files are limited to 2 MB and validated before restore; restoring replaces existing records only after explicit confirmation. CSV imports are limited to 5 MB and validate finite positive amounts. XLSX imports enforce a 10 MB source-file limit, a 10 MB total expanded-content limit, a 5 MB per-entry limit and a 64-entry limit. Habit checkmarks reset when the local calendar date changes. Android automatic cloud backup is disabled for the app; users can create an explicit JSON backup from Settings. The app labels unavailable capabilities honestly; it does not present local command shortcuts as an LLM.
