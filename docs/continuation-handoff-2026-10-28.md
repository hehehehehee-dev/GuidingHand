# GuidingHand continuation checkpoint — October 28, 2026

The user authorized autonomous continued improvement and real-phone inspection while away.
Work remains on `feat/elder-coach-core`; main and the original phone installation are retained.
Do not push GitHub. Do not send real messages/photos, call, post, purchase, delete user data,
or enter credentials/OTP/payment details during unattended inspection.

## Current installation

- Side-by-side package `vn.guidinghand.candidate`: **1.5.2-candidate, code 11**.
- APK: `build/outputs/GuidingHand-1.5.2-candidate.apk`.
- Original `vn.guidinghand`: **1.3, code 4**, unchanged.
- Accessibility is enabled for Candidate only.
- Xiaomi serial `X4YDVCV4LFFUKFLF`; temporary USB backend port **8768**.
- Candidate gateway PID is in `backend/.state/candidate-gateway.pid`.
- Provider credentials remain in ignored backend environment configuration; never print them.
- The app session token is preserved; adb reverse 8768 was restored after USB reconnect.
- These facts do not constitute an end-to-end task pass.

## Observed progress and unresolved failure

Current 1.5.2 changes are installed, but its full messaging workflow is not yet
observed. The observations below are historical Candidate1.5 evidence, not
proof of 1.5.2 success.

The user confirmed Candidate 1.5 recognized the request and pointed to the requested app.
They then requested messaging a recipient and reported that continuation failed.
Real phone trace `build/phone-runs/20261028T085653Z/trace.json` shows:

- First request `4cb30b05-eac9-4cc4-b311-616877f125ab`: Spark 2297ms,
  SAM 1078ms, then native WAITING_TAP.
- Follow-up request `70bd481d-6e05-4c53-8acc-33b16a305abd`: Spark 5250ms,
  SAM 890ms, then native ASKING.

Both providers ran on the phone request. The exact native rejection reason was not
captured in that older build. Do not label it an ASR error, edge-clipping error or
task-refinement error without evidence. No message was sent during agent inspection.

## Changes in 1.5.1

- Native ASKING can now carry a fixed `reasonCode`, logged as `guide_stop` without
  goals, UI text, recipient names, images or audio. Ring-edge failure is distinct
  from task refinement, recipient-title, stage/context and SAM/node mismatch.
- General tasks may evolve after a new spoken user request. A native utterance
  revision authorizes task updates only after new user input, not screen changes.
  The backend receives `allow_task_update` through the same model interface.
  Existing photo-task recipient and delivery safeguards remain separate and strict.
- Changed task bindings clear prior progress/evidence; a new operation does not
  inherit proof of completion. This is a code fix, not a verified explanation of
  the earlier observed rejection.
- Candidate debug build and backend Python syntax compilation pass. No new
  synthetic model run or automated test total is used as proof of product quality.

## Current external blocker

The user reported enabling **USB debugging (Security settings)**. Nevertheless,
the explicitly authorized adb tap was still rejected with `INJECT_EVENTS`
SecurityException, including after one normal USB reconnect. Read-only ADB works.
`com.android.shell` lists `INJECT_EVENTS: granted=true`; Android/vendor policy still
rejects actual injection. Do not repeatedly retry, grant permissions through shell,
disable security features, add runtime target-app automation, or try another input
mechanism to bypass this restriction. Do not wake the user with repeated questions.

No unattended complete phone workflow has been demonstrated. If no new phone
evidence is available, do bounded useful code review and remain quiet; do not
change working code merely to create activity or run repetitive billed fixtures.

## Continuation

The user requested stopping scheduled work. The heartbeat
`ti-p-t-c-c-i-thi-n-guidinghand-tr-n-xiaomi` was **deleted** successfully.
No scheduled continuation is active. The latest request authorizes direct
improvement in this chat; do not recreate a schedule without a new request.

Next useful evidence: the next actual follow-up request and its `guide_stop` code.
Then fix that specific invariant rather than removing validation or guessing its cause.
The full multi-app and Zalo send-photo workflows remain **not passed**.

## Heartbeat checkpoint — 09:36 UTC

Phone remains connected but is locked (`showing=true`). No injection attempt,
unlock or new model call was made. Read-only trace collection is in
`build/phone-runs/20261028T093610Z/`; no new post-install follow-up failure with a
`guide_stop` code was observed. Budget ledger remains requests 74, spent
0.409844 USD with the existing 0.01 USD uncertain reservation untouched.

