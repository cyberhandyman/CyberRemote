# CLAUDE.md — Open-Source Android Remote for Apple TV (Companion Protocol)

## What this project is

A free, open-source, ad-free Android app that controls Apple TV (tvOS 13+,
focus on tvOS 15–26) over the local network, by natively implementing Apple's
**Companion Link protocol** — the same protocol family used by the iOS Control
Center remote widget. No cloud, no bridge server, no companion daemon on a
Mac/PC. The phone talks directly to the Apple TV.

Core v1 features (parity with the iOS remote widget for navigation + typing):
- D-pad / select / menu(back) / home, play-pause, volume, power (wake/sleep)
- Touchpad swipe navigation
- **Full keyboard text input** — when a text field is focused on the Apple TV,
  the phone's soft keyboard types directly into it (search boxes, passwords,
  URLs), exactly like the iOS remote's keyboard. This is a headline feature,
  not an afterthought.
- App list + launch

- Repo layout goal: publishable on GitHub, installable via GitHub Releases APK
  (and later F-Droid).
- License: MIT (protocol knowledge comes from community reverse-engineering
  projects, primarily pyatv, which is MIT).
- Privacy: the app makes **zero** network connections other than the local
  LAN connection to the Apple TV. No analytics, no ads, no telemetry.
- Naming/trademark: do NOT name the app "Apple TV Remote". Use a neutral name
  (working name: **Companion Remote**). "Apple TV" may only appear
  descriptively ("works with Apple TV") with a disclaimer that this is not an
  Apple product.

## Non-goals (v1)

- No "now playing" metadata / artwork (that requires the MRP-over-AirPlay-2
  tunnel — an order of magnitude more work; may become v2).
- No AirPlay streaming, no screen mirroring.
- No iOS version, no IR, no Bluetooth HID fallback.
- No support for Apple TV 3 and older (DMAP/DACP legacy protocol).

## Architecture

Two Gradle modules, strict separation:

```
companion-remote/
├── CLAUDE.md
├── protocol/          # Pure Kotlin/JVM library. ZERO Android dependencies.
│   └── src/main/kotlin/.../
│       ├── opack/     # OPACK encoder/decoder
│       ├── tlv8/      # TLV8 encoder/decoder (HAP pairing data)
│       ├── crypto/    # SRP-6a (HAP variant), HKDF, Ed25519, X25519,
│       │              # ChaCha20-Poly1305 session (BouncyCastle)
│       ├── hap/       # Pair-setup (M1–M6), pair-verify (M1–M4)
│       ├── companion/ # Frame codec, encrypted session, HID commands,
│       │              # touch gestures, RTI text input, app list/launch,
│       │              # power, events
│       └── client/    # High-level CompanionClient facade (coroutines)
├── cli/               # Small JVM command-line harness that uses :protocol
│                      # (scan/pair/command). THE primary integration-test
│                      # tool — protocol bugs are debugged here, not in the app.
├── app/               # Android app. Jetpack Compose + Material 3.
│                      # Discovery (NsdManager), pairing UI, remote UI,
│                      # credential storage. Depends only on :protocol.
└── references/        # git submodules / clones, read-only, NOT shipped:
    ├── pyatv/                    # canonical reference implementation
    └── node-appletv-remote/     # TypeScript port, good for porting logic
```

Rules:
- `:protocol` must compile and pass all tests with plain `java`/JUnit on any
  machine — this is what makes the protocol fully unit-testable without a
  device and reusable by other projects.
- All I/O in `:protocol` goes through a tiny `Transport` interface
  (send/receive bytes) so tests can drive it with recorded byte streams.
- `:app` contains no protocol logic. If you find yourself writing byte
  handling in `:app`, stop and move it to `:protocol`.

## Tech stack

- Kotlin 2.x, coroutines. Gradle Kotlin DSL, version catalog.
- Android: minSdk 26, targetSdk latest stable. Jetpack Compose, Material 3.
- Crypto: **BouncyCastle** (works identically on JVM tests and Android).
  Do not use Android-only providers in `:protocol`.
