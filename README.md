# DailySmart — Native Android App

The current native Android application is in `DailySmartV2/`.

## Technology
- Kotlin 1.9.24
- Jetpack Compose and Material 3
- Offline-first SQLite persistence (via `SQLiteOpenHelper`; not Room)
- Android Gradle Plugin 8.5.2
- Application ID: `com.dailysmart.app.v2`
- Current app version: 2.13.0 (versionCode 14)

## Build and download
The `dev` branch is the integration branch; feature work is reviewed through pull requests. GitHub Actions builds and tests the debug APK:
1. Open the [DailySmart V2 build workflow](https://github.com/zouhairboutakourte/Dailysmart/actions/workflows/build-dailysmart-v2.yml).
2. Select a successful run for the branch you want to test.
3. Under **Artifacts**, download `DailySmart-V2-Native-APK`.
4. Extract the ZIP and install the APK on an Android device.

Workflow artifacts are temporary and expire according to GitHub's artifact-retention policy. Debug APKs are for testing, not Play Store release builds.

## Open the project
Open the `DailySmartV2/` folder in Android Studio, allow Gradle sync to finish, then run the app on an emulator or connected Android device.

See [DailySmartV2/README.md](DailySmartV2/README.md) for implemented features, limitations, Firebase setup and data-safety details.

## Development policy
Do not commit directly to `main`. Develop on feature branches and review changes through pull requests targeting `dev`.