Historical trace contains repeated SAFETY_PAUSED emissions. Source now makes
`pauseForSensitive` idempotent once latched, avoiding repeated cancellations and
overlay emissions for duplicate protected-screen events. This does not relax the
gate or authorize resume. Candidate debug build passes; the pending APK is
`build/outputs/pending/GuidingHand-pause-idempotent.apk`, **not installed** and not
phone-verified. It still has the source version 1.5.1/code10; bump the version
before promoting a new candidate rather than confusing it with the installed
1.5.1 artifact/hash. Do not replace the installed build merely to repeat an
unchanged blocked inspection.

## Heartbeat checkpoint — 10:06 UTC

Phone remains connected and locked. Since the current service session started at
uptime 398643052, its trace contains only the META_CORE initialization event:
zero new planning requests and zero `guide_stop` events. The usage ledger is
unchanged (74 requests, 0.409844 USD spent, existing 0.01 reserve retained).
Backend stderr is empty. No input, unlocking, installation, provider call or
additional code change was attempted. The next diagnostic still requires an
actual follow-up interaction after unlocking; no new cause can be inferred.

## Heartbeat backoff — 14:06 UTC

Read-only checks through 14:06 UTC show the same connected but locked phone,
unchanged ledger and empty backend stderr. The existing heartbeat was updated,
not duplicated, to run every two hours and focus on readiness/new diagnostic
evidence. The prompt records that USB reconnection was already tried, so future
runs must not repeat it or blocked injection. No app build, installation,
provider request or purported task success was added during these unchanged checks.

## Direct work resumed — user request, October 28 local time

The user deleted the schedule and then explicitly requested direct improvement.
The schedule remains deleted. Current phone is connected/unlocked; read-only
evidence was collected under `build/phone-runs/20261029T003921Z` (UTC folder name).
No new prior-runtime diagnostic identifies the exact messaging rejection yet.

Installed **1.5.2-candidate/code11** changes:

- Separate standalone follow-up utterances (`goal.updates`) from clarification
  pairs. Spark sees that the final chronological answer is the latest utterance;
  older opening requests/updates must not override a newer request or reply.
- Screen-change observations include the previous instruction, target label and
  source package in the in-memory model request. They are not logged and do not
  count as proof of sending/completion.
- Card natural height is capped at 42% of available screen height while respecting
  minimum typography/target sizes. Large-font minima can exceed this cap. Content
  scrolls; Speak/Repeat/Stop remain outside the scroll area. Transcript is hidden
  while pointing and visible during clarification/errors.
- General task refinement failures ask about the next action rather than always
  asking for a photo. More fixed rejection codes identify backend contract,
  missing target/SAM and edge-ring failures without logging screen text.
- The earlier idempotent safety-pause source fix is included in this installation.

`assembleDebug lintDebug` and Python syntax compilation pass. APK installation
and own MainActivity launch succeeded; Candidate Accessibility remains enabled.
Backend `/core/capabilities` returns HTTP200 and both task kinds, without a Meta
request. Temporary USB port8768 is restored. Usage remains requests74,
0.409844 USD spent plus the existing 0.01 pending reservation. Original1.3 and
main retained; no push and no new input-permission bypass/retry. No new end-to-end
phone messaging or send-photo completion is claimed.

## Overnight checkpoint — October 29 local time

The temporary overnight heartbeat was deleted when the user returned. No phone
build was replaced and no provider fixture or paid model request was run. The
Xiaomi was disconnected when checked, so there is no new real-phone workflow
evidence.

Added deterministic regression coverage for extending an existing general app
task with a later spoken request (for example, opening Instagram and then asking
to message Minh). The tests assert that the original goal remains available, the
latest utterance reaches the planner, a fresh utterance revision authorizes the
task refinement, and prior verified steps are not inherited by the new operation.
Launcher test fixtures now carry the same explicit package binding required by
runtime launcher nodes; a search field cannot masquerade as a launcher icon.

Backend core tests pass (32 tests) and `CoreGuideEngineTest` passes (44 tests).
These are deterministic contract tests only, not evidence that messaging works
end to end on the phone. The installed Candidate remains 1.5.2/code11.

## Candidate 1.5.3 built — October 29 local time

