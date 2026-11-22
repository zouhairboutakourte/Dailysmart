# DailySmart V2 — Product Brief (Source of Truth)

## Product objective
Evolve DailySmart V2 (`com.dailysmart.app.v2`) into an intelligent, offline-first Android Personal OS. It centralizes personal finance, tasks/goals and spreadsheet workflows in a modern Dark Navy/Cyan interface, supported by a fast, responsive assistant.

## Essential capabilities

### 1. Local-first AI assistant
- Implement and evolve `AssistantEngine.kt` for fast natural-language commands in Arabic, Moroccan Darija, French and English.
- Extract financial amounts dynamically and understand common user phrasing, including Arabizi/transliterated Darija.
- Execute supported local actions (add expense/income, quick finance queries, task/goal actions) with minimal latency.
- Use deterministic local parsing for common commands and reserve Gemini for requests that benefit from broader language understanding.
- Validate proposed actions and require confirmation for consequential writes.
- Do not embed Gemini API keys in the APK. Prefer Firebase AI Logic with App Check or a secure server-side proxy.

### 2. Finance and budgets
- Track income, expenses, balances, monthly budgets and savings goals.
- Show a clear finance snapshot on Home and in suitable Android widgets.
- Keep calculations and ledger data local; clearly distinguish current-month totals, available budget and savings.

### 3. Excel / CSV
- Evolve `ExcelWorkbook.kt` for robust XLSX and CSV import/export.
- Handle malformed, oversized or unsupported files without crashing or silently dropping rows.
- Run file parsing and writing asynchronously on `Dispatchers.IO`; use `Dispatchers.Default` for CPU-heavy transformations where appropriate.
- Report skipped/rejected rows and preserve source data on failed imports.
- Export safely, including protection against spreadsheet formula injection.

### 4. Offline-first reliability, privacy and localization
- Persist core data locally and keep the app useful without connectivity.
- Support Arabic RTL, French, English and Darija command input.
- Avoid exposing secrets in source, APKs, logs or CI output.
- Keep database migrations, backup/restore and money precision lossless and auditable.
- Use secure storage only where sensitive local secrets are genuinely needed.

### 5. CI/CD
- Maintain automated Android APK builds with GitHub Actions on branch `dev`, using `.github/workflows/build-dailysmart-v2.yml` unless repository review establishes a better existing workflow.
- Run unit tests before packaging; verify application ID is exactly `com.dailysmart.app.v2`.
- Upload the APK as a workflow artifact.
- Never push V2 work into `main` as part of routine development.

## Audience
Active users, independent workers, freelancers and gig-economy workers managing variable income, tasks and everyday data from a smartphone. The experience should suit multilingual users who need a fast, lightweight, dependable offline tool.

## Technology direction
- Native Android, Kotlin, Jetpack Compose and Material 3.
- Dark Navy `#0A0F1D` with Cyan `#00E5FF` as the primary visual system.
- Local persistence (prefer structured storage such as Room for ledger/task data; SharedPreferences/DataStore for small settings).
- Kotlin coroutines; keep disk and spreadsheet work off the main thread.
- Gemini through a secure integration (Firebase AI Logic/App Check or server proxy); never hard-code an API key.
- XLSX/CSV support in `ExcelWorkbook.kt`.

## Product quality gates
- No demo-only or fabricated finance data in production screens.
- No silent loss of money records, spreadsheet rows or backup contents.
- No blocking file I/O on the UI thread.
- Arabic RTL and language switching must cover actual content, not only navigation labels.
- Network/AI failure must not prevent local finance, task or habit workflows.
- A green CI run is required before calling an APK build successful; real-device checks remain separately identified.

## Marketing direction
Create short, concrete demonstrations for TikTok, Instagram Reels, YouTube Shorts, X and LinkedIn. Show Darija/French/Arabic assistant commands, quick finance capture, Excel import/export and the Dark Navy/Cyan interface. Reach freelancer, student, creator and productivity/personal-finance communities organically. Iterate from feedback on `dev` APK builds and focus messaging on real use cases such as irregular-income budgeting and faster spreadsheet handling.

## Implementation notes
This brief records the user's product direction. It does not claim that every listed capability is already implemented. Current code and test status must be audited independently and reported honestly.
