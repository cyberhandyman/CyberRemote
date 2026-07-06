# Protocol notes — verified against pyatv source

Reference: `references/pyatv` at **v0.18.0 + 304 commits (b277a4c8)**.
Everything below was read directly from source, with exact file/function
references. When CLAUDE.md and this file disagree, this file wins (it is the
porting record).

## OPACK — `pyatv/support/opack.py`

`pack()`/`unpack()` verified in full; test vectors live in
`tests/support/test_opack.py` (ported to `:protocol` golden tests).

Encoder specifics that matter:
- Ints: `< 0x28` → single byte `value + 8`; otherwise `0x30..0x33` +
  1/2/4/8 bytes **little-endian**, smallest size that fits. Decoder returns a
  "sized int" preserving the encoded width (`0x30 + log2(size)`), and the
  encoder re-encodes such values at that width even when smaller would fit
  (`_sized_int`, needed for encode∘decode = identity).
- Floats: `0x35` float32 (decode only in practice), `0x36` float64,
  little-endian. pyatv always packs floats as float64.
- Strings: `0x40+len` for len ≤ 0x20, else `0x61..0x64` + 1/2/3/4-byte LE
  length. NOTE: `0x63` uses a **3-byte** length (not power-of-two).
- Bytes: `0x70+len` for len ≤ 0x20, else `0x91..0x94` + 1/2/4/8-byte LE
  length (`1 << (low_nibble - 1)`), asymmetric with strings.
- List `0xD0+n`, dict `0xE0+n`; n ≥ 15 ⇒ `0xDF`/`0xEF` endless form,
  terminated by `0x03`. **The 15th element is inside the endless form** (i.e.
  count nibble 0xF means "endless", not "15").
- Pointers: every packed object with encoded length > 1 byte is appended to an
  object list (booleans/none/small ints/lists/dicts excluded on decode side —
  see below); re-occurrence packs as `0xA0+idx` (idx ≤ 0x20) or `0xC1..0xC4` +
  1/2/4/8-byte LE index.
- Decode-side object list: values are appended **unless** bool/None/small-int
  (0x08–0x2F)/list/dict, and only if not already present (`value not in
  object_list`). This "not in" check uses Python equality — port must match
  (structural equality, and note `True == 1` style pitfalls do not arise
  because bools are never added).
- UUID `0x05` + 16 bytes big-endian (RFC 4122 byte order).
- Absolute time `0x06` + 8 bytes: decoded as plain int in pyatv; not packed.
- `None` → `0x04`, `True` → `0x01`, `False` → `0x02`.

## TLV8 — `pyatv/auth/hap_tlv8.py`

- `read_tlv` merges repeated tags (fragmented values > 255 bytes) by
  concatenation; `write_tlv` splits values > 255 bytes into repeated tags.
- Tags: Method=0x00 Identifier=0x01 Salt=0x02 PublicKey=0x03 Proof=0x04
  EncryptedData=0x05 SeqNo=0x06 Error=0x07 BackOff=0x08 Signature=0x0A
  Name=0x11 Flags=0x13.

## Frames — `pyatv/protocols/companion/connection.py`

- Header: 1 byte type + 3 bytes payload length **big-endian**; encrypted
  frames' length includes the 16-byte Poly1305 tag.
- FrameType enum (full): NoOp=1, PS_Start=3, PS_Next=4, PV_Start=5, PV_Next=6,
  U_OPACK=7, E_OPACK=8, P_OPACK=9, PA_Req=10, PA_Rsp=11,
  SessionStartRequest=16 … FamilyIdentityUpdate=34. We use 3,4,5,6,8.
- Encryption is applied only when `len(payload) > 0`; a zero-length payload is
  sent/received in plaintext even on an encrypted session
  (`CompanionConnection.send` / `data_received`).
- AAD for AEAD = the exact 4-byte header of that frame.
- Nonce = per-direction message counter, 12-byte little-endian, starts at 0,
  +1 per encrypted message (`support/chacha20.py`, `Chacha20Cipher` with
  `nonce_length=12`).
