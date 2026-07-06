# BUILD_REPORT — autonomous M0→M8 run (2026-07-06)

Living document, updated per milestone. Environment: macOS, JDK 21
(Android Studio JBR at `/Applications/Android Studio.app/Contents/jbr/
Contents/Home`), Gradle 8.14.3 (wrapper), Android SDK at
`~/Library/Android/sdk` (platform 34 installed; 35 fetched on demand).

## Recon (pre-M0) — DONE

- Verified all CLAUDE.md protocol assertions against pyatv b277a4c8
  (v0.18.0+304). Findings + exact porting sources: `docs/protocol-notes.md`.
- Key corrections made to CLAUDE.md:
  - RTI `_tiD` payloads are NSKeyedArchiver **binary plists**, not UTF-8 ⇒
    `:protocol` needs a minimal bplist codec (extra, unplanned component).
  - Pair-setup HKDF input is K = SHA512(S); srptools strips leading zeros on
    integer terms in M1/M2 proofs (pyatv is srptools-based; srptools 1.0.1
    source downloaded and read — it is NOT in references/).
  - M5 TLV 0x11 OPACK = `{"name": …}` only.
  - Connect sequence includes `_touchStart` + `_tiStart`.
  - Touch phases Press=1 Hold=3 Release=4 Click=5; wake/sleep sent as single
    up-event.
- node-appletv-remote confirmed: mostly MRP/protobuf, but has a Companion
  session under `src/companion/` and fast-srp-hap based SRP — cross-check
  only.

## Milestones

| M | Status | Notes |
|---|--------|-------|
| M0 scaffold | done | Gradle 8.14.3 wrapper, :protocol+:cli, version catalog, CI, MIT |
| M1 OPACK+TLV8 | done | all pyatv vectors ported (incl. 257×2 pointer vector), green first run |
| M2 frame codec/session | done | Transport abstraction, XID matching, segmented reads, cipher hook |
| M3 HAP crypto+pairing | done (code+tests) | srptools golden vector + fake-ATV integration; NEEDS REAL DEVICE |
| M4 commands | done (code+tests) | connect sequence, HID, apps, power, touch; NEEDS REAL DEVICE |
| M5 Android app v0.1 | done (code) | assembleDebug green; NEEDS REAL DEVICE for daily-drive |
| M6 RTI keyboard | done (code+tests) | bplist codec byte-identical w plistlib; NEEDS REAL DEVICE (CJK/emoji/secure) |
| M7 touchpad+apps | done (code+tests) | ordered touch channel, app grid; NEEDS REAL DEVICE |
| M8 release polish | done | README+FAQ, CONTRIBUTING, issue templates, release workflow, signed-or-debug assembleRelease green, v1.0.0 tag local |

## Key decisions

- SRP implemented from scratch on BigInteger + BouncyCastle digests,
  replicating srptools semantics exactly (incl. minimal-length int encoding).
  Fresh random 32-byte SRP private key (pyatv reuses Ed25519 seed — quirk,
  not wire-relevant).
- bplist codec is hand-rolled, supports only the types the RTI payloads and
  keyed-archiver reading need (dict/array/string/bytes/int/real/bool/UID/
  null/date-as-skip). Golden vectors generated with python3 plistlib.
- `:cli` uses jmDNS for discovery, hand-rolled arg parsing (no extra deps).
- Credentials string format identical to pyatv (4 hex fields, `:`-joined)
  for interop with `atvremote`.
- App: single-activity Compose, DataStore(+Keystore AES-GCM wrap) for creds.

## Deviations from CLAUDE.md

- Milestone-per-session rule intentionally overridden by user for this run.
- CLAUDE.md protocol claims corrected (see Recon above).
- `_systemInfo` `_sv` kept at pyatv's "170.18"; `_i`/`_pubID` generated
  per-install instead of real device identifiers.

## Checklist ① — requires the real Apple TV

1. `cli pair --host <ip>` → PIN appears on TV, pairing completes, creds file
   written (M3).
2. `cli verify --host <ip>` reconnect with stored creds (M3).
3. All buttons via `cli command …` visibly work; wake/sleep (M4).
4. App: discovery finds the ATV (multicast lock), pairing flow, remote
   screen daily-drive (M5).
5. Keyboard: focus a TV search field → app keyboard mode syncs; ASCII, CJK
   (Japanese/Korean), emoji; secure fields; graceful no-op unfocused (M6).
6. Touchpad swipes + app list/launch (M7).
7. Reconnect after ATV reboot (port change) and after phone Wi-Fi toggle.
8. atvproxy capture (optional) to turn real frames into regression vectors.

## Checklist ② — known gaps / TODO / suspicious

- SRP M1 leading-zero stripping: matches srptools/pyatv, but if the ATV pads
  A/B there is a ~0.8% per-pairing chance of proof mismatch → retry pairing
  (documented in code; same exposure as pyatv).
- `_systemInfo` payload uses semi-random values copied from pyatv; if a
  device rejects them, compare against a fresh atvproxy capture.
- SystemStatus subscription: newer tvOS may not answer FetchAttentionState;
  power state then comes only from pushed events (mirrors pyatv 2024+ fix).
- bplist decoder is minimal: no unicode-UTF16 strings written (reader
  handles them), no sets, no large collections >0xFFFFFFFF; RTI documentState
  from a real device may contain types the reader skips — verify on device.
- AEAD desync policy: any decrypt failure closes the connection (per
  CLAUDE.md gotcha 7); reconnect storm-guard is basic (exponential backoff,
  cap 30 s) — tune on device.
- CI release workflow (tag → signed APK) exercised only up to assembleRelease
  locally; the signing-secret path runs first on a real tag. The tag v1.0.0
  exists locally only — push it (with signing secrets configured) to publish.
- Android app has no instrumentation tests (unit tests only in :protocol).
- pyatv restarts the RTI session (`_tiStop`+`_tiStart`) on every text
  command; with the app's per-keystroke (debounced 250 ms) textSet this may
  feel laggy on device — if so, cache the sessionUUID from focus events
  instead of restarting (a deviation from pyatv, so only with device proof).
- App keyboard has no "return/submit" key mapping (pyatv has none either);
  tvOS search fields filter live, so likely fine.
- `resolveByName` re-scans mDNS on every (re)connect; if mDNS fails the app
  falls back to the last known host:port — after a reboot on a network
  without mDNS, manual IP entry is the only path.
- FetchLaunchableApplicationsEvent may require user-account permissions on
  multi-user tvOS; error path surfaces the device's `_em` string.
