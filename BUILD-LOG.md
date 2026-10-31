> Historical Phase 1 record. These results do not verify the redesigned Spark → SAM → Voice core. See README.md for current wiring and validation status.

# GuidingHand 1.0 — build and verification

Release version: **1.0**

Application ID: `vn.guidinghand`

Internal Android upgrade code: `14` (retained for updating the existing installation without uninstalling).

## Development milestones

- 2026-10-25: created the Kotlin/Compose Android project, local semantic planning seam, accessibility adapter, verification engine, overlays, family preferences and initial tests. Repaired workstation Java certificate trust using an ignored local trust store; certificate verification remains enabled.
- 2026-10-25: developed the English/Vietnamese interface, pointing-hand identity, compact conversation, draggable bubble, attached help cards and family-care design. Tree scans and TTS binder work run off the main thread. Added installed-app selection, Instagram messaging semantics, current-screen control grounding and launcher search recovery.
- 2026-10-26: added lock-screen suppression, separate speech-recognition language selection, app-name pronunciation matching and explicit app confirmation. The user corrected an earlier Vietnamese-recognition report: the phone did not have offline Vietnamese support. Do not count that earlier answer as a successful acoustic test.
- 2026-10-26: added provider-language checks, explicitly requested model download and per-utterance online speech consent. The user confirmed online speech hears a request. At that milestone GuidingHand had no INTERNET permission; the disclosed system recognizer handled network recognition.
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

## Initial Meta debug integration — 2026-10-27

- Added an optional `MetaGuideModel` wrapper and loopback USB gateway for Standard `muse-spark-1.3`. Provider credentials remain in ignored backend configuration. Android receives a separate local pairing token and requires explicit in-app test consent; service reconnection disables Meta mode.
- Spark may select only an enumerated control from existing local task rules. Fresh screen fingerprints and request generations reject stale output. Accessibility bounds, local translated instructions, Android TTS and screen-state verification remain authoritative. SAM and Meta speech transcription remain planned.
- Added INTERNET permission. The debug network policy allows cleartext only to loopback; the release policy does not. The gateway binds only to loopback port8766 and is reached through an ADB reverse tunnel.
- Gateway checks: **13 passed**. Native checks: **94 JVM tests, zero failures**, including eight new choice-grounding and stale-response cases. `assembleDebug testDebugUnitTest lintDebug` passed after fixing the debug network policy's required `includeSubdomains` attribute. Lint reports zero errors, with existing warnings remaining.
- Two real API checks passed: a minimal text request and a synthetic test-chat screenshot whose expected Photos control was selected. Conservative combined token estimate: **USD0.00400875**. This is neither a held-out accuracy result nor measured phone latency.
- The gateway enforces a USD5 initial test ceiling within the authorized USD150 project budget, reserves worst-case cost before requests and retains uncertain reservations. No automatic provider retry, public deployment or Git push.
- Initial physical check: the user followed the Meta opt-in/Calculator test instructions and reported a ring on the correct control. Compared with the pre-test ledger, three additional requests settled with no pending reservations; conservative cumulative cost was USD0.01425675. This corroborates gateway activity, not an independently captured control identity or measured accuracy. Full task and lifecycle acceptance remain pending.
- Installed the updated debug APK with `adb install -r` on the Xiaomi. Package readback remains **versionName1.0 / versionCode14**; Accessibility is Bound with capability161 and no binding/crashed service. The USB tunnel on8766 and authenticated gateway health check passed; pairing does not enable Meta mode. No AndroidRuntime error appeared in the app-process check.
- Deliverable SHA256: `B3111A085436F37011523795686A4AE994B5DCE2E6427C6DBDE74564B77C2549`. The deliverable and generated APK match. A byte-level check found no provider key in APK entries or Git-visible files; ignored configuration and state remain excluded.

## Goal guidance and pointing hand — 2026-10-27