- Keys: HKDF-SHA512(salt="", info="ClientEncrypt-main") for client→ATV,
  info="ServerEncrypt-main" for ATV→client, IKM = pair-verify X25519 shared
  secret (`protocol.py` SRP_SALT/SRP_OUTPUT_INFO/SRP_INPUT_INFO +
  `hap_srp.py verify2`).

## E_OPACK messaging — `pyatv/protocols/companion/protocol.py`

- Message: `{_i, _t, _x, _c}`; `_t`: 1=Event, 2=Request, 3=Response.
- `_x` (XID) added to every outgoing OPACK frame (even events); responses
  matched by `_x` when `_t == 3`. pyatv seeds XID with `randint(0, 2**16)` and
  increments.
- Errors: response dict containing `_em` (error message) ⇒ command failed.
  (`_ec`/`_ed` may also appear; pyatv only checks `_em`.)
- Auth frames (PS_*/PV_*) carry no XID; responses to PS_Start and PS_Next both
  arrive as PS_Next (same for PV), and are matched by frame type.

## Pair-setup — `companion/pairing.py` + `auth/hap_srp.py` + srptools 1.0.1

pyatv delegates SRP math to the `srptools` pip package (NOT vendored in
pyatv). Formulas verified from srptools 1.0.1 `context.py`/`client.py`/
`common.py` (downloaded and read during recon):

- Group: RFC 5054 3072-bit, g=5, H=SHA-512. Username `"Pair-Setup"`,
  password = 4-digit PIN as string.
- k = H(N | PAD(g)) (g left-padded to 384 bytes; N minimal — top bit set so
  384 bytes anyway).
- x = H(salt_bytes | H("Pair-Setup" + ":" + pin)); inner hash digest bytes.
- a = 256-bit (pyatv passes the 32-byte Ed25519 seed hex as SRP private —
  a quirk, not a requirement; any 32-byte random works). A = g^a mod N.
- u = H(PAD(A) | PAD(B)) (both padded to 384 bytes).
- S = (B − k·g^x)^(a + u·x) mod N.
- **K = H(S)** — 64 bytes. This K (not raw S) is the input to all pair-setup
  HKDFs (pyatv `step3` uses `unhexlify(self._session.key)` where
  `session.key` is K).
- **M1 = H( bytes(H(N) XOR H(g)) | bytes(H(I)) | salt | bytes(A) | bytes(B) | K )**
  where `bytes(int)` is minimal-length big-endian (srptools `hash()` int
  conversion **strips leading zeros**, including for A, B and the XOR term).
  This differs from a strict HAP/RFC reading (padded A/B) but is what pyatv
  ships and is accepted by real devices.
- M2 (device proof) = H( bytes(A) | M1 | K ).
- BouncyCastle's SRP6Client is unusable here (different proof formula) —
  implemented from scratch in `:protocol` crypto.

Message flow (`CompanionPairSetupProcedure`):
- M1: PS_Start frame, OPACK `{"_pd": tlv{Method:0x00, SeqNo:0x01}, "_pwTy": 1}`.
- M2 response (arrives as PS_Next): tlv{Salt, PublicKey(B), SeqNo}.
- M3: PS_Next `{"_pd": tlv{SeqNo:0x03, PublicKey(A), Proof(M1)}, "_pwTy": 1}`.
- M4 response: tlv{Proof(M2)} — verify.
- M5: derive `ios_device_x = HKDF(K, salt="Pair-Setup-Controller-Sign-Salt",
  info="Pair-Setup-Controller-Sign-Info")`, `session_key = HKDF(K,
  "Pair-Setup-Encrypt-Salt", "Pair-Setup-Encrypt-Info")`.
  Inner tlv{Identifier: pairing_id, PublicKey: ed25519_ltpk,
  Signature: Ed25519.sign(ios_device_x | pairing_id | ltpk),
  Name(0x11): OPACK{"name": <display name>}}. pyatv sends **only** `name`
  in the 0x11 OPACK dict (CLAUDE.md previously claimed model/accountID/
  altIRK/MACs — not true for pyatv; plain `{"name": …}` works).
  Encrypt: ChaCha20-Poly1305, key=session_key, nonce="PS-Msg05" left-padded
  with 4 zero bytes to 12, no AAD. Send as PS_Next
  `{"_pd": tlv{SeqNo:0x05, EncryptedData}, "_pwTy": 1}`.