- mDNS: `NsdManager` on Android (acquire a `WifiManager.MulticastLock`
  first — discovery silently fails without it). In `:cli` use jmDNS.
- Persistence: Jetpack DataStore for credentials; wrap secrets with an
  Android Keystore–backed key. (Do not use the deprecated
  EncryptedSharedPreferences.)
- CI: GitHub Actions — JVM tests on every push, assembleRelease on tag,
  attach APK to GitHub Release.

## Protocol summary (verified against pyatv docs)

Canonical spec: https://pyatv.dev/documentation/protocols/ (section
"Companion Link"). Source of truth for behavior: `references/pyatv/pyatv/`
(`protocols/companion/`, `auth/`, `support/opack.py`). When this file and
pyatv disagree, **pyatv wins** — port, don't invent.

### Discovery
- Zeroconf service type: `_companion-link._tcp.local.`
- TXT records include `rpMd` (model, e.g. `AppleTV6,2`), `rpVr` (version),
  `rpFl` (flags). Most other values rotate for privacy; ignore them.
- The port can change after reboot; re-resolve on every connect, never cache.

### Frame format (TCP)
```
| Frame type (1 byte) | Length (3 bytes, big-endian) | Payload |
```
Frame types used: `0x03` PS_Start, `0x04` PS_Next, `0x05` PV_Start,
`0x06` PV_Next, `0x08` E_OPACK (all remote-control traffic after auth).

### OPACK
Apple's compact serialization (bools, ints, floats, strings, bytes, arrays,
dicts, UUIDs, pointers/back-references, endless collections terminated by
0x03). Full type table is in the pyatv protocol docs. Implement encode +
decode including **pointers (0xA0–0xC4)** and endless collections — the ATV
uses pointers in responses. Little-endian where applicable.

### Pairing — HAP pair-setup (PIN on TV screen)
- Frames: client sends PS_Start (M1), then PS_Next; payload is an OPACK dict
  `{"_pd": <TLV8 bytes>, "_pwTy": 1}`.
- TLV8 inside `_pd` per HAP: 0x00 method, 0x01 identifier, 0x02 salt,
  0x03 public key, 0x04 proof, 0x05 encrypted data, 0x06 state (M1..M6),
  0x0A signature, 0x1B (seen from ATV, opaque).
- SRP-6a, HAP flavor: 3072-bit group (RFC 5054), SHA-512,
  username `"Pair-Setup"`, password = 4-digit PIN shown on the TV.
  **Warning:** BouncyCastle's `SRP6Client` computes client proof M1 as
  H(A|B|S), which is NOT what HAP uses. pyatv delegates SRP to the
  `srptools` pip package (not vendored!); the verified formulas — including
  the facts that HKDF input is K = SHA512(S) and that integer terms in the
  proofs are minimal-length big-endian (leading zeros stripped) — are
  recorded in `docs/protocol-notes.md`. Port from there; verify with vectors.
- M5/M6 exchange long-term Ed25519 identities inside ChaCha20-Poly1305
  encrypted TLV8. M5's encrypted payload additionally carries TLV tag 0x11
  with OPACK data — pyatv sends just `{"name": <display name>}` (nothing
  more); the ATV shows `name` in Settings.
- Persist after success: our Ed25519 keypair, our pairing identifier, the
  ATV's Ed25519 public key + identifier. This tuple = "credentials".

### Session — HAP pair-verify (every connection)
- PV_Start/PV_Next, states M1–M4, OPACK dict `{"_pd": <TLV8>, "_auTy": 4}`.
- X25519 ephemeral key exchange + Ed25519 signatures over the long-term keys
  from pairing. Port from pyatv `auth/hap_pairing.py` / `hap_session.py`.

### Encryption (after verify)
- ChaCha20-Poly1305. HKDF-SHA512 with salt `""` and info
  `ClientEncrypt-main` (client→ATV) / `ServerEncrypt-main` (ATV→client).
- Nonce = per-direction message counter starting at 0, encoded as 12-byte
  little-endian, incremented per message.
