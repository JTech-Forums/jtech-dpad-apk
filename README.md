<p align="center">
  <img src="assets/logo.png" width="120" alt="JtechForums">
</p>

## JtechForums App

Android app for [JtechForums](https://forums.jtechforums.org/dumb) with D-Pad navigation support and push notifications.

## About

This app is a frontend for [forums.jtechforums.org/dumb](https://forums.jtechforums.org/dumb) — a minimal, fast UI for Discourse built without modern JavaScript. It runs on everything from old devices to new flagships.

## Compatibility

Supports Android 6.0+ and Chrome WebView 44+.

## Download

Get the latest APK from [Releases](https://github.com/JTech-Forums/jtech-dpad-apk/releases/latest). Each release has two:

- `jtech-<version>.apk`: the standard app.
- `jtech-<version>-no-foreground-notifications.apk`: the same app without the persistent "Listening for notifications" notification. Push notifications still work, but Android may pause them briefly when it needs memory. Built from the `no-foreground-notifications` branch.

Both are signed with the same key, so either installs as an update over the other.

## Build

```
./gradlew assembleRelease
```

## Hardware soft keys

On keypad phones (no touchscreen, or a physical D-pad) on Android 8.0+, the forum's soft-key bar is drawn natively and driven by the phone's real soft keys. The left / center / right labels are always the page's own, on every screen and popup. The forum's **Preferences › Soft-key bar** setting controls it: Keypad phones = automatic, Always = on, Never = off. Touch phones and older Android keep the page's own bar.

To force it on a touch device for testing: `adb shell settings put global jtech_force_dpad 1`

## License

GPL-3.0

### Yapchik: the hardware soft-key engine

[Yapchik](https://github.com/theonionsarewatching/yapchik) by **theonionsarewatching**, vendored as the [`:yapchik`](yapchik/) module.

- Copyright © 2026 theonionsarewatching
- GNU Lesser General Public License v3.0 or later. See [`yapchik/LICENSE`](yapchik/LICENSE) and [`yapchik/NOTICE`](yapchik/NOTICE), which lists the local changes.
