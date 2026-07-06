# Companion Remote

A free, open-source, **ad-free Android remote for Apple TV** (tvOS 15+).
It speaks Apple's Companion Link protocol directly over your local network —
no cloud, no bridge server, no companion daemon, no telemetry. The phone
talks straight to the Apple TV.

> Companion Remote is an independent open-source project. It is **not
> affiliated with, endorsed by, or sponsored by Apple Inc.** "Apple TV" is a
> trademark of Apple Inc., used here only to describe compatibility.

## Features

- **Full keyboard input** — when a text field is focused on the TV, your
  phone's keyboard opens automatically and types straight into it (search
  boxes, passwords, URLs). CJK and emoji included.
- D-pad, select, back, home, play/pause, volume
- Wake and sleep
- Touchpad swipe navigation
- App list + launch
- Pairing with the PIN shown on the TV (HomeKit-style pair-setup); your
  credentials never leave the phone (encrypted with the Android Keystore)

## Install

Grab the APK from [GitHub Releases](../../releases) and install it
(you may need to allow installs from unknown sources).

Requirements: Android 8.0+ (API 26), an Apple TV HD/4K on tvOS 15–26, and
both devices on the **same Wi-Fi network / subnet**.

## First run

1. On the Apple TV, check `Settings → AirPlay and HomeKit → Allow Access`
   permits devices on your network.
2. Open Companion Remote — your Apple TV should appear in the list.
3. Tap it, enter the 4-digit PIN shown on the TV, done.

## FAQ

**The app doesn't find my Apple TV.**
Discovery uses mDNS (Bonjour), which only works within the same broadcast
domain. Phone and TV must be on the same subnet/VLAN, and your router must
not block multicast ("AP/client isolation" breaks it). As a fallback, use
*Connect by IP* (the ⛓ icon) — note the port changes after every Apple TV
reboot; you can find the current one with any Bonjour browser.

**Pairing fails or no PIN appears.**
Check `Settings → AirPlay and HomeKit → Allow Access` on the TV. If a PIN
appears but pairing still fails, try once more — then open an issue.

**It stopped working after a tvOS update.**
The Companion protocol is private and reverse-engineered (by the
[pyatv](https://github.com/postlund/pyatv) project); tvOS updates can break
it at any time. Check open issues — breakage is usually fixed in pyatv
first, and we port the fix.

**Typing doesn't reach the TV.**
The keyboard only works while a text field is focused on the TV — the app
shows "Typing on …" when it is. Password fields on some tvOS versions block
reading the current text; replacing/typing still works.

**Why does the app need the multicast permission?**
`CHANGE_WIFI_MULTICAST_STATE` is required for mDNS discovery on Android;
without a multicast lock the scan silently finds nothing. The app makes no
network connections other than to your Apple TV.

**Now playing info / artwork?**
Not in v1 — that needs a different protocol (MRP over AirPlay 2). Planned
as a possible v2.

## Building

```bash
./gradlew :protocol:test          # protocol unit tests (pure JVM)
./gradlew :app:assembleDebug      # APK at app/build/outputs/apk/debug/
./gradlew :cli:run --args="scan"  # CLI harness for protocol debugging
```

## Modules

- `protocol/` — pure Kotlin/JVM implementation of the Companion Link
  protocol: OPACK, TLV8, HAP pair-setup/pair-verify (SRP-6a), the
  ChaCha20-Poly1305 session, HID/touch/app/power commands and RTI text
  input (binary-plist keyed archives). No Android dependencies — reusable.
- `cli/` — command-line harness (`scan`, `pair`, `command`, `text-set`,
  `focus-state`, …). The primary integration-test tool against real
  hardware.
- `app/` — the Android app (Jetpack Compose, Material 3).
- `docs/protocol-notes.md` — the verified protocol details and where each
  piece was ported from.

## Credits & license

MIT. The protocol knowledge comes from the outstanding reverse-engineering
work of [pyatv](https://github.com/postlund/pyatv) (MIT) — this project is
a from-scratch Kotlin implementation following pyatv's documented behavior.