- AAD = the 4-byte frame header of the encrypted frame (e.g. payload of 3
  bytes as E_OPACK ⇒ AAD `0x08 0x00 0x00 0x13`). Auth tag (16 bytes) is
  appended; frame length includes it.

### E_OPACK messaging
Every message is an OPACK dict:
- `_i` = message id (e.g. `_systemInfo`, `_hidC`), `_t` = 1 event /
  2 request / 3 response, `_x` = XID echoed in the response,
  `_c` = content dict.
- On connect (pyatv order, `companion/api.py connect()`): `_systemInfo`
  (non-null `_i` required for SystemStatus pushes) → `_touchStart`
  (`{_width: 1000.0, _height: 1000.0, _tFl: 0}`, required before `_hidT`;
  rebases touch timestamps) → `_sessionStart` with
  `_c = {"_srvT": "com.apple.tvremoteservices", "_sid": <random 32-bit>}`,
  combine returned `_sid` as `(remote_sid << 32) | local_sid` →
  `TVRCSessionStart` (`ProtocolVersionKey: "1.2"`; older devices may error —
  ignore) → `_tiStart` (text-input session) → subscribe `_iMC`.

### Commands
- Buttons: `_i="_hidC"`, `_c={"_hBtS": 1|2 (down|up), "_hidC": <code>}`.
  Codes: 1 Up, 2 Down, 3 Left, 4 Right, 5 Menu, 6 Select, 7 Home,
  8 VolUp, 9 VolDown, 10 Siri, 11 Screensaver, 12 Sleep, 13 Wake,
  14 PlayPause, 15/16 Channel +/-, 17 Guide, 18/19 Page Up/Down.
  A "press" = down event then up event.
- Touchpad: events `_i="_hidT"` (`_t=1`), `_c={"_ns": <nanos>, "_tFg": 1,
  "_cx": 0..1000, "_cy": 0..1000, "_tPh": phase}`; phase 1 press,
  3 hold/move (~every 20 ms), 4 release, 5 tap. Single tap = `_hidC`
  select down + up + one `_hidT` with `_tPh=5`.
- Apps: `FetchLaunchableApplicationsEvent` (returns bundleId→name map),
  `_launchApp` with `_c={"_bundleID": ...}` (requires an active session).
- Power/state: `FetchAttentionState`; subscribe via `_interest`
  `_regEvents: ["SystemStatus"]` (states: 1 asleep, 2 screensaver, 3 awake).
- Media-control availability event: `_iMC` bitmask (play/pause/volume bits).

### Text input — RTI over Companion (headline feature, dedicated milestone M6)

This is what makes the phone act as the Apple TV keyboard. It is implemented
in pyatv as the `Keyboard` interface over Companion; behavior to replicate:

- **Focus state**: the ATV pushes an event when a text field gains/loses focus
  (pyatv exposes this as `text_focus_state`: focused vs unfocused). The app's
  soft keyboard should auto-open when a field is focused on TV and dismiss when
  it loses focus. Subscribe for these events the same way as SystemStatus.
- **Get current text**: request that returns the text currently in the focused
  field (pyatv `text_get`). Use it to pre-fill / sync the phone's edit buffer.
- **Set / replace text**: send the full string to replace the field contents
  (pyatv `text_set`). Simplest reliable path: mirror the phone's edit box to
  the TV field by sending the whole current string on every change.
- **Append text**: input characters at the cursor (pyatv `text_append`).
- **Clear text** (pyatv `text_clear`).

The wire format is E_OPACK messages carrying a text-input ("RTI") session
whose `_tiD` payloads are **NSKeyedArchiver binary plists** (bplist00), NOT
plain UTF-8 — `:protocol` includes a minimal bplist codec for this. Session:
`_tiStart`/`_tiStop` requests; focus events `_tiStarted`/`_tiStopped` (focus
== presence of `_tiD`); typing via `_tiC` events (`{_tiV: 1, _tiD:
<archive>}`). Current text and sessionUUID are extracted from the `_tiStart`
response archive. Ported verbatim from
`references/pyatv/pyatv/protocols/companion/api.py` (`text_input_command`),
`plist_payloads/rti_text_operations.py` and `keyed_archiver.py`; details in
`docs/protocol-notes.md`. Golden bplist vectors generated with Python
plistlib. Practical notes to verify against a device:
handle non-ASCII (CJK/emoji — your own use case includes Japanese/Korean),
password fields (secure-entry may block get/append; set/replace still works),
and the case where no field is focused (commands should no-op gracefully, and
the app should tell the user "focus a text field on the TV first").