The engine no longer labels a matched general-app tap as wrong-tap recovery just
because native verification cannot prove the whole operation. It records the
matched tap and changed screen as observation evidence, keeps the goal, and
replans from the resulting state. It still does not mark the action complete or
allow the model to claim completion.

Added a regression for this transition. Backend core tests pass 32/32 and
`CoreGuideEngineTest` passes 45/45. `assembleDebug lintDebug` passes. The verified
artifact is `build/outputs/GuidingHand-1.5.3-candidate.apk`, code12, SHA256
`cfe85f08ac61b8756433b294bd79dc13d888d6c24d863a6b00a9388c01dffd1c`.

The Xiaomi was disconnected, so 1.5.3 is **not installed or phone-verified**.
Installed Candidate remains 1.5.2/code11. No provider request, message, photo,
call, GitHub push or permission bypass was made.

## Voice-message plan progress — October 29 local time

The reviewed plan is `docs/voice-message-ux-plan-2026-10-29.md`. Its first three
checkpoints are complete: private RAM-only draft state, a static elder-focused visual
prototype, and a strict structured speech-purpose contract. The prototype passed button
containment checks at simulated font scales 1.0, 1.3 and 1.5; the ring is absent during
speech/review and appears only when pointing at the target-app step.

`VoiceDraftSession` requires explicit review and copy-consent transitions, overwrites a
replaced draft, rejects sensitive/invalid content, and is cleared by Stop, lock/screen-off,
sensitive pause, a new goal, or service destruction. It has no persistence or logging
surface. Spark's strict schema now requires `general`, `recipient`, or `message_content`;
native receives the enum without inspecting localized question text. Backend core tests
pass 34/34 and targeted Android engine/draft tests pass. No provider call or phone install
was made. Checklist item4 (purpose-aware mic sheet) is next.

## Candidate 1.5.4 voice-message build — October 29 local time

The purpose-aware mic, private transcript review, explicit temporary-copy consent, and
recipient-plus-composer Send gate are implemented behind the one planner contract. Muse
Voice Transcribe remains batch input; the UI does not pretend to stream words. The draft
stays RAM-only, is not logged, and is cleared on Stop, lock/screen-off, sensitive pause,
new goal, and service destruction. Copy requires a separate elder tap and cleanup clears
the clipboard only while it still exactly matches GuidingHand's owned draft. Paste and
Send remain elder actions.

The runtime review card has one filled primary action, one outlined correction action and
a 24sp quotation surface. The existing measured cream/dark-green contrast remains
13.258:1. The static prototype passed action containment at simulated font scales 1.0,
1.3 and 1.5; runtime phone screenshots remain pending.

Full backend discovery passes 65/65 tests. Android `testDebugUnitTest`, `assembleDebug`,
and `lintDebug` pass. The verified side-by-side artifact is
`build/outputs/GuidingHand-1.5.4-candidate.apk`, code13, SHA256
`412c6ffb39045ebf0c6041d8e5a9cbaed02ad1a4c5f0540f8d2da82b4cceb5d3`.
The Xiaomi is disconnected, so this build is **not installed or phone-verified**; installed
Candidate remains 1.5.2/code11. No paid provider call, real message/photo, GitHub push, or
permission bypass was made.

## Candidate 1.5.6 Vietnamese speech fix — October 30 local time

The installed Candidate now records at Muse Voice Transcribe's native 24 kHz when the
device supports it, with a safe 16 kHz fallback. The backend derives `languageBias` from
the saved app language instead of hard-coding one language, and sends a bounded set of
installed-app labels plus the configured family name as vocabulary hints. These hints are
disclosed in the voice-consent copy, remain bounded to 32 entries, and are never logged.

The earlier side-by-side Candidate had no saved language and therefore defaulted to English.
The authenticated USB development pairing now accepts an explicit language and verifies
that it was actually persisted. Pair delivery uses CLEAR_TOP + SINGLE_TOP rather than a
force-stop because Xiaomi may disable Accessibility after a force-stop.

Candidate **1.5.6/code15** is installed and paired with Vietnamese saved. Backend tests pass
66/66; Android unit tests, assembleDebug and lintDebug pass. Artifact:
`build/outputs/GuidingHand-1.5.6-candidate.apk`, SHA256
`4258ba39f4f468f14c6924615bc9e9021b7a63f6db60b2864513899c9636aa2e`.
No paid transcription was invoked during deterministic verification. Xiaomi disabled the
Candidate Accessibility service during the update/restart sequence, so a real Vietnamese
utterance is still pending the user's manual re-enable and speech. No recognition-success
claim is made yet.

