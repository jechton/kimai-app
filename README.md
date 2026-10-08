<p align="center"><img src="docs/logo.svg" alt="Tick logo" width="96" height="96"></p>

# Tick

[![CI](https://github.com/jechton/kimai-app/actions/workflows/ci.yml/badge.svg)](https://github.com/jechton/kimai-app/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/jechton/kimai-app)](https://github.com/jechton/kimai-app/releases)

Android client for [Kimai](https://www.kimai.org). Kotlin, Jetpack Compose, Material 3 with dynamic color.

- Start and stop timers, pick project and activity, add a description
- "Start again" front and center, reusing the last entry's project/activity/description
- Ongoing notification with a live chronometer and a Stop button
- Recent entries grouped by day: start again, edit (description, start and end date and time), delete
- Edit a running entry's description and start time without stopping it
- Add a past entry directly (project, activity, description, start and end) without starting/stopping a timer
- Home screen widget (Stop / Start last) and a Quick Settings tile
- Times follow your system 12/24h setting. Override in the top-right menu, Time format.
- Picks up timers started elsewhere (web UI) every 15 minutes in the background, and on app open
- Offline queue: start, stop, restart, add, edit and delete work without a connection and replay once it's back. A banner shows how many changes are waiting.

## Sign in

Create an API token in your Kimai user profile and enter the server URL plus the token, or scan
(or upload a screenshot of) Kimai's login QR code to fill both in automatically.
Servers that only support the legacy API password: fill in the optional username field too.
Plain `http://` servers are blocked by Android's default cleartext policy. Use https.

## Notes

- On Android 14+, a non-foreground-service notification can be swiped away. It comes back on the next sync or app open.
- Not yet: multiple servers.

## Build on NixOS

```sh
git init && git add -A      # flakes only see tracked files
nix develop                 # JDK 17 + Android SDK 35 + adb, aapt2 override set
./gradlew assembleDebug     # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # with the phone connected over adb
```

With direnv: `direnv allow` instead of `nix develop`. direnv also puts `scripts/` on
`PATH`, so `tick build`, `tick install`, `tick avd` (create the emulator once) and
`tick emulator`/`tick run` work directly.

First build downloads Gradle 8.10.2 and the Maven dependencies, so it needs network.

Android Studio is optional. If you want it, add `pkgs.android-studio` to `packages` in `flake.nix`
and set the SDK path to `$ANDROID_HOME` in the project settings.

## Formatting and CI

Code style is enforced with [ktlint](https://pinterest.github.io/ktlint/):

```sh
./gradlew ktlintFormat   # auto-fix
./gradlew ktlintCheck    # check only, what CI runs
```

GitHub Actions (`.github/workflows/ci.yml`) runs `ktlintCheck` and `assembleDebug` on every push and PR.

## Releasing

Pushing a tag like `v0.2.0` runs the `release` job in `.github/workflows/ci.yml` (after the
`build` job passes), which builds a signed release APK (`versionName` taken from the tag) and
attaches it to a new GitHub Release.

Needs these repo secrets, set once:

- `RELEASE_KEYSTORE_BASE64` — a release keystore, base64-encoded (`base64 -w0 release.keystore`).
  Generate one with
  `keytool -genkeypair -v -keystore release.keystore -alias tick -keyalg RSA -keysize 2048 -validity 10000`
  and keep it somewhere safe — losing it means you can never publish an update under the same signature.
- `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` — match what you used above.

Without those secrets, `assembleRelease` still builds (useful for CI on PRs that touch release
config) but the APK comes out unsigned.

### NixOS notes

AGP downloads its own `aapt2`, which is a dynamically linked binary and fails on NixOS.
The dev shell sets `GRADLE_OPTS` to use the Nix-provided one instead. If you build outside
the shell, pass it by hand:

```sh
./gradlew assembleDebug -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/35.0.0/aapt2
```

If `flake.nix` bumps `buildToolsVersion`, change it in `app/build.gradle.kts` too.

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
