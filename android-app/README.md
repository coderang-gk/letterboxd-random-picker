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

This folder is a standalone Android Studio project.

1. Open `android-app/` in Android Studio.
2. Let Android Studio download the Gradle wrapper and SDK components it needs.
3. Run the app on a device with Android 7.0 or newer for lock-screen-only wallpaper support.

## Notes

- `WorkManager` does not guarantee an exact run time. The app checks daily and only applies a new wallpaper when the weekly movie changes.
- On Android versions below 7.0, the fallback wallpaper call may apply to both home and lock screen.