## Candidate 1.5.8 continuous-progress fix — October 30 local time

Real-phone trace from 1.5.6 showed successful Vietnamese transcription in roughly two
seconds, followed by five-to-eight seconds of planning. The apparent disappearing bubble
was caused by removing every overlay for the safety-checked screenshot and restoring it
only after the provider returned. Candidate 1.5.8 now shows a disabled **Đang nghe…** chip
during the mic session and restores the current progress card immediately after capture,
before waiting for Spark/SAM.

Launcher transitions now wait briefly for two matching safe reads before planning. If the
screen changes during capture, the result is discarded as stale and the engine re-reads
from the new state while preserving the goal; it no longer immediately converts that race
into a `cannot read` prompt. A genuinely unlabeled, cluttered, sensitive, or locked screen
still stops rather than guessing.

Candidate **1.5.8/code17** is installed, paired in Vietnamese, and its Accessibility
service is bound. Backend tests pass 66/66; Android unit tests, assembleDebug and lintDebug
pass. Artifact SHA256:
`7e4e7ccc2aba4509db085da1773c8f8117c3a73a5b60613b07eb5dbe112cecc7`.
The new end-to-end Home transition still needs one human voice/tap observation; no task
success or universal-app claim is made from deterministic tests alone.

## Candidate 1.5.9 retry and movable-mic fix — October 30 local time

Real-phone trace identified `snapshot_unavailable` while the Accessibility tree remained
SAFE. The retry action existed inside the scroll area and was not visible without scrolling.
For this state, **Kiểm tra lại** is now pinned as the only primary footer action beside Stop.
Android screenshot capture retries once after 500 ms when the exact safety-checked screen is
still current, logs only the Android error code, and restores the progress overlay on every
exit path.

The separate mic window now has a 48dp labelled **Kéo để di chuyển** handle and is constrained
inside system bars while dragging. It still owns no target-app action and screen reading stays
suspended while it records.

Candidate **1.5.9/code18** is installed, paired in Vietnamese, and Accessibility is bound.
Android unit tests, assembleDebug and lintDebug pass; backend tests remain 66/66. Artifact
SHA256: `a3e9393693e6c7709a7d35f611a89fa38121c5cb636e0ef4c01ffa762905ddf8`.
Human verification of the pinned retry button and drag gesture remains pending.

## Candidate 1.5.10 Xiaomi screenshot fallback — October 30 local time

Real-phone trace isolated the recurring Home/Chrome failure from the planner: the
Accessibility tree remained SAFE, but Xiaomi returned Android screenshot error 1 for two
consecutive `takeScreenshotOfWindow` calls. During each attempt the old implementation
also suspended the guide overlay, which made Help appear to disappear while Android was
waiting to fail.

Candidate 1.5.10 keeps the progress UI visible for one window-capture attempt. If Xiaomi
returns an error, it does not retry the same failing API: it briefly suspends only the
GuidingHand overlay, re-verifies the exact safety-checked screen and absence of another
active/focused intersecting window, then makes one display-capture fallback. The existing
`finally` restoration covers success, failure and stale-screen exits. Spark and SAM still
receive the same accepted image; no target-app input automation was added.

Candidate **1.5.10/code19** is installed, paired in Vietnamese, and Accessibility is bound.
Android unit tests, assembleDebug and lintDebug pass. Full backend discovery passes 66/66
with the bundled SAM parser. Artifact SHA256:
`c6911c3e6c3889de907e32f6167cd688ee367fbdc4f611fb6b710d1c7140e275`.
The specific Chrome search needs one new human interaction to confirm whether Xiaomi's
display-capture fallback succeeds on the live screen; no Chrome-task success is claimed yet.

## Candidate 1.5.11 SAM runtime and visible-retry fix — October 30 local time

The first real 1.5.10 interaction verified the Xiaomi fallback itself: trace recorded
`capture_failed_window/android_1` followed by `capture_display/success`, then Spark returned
in about 4.7 seconds. The subsequent block was not a cable failure or budget exhaustion.
The development gateway had been started without the bundled `meta_sam_parser` on its Python
path, so the first task requiring a SAM region stopped before a provider SAM request.

