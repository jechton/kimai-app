<p align="center"><img src="docs/logo.svg" alt="Tick logo" width="96" height="96"></p>

# Tick

[![CI](https://github.com/jechton/kimai-app/actions/workflows/ci.yml/badge.svg)](https://github.com/jechton/kimai-app/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/jechton/kimai-app)](https://github.com/jechton/kimai-app/releases)
[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="60">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/jechton/kimai-app)

A small Android client for [Kimai](https://www.kimai.org) time tracking. Start a timer in one tap
from the app, the notification, a home screen widget or a Quick Settings tile.

Built with Kotlin, Jetpack Compose and Material 3 (dynamic color). English, German and Spanish.

## Features

**Timer**
- Start and stop timers, pick project and activity, add a description
- Last project and activity are preselected, so starting is one tap
- Forgot to start? "5m / 15m / 30m ago" buttons start the timer in the past (presets are editable)
- Edit a running entry's description and start time without stopping it
- Ongoing notification with a live chronometer and a Stop button. On Android 16+ it asks to be a Live Update (Samsung Now Bar)

**Entries**
- Recent entries grouped by day: start again, edit (description, start and end), delete
- Add a past entry directly, without starting a timer
- Projects and activities are cached, so the pickers work offline

**Week**
- Weekly hours with a progress bar toward your target (Kimai contract, or your own), per-day totals, previous weeks via the arrows
- Pace line: how far ahead or behind you are today, based on the days you work
- Estimated weekly pay from Kimai's per-entry rates or your own hourly rate, minus optional tax
- Team overview

**Everywhere else**
- Home screen widget: Stop / Start last, recent entries, week total. Long-press it and choose reconfigure for opacity, theme and what to show
- Quick Settings tile to start and stop
- Picks up timers started elsewhere (web UI) every 15 minutes in the background, and on app open
- Offline queue: every timer change works without a connection and replays once it's back. A banner shows how many are waiting
- Times follow your system 12/24h setting, or override it in the top-right menu

Most settings live in the top-right menu.

## Sign in

Create an API token in your Kimai user profile, then enter the server URL and the token. Or scan
(or upload a screenshot of) Kimai's login QR code to fill both in.

- Servers that only support the legacy API password also need the optional username field.
- Plain `http://` servers are blocked by Android's default cleartext policy. Use https.

## Notes

- The ongoing notification runs in a foreground service so it can't be swiped away. If Android refuses to start the service from the background, it falls back to a plain notification. If that is swiped away it is re-posted, and promoted to the service on the next sync or app open.
- Update check (Settings, on by default): when the app opens, at most every 12 hours, it asks GitHub for the latest release. If it is newer, a banner offers Install (it downloads, then opens Android's installer) and a link to the release page. Android asks you to allow installs from Tick the first time. Turn it off in Settings. The only request it makes is to `api.github.com`.
- Languages follow the system language. Strings live in `app/src/main/res/values*/strings.xml`. A new language needs every key from `values/strings.xml`.

## Build

The repo builds with Nix (NixOS-first). Android Studio is optional.

```sh
git init && git add -A      # flakes only see tracked files
nix develop                 # JDK 17 + Android SDK 35 + adb, aapt2 override set
./gradlew assembleDebug     # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # with a phone or emulator connected over adb
```

With direnv: `direnv allow` instead of `nix develop`. direnv also puts `scripts/` on `PATH`, so
`tick build`, `tick install`, `tick avd` (create the emulator once), `tick emulator` and `tick run` work directly.

The first build downloads Gradle 8.10.2 and the Maven dependencies, so it needs network.
For Android Studio, add `pkgs.android-studio` to `packages` in `flake.nix` and set the SDK path to `$ANDROID_HOME`.

**NixOS and aapt2.** AGP downloads its own `aapt2`, which is dynamically linked and fails on NixOS.
The dev shell sets `GRADLE_OPTS` to use the Nix one instead. Outside the shell, pass it by hand:

```sh
./gradlew assembleDebug -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/35.0.0/aapt2
```

If `flake.nix` bumps `buildToolsVersion`, change it in `app/build.gradle.kts` too.

## Tests and style

```sh
./gradlew testDebugUnitTest   # JUnit, pure JVM: the pace logic
./gradlew ktlintFormat        # auto-fix style
./gradlew ktlintCheck         # check only, what CI runs
```

GitHub Actions (`.github/workflows/ci.yml`) runs `ktlintCheck` and `assembleDebug` on every push and PR.
Code style is enforced with [ktlint](https://pinterest.github.io/ktlint/).

## Releasing

Pushing a tag like `v0.2.0` runs the `release` job in `.github/workflows/ci.yml` (after `build` passes).
It builds a signed release APK (`versionName` comes from the tag) and attaches it to a new GitHub Release.

It needs these repo secrets, set once:

- `RELEASE_KEYSTORE_BASE64`: a release keystore, base64-encoded (`base64 -w0 release.keystore`).
  Generate one with
  `keytool -genkeypair -v -keystore release.keystore -alias tick -keyalg RSA -keysize 2048 -validity 10000`
  and keep it safe. Losing it means you can never publish an update under the same signature.
- `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`: match what you used above.

Without the secrets, `assembleRelease` still builds, but the APK is unsigned.

## Layout

```
app/src/main/java/app/tick/kimai/
  data/       Kimai REST client, models, prefs, update check
  TimerRepository.kt   one place for sync/start/stop; updates notification, widget, tile
  notify/     ongoing notification + Stop receiver
  widget/     Glance home screen widget
  tile/       Quick Settings tile
  work/       15 minute background sync
  ui/         Compose screens, dialogs and theme
```
