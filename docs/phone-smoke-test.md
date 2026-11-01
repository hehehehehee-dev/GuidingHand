# Read-only real-phone smoke test

This check collects bounded evidence from the connected Android phone without injecting taps,
typing, swipes, calls, messages, or target-app actions. It is suitable for unattended diagnostics,
but it is not a substitute for a person completing the elder flow.

## Run

From the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File tools/phone-smoke-test.ps1
```

To update only the side-by-side Candidate first:

```powershell
./gradlew.bat assembleDebug -PcandidateBuild=true
powershell -ExecutionPolicy Bypass -File tools/phone-smoke-test.ps1 -InstallCandidate
```

The installer verifies `output-metadata.json` and refuses an APK built as the original
`vn.guidinghand` package.

To capture a screenshot only when the foreground package is Xiaomi Home or GuidingHand Candidate:

```powershell
powershell -ExecutionPolicy Bypass -File tools/phone-smoke-test.ps1 -CaptureSafeScreen
```

Reports are written under `build/outputs/phone-smoke/`, which is ignored by Git. Reports contain
package/state metadata and event counts, not screen text, transcripts, goals, recipients, images,
audio, provider output, credentials, or the device serial. A screenshot is opt-in and refused outside the safe
foreground allowlist.

## What it proves

- the connected device is authorized, awake, and unlocked;
- the installed package is the Candidate identity;
- the Candidate Accessibility service is enabled and not listed as crashed;
- no new fatal exception appears during the bounded observation;
- the overlay does not repeatedly detach/attach while the phone is otherwise idle.

## What remains user-driven

Xiaomi currently rejects `adb shell input` with `INJECT_EVENTS`. The test therefore makes no claim
about a user tapping Help, recording speech, correcting a transcript, following a ring, recovering
from a wrong tap, or completing a target-app task. Those steps must be performed by the user while
the read-only collector observes logs and state.