The Candidate gateway now runs with the bundled parser available. Error copy no longer
combines provider connection, missing SAM runtime, and budget limit into one misleading
sentence. Native trace preserves those bounded diagnostic codes without recording screen or
goal content. Every recoverable ASKING/UNAVAILABLE state now places a full-width
**Kiểm tra lại** in the fixed footer instead of the height-constrained scrolling action area;
the thin clipped green action seen on the phone is removed.

Candidate **1.5.11/code20** is installed, paired in Vietnamese, and Accessibility is bound.
Android unit tests, assembleDebug and lintDebug pass; full backend discovery passes 66/66
with the bundled parser. Artifact SHA256:
`b998e68ec0ab0193883382853cdac98567d96596cdf3f939188f241ff747705b`.
The exact spoken Home-button question must be repeated once for live end-to-end Spark+SAM
confirmation; no successful ring is claimed before that interaction.

## Candidate 1.5.12 compact guide and edge-region fix — October 30 local time

The collapsed `Tiếp` control was a plain circular text chip. It is now a high-contrast
yellow-outlined dark-green pill with the existing GuidingHand hand mark, `Tiếp tục` copy,
ripple feedback and elevation. It retains the 72dp primary-target minimum, can still be
dragged, and its content description remains explicit rather than relying on the icon.

A real Facebook search screen reached Spark and SAM but native rejected the returned region
as too close to the display edge. Candidate 1.5.12 keeps the 12dp gap and both 6dp ring
strokes. If SAM has included adjacent header chrome, native may use the exact clickable UI
bounds only when the two boxes overlap by at least 50% and the complete ring fits onscreen.
The step records both raw SAM and the safety-bounded visual region with source
`sam-3.1-ui-bounded`; the engine independently validates this provenance. Weak overlap or a
truly edge-clipped control still stops instead of drawing a misleading partial ring.

Candidate **1.5.12/code21** is installed, paired in Vietnamese, and Accessibility is bound.
Android unit tests, assembleDebug and lintDebug pass; full backend discovery passes 66/66.
Artifact SHA256:
`54bf65bf32d5b89f915c0b4c02350f20e2fe89b48206ae92ffe4bc693fc1b4bc`.
The new pill and the same Facebook Search target await direct human visual confirmation; no
live ring-success claim is made yet.

## Candidate 1.5.13 edge-hugging ring — October 30 local time

The product decision changed after phone review: a legitimate control at the display edge
must still be pointed out rather than rejected. Candidate 1.5.13 draws the non-touchable
double border for edge targets. On an overflowing side, the black and yellow stroke centers
are pulled inward separately so both full 6dp strokes remain visible and distinct. The 12dp
gap remains unchanged on every side with room; it is reduced only on the physically
constrained edge. Normal targets retain the original 12dp + 6dp + 6dp geometry.

Off-display or invalid model geometry still stops. The existing strong-overlap UI-bounding
path remains preferred when SAM merely included extra header chrome. Pure geometry tests
cover top-left edge adjustment and unchanged ordinary spacing.

Candidate **1.5.13/code22** is installed, paired in Vietnamese, and Accessibility is bound.
Android unit tests, assembleDebug and lintDebug pass; full backend discovery passes 66/66.
Artifact SHA256:
`d6f48704c052e4d69aa877eee4d618fccdc381c4774f1b41c2e53eb97fe858b4`.
The Facebook Search edge target still awaits one direct phone observation after install.

## Candidate 1.5.14 stable snapshot and press feedback — October 30 local time

All overlay buttons now provide a high-salience pressed state: the surface changes to bright
yellow and the label changes to dark green while held. Focus uses a lighter yellow, disabled
controls use neutral grey, and the draggable compact guide explicitly manages its pressed
state so a tap is visible before opening. This is in addition to ripple feedback and does not
rely on colour alone because labels, outlines and control shape remain present.

Planning now binds one safety-checked screenshot to a `planningFingerprint` containing the
app/window, navigation generation, safety state and actionable geometry. Decorative,
non-interactive text or image animation may change the full screen fingerprint without
discarding that captured frame. Spark and SAM still receive the exact same encoded image.
Before a ring is accepted, native re-reads the screen and requires the selected target's id,
bounds, class, clickability, password/selection state and labels to remain identical. App or
window changes, actionable geometry changes, sensitive content, or a replaced target still
discard the result. While waiting for a tap, unrelated animation is ignored but a real tap or
target change continues through normal verification/recovery.