- M6 response: tlv{EncryptedData} decrypted with nonce "PS-Msg06" ⇒
  tlv{Identifier: atv_id, PublicKey: atv_ltpk, Signature} (signature NOT
  verified by pyatv). pairing_id = str(uuid4()) as ASCII bytes.
- Credentials = (atv_ltpk, our ed25519 seed, atv_id, pairing_id); serialized
  by pyatv as 4 hex fields joined with ":" (`auth/hap_pairing.py`).

## Pair-verify — `companion/auth.py` + `hap_srp.py initialize/verify1/verify2`

- M1: PV_Start `{"_pd": tlv{SeqNo:0x01, PublicKey: X25519 ephemeral pub},
  "_auTy": 4}`.
- M2 (as PV_Next): tlv{PublicKey: server ephemeral, EncryptedData}.
  shared = X25519(our_priv, server_pub);
  session_key = HKDF-SHA512(shared, salt="Pair-Verify-Encrypt-Salt",
  info="Pair-Verify-Encrypt-Info", 32);
  decrypt EncryptedData with key=session_key, nonce="PV-Msg02" (8-byte text,
  4 zero bytes prepended), no AAD ⇒ tlv{Identifier, Signature};
  require Identifier == atv_id; verify Ed25519(atv_ltpk) over
  (server_pub | atv_id | our_pub).
- M3: sig = Ed25519(ltsk).sign(our_pub | client_id | server_pub);
  PV_Next `{"_pd": tlv{SeqNo:0x03, EncryptedData: enc(tlv{Identifier:
  client_id, Signature: sig}, nonce="PV-Msg03")}}` (no `_auTy`).
- M4 response ignored beyond error check.
- Session keys from **raw shared secret** (not hashed): see Frames above.

## Connect sequence — `companion/api.py CompanionAPI.connect()`

Order after encryption is up:
1. `_systemInfo` (Request). Payload (`system_info()`): `{_bf: 0, _cf: 512,
   _clFl: 128, _i: <stable id string>, _idsID: <client_id bytes>,
   _pubID: <MAC-like string>, _sf: 256, _sv: "170.18", model, name}`.
   A null `_i` stops TVSystemStatus push events.
2. `_touchStart` (Request) `{_width: 1000.0, _height: 1000.0, _tFl: 0}` —
   **required before `_hidT` touch events**; also (re)bases the touch
   timestamp.
3. `_sessionStart` (Request) `{_srvT: "com.apple.tvremoteservices",
   _sid: <random 32-bit>}`; combined sid = (remote_sid << 32) | local_sid.
4. `TVRCSessionStart` (Request) `{ProtocolVersionKey: "1.2"}` — errors on
   older tvOS, ignore failure. Needed for FetchAttentionState on newer tvOS.
5. `_tiStart` (Request) `{}` — starts text-input session; response `_c` may
   contain `_tiD` if a field is already focused.
6. Subscribe `_iMC`: event `_interest` `{_regEvents: ["_iMC"]}`.

Disconnect: unsubscribe events, `_sessionStop {_srvT, _sid}`, `_touchStop
{_i: 1}`, `_tiStop {}`.

## Commands — `companion/api.py`

- HID buttons: Request `_hidC` `{_hBtS: 1(down)|2(up), _hidC: code}`.
  Codes (HidCommand enum): 1 Up, 2 Down, 3 Left, 4 Right, 5 Menu, 6 Select,
  7 Home, 8 VolumeUp, 9 VolumeDown, 10 Siri, 11 Screensaver, 12 Sleep,
  13 Wake, 14 PlayPause, 15 ChannelIncrement, 16 ChannelDecrement, 17 Guide,
  18 PageUp, 19 PageDown.
- Touch: Event `_hidT` `{_ns: <nanos since _touchStart>, _tFg: 1, _cx, _cy,
  _tPh: phase}`; phases (TouchAction in `pyatv/const.py`): Press=1, Hold=3,
  Release=4, Click=5. Swipe = Press, then Hold every ~16 ms
  (TOUCHPAD_DELAY_MS) interpolating toward target, then Release.
  Click(select-via-touch) = `_hidC` select down, 20 ms, up, then one `_hidT`
  with `_tPh=5` at (1000, 1000).
