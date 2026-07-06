# Contributing

Thanks for helping! A few ground rules keep this project healthy:

## Ground rules

- `protocol/` is a **pure Kotlin/JVM** module: zero Android dependencies,
  everything unit-testable with plain JUnit. If you're writing byte handling
  in `app/`, stop and move it to `protocol/`.
- Protocol behavior is **ported from pyatv, not invented**. Before touching
  protocol code, read the matching pyatv source (`references/` submodules)
  and cite the file/function in your commit message. `docs/protocol-notes.md`
  records what has been verified so far — keep it up to date.
- No protocol code merges without unit tests; codecs and crypto need golden
  test vectors.
- Debug protocol issues with the `cli/` harness against real hardware, never
  through the Android app. `--dump-frames` hex-logs all traffic (truncated —
  never log credentials or keys).
- Conventional commits (`feat:`, `fix:`, `test:`, `docs:`); run
  `./gradlew :protocol:test` before every commit.

## Setting up

```bash
git clone --recurse-submodules <this repo>
./gradlew :protocol:test
```

An Android SDK is only needed for `:app`; `:protocol` and `:cli` build with
any JDK 17+.

## Reporting protocol breakage

tvOS updates occasionally break the protocol. When that happens:
1. Check pyatv's issue tracker first — they usually see it first.
2. Capture traffic with pyatv's `atvproxy` if you can and attach the
   (redacted) capture to your issue.