Candidate **1.5.14/code23** is installed, paired in Vietnamese, and Accessibility is bound.
All 78 Android unit tests, assembleDebug and lintDebug pass; full backend discovery passes
66/66. Artifact SHA256:
`a3e3e549f2f5b460cc209c38700249b33b063eb3c9762feb65aa02b6d5472a56`.
Animated-background behavior and the stronger pressed states await direct phone observation.

## Candidate 1.5.15 directional hand cue — October 30 local time

Every WAITING_TAP step now keeps the non-touchable black/yellow ring and adds a separate
directional GuidingHand marker. The marker is a code-native vector, not an emoji: black outer
stroke, bright-yellow inner stroke and fingertip dot. It prefers the space below the target,
then above, right or left, rotating to point toward the control. It overlaps the target by
only 12dp, remains fully inside the display, and is included in the guide-card exclusion area
so the card avoids both cues. Because it shares the ring overlay, it cannot intercept the
elder's tap.

Candidate **1.5.15/code24** is installed, paired in Vietnamese, and Accessibility is bound.
All 79 Android unit tests, assembleDebug and lintDebug pass; full backend discovery passes
66/66. Artifact SHA256:
`bfd3115c1facfe93ce8c87e901e61454bdf2261b5c14afd02ae78fa0df821493`.
The hand's size and placement await one direct phone visual check on a real target.

## Candidate 1.5.16 in-app accessibility setup — October 30 local time

The in-app setup is now organized as an elder-and-family accessibility hub rather than a
single undifferentiated list. It groups language, respectful Vietnamese forms of address,
guidance tone, vision and motion, family contact, and a collapsed technical section. The
main screen also reports service readiness and summarizes the selected language and visual
profile. The floating Help overlay is hidden while GuidingHand's own setup screens are in
the foreground so it does not cover settings; it is restored when another app becomes
active.

Vision choices include high contrast, protanopia, deuteranopia, tritanopia and monochrome.
The selected accent is used by the non-touchable double ring, directional hand and pressed
state. Geometry, black outlines and text remain present, so color is never the only cue.
Measured contrast against the dark guide ink is respectively 10.72:1, 8.83:1, 9.42:1,
8.31:1 and 13.59:1. A separate control can hide the hand without disabling the ring, and
Reduce motion removes non-essential ripple motion while retaining visible pressed states.

Candidate **1.5.16/code25** is installed and paired in Vietnamese. All 80 Android unit
tests, assembleDebug and lintDebug pass; full backend discovery passes 66/66. Real-phone
screenshots verify the settings hierarchy and unobscured setup screen only; no target-app
task success is claimed. Artifact SHA256:
`a53680d4f0ba0b416afe00415db065c2f7bd16f2c77d6da64b4950cbcb97d811`.

## Candidate 1.5.17 overlay stability and planner prompt — October 30 local time

Historical read-only Xiaomi trace contained 1,462 `screen_step/ASKING` events and 2,623
safe screen reads, plus 56 backend round trips averaging 5,881 ms (Spark averaged 4,985 ms).
These events span earlier Candidate builds; they are a diagnosis, **not** a 1.5.17 speed
measurement. The service no longer performs a second overlay render after an engine emission,
and the ring stays attached while the same target is displayed. The safety gate now precompiles
its sensitive patterns and normalizes each screen label once; a new test checks Vietnamese,
English, password flags and a benign longer word. The three footer labels now
fit the usual single row at 22sp; full action descriptions remain for screen readers. The
Spark prompt is 415 words and retains multi-app dialogue, safety and elder-action rules.

Candidate **1.5.17/code26** is installed on Xiaomi, with Accessibility enabled, Vietnamese
pairing and USB development gateway port 8768 available. The gateway was restarted to load
the prompt. A temporary backend session credential that appeared in a diagnostic tool result
was rotated and the phone re-paired; the Meta provider key and budget ledger were not reset.
All **81 Android unit tests**, `assembleDebug`, `lintDebug`, and **66 backend tests** pass.
APK SHA256: `7c50baa31f7ecf3c2db8fd5e9d64f74d3e7a2e2137d6c524c0148d89bf892bf6`.
One owned synthetic app-confirmation fixture using real Spark returned the expected
`send_photo_zalo/clarify` decision in 7,390 ms. This does not establish a speed gain.
No live target-app step or new latency measurement has yet verified the improvement. The
send-photo task remains unpassed. Next: one actual elder-initiated guide step in a harmless
app, observe card/ring persistence and record per-step latency without sending or posting.