- Media control: Request `_mcc` `{_mcc: cmd, ...args}`; cmds: Play=1 Pause=2
  NextTrack=3 PreviousTrack=4 GetVolume=5 SetVolume=6(`_vol` 0..1)
  SkipBy=7(`_skpS` seconds, float; negative = backward).
- Apps: Request `FetchLaunchableApplicationsEvent` `{}` ⇒ `_c` is
  {bundleId: name}; Request `_launchApp` `{_bundleID: id}` (or `_urlS` for
  URLs) — requires `_sessionStart` first.
- Accounts: `FetchUserAccountsEvent` / `SwitchUserAccountEvent`
  `{SwitchAccountID: id}`.
- Power: Request `FetchAttentionState` ⇒ `_c.state`; SystemStatus values:
  1 Asleep, 2 Screensaver, 3 Awake, 4 Idle(unverified). Newer tvOS may reply
  "No request handler" — pyatv treats fetch as optional and relies on
  subscribed `SystemStatus`/`TVSystemStatus` events instead.
  Wake/Sleep = HID 13/12 (down+up NOT needed: pyatv sends only
  `hid_command(False, Wake)` i.e. a single up event, see
  `CompanionPower.turn_on`).
- Media-control flags event `_iMC`: `_c._mcF` bitmask — Play 0x1 Pause 0x2
  NextTrack 0x4 PreviousTrack 0x8 FastForward 0x10 Rewind 0x20 Volume 0x100
  SkipForward 0x200 SkipBackward 0x400.

## Text input (RTI) — `companion/api.py` + `plist_payloads/rti_text_operations.py` + `keyed_archiver.py`

**The `_tiD` payload is an NSKeyedArchiver binary plist (bplist00), not plain
UTF-8.** (CLAUDE.md originally guessed "UTF-8 payload" — wrong.)

- Session: Request `_tiStart` `{}` ⇒ response `_c._tiD` present iff a text
  field is focused. `_tiD` is a keyed archive; extract:
  - `sessionUUID` (16 bytes) via `$top.textOperations`? No — paths from
    `$top` directly: `["sessionUUID"]` and
    `["documentState", "docSt", "contextBeforeInput"]` (current text),
    following plist UID references at each step
    (`keyed_archiver.read_archive_properties`).
- Focus events: `_tiStarted` (field gained focus, has `_tiD`), `_tiStopped`
  (lost focus, no `_tiD`). pyatv derives focus state from presence of `_tiD`
  key. `_tiStart` response is treated the same way.
- Typing: Event `_tiC` `{_tiV: 1, _tiD: <archive>}` where archive is one of
  two fixed NSKeyedArchiver payloads ($archiver = "RTIKeyedArchiver",
  $version = 100000):
  - insert text: RTITextOperations{targetSessionUUID: NSUUID(session bytes),
    keyboardOutput: TIKeyboardOutput{insertionText: <text>}}
  - clear: RTITextOperations{targetSessionUUID, keyboardOutput:
    TIKeyboardOutput{}, textToAssert: ""}
  Exact object tables ported 1:1 from `rti_text_operations.py` (object order
  and UID numbering preserved; golden vectors generated with Python
  plistlib).
- pyatv's text_get/set/append/clear (`text_input_command`) **restarts** the
  ti session first (`_tiStop` + `_tiStart`) to get fresh sessionUUID +
  current text, then sends `_tiC` events (clear first if replacing).
- text_get returns None when nothing is focused (no `_tiD`) — no-op path.

## Real-device findings (verified against an Apple TV 4K, AppleTV14,1, 2026-07-06)

Captured with the `cli` against a live device. These are behaviors observed
on hardware that differ from — or refine — the pyatv-derived notes above.

### Pairing / commands (M3, M4) — all confirmed working
- Full pair-setup (PIN on screen) + pair-verify + encrypted session verified
  end to end; SRP proof / HKDF salts+infos / ChaCha nonce+AAD are all correct
  as ported (no adjustments needed). Credentials round-trip in the
  4-hex-field format.
