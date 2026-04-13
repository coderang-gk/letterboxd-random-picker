# Android App

This app reads the weekly movie feed published by the GitHub Action and applies the movie poster as the Android lock screen wallpaper.

## Architecture

- GitHub Action publishes `public/latest-movie.json` on the `main` branch.
- The app fetches that JSON from the default raw GitHub URL.
- A background `WorkManager` job checks once a day for a new movie ID.
- The lock screen wallpaper is only updated when the weekly movie ID changes.

## Default feed URL

- `https://raw.githubusercontent.com/coderang-gk/letterboxd-random-picker/main/public/latest-movie.json`

## Building

This folder is a standalone Android project and can be built from the command line.

### Command line build

The project now includes a Gradle wrapper, so Android Studio is optional.

1. Ensure these are installed:
   - JDK 17
   - Android command-line tools
   - Android SDK platform packages for API 35
2. Export:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH
export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools
export GRADLE_USER_HOME="$PWD/.gradle-user-home"
export GRADLE_OPTS='-Dorg.gradle.native=false -Dorg.gradle.console=plain'
```

3. Build:

```bash
./gradlew assembleDebug
```

4. The generated APK will be at:

```bash
app/build/outputs/apk/debug/app-debug.apk
```

### Running

Run the app on a device with Android 7.0 or newer for lock-screen-only wallpaper support.

## Notes

- `WorkManager` does not guarantee an exact run time. The app checks daily and only applies a new wallpaper when the weekly movie changes.
- On Android versions below 7.0, the fallback wallpaper call may apply to both home and lock screen.
