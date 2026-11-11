# DailySmart — Native Android App

The current native Android application is in `DailySmartV2/`.

## Technology
- Kotlin 1.9.24
- Jetpack Compose and Material 3
- Offline-first SQLite persistence (via `SQLiteOpenHelper`; not Room)
- Android Gradle Plugin 8.5.2
- Application ID: `com.dailysmart.app.v2`
- Current app version: 2.5.0 (versionCode 6)

## Build and download
The `dev` branch contains the active development version. GitHub Actions builds and tests the debug APK:
1. Open the [DailySmart V2 build workflow](https://github.com/zouhairboutakourte/Dailysmart/actions/workflows/build-dailysmart-v2.yml).
2. Select the latest successful run on `dev`.
3. Under **Artifacts**, download `DailySmart-V2-Native-APK`.
4. Extract the ZIP and install the APK on an Android device.

The APK artifact is temporary and expires according to GitHub's artifact-retention policy.

## Open the project
Open the `DailySmartV2/` folder in Android Studio, allow Gradle sync to finish, then run the app on an emulator or connected Android device.

See [DailySmartV2/README.md](DailySmartV2/README.md) for implemented features, limitations, and data-safety details.

## Development policy
Active work is on `dev`. The `main` branch is not modified by this workflow.