- `_hidC` buttons (home, arrows), `_launchApp`, `FetchAttentionState`
  (returned `state=3` Awake), `FetchLaunchableApplicationsEvent` (full app
  map incl. CJK names) all work over the encrypted session.
- Practical gotcha: the device closes an **idle** pairing TCP connection
  after ~30–60 s. Between PS_Start (which makes the PIN appear) and PS_Next
  the user must enter the PIN reasonably promptly, or the socket is gone and
  M3 fails with "connection closed" before it is even sent. Not a protocol
  bug — just enter the PIN quickly (the cli reads it on stdin).

### RTI text-input focus — the `_tiD` session UUID comes from the EVENT, not the response

This is the significant real-device deviation from pyatv's flow.

- pyatv's `text_input_command` does `_tiStop` then `_tiStart` and reads the
  session UUID from the **`_tiStart` response** (`_c._tiD`). On this tvOS the
  `_tiStart` response `_c` is **empty** even while a field is focused — the
  session UUID is never in the response.
- Instead, `_tiD` (a keyed-archive carrying `sessionUUID` and the current
  document state) arrives only in the **`_tiStarted` event**, which the
  device pushes when a text field transitions **unfocused → focused**. A
  `_tiStopped` event (empty `_c`) is pushed on focus loss. Verified by
  watching the event stream: focusing a search box emits
  `_tiStarted content-keys=[_tiV, _tiD]`; leaving it emits `_tiStopped`.
- Consequences for the port (implemented in `CompanionClient`):
  - Focus state and the cached `_tiD` are driven **only** by
    `_tiStarted`/`_tiStopped` events, never inferred from an empty
    `_tiStart` response (an empty `_c` must NOT be read as "unfocused" —
    hence `KeyboardFocusState.Unknown` until the first event).
  - `textInputCommand` uses the `_tiD` cached from the latest `_tiStarted`
    event; it does **not** `_tiStop`+`_tiStart` on every call (that would
    tear down the session and never yields the UUID here). It falls back to
    one `_tiStart` (for pyatv-style devices that do answer with `_tiD`) and
    waits briefly for either source.
  - Because the UUID only appears on the unfocused→focused transition, a
    **one-shot** command against an already-focused field cannot get it
    (the cli `text-set` then no-ops). A **long-lived** connection that is
    already listening when the field gains focus does — which is exactly the
    Android app's model, and the cli `text-live` command (added for this
    verification: connect, wait for the focus event, then type).
- Verified typing on hardware: `hello 你好 🙂 한국어` (ASCII + Chinese +
  emoji + Korean) landed correctly in the App Store search box via the
  cached-`_tiD` path. Graceful no-op (prints "focus one first") confirmed
  when nothing is focused.

## Credentials string format — `auth/hap_pairing.py`

`ltpk_hex:ltsk_hex:atv_id_hex:client_id_hex` (4 fields). We keep the same
format for interop with pyatv (`atvremote --companion-credentials`).

## Deviations from CLAUDE.md found during recon (CLAUDE.md updated)

1. RTI payloads are NSKeyedArchiver bplists; `:protocol` therefore includes a
   minimal binary-plist codec (write + UID-following reader).
2. M5 TLV 0x11 OPACK dict contains only `{"name": …}` in pyatv.
3. HKDF input for pair-setup keys is K = SHA512(S), not raw S; srptools
   int-encoding strips leading zeros in proof concatenations.
4. Connect sequence includes `_touchStart` and `_tiStart` (not mentioned
   before); `_hidT` timestamps are relative to `_touchStart`.
5. Touch phase enum: Press=1 Hold=3 Release=4 Click=5 (tap-select uses
   Click=5 at 1000,1000 after a `_hidC` select press with 20 ms gap).
6. pyatv sends wake/sleep as a single "up" (`_hBtS: 2`) event only.
7. node-appletv-remote is primarily an MRP implementation (protobuf), but
   `src/companion/` contains a Companion session (opack/framing/pair-setup)
   and `src/auth/srp.ts` uses fast-srp-hap's `hap` params — useful as a
   cross-check, not a full second Companion implementation.
