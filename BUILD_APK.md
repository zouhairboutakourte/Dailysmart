# Build Signed Release APK for Daily Smart

### 1. Generate Keystore
```bash
keytool -genkey -v -keystore app/dailysmart-release-key.jks \
  -alias dailysmart -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass dailysmartpass -keypass dailysmartpass \
  -dname "CN=DailySmart, OU=Mobile, O=DailySmartApp, L=Casablanca, C=MA"
```

### 2. Assemble Release
```bash
./gradlew assembleRelease
```

### 3. Release APK Location
```
app/build/outputs/apk/release/app-release.apk
```