- Replaced the thick target outline with a native vector pointing hand and a restrained 2dp motion. Placement selects a target edge and rotates above/sideways near screen boundaries. The non-touchable swipe cue uses the same hand. Card placement avoids both the control and the pointer body; lock, hide and Stop cancel animation, and disabled animations keep it static. A local vector preview was inspected on light and dark backgrounds; this does not replace physical visual acceptance.
- Obvious local controls and known templates now return without a provider request. Meta goal mode accepts a confirmed free-form request in the current app, chooses from actual labeled controls, and re-plans from new screens with at most five transition observations. The observations distinguish matched and wrong taps but never claim verified success. New goals clear the observation context.
- Added localized clarification and result-review states. A model-proposed result requires the user's confirmation and is not saved as independently verified completion. General mode excludes consequential final controls; no model coordinates, model-authored instructions or automatic taps/typing are accepted. Existing template verification remains unchanged.
- Real API check: six synthetic Calculator states selected 2 → 4 → Multiply → 7 → Equals, then returned `review` at 168. The fixtures contained no phone content. This is a small functional smoke check, not held-out accuracy or measured phone latency. Conservative cumulative gateway cost after it: **USD0.03778975**, no pending reservations.
- `assembleDebug testDebugUnitTest lintDebug` passed: **106 JVM tests, zero failures; lint zero errors**. Backend: **17 tests passed**. Tests cover general-mode choice boundaries, wrong-tap observations, no false completion, clarification, bounded history and pointer placement at screen edges.
- Installed with `adb install -r`, kept **versionName1.0 / versionCode14**, restarted the owned loopback gateway, checked its new goal-guidance feature, paired USB and verified Accessibility Bound with no binding/crashed services. SAM and Muse Voice remain unimplemented.
- Repaired entry from GuidingHand itself: instead of an instruction to open another app, show an installed-app picker. A user-selected app opens, its actual foreground package is checked, and any unconfirmed goal is preserved. Opening is explicitly requested by that app button; no model selects or launches an arbitrary package. The latest code passed the same 106 JVM tests and lint with zero errors before reinstalling.
- User reported that selecting Calculator opened it and showed Try an example. They then confirmed the pointing-hand sequence reached **168** and asked whether it was finished. Seven further gateway requests settled after the six synthetic checks; conservative cumulative estimate **USD0.06988625**, no pending reservations. This is a reported physical multi-step result corroborated by usage counters; no independent target trace, latency benchmark or other-goal accuracy is claimed.
- Latest deliverable SHA256: `B6EB44FF8EB14B9924C7E5C414DED618A7DB4C4E403B91E3E7A26EF14AEA3061`. Version readback remains1.0/code14; provider key absent from APK entries and Git-visible files. No public deployment or Git push.

## Final live-use, point-first policy — 2026-10-27

- The owner explicitly stopped trials and requested real use. No further user acceptance prompts, paid API smoke calls or functional-test runs are performed after that request. Historical119 JVM /22 backend results predate this final policy. Final verification is compilation, static lint, source inspection and installation only.
- All destination changes now enter Home/launcher guidance. A thin ring encloses the real observed icon, with the existing hand pointer and one localized spoken step. The default waits for the elder's tap and observes the exact destination package before continuing the preserved goal.
- The only target-app launch call is `openLauncherAppForMe`, reached by the explicit launcher-card **Mở giúp tôi / Open for me** action. It re-reads a safe, complete launcher/search screen, checks the active open task and installed launch intent, and verifies the resulting package. No launcher permission carries into in-app actions.
- Removed the automatic in-app/web search-intent paths. Queries are hints for manual typing. Existing tasks that need another app are deferred behind the same launcher step; Wi-Fi guidance now points through Settings instead of automatically opening its subpage. There is no automatic tap, typing, sending or confirmation inside a target app.
- Removed the trial labels and Calculator demo from the app. Normal everyday suggestions remain. The phone is configured for Vietnamese UI, instructions and speech. The owner-authorized live session ceiling is USD150 with the prior usage ledger preserved; provider credentials remain backend-only.
- Earlier technical logs showed complete=false at100 nodes and about100ms, consistent with the old35-level depth guard in the browser. The depth allowance is now96, retaining node/time/safety bounds. This is a source repair; no further physical browser result is claimed after the stop-trials instruction.
- `assembleDebug lintDebug` passed with zero lint errors. App display version remains **1.0**, internal code14. Installed APK SHA256: `40479A9ED7702BBE7B5CCF79EF1D362285DA2E1516F083C018ABAC5753D60FEC`. Byte inspection found no provider key in APK entries or Git-visible files. No Git push or public deployment.
