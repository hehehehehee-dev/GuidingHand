# GuidingHand 1.0 — build and verification

Release version: **1.0**

Application ID: `vn.guidinghand`

Internal Android upgrade code: `14` (retained for updating the existing installation without uninstalling).

## Development milestones

- 2026-10-25: created the Kotlin/Compose Android project, local semantic planning seam, accessibility adapter, verification engine, overlays, family preferences and initial tests. Repaired workstation Java certificate trust using an ignored local trust store; certificate verification remains enabled.
- 2026-10-25: developed the English/Vietnamese interface, pointing-hand identity, compact conversation, draggable bubble, attached help cards and family-care design. Tree scans and TTS binder work run off the main thread. Added installed-app selection, Instagram messaging semantics, current-screen control grounding and launcher search recovery.
- 2026-10-26: added lock-screen suppression, separate speech-recognition language selection, app-name pronunciation matching and explicit app confirmation. The user corrected an earlier Vietnamese-recognition report: the phone did not have offline Vietnamese support. Do not count that earlier answer as a successful acoustic test.
- 2026-10-26: added provider-language checks, explicitly requested model download and per-utterance online speech consent. The user confirmed online speech hears a request. GuidingHand itself has no INTERNET permission; the disclosed system recognizer handles any network recognition.
- 2026-10-26: repaired idle launcher observation, grounded Search/icon targets from actual screen semantics and added explicitly requested launcher-only swipes. Downward navigation opened a system panel on the Xiaomi, so it was removed. The final direction cycle is left, right, up.
- 2026-10-26: softened the swipe cue, added the moving demonstration circle and completed single-language interface/instruction handling. Common external control labels are translated locally; unknown controls use localized numbered choices and highlighted-control guidance. App/provider/contact names and user input retain their spelling.

## Build and installation evidence

- The unchanged application logic passed `assembleDebug testDebugUnitTest lintDebug`: **86 JVM tests, zero failures; lint zero errors**.
- After setting the release display version to **1.0**, `assembleDebug` passed. APK manifest inspection and output metadata both reported `versionName=1.0` and internal `versionCode=14`.
- The deliverable at `build/outputs/GuidingHand-debug.apk` matched the generated APK by SHA256 and installed with `adb install -r`; the app was not uninstalled.
- Xiaomi package readback reported `versionName=1.0`. Accessibility was Bound with capability161, Binding/Crashed services empty, and no app-process AndroidRuntime error was observed.
- The final repository publication only cleans release documentation and commit history. Application code, dependencies, features and upgrade metadata remain unchanged. No new functional-test run is claimed for documentation-only publication work.

## Physical results reported by the user

- The hand bubble opens a compact overlay.
- Dragging the bubble moves its attached card, and Help menu returns to the root help prompt.
- The mini-chat is compact and draggable.
- Guide hides on the locked phone and returns after unlock.
- Online speech hears the request; this does not establish measured acoustic accuracy.
- Launcher swiping works and guidance continues.
- The final subtle cue is easy to see and swipes correctly on the Xiaomi.
- Both English and Vietnamese interface modes use the correct language after switching and checking the guide and speech dialog.

These reports do not establish every lifecycle, speech-provider/error, audible-TTS or other-device variant. Complete Instagram messaging, additional search/folder layouts and the remaining safety/task acceptance checks are tracked in `REAL-PHONE-TESTS.md`. No private app, password, payment or lock-screen screenshots were exported.