## Candidate 1.5.18 Help bubble flicker — October 30 local time

The elder reported continuous Help-bubble blinking. Code inspection found that
`onAccessibilityEvent` treated events from GuidingHand's own accessibility overlay as though
the GuidingHand app itself were foregrounded, hid the bubble, and let the next launcher event
show it again. The handler now checks for a foreground **application** window before hiding
the overlay. Repeated `showBubble()` calls also avoid changing identical drawable and view
properties; repeated listening events keep an unchanged busy bubble mounted. Debug trace
records only `bubble_attached`/`bubble_detached` transitions.

Candidate **1.5.18/code27** is installed on the Xiaomi; Accessibility remains enabled.
Android 81/81 tests, assembleDebug and lintDebug pass. Backend code is unchanged from the
66/66-test 1.5.17 build. APK SHA256:
`95ae21e56b6aee2e7f0e9993111322bd9d257b4bf94ae71e1f711afd6421957a`.
Read-only trace with Home foreground for 15 seconds after install recorded one bubble attach
and zero detaches: `build/outputs/phone-bubble-1.5.18.json`. This verifies idle stability
for that interval, not behavior during microphone or target-app screenshot capture. No
target-app input or provider call was made. The send-photo task remains unpassed.

## Candidate 1.5.19 elder action hierarchy — October 30 local time

Two audited Codex skills were installed locally for future UI work:
`mobile-ui-ux-designer` from `mdrmuhaimin/agentic-skills` and `ui-ux-pro-max` from
`sidiangongyuan/codex-skills-library`. The first contains only Markdown guidance and
references. The second contains local CSV search scripts; audit found no network or shell
execution. Search results were treated as evidence rather than copied mechanically. A generic
video-hero/red-blue recommendation was rejected as irrelevant to this native elder assistant.

The applied finding is action hierarchy. READING, PLANNING, VERIFYING and RECOVERING states
no longer show Speak or Replay, which previously invited a duplicate request while work was
already in progress. Stop remains available. Safety pauses, unavailable states and recovery
questions now use an assertive TalkBack live region; ordinary instructions remain polite.
The product-specific rules are recorded in `docs/elder-ui-contract-2026-10-30.md`.

Candidate **1.5.19/code28** is installed and Accessibility remains enabled. Android 81/81
tests, assembleDebug and lintDebug pass. Backend code is unchanged from the 66/66-test build.
APK SHA256: `c01a7ff2a9dae7d4af0c1301b963c28a2108922e2ff6707c4e779cc2958c3b35`.
The Xiaomi was dozing during final review, so the 1.5.19 busy card has not received rendered
visual QA. No provider call or target-app action was made. The send-photo task remains unpassed.

## Candidate 1.5.20 controlled voice input — October 30 local time

The mic sheet no longer begins recording merely because Help was opened. Its calm ready state
has one 72dp primary action with a native microphone mark: **Chạm để nói**. The same control
becomes **Dừng nghe** after one tap. A real PCM-level meter and elapsed timer provide visible
feedback; they do not fabricate live transcript words. Muse Voice Transcribe remains batch
speech-to-text and runs only after the elder stops. The movable 24sp sheet keeps **Gõ chữ** as
an outlined secondary fallback, with clear pressed surfaces and haptic feedback.

Every recognized utterance now pauses at a large-text **Bác nói:** review. General goals and
recipient names reach Spark only after **Đúng rồi**; message content retains its separate
review and explicit temporary-copy consent. Typed fallback is also rechecked against the
current screen after the input window closes. Password, payment, OTP and unreadable screens
still stop before provider or planner use. No target-app tap, type, paste, send, swipe or call
was added.

Candidate **1.5.20/code29** is installed on Xiaomi and Accessibility remains bound. Android
81/81 tests, `assembleDebug` and `lintDebug` pass; backend code is unchanged from the last
66/66-test build. APK SHA256:
`48d00e91f5fb0129957046e9f35011c88bd744655b7061cae587233eefbd1460`.
The main Candidate screen was rendered on the real phone. The mic Activity remains correctly
non-exported, so adb could not open it directly for a rendered checkpoint; one elder-initiated
Help → mic interaction is still required. No provider request or target-app action was made,
and the send-photo task remains unpassed.

