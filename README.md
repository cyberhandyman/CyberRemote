# Companion Remote

A free, open-source, ad-free Android remote for Apple TV (tvOS 15+), talking
the Companion Link protocol directly over your local network. No cloud, no
bridge server, no telemetry — your phone talks straight to the Apple TV.

> Companion Remote is an independent open-source project. It is **not
> affiliated with, endorsed by, or sponsored by Apple Inc.** "Apple TV" is a
> trademark of Apple Inc., used here only to describe compatibility.

## Features (v1)

- D-pad / select / back / home, play-pause, volume, wake & sleep
- Pairing with the PIN shown on the TV (HomeKit-style pair-setup)
- **Full keyboard input**: when a text field is focused on the TV, type with
  your phone's keyboard — including CJK and emoji
- Touchpad swipe navigation
- App list + launch

## Modules

- `protocol/` — pure Kotlin/JVM implementation of the Companion Link
  protocol (OPACK, TLV8, HAP pairing/verify, encrypted session, commands,
  RTI text input). No Android dependencies; reusable by other projects.
- `cli/` — JVM command-line harness (scan / pair / command / text) used for
  integration testing against real hardware.
- `app/` — the Android app (Jetpack Compose).

## Status

Work in progress; see `BUILD_REPORT.md`.

## License

MIT. Protocol knowledge derives from the excellent reverse-engineering work
of [pyatv](https://github.com/postlund/pyatv) (MIT).