UX target: a dedicated "keyboard" mode in the app with a real Android
`TextField`; keep it in sync with the TV field bidirectionally, and show the
focus state so the user knows when typing will land.

## Milestones — work strictly in order, one per session

**M0 — Scaffold.** Gradle multi-module skeleton, version catalog, JUnit,
GitHub Actions (JVM tests), MIT LICENSE, README stub, `references/`
submodules. ✅ `./gradlew :protocol:test` green in CI.

**M1 — OPACK + TLV8 codecs.** TDD. Test vectors: every example in the pyatv
protocol docs (e.g. `E3416102416244746573744163A2` ⇒
`{"a": false, "b": "test", "c": "test"}`) plus round-trip property tests
ported from `references/pyatv/tests/support/test_opack.py`.
✅ All vectors pass, encode∘decode = identity.

**M2 — Frame codec + plaintext session plumbing.** Frame reader/writer over
a `Transport` abstraction, XID correlation, request/response matching,
coroutine-based `CompanionConnection`. ✅ Unit tests with fake transport,
including partial-read/segmented frames.

**M3 — HAP crypto + pair-setup + pair-verify.** SRP (HAP flavor), HKDF,
Ed25519/X25519, ChaCha20-Poly1305 session with correct nonce/AAD.
`cli`: `pair --host <ip>` (prompts for PIN) and `verify` commands.
✅ Real-device: pairing succeeds against an actual Apple TV, credentials
persisted to JSON, reconnect+verify works. This is the hardest milestone —
expect several debug iterations; add a `--dump-frames` hex logger to `cli`.

**M4 — Commands over encrypted session.** `_systemInfo`, `_sessionStart`,
`TVRCSessionStart`, `_hidC` buttons, `FetchAttentionState`.
`cli`: `command up|down|left|right|select|menu|home|play_pause|volume_up|...`.
✅ Real-device: all buttons visibly work; wake/sleep works.

**M5 — Android app v0.1.** Discovery list (NsdManager + MulticastLock),
pairing flow with PIN entry, credential store, remote screen: D-pad,
select, menu/back, home, play/pause, volume, power. Auto-reconnect with
pair-verify on foreground; graceful errors when the ATV is unreachable.
✅ Usable daily driver APK.

**M6 — Text input / keyboard (headline feature).** Port RTI text-input from
pyatv `Keyboard` (see "Text input" section above): focus-state subscription,
get/set/append/clear. `cli`: `text-set "hello"`, `text-append`, `text-clear`,
and a `focus-state` watch command. Then wire the Android UI: a keyboard mode
with a live `TextField` synced bidirectionally to the TV field, auto-open on
focus, focus indicator. Real-device tests: type into an App Store / YouTube
search box, verify CJK + emoji (Japanese/Korean input), verify graceful no-op
when nothing is focused. ✅ Phone reliably types into Apple TV text fields.

**M7 — Touchpad + app launcher.** Swipe gesture surface (`_hidT` streams),
app grid via `FetchLaunchableApplicationsEvent` + `_launchApp`.
✅ Feature parity with commercial apps' paid tiers.

**M8 — Release polish.** App icon, dark theme, haptics, README with
screenshots + GIF, FAQ (see Gotchas), signed release workflow, versioning,
CONTRIBUTING.md, issue templates. ✅ v1.0.0 tag published.

## Testing strategy

- `:protocol` is developed test-first; no protocol code merges without
  unit tests. Crypto and codecs must have golden test vectors.