## Candidate 1.5.21 visual home — October 30 local time

The landing screen no longer presents a long instruction document. It reuses the existing
elder-and-family illustration as the central story, followed by a three-icon journey:
**Nói → Nhìn → Tự bấm**. One filled 76dp voice action dominates the screen. Setup is a
secondary outlined action. Version, language, model label and the longer safety explanation
are deferred behind **Xem cách hoạt động**.

The home status now refreshes while Accessibility reconnects after an update. When the
service is enabled but still binding, the voice action shows **Đang chuẩn bị…** and stays
disabled instead of incorrectly opening Android settings. The screen contains no looping
decorative animation. Meaning remains available through labels and shapes, not images or
color alone.

Candidate **1.5.21/code30** is installed and Accessibility remains bound. Android 81/81
tests, `assembleDebug` and `lintDebug` pass. The final home screen was rendered on the Xiaomi
at its current large font scale; the brand, illustration, three visual steps and all actions
fit without clipping. Evidence: `build/outputs/guidinghand-1.5.21-home-final.png`. APK SHA256:
`2a0920cada418b08f7e4c5939f9abf1f2ec0714e67a392cd98202f08f549ff5f`.
No provider request or target-app action was made. The send-photo task remains unpassed.

## Candidate 1.5.22 immediate language refresh — October 30 local time

The English preference was already saved correctly on the Xiaomi, but MainActivity retained
the Vietnamese composition created before Settings opened. MainActivity now observes its
resume lifecycle and re-reads SharedPreferences when the user returns. This changes the
visible Home language immediately without force-stopping or reopening the app. The service,
overlay, speech input bias and offline TTS already read the saved language dynamically.

Candidate **1.5.22/code31** is installed and Accessibility remains bound. Android 81/81
tests, `assembleDebug` and `lintDebug` pass. Read-only device evidence first confirmed
`language=en`; the rendered Home and UI tree then showed **Ready**, **You speak. I point.
You tap.**, **Speak to get help**, **Settings** and **How it works**, with no Vietnamese
Home label. Screenshot: `build/outputs/guidinghand-1.5.22-english.png`. APK SHA256:
`50297db43eb84db11daaabf1502865b776ecf5ba9dd2cfc3808c58c473d16b96`.

## Candidate 1.5.23 own-screen overlay lifecycle fix — October 30 local time

A four-second real-phone recording showed the floating Help control appearing roughly three
seconds after the GuidingHand Home screen was already visible, covering the in-app journey.
The service previously hid the overlay only when an Accessibility event itself belonged to
GuidingHand. A later System UI or overlay event could therefore recreate Help even while the
setup Activity remained foreground. Overlay rendering now checks foreground ownership at
service connection, screen broadcasts, engine emissions, preference refresh, coalesced screen
updates, and before handling every Accessibility event. The separate microphone Activity
retains its compact listening indicator.

Candidate **1.5.23/code32** builds successfully and Android unit tests pass. APK SHA256:
`17bb34e78780d48d0e0d3916f80b984e5e951e2ca0f6d3a288ca5952d4b57a85`.
Xiaomi rejected unattended ADB installation with `INSTALL_FAILED_USER_RESTRICTED`; the APK
was copied to `Downloads/GuidingHand-1.5.23-candidate.apk` and the package installer opened.
Installation and the physical no-reappearance check remain pending the user's tap. No provider
request or target-app action was made.

## Candidate 1.5.24 anchored Help card — October 31 local time

The compact Help control remains visible while a guide card is open. The card is now laid out
next to the Help control, avoids both the target ring and the anchor, and repositions during the
same drag frame when the elder moves Help. This keeps the way back to the assistant visible and
prevents a detached card from covering the control that opened it.

Candidate **1.5.24/code33** builds as package `vn.guidinghand.candidate`; Android unit tests pass.
APK SHA256: `5883e87f5cf680a81adfccdbb2380dbddf1536cdb81cb878a89949c93b4bf296`.
The APK was installed through Android's package manager from `/data/local/tmp` after Xiaomi
rejected the `/sdcard/Download` path. Read-only package evidence reports `versionName=1.5.24-candidate`,
`versionCode=33`; Accessibility is enabled and Bound with no binding service. Visual verification
of the anchored card remains pending.
