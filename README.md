# DailyGlow Android

DailyGlow Android opens the deployed DailyFlow website in an Android WebView. The website remains the source of truth for its interface, account, and content, so web deployments appear in the app without rebuilding the APK.

## Included

- The same deployed DailyFlow site: vocabulary, insights, and daily training.
- Web login and persistent site cookies/storage.
- Android back navigation, loading progress, offline retry, and external-link handoff.
- Release APKs signed with the repository's existing Android release keystore.

## Build

The repository workflow `.github/workflows/android-webview.yml` builds a signed APK on pushes to `feat/dailyflow-webview-android` and can also be started manually from GitHub Actions. It requires these repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`

The APK is uploaded as the `dailyglow-webview-signed-apk` workflow artifact. It is not published as a GitHub Release.

For a local debug build, open the repository in Android Studio with JDK 17 and Android SDK 35, then run:

```sh
./gradlew assembleDebug
```

## Website address

The app points to the current DailyGlow EdgeOne deployment through `BuildConfig.DAILYFLOW_URL` in `app/build.gradle.kts`. Change this value only when the production website address changes.

## Security notes

- JavaScript and DOM storage are enabled because the deployed site uses a modern web application and web sign-in.
- Cleartext HTTP, file access, and mixed-content requests are disabled.
- External HTTPS links open outside the app.
