# Tick

Android client for [Kimai](https://www.kimai.org). Kotlin, Jetpack Compose, Material 3 with dynamic color.

- Start and stop timers, pick project and activity, add a description
- Ongoing notification with a live chronometer and a Stop button
- Recent entries grouped by day: start again, edit (description, start and end date and time), delete
- Home screen widget (Stop / Start last) and a Quick Settings tile
- Times follow your system 12/24h setting. Override in the top-right menu, Time format.
- Picks up timers started elsewhere (web UI) every 15 minutes in the background, and on app open

## Build on NixOS

```sh
git init && git add -A      # flakes only see tracked files
nix develop                 # JDK 17 + Android SDK 35 + adb, aapt2 override set
./gradlew assembleDebug     # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # with the phone connected over adb
```

With direnv: `direnv allow` instead of `nix develop`.

First build downloads Gradle 8.10.2 and the Maven dependencies, so it needs network.

Android Studio is optional. If you want it, add `pkgs.android-studio` to `packages` in `flake.nix`
and set the SDK path to `$ANDROID_HOME` in the project settings.

### NixOS notes

AGP downloads its own `aapt2`, which is a dynamically linked binary and fails on NixOS.
The dev shell sets `GRADLE_OPTS` to use the Nix-provided one instead. If you build outside
the shell, pass it by hand:

```sh
./gradlew assembleDebug -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/35.0.0/aapt2
```

If `flake.nix` bumps `buildToolsVersion`, change it in `app/build.gradle.kts` too.

## Sign in

Create an API token in your Kimai user profile and enter the server URL plus the token.
Servers that only support the legacy API password: fill in the optional username field too.
Plain `http://` servers are blocked by Android's default cleartext policy. Use https.

## Layout

```
app/src/main/java/app/tick/kimai/
  data/       Kimai REST client, models, prefs
  TimerRepository.kt   one place for sync/start/stop; updates notification, widget, tile
  notify/     ongoing notification + Stop receiver
  widget/     Glance home screen widget
  tile/       Quick Settings tile
  work/       15 minute background sync
  ui/         Compose screens and theme
```

## Notes

- On Android 14+, a non-foreground-service notification can be swiped away. It comes back on the next sync or app open.
- Not yet: editing a running entry, offline queue, multiple servers.