- The `cli` harness is the integration-test tool against real hardware.
  Never debug protocol issues through the Android app.
- Optional deep-debug: pyatv ships `atvproxy`, a MITM that decrypts
  Companion traffic between the real iOS remote widget and the ATV — use it
  on any laptop to capture ground-truth byte streams when stuck, and turn
  captures into regression test vectors.
- Real-device checklist per release: pair from scratch, reconnect after ATV
  reboot (port changes!), reconnect after phone Wi-Fi toggle, sleep/wake,
  all buttons, touchpad swipe, app launch, and text input — including
  focusing a TV search field (keyboard auto-opens), typing ASCII, typing
  CJK/emoji, clearing, and typing while nothing is focused (graceful no-op).

## Gotchas / known risks (also seed the README FAQ from these)

1. **This is a reverse-engineered private protocol.** tvOS updates can break
   it at any time. Track pyatv releases/issues; they fix breakage first.
2. **mDNS is same-broadcast-domain only.** Phone and ATV must be on the same
   subnet/VLAN (or the router must run an mDNS reflector). Also support
   manual IP entry as a fallback for exotic networks.
3. **Multicast lock** is mandatory on Android or discovery returns nothing.
4. Apple TV setting `Settings → AirPlay and HomeKit → Allow Access` must
   permit devices on the same network; pairing PIN appears under
   remote-pairing when the client initiates pair-setup.
5. The Companion **port changes** (ephemeral, ~49152+); always re-resolve.
6. BouncyCastle SRP proof formula differs from HAP — see Pairing section.
7. Sequence-number nonces mean **one lost/duplicated frame desyncs the
   session** — treat any AEAD failure as fatal, reconnect and re-verify.
8. Some commands silently no-op without `_sessionStart` (notably
   `_launchApp`); `TVRCSessionStart` errors on older tvOS — ignore.
9. Don't log credentials or derived keys; hex dumps gated behind a debug
   flag and truncated.
10. Trademark: neutral app name, disclaimer in README and Play-style
    description ("not affiliated with Apple Inc.").

## Working conventions for Claude Code

- Read this file fully at session start. Work on exactly ONE milestone per
  session; do not start the next without being asked.
- Before writing protocol code, read the corresponding pyatv source in
  `references/` and cite the file/function you ported from in the commit
  message.
- Conventional commits (`feat:`, `fix:`, `test:`, `docs:`); one commit per
  logical step; run `./gradlew test` before every commit.
- When a real-device step is reached, print exact instructions for the human
  (what to run, what to look for on the TV) and pause.
- Keep public API of `:protocol` documented with KDoc — it will be consumed
  by other projects.
- If observed device behavior contradicts this file or pyatv docs, write the
  finding into `docs/protocol-notes.md` with the capture that proves it.

## Commands

```bash
./gradlew :protocol:test          # unit tests (run constantly)
./gradlew :cli:run --args="scan"  # discover ATVs (from a real network)
./gradlew :cli:run --args="pair --host 192.168.x.x"
./gradlew :cli:run --args="command select --host 192.168.x.x"
./gradlew :cli:run --args="text-set 'hello world' --host 192.168.x.x"
./gradlew :cli:run --args="focus-state --host 192.168.x.x"   # watch keyboard focus
./gradlew :app:assembleDebug      # APK at app/build/outputs/apk/debug/
```

## References (clone into references/, read before coding)

- pyatv (canonical): https://github.com/postlund/pyatv — esp.
  `pyatv/protocols/companion/` (incl. keyboard/RTI text input),
  `pyatv/auth/`, `pyatv/support/opack.py`, `docs/documentation/protocols.md`,
  `tests/`. Keyboard interface docs: https://pyatv.dev/development/keyboard/
- Protocol docs (rendered): https://pyatv.dev/documentation/protocols/
- TypeScript port (porting aid): https://github.com/energee/node-appletv-remote
  (`src/companion/`, `src/auth/`)
- HAP non-commercial spec (SRP/TLV8 details): Apple developer site
- OPACK tooling: https://github.com/fabianfreyer/opack-tools
