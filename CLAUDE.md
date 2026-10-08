# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Tick: an Android client for [Kimai](https://www.kimai.org) (Kotlin, Jetpack Compose, Material 3 with dynamic color).

- Start/stop timers, pick project and activity, add a description
- Ongoing notification with a live chronometer and a Stop button
- Recent entries grouped by day: start again, edit (description, start/end date and time), delete
- Home screen widget (Stop / Start last) and a Quick Settings tile
- Times follow the system 12/24h setting, overridable in the top-right menu (Time format)
- Picks up timers started elsewhere (web UI) every 15 minutes in the background, and on app open

## Build and run

This repo builds via Nix (NixOS-first); Android Studio is optional.

```sh
nix develop                 # JDK 17 + Android SDK 35 + adb, aapt2 override set
./gradlew assembleDebug     # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # with a device/emulator connected over adb
```

With direnv: `direnv allow` instead of `nix develop`. First build needs network (downloads Gradle 8.10.2 and Maven deps).

There is no test suite in this repo currently.

### NixOS specifics

- AGP downloads its own `aapt2`, which is dynamically linked and fails on NixOS. The dev shell sets `GRADLE_OPTS` to point at the Nix-provided `aapt2` instead. Outside the shell: `./gradlew assembleDebug -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/35.0.0/aapt2`.
- If `flake.nix` bumps `buildToolsVersion`, change it in `app/build.gradle.kts` too (there's a comment marking both spots).
- `flake.nix`'s `androidComposition` includes the emulator and an x86_64 `google_apis_playstore` system image for `android-35`, so an AVD can be created and run from the dev shell:
  ```sh
  avdmanager create avd -n tick -k "system-images;android-35;google_apis_playstore;x86_64" -d pixel_6
  ANDROID_AVD_HOME=$HOME/.local/share/android/avd emulator -avd tick
  ```
  (avdmanager on this SDK version creates AVDs under the XDG data dir rather than `~/.android/avd`, hence the explicit `ANDROID_AVD_HOME`.)

## Sign-in flow

Users create an API token in their Kimai user profile and enter the server URL plus the token. Servers that only support the legacy API password also need the optional username field (sends `X-AUTH-USER`/`X-AUTH-TOKEN` instead of a bearer token — see `KimaiApi`). Plain `http://` servers are blocked by Android's default cleartext policy; only `https://` is supported. `MainViewModel.normalizeUrl` adds `https://`, strips trailing slashes and a trailing `/api`.

## Architecture

```
app/src/main/java/app/tick/kimai/
  data/       Kimai REST client (KimaiApi), models, SharedPreferences-backed Prefs
  TimerRepository.kt   single place for sync/start/stop; fans out to notification, widget, tile
  MainViewModel.kt     app UI state (StateFlow<UiState>), delegates all timer I/O to TimerRepository
  notify/     ongoing notification + its Stop action BroadcastReceiver
  widget/     Glance home screen widget
  tile/       Quick Settings tile
  work/       15-minute background sync (WorkManager periodic job, registered in TickApp)
  ui/         Compose screens (Screens.kt) and theme
```

**`TimerRepository` is the hub.** Every surface (app UI, ongoing notification, home screen widget, Quick Settings tile) reads from and writes through it, never directly from `KimaiApi`. Its `sync()` fetches recent timesheets from Kimai, derives the single `TimerState` (currently-running entry + most recent finished one), persists it to `Prefs`, and calls `refreshSurfaces()` to push that state out to the notification, widget, and tile. All mutating calls (`start`, `stop`, `restart`, `delete`, `updateEntry`, `toggle`) end by re-calling `sync()` so every surface converges on server truth after any action — there's no separate local-state-update path to keep in sync by hand.

`toggle()` is what the widget button and quick tile call: stop if a timer is running, otherwise restart the last entry. It takes `updateTile: Boolean` to avoid recursion when called *from* `TimerTileService` itself.

**Offline queue.** Mutating calls in `TimerRepository` go through `perform()`: replay any queued changes first, then try the call; on a network failure (not an `ApiException`) the change is stored as a `PendingAction` in `Prefs`, the cached `TimerState` is updated optimistically, and `SyncWorker.flushWhenOnline` schedules a replay when connected. `flushQueue()` (also run at the start of every `sync()`) replays in order under a process-wide mutex and drops changes the server rejects. Timers started offline have no id, so a queued `Stop` ends whatever timer is running when replayed. Queued changes are lost on sign out.

**Auth header logic lives in `KimaiApi`**: bearer token by default, or `X-AUTH-USER`/`X-AUTH-TOKEN` headers when `legacyUser` (from the optional username field) is non-blank.

**`Entry.project`/`Entry.activity` use `RefSerializer`** because Kimai's timesheets endpoint returns project/activity as a bare id in some contexts and a full object (`{id, name}`) in others — the custom `JsonTransformingSerializer` normalizes both to `Ref`.

**Times are handled in the user's Kimai timezone, not the device's.** `MainViewModel.login` stores `me().timezone` from Kimai into `Prefs.userTimezone`; `TimerRepository.userZone()` falls back to the system default only if that's unset. Start/stop/update calls format timestamps via `Fmt` using that zone before sending them to the API.

**State flow:** `MainViewModel` holds one `UiState` `StateFlow` that the Compose UI (`Screens.kt`) collects. Every mutating action goes through `launchBusy`, which toggles `busy`, runs the suspend block, and turns exceptions into a user-facing `error` string (`describe()` special-cases `UnknownHostException`). There is no separate error/loading state machine beyond these two fields.

**Widget note:** `androidx.glance.action.actionStartActivity<T>()` (zero-arg, reified) lives in the `androidx.glance.action` package (core Glance), not `androidx.glance.appwidget.action` (which only has `Intent`-based overloads that require an explicit `intent` argument). Easy to import the wrong one and get a confusing "No value passed for parameter 'intent'" compile error.

## Known gaps (per README)

Not yet implemented: multiple servers. On Android 14+, a non-foreground-service notification can be swiped away; it comes back on the next sync or app open.
