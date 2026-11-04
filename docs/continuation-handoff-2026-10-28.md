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

## Candidate 1.5.25 anchored-card sizing fix — October 31 local time

The first anchored-card implementation still fell back to the bottom lane when the full card
height did not fit above Help. The placement solver now keeps the card attached to Help with a
shorter scrollable box before considering any distant fallback lane. The card still avoids the
ring and Help anchor.

Candidate **1.5.25/code34** builds and installs as `vn.guidinghand.candidate`. Android unit tests
pass; package readback reports `versionName=1.5.25-candidate`, Accessibility enabled and Bound.
APK SHA256: `394027982a8b1ed291ae3dcbe843c4bc6b30804910f63ca67be1d5886d6f1526`.

## Candidate 1.5.26 submission reliability — October 31 local time

The Home readiness badge now requires the Accessibility service, microphone permission, bound
service, and an authenticated no-credit `/core/capabilities` contract probe. Offline, expired,
unconfigured, and incompatible backends are shown as distinct recovery states. The probe is
bounded to two attempts per Activity resume and never calls a model. The disabled challenge
control was removed.

Transient core failures now map to distinct actionable states without automatic paid retries.
Android records only bounded geometry, byte counts, reason codes, and timing events; stale AI
results are discarded with reason codes when the screen changes. The evidence collector and new
submission report summarize these privacy-safe events while leaving ring accuracy and a real-phone
pass as `TBD` until reviewed by a person.

The backend now has a public detail-free `/healthz`, authenticated capabilities, proxy-aware HTTPS,
a non-root Dockerfile, persistent budget-ledger deployment instructions, environment validation,
and GitHub verification for backend and Android. Local verification is **72/72 backend tests** and
**84/84 Android unit tests**, plus `lintDebug` and `assembleDebug`. Docker itself is not installed on
this workstation, so the container has not been built here. A local two-screen JPEG benchmark made
six measurements and zero provider calls; production image defaults were deliberately retained
because SAM accuracy has not yet been re-certified. This candidate is built only and has not been
installed or phone-certified in this entry. Verified package metadata is
`vn.guidinghand.candidate`, `versionName=1.5.26-candidate`, `versionCode=35`; APK SHA256:
`6335e2719b3c073638a098174abd4fbe1a1e0e6df96a29cb10d24ea72f5adb60`.

GitHub verification run `41184048906` independently passed all three jobs: the 72-test backend
suite, the Android candidate build/lint/test plus package/version assertions, and a Linux container
build followed by a live `/healthz` request with fixture-only environment values. The health check
made no provider call. This supplies container-build evidence despite Docker being unavailable on
the Windows workstation; it is not evidence of a production cloud deployment or phone task pass.

The backend now ignores `X-Forwarded-Proto` unless
`GUIDINGHAND_TRUST_PROXY_HEADERS=true` is explicitly configured. This closes the direct-client
header-spoofing path while preserving managed TLS termination behind a private trusted proxy.
Tests cover both the safe default rejection and the explicitly trusted deployment path; local
verification remains 72/72 backend tests and 84/84 Android tests with lint/assemble passing.

The persistent budget ledger now uses a bounded operating-system advisory lock instead of treating
the existence of `usage.lock` as ownership. The lock is released automatically when a Gunicorn
worker exits or crashes, while concurrent workers still serialize reservations. This prevents one
terminated worker from leaving the deployed backend permanently stuck in `budget_locked`. A stale
lock-file regression test raises the backend suite to **73/73**; Android remains **84/84** with
lint/assemble passing.

Submission evidence now keeps trace provenance separate from the source revision being verified.
Every report records the trace filename and SHA-256 and defaults its candidate version to `TBD`
unless collection evidence supplies one, so an older phone trace cannot be presented as current
1.5.26 performance. The read-only collector also now reads package/version metadata from
`vn.guidinghand.candidate` rather than accidentally reporting the original app. The backend suite
is **74/74**; Android remains **84/84** with lint/assemble passing.

GitHub verification run `41200752173` passed the complete fixture-only app-facing cloud contract
inside the Linux container. It verified the public detail-free health endpoint, direct HTTP
rejection, bearer-auth rejection, and an authenticated capabilities response through explicitly
trusted forwarded HTTPS, including the expected snapshot protocol, Muse Voice Transcribe model,
and `automatic_actions=false`. The run made no provider call; all backend, Android, and container
jobs passed. This is deployment-contract evidence, not evidence of a production cloud deployment
or a real-phone task pass.

## Candidate 1.5.27 confidence and evaluation evidence — October 31 local time

Spark/SAM/Accessibility target fusion now treats display-edge proximity as geometry rather than
uncertainty. A production ring still requires a real clickable node and a valid SAM result with at
least 50% overlap; a legitimate edge control uses its exact UI bounds for visible rendering while
retaining the raw SAM region and provenance. Both black/yellow strokes and the hand pointer remain
inside the display. Mismatched sources stop without a ring.

An exact-full-screen Step cache now removes duplicate provider work caused by repeated identical
Accessibility events. It is RAM-only, bounded to eight entries and 30 seconds, stores no image,
and keys the exact screen plus complete goal/task/dialogue state and backend/session fingerprint.
Stop, a new goal, lock or a sensitive screen clears it. Any changed screen misses the cache.

GuideEngine now retains a bounded content-free transition graph with explicit `wrong_tap`,
`unexpected_screen`, `cannot_read` and `safety_gate` reasons. The privacy-safe Meta trace viewer
can render this graph together with provider timings, fusion decisions and stale-result events,
but never reads arbitrary trace fields or displays screen/audio/goal/message/recipient/token data.

Local deterministic evidence is **91/91 Android tests**, lint and Candidate assemble, plus **79/79
backend tests**. The fixture suite covers 24 safety cases and eight fusion-geometry cases with zero
provider calls; it deliberately leaves human-reviewed ring accuracy and older-adult usability as
`TBD`. Candidate identity is `vn.guidinghand.candidate`, `1.5.27-candidate`/code 36, with a distinct
label in every locale. APK SHA-256 is
`f5f76e5c001848696a567925bd9e3c3aabde10de7191f983579437f772cb021f`. GitHub verification run
`41259582278` passed Android, backend and container jobs for commit `92a9b6d`, including Candidate
identity and deterministic evidence generation. The build is not installed or phone-certified in
this entry; cloud deployment remains deferred.

## Stable waiting UX and perceived-latency evidence — November 1 local time

The overlay now treats an identical rendered state as idempotent, so repeated Accessibility events
do not rebuild the same bubble/card and cause visible flicker. Active transcription uses a dedicated
working state with only Stop available instead of presenting a misleading retry action that could
start duplicate work. Hiding the overlay resets the render identity so a later legitimate show still
renders normally.

Privacy-safe `goal_started` events now let the submission report derive goal-to-first-guidance
latency without recording goal text, transcript, screen content, recipient, image, audio, token, or
provider output. The report also counts exact-screen cache hits as duplicate model work avoided.
These measurements remain trace-dependent and do not turn fixture data into phone claims.

Local verification is **79/79 backend tests**, Android unit tests, lint, and Candidate assemble.
GitHub verification run `41286612609` passed the backend, Android, and container jobs for commit
`04204b5`. The change is pushed to `main` but was not installed or phone-certified while the user
was away; current-device UX and latency remain `TBD` until reviewed through a real interaction.

## Bounded Xiaomi smoke evidence — November 1 local time

After explicit authorization, the existing Candidate package was updated in place with `adb install
-r`; the original GuidingHand package and Candidate data were not removed. The reusable
`tools/phone-smoke-test.ps1` collector then passed on the connected unlocked Xiaomi for Candidate
identity (`vn.guidinghand.candidate`, `1.5.27-candidate`/code 36), awake/unlocked state,
Accessibility enabled/not crashed, no new fatal exception, and no bubble attach/detach churn during
an eight-second idle observation.

This is real-phone read-only evidence, not a simulated task pass. The report explicitly records
`inputInjectionUsed=false` and `userInteractionClaimed=false`, omits the device serial and all user
content, and stores output only under the ignored build directory. A single ADB tap attempt confirmed
that Xiaomi still rejects injected input with `INJECT_EVENTS`; it was not repeated or bypassed.
Speech, transcript correction, ring accuracy, step advancement, and target-app completion therefore
remain `TBD` until the user performs those interactions.

## Help-anchor drag correction — November 1 local time

The previous drag branch moved a safe Help bubble but persisted its new anchor and re-laid out the
card only when the candidate position overlapped the highlighted target—the rejected case. This
could leave the card behind the bubble or remember an invalid anchor. Accepted Help drags now update
the bubble window, persist the safe anchor, and place the attached card in the same frame. A drag
that would cover the padded pointed area changes neither window nor anchor. The behavior remains
limited to GuidingHand's own overlay and never injects an action into the target app.

A pure geometry regression test covers accepted, rejected, absent-target, and empty candidates.
Android Candidate unit tests, lint, and assemble plus all 79 backend tests pass. The Candidate APK
was installed in place and the read-only Xiaomi smoke check passed with no new fatal exception or
idle overlay churn. Because Xiaomi blocks injected input, the actual finger-drag/card-follow motion
remains `TBD` until the user performs it.

## Preference-aware overlay identity — November 1 local time

The duplicate-render guard previously omitted the selected language and Reduce motion preference.
Saving either setting while the guide state was otherwise unchanged could therefore return early,
leaving the Help/action labels or ripple behavior stale until another screen transition. Overlay
render identity now includes both preferences, causing one intentional refresh after Save while
keeping repeated identical Accessibility events idempotent.

A deterministic regression test proves that duplicate events retain the same identity while
language and Reduce motion changes invalidate it independently. Android Candidate tests, lint, and
assemble plus all 79 backend tests pass. The updated Candidate was installed in place and the
read-only Xiaomi smoke check passed without a new fatal exception or idle overlay churn. Manual
visual confirmation of switching each preference remains `TBD` until the user performs it.

## Speech-purpose-aware accessibility action — November 1 local time

The overlay render identity now includes the planner's structured `speechPurpose`. Previously a
purpose-only dialogue update could be suppressed as a duplicate, leaving a screen reader to announce
“Speak your request” when the next action was actually “Dictate the message,” or vice versa. The app
still does not infer purpose from visible question text; the planner-provided enum remains the source
of truth.

A deterministic regression test covers GENERAL → MESSAGE_CONTENT while proving identical purposes
remain idempotent. Android Candidate tests, lint, and assemble plus all 79 backend tests pass. The
updated Candidate was installed in place and the read-only Xiaomi smoke check passed with no new
fatal exception or idle overlay churn. A spoken screen-reader walkthrough remains human evidence and
is not claimed here.

## Honest offline-voice recovery — November 1 local time

Android system TTS remains the only speech-output path and still runs at 0.85 speed with an
installed offline voice. The Vietnamese repeat-failure message previously told the elder to check
a connection, although network access cannot repair a missing offline voice. It now names the
offline voice and asks a family member to check that it is installed. The task and current screen
remain available through the existing recovery state; no cloud speech fallback was added.

A deterministic GuideEngine regression proves that the failure copy names the offline voice and
does not mention a connection. Local verification is **95/95 Android tests**, lint, Candidate
assemble, and **79/79 backend tests**. The Candidate was updated in place, preserving the original
GuidingHand package, and the read-only Xiaomi smoke check passed with no new fatal exception or idle
overlay churn. A person triggering the missing-voice failure remains `TBD`; the smoke check does not
claim that interaction.

## Ephemeral visible-transcript boundary — November 1 local time

The overlay's “Bác nói / You said” line now has explicit RAM-only ownership instead of living only
inside a `TextView`. Its backing characters and visible text are cleared before lock hiding,
password/payment/OTP safety rendering, Stop, and every new task. Overlay suspension for one
safety-checked screenshot does not clear it because capture is not a privacy boundary and the
current task must remain intact.

A deterministic regression covers replacement and clearing of the ephemeral display text. Local
verification is **96/96 Android tests**, lint, Candidate assemble, and **79/79 backend tests**. The
Candidate was updated in place and the read-only Xiaomi smoke check passed without a new fatal
exception or idle overlay churn. A person confirming the lock/unlock and safety-pause visuals remains
`TBD`; no phone interaction is claimed by the smoke check.

## Transcript-row reflow and bounded smoke evidence — November 1 local time

Changing the visible “Bác nói / You said” transcript now advances a content-free in-memory revision
used by the overlay render identity. Showing, replacing, or clearing that row therefore triggers one
card remeasurement, while identical Accessibility events remain idempotent. The identity stores no
transcript text, length, or hash. This preserves the existing scrollable body and fixed footer for
compact screens and large text; human review of card coverage remains `TBD`.

The read-only phone collector no longer compares two complete Logcat buffers by prefix. It writes a
random content-free marker and considers only later lines, so circular-buffer rotation cannot turn
historical bubble events into current churn. It does not clear Logcat, use ADB input, or add the marker
to its report. The corrected collector passed on the connected Xiaomi with zero bounded attach or
detach events and no new fatal exception.

Local verification remains **96/96 Android tests**, lint, Candidate assemble, and **79/79 backend
tests**. The Candidate was updated in place before the bounded smoke check. The original GuidingHand
package remains untouched.
# 2026-11-02 — Candidate 1.5.28

- Dynamic screens now require three matching safe reads before planning. An unstable screen keeps the current goal, shows an automatic waiting state, and never asks the elder to find a missing retry control.
- A stale Spark result returns to automatic settling. Repeated stale results no longer become a manual “Check again” loop.
- The Help bubble is re-laid out after the SAM ring is known. If no safe position exists, Help hides instead of covering the target.
- Backend safety now uses password flags, editable roles, clickable controls, and explicit OTP/payment prompts. Plain articles and ordinary email fields no longer hard-stop from words alone.
- Spark must echo the requested locale. Instructions, questions, and options that conflict with it are rejected before SAM instead of reaching mixed-language UI.
- Planner guidance now forbids using ads, unrelated edge controls, or generic arrows as substitutes for scroll/navigation.
- Verified locally: 82 backend tests; 96 Android unit tests; Android lint and Candidate assemble passed. Candidate 1.5.28/code 37 installed side-by-side; read-only phone smoke passed with Accessibility enabled and no new fatal exception. Elder-driven task behavior remains pending a real interaction test.

# 2026-11-02 — Candidate 1.5.29

- Long batch transcripts now use a 3–7 line editable viewport with a visible vertical scrollbar, large 24sp text, and an explicit scroll instruction.
- Transcript review opens at the newest text while preserving manual up/down scrolling and full editing before confirmation.
- The floating confirmation card also scrolls to the transcript on first render while keeping its fixed actions reachable.
- Android unit tests, lint, and Candidate assemble passed. Candidate 1.5.29/code 38 installed side-by-side; read-only phone smoke passed. Rendered long-transcript interaction remains pending a person-driven check.

# 2026-11-02 — Candidate 1.5.30 compact coach card

- Ordinary reading, planning, recovery, navigation and tap guidance now use one compact card row with a 24sp instruction and a labelled Hand/Hide drag control.
- The separate Help bubble is removed while a card is open, eliminating the duplicated floating control visible in the phone video.
- Waiting steps expose Replay and Stop only. Speak appears only in expanded states that can accept a new answer.
- Clarifications, transcript review, unavailable states and safety pauses keep the full card and all required recovery actions.
- The compact card is 320dp maximum width and does not reserve the previous extra 72dp content height. Text and touch-target accessibility minima are unchanged.
- Android unit tests, lint, and Candidate assemble passed. Candidate 1.5.30/code 39 installed side-by-side; read-only phone smoke passed. Rendered compact-card interaction remains pending a person-driven check.

# 2026-11-02 — Candidate 1.5.31 demo regressions

- Added backend and Android language guards. The saved English preference can no longer display Vietnamese planner copy; wrong-language output fails closed while retaining the task.
- Added a bounded two-frame visual stability gate before upload. Only the final safety-checked frame is shared with Spark and SAM. Visual motion schedules a fresh automatic read without spending a provider call. A real Android capture failure still exposes the existing manual recovery instead of looping forever.
- Long transcript review now shows a localized 20sp scroll instruction while fixed actions remain outside the scrolling body.
- Added the device-resolved default browser as grounded context. Spark may use it only after understanding a web goal and an app-neutral answer.
- Spark prompt now treats dialogs, permission sheets, and ads as temporary obstacles and must continue the bound goal after a real dismiss action.
- Candidate identity: `vn.guidinghand.candidate`, version `1.5.31-candidate`, code `40`.
- Verification: 84 backend tests; 101 Android unit tests; Android lint and Candidate assemble passed. Read-only Xiaomi smoke passed after install and pairing. Safe Candidate home screenshot rendered without clipping.
- Still requires person-driven evidence: selected-language flow, long transcript scroll, Chrome settle/recovery, popup recovery, ring accuracy, and latency.

# 2026-11-02 — Candidate 1.5.32 attached coach

- Reverted the compact-card regression that separated Help from its guide card and left the card looking fixed to the bottom of the display.
- Help remains visible as the single draggable anchor whenever guidance is open. The card is placed beside Help and is laid out again in the same frame while Help moves.
- The card no longer has an independent drag gesture, so the two surfaces cannot drift apart. Compact instructions and the existing large-text/touch-target minima remain unchanged.
- Added a regression API and unit test that require the open card placement to receive the Help bounds and remain directly adjacent without overlap.
- Candidate identity: `vn.guidinghand.candidate`, version `1.5.32-candidate`, code `41`.
- Verification: 84 backend tests from the unchanged backend; 102 Android unit tests, lint, and Candidate assemble passed. Candidate 1.5.32/code 41 was installed side-by-side and passed the read-only Xiaomi smoke check; person-driven verification of dragging Help with the open card remains `TBD`.

# 2026-11-02 — Candidate 1.5.33 compact voice choice

- Removed the optional “You can say…” example from the ready and error states of the voice window to reduce height and reading load.
- The purpose-specific prompt, 72dp microphone action, Type alternative, and Cancel remain. The same row appears only as an essential scroll/edit hint during transcript review.
- Candidate identity: `vn.guidinghand.candidate`, version `1.5.33-candidate`, code `42`.
- Verification: 102 Android unit tests, lint, and Candidate assemble passed. Rendered voice-window height on the Xiaomi remains pending a person-driven open because ADB input injection is blocked.

# 2026-11-02 — Candidate 1.5.34 companion and Home cue

- Added an optional `Hand / Young helper` companion choice in Settings. The fictional young-helper illustration changes only the Help icon; copy states that it remains the GuidingHand assistant and never a real relative. Existing Vietnamese `Cháu` address remains a separate explicit preference.
- Added a structured `navigation_cue` to the Spark contract. `home_swipe_up` is accepted only for an `await` navigation response when the screenshot visibly supports gesture navigation; it is rejected on tap steps and defaults to `none` when uncertain.
- The Android overlay renders a static, non-touchable black/accent upward arrow with the optional hand at the bottom. Help and its card avoid the cue. No gesture is injected, and reduced-motion users receive the same static cue.
- Candidate identity: `vn.guidinghand.candidate`, version `1.5.34-candidate`, code `43`.
- Verification: 85 backend tests; 103 Android unit tests; Android lint and Candidate assemble passed. Candidate installed side-by-side, development backend re-paired, and read-only Xiaomi smoke passed. Settings rendered without clipping at the initial viewport. Person-driven mascot selection and a real Spark Home-gesture task remain `TBD`.

# 2026-11-02 — Candidate 1.5.35 automatic Settings save

- Removed the Settings Save button. Language, address, mascot, accessibility choices, family contact, consent, and development connection values now persist automatically when changed.
- A visible `Changes save automatically / Thay đổi được tự động lưu` status replaces the action button, so saving is not a hidden behavior.
- Model-dependent fields debounce model reload for 450 ms after typing stops. Ordinary visual and language preferences refresh immediately without restarting the current model after every character.
- Candidate identity: `vn.guidinghand.candidate`, version `1.5.35-candidate`, code `44`.
- Verification: 103 Android unit tests, lint, and Candidate assemble passed. Person-driven verification that leaving Settings immediately after a change preserves the selected value remains `TBD`.

# 2026-11-02 — Candidate 1.5.36 concise-flow audit

- Removed the English-only `How the assistant speaks` Settings card. It had no control or recovery action, and the Companion section already provides the required disclosure that GuidingHand never impersonates a real relative.
- Removed the long non-interactive Settings introduction after checking the real Xiaomi render. The visible automatic-save status remains, while the first actual choice now appears sooner.
- The confirmed `You said / Bác nói` line now appears only when it helps resolve a clarification, unsupported request, unreadable screen, or unavailable service. It no longer repeats inside reading, planning, verification, navigation, tap guidance, completion, transcript review, or safety states.
- Removed a fixed 250 ms delay between a completed transcription and transcript review. Session, lock, safety, and stale-result checks still run before the transcript becomes task input.
- Retained the 120 ms Accessibility-event coalescing, bounded native-screen settling, overlay-free capture pause, and 180 ms two-frame visual stability check. Each prevents duplicate work, stale planning, or guidance from a changing screen. None is a decorative delay.
- Retained transcript review, explicit copy consent, Repeat, Stop, Call family, family contact, accessibility choices, and the collapsed technical section. Each provides safety, error recovery, or a user choice requested for older adults.
- Candidate identity: `vn.guidinghand.candidate`, version `1.5.36-candidate`, code `45`.
- Verification: 85 backend tests; 104 Android unit tests; Android lint and Candidate assemble passed. Candidate 1.5.36/code 45 was installed side-by-side, the temporary backend connection was paired again, and the read-only Xiaomi smoke check passed without a new fatal exception or idle overlay churn. A safe Settings capture confirms the first real choice appears earlier. Person-driven card-height and perceived-latency review remains `TBD` until a real interaction.

# 2026-11-02 — Candidate 1.5.37 lifecycle-driven microphone opening

- Removed both fixed 350 ms waits used when starting voice help from a GuidingHand setup screen.
- The main action now delegates directly to the service. The service moves its own setup task to the background and continues as soon as Android reports the real `onPause` lifecycle state.
- A one-second timeout is a failure bound, not an unconditional delay. On failure the app stops with a recovery instruction instead of reading GuidingHand's own window or opening the microphone over Settings.
- Remaining runtime waits were audited. Event coalescing, settled-screen reads, screenshot overlay removal, the two-frame visual-motion gate, and Settings/backend debounce remain because they prevent duplicate provider work, stale frames, or repeated model reloads. VoiceActivity closure still uses bounded state polling before a fresh safety read; it exits as soon as the activity closes and is not an unconditional wait. The transcript-review delay is not retained.
- Candidate identity: `vn.guidinghand.candidate`, version `1.5.37-candidate`, code `46`.

# 2026-11-02 — Candidate 1.5.38 contextual companion cue

- The optional Young helper mascot now has a 5-unit cream silhouette around its dark hair. The previous hair and tile colors were nearly identical; the outline preserves the established dark Help surface while separating the character at the 40dp runtime icon size without relying on color alone.
- When the Young helper is selected, tap guidance now draws a transparent, non-touchable helper beside the double ring with the short localized label `Bấm đây / Tap here`. The helper cue uses zero overlap with the target and the card avoids the full composite bounds. Home navigation uses the same helper beside the existing upward arrow with `Vuốt lên / Swipe up`. The hand cue remains unchanged when the Hand mascot is selected, and the Settings toggle now accurately refers to either guide pointer.
- Candidate identity: `vn.guidinghand.candidate`, version `1.5.38-candidate`, code `47`.
- Verification: 85 backend tests; 106 Android unit tests; Android lint and Candidate assemble passed. Candidate 1.5.38/code 47 was installed side-by-side and its identity, enabled Accessibility service, idle overlay stability, and absence of a new fatal exception passed the read-only Xiaomi smoke check. The phone was locked, so person-driven visual verification of the helper beside a real tap target and the Home swipe cue remains `TBD`.

# 2026-11-02 — Candidate 1.5.39 language-following anchored coach

- Muse Voice Transcribe now omits `languageBias`, using the API's documented automatic language detection and code-switching support. After transcript review begins, a deterministic language policy changes the assistant session to Vietnamese or English only when the transcript contains clear linguistic evidence. Names and app labels keep the existing language.
- The detected language is applied before transcript review, planning, output validation, overlay copy, and offline Android TTS. Settings now describes its language choice as the starting language rather than a permanent lock.
- Removed the secondary accessibility-overlay busy pill. While the focusable microphone window is active, every Help/card/ring overlay stays suspended. After it closes, transcription and planning progress use the single anchored card.
- Help keeps its current valid position across repeated renders. An open card uses a zero-gap shared edge with Help, prefers the space above it, and collapses instead of using a detached bottom fallback.
- Candidate identity: `vn.guidinghand.candidate`, version `1.5.39-candidate`, code `48`.
- Verification: 85 backend tests; 111 Android unit tests; Android lint and Candidate assemble passed. Candidate 1.5.39/code 48 installed side-by-side and passed the read-only Xiaomi identity, Accessibility, idle-overlay-stability, and no-new-fatal-exception checks while the device was awake and unlocked. The updated backend is healthy on the temporary USB path, with Spark and Muse Voice Transcribe capabilities present. A person-driven bilingual speech run is still required to verify the language switch, seamless Help/card edge, and absence of the former secondary `Listening…` pill.

# 2026-11-02 — Candidate 1.5.40 exclusive microphone window lease

- Real-phone logs found two microphone-open requests 429 ms apart. The first window could close after the second appeared and clear the shared static activity flag, allowing the Help overlay to reattach as a small `Listening…` pill below the still-visible microphone window.
- Added a unique service-owned lease for every microphone or transcript-editor window. The lease exists before Android launches the activity, rejects a second open, suppresses every accessibility overlay during opening and display, and ignores a stale close from any older window.
- A 1.5-second lifecycle timeout is only a failure bound when Android never confirms the requested window. It does not delay a successful open.
- Candidate identity: `vn.guidinghand.candidate`, version `1.5.40-candidate`, code `49`.
- Verification: 85 backend tests; 113 Android unit tests; Android lint and Candidate assemble passed. Candidate 1.5.40/code 49 installed side-by-side, re-paired to the healthy USB backend, and passed the read-only Xiaomi identity, Accessibility, idle-overlay-stability, and no-new-fatal-exception checks while unlocked. A person must still open one real microphone window to confirm that no second `voice_open_requested` event or small `Listening…` overlay appears.

# 2026-11-02 — Candidate 1.5.41 stable Help anchor and browser safety context

- Real-phone content-free overlay logs showed the open `ASKING` card moving from top 395 to top 819 across successive inset-animation frames after the microphone window closed. Help now remembers the exact geometry selected by the layout engine while its overlay is temporarily removed; transient view measurement and keyboard/safe-area animation cannot become a new downward anchor. Dragging Help updates the same remembered anchor.
- Chrome no longer becomes sensitive merely because its editable address bar is present while an ordinary page mentions a password or payment. A browser still hard-stops for Android password nodes, a sensitive page input paired with an explicit password/OTP/payment prompt, or an explicit transaction action such as `Pay now`. Android and backend policies use the same rule.
- Candidate identity: `vn.guidinghand.candidate`, version `1.5.41-candidate`, code `50`.
- Verification: 87 backend tests; 116 Android unit tests; Android lint and Candidate assemble passed. Candidate 1.5.41/code 50 installed side-by-side and passed the read-only Xiaomi identity, Accessibility, awake/unlocked, idle-overlay-stability, and no-new-fatal-exception checks. The temporary backend was restarted with the updated policy, reports `muse-spark-1.3` ready, and USB reverse port 8768 is restored. APK SHA256: `fe66937480c837cda7eda9fe543eb764dafa55984d058b3280f7b6e36dac0b06`. Person-driven verification that the card remains attached after one microphone run and that a benign Chrome page no longer pauses remains `TBD`.

# 2026-11-03 — Candidate 1.5.42 compact anchored microphone window

- Real Xiaomi logs showed Help attaching and detaching repeatedly while `VoiceActivity` remained foreground. The microphone lease already prevented a second activity, but an underlying-app Accessibility event could still call `showBubble`. The overlay now receives the service-owned suppression state and refuses every bubble, card, or ring mount while the microphone window is opening or active. The event and screenshot-restoration paths use the same gate.
- The microphone window snapshots Help's last bounds before overlays are removed. It is initially transparent, measured, placed directly beside that anchor, and revealed on the next frame. It no longer renders at a provisional position and then jumps to a bottom-sheet position. A missing anchor uses a stable top-right fallback.
- The sheet width changed from 360dp to 336dp. The 72dp microphone action remains full width; the 48dp `Type / Cancel` secondary actions share one row. Instruction text remains at least 24sp and button labels remain at least 22sp.
- Candidate identity: `vn.guidinghand.candidate`, version `1.5.42-candidate`, code `51`.
- Verification: 87 backend tests; 118 Android unit tests; Android lint and Candidate assemble passed. Candidate 1.5.42/code 51 installed side-by-side and passed the read-only Xiaomi identity, Accessibility, awake/unlocked, idle-overlay-stability, and no-new-fatal-exception checks. APK SHA256: `6c3f72da8308e78645524c97b57b89868e64882816a7bf3588242d3b80473015`. Person-driven verification of the first visible microphone frame, compact balance, and zero overlay churn during a real recording remains `TBD`.

## 2026-11-03 — Reproduced cloud speech blocker in Firestore transaction API

- The pinned google-cloud-firestore 2.21.0 SDK returns an iterator from `Transaction.get(document)`. `FirestoreBudget.transaction` treated it as a DocumentSnapshot and accessed `.exists`, causing AttributeError during budget reservation, before any Meta speech request. The outer speech handler converted this into `speech_unavailable`.
- Reproduced with the real SDK and mocked network RPCs: speech failed with `speech_unavailable` before the fix; after using `DocumentReference.get(transaction=tx)`, speech reserves and settles correctly. Existing ledger and budget-limit rejection remain intact; no real provider or Firestore calls were made.
- Previous explanations blaming parser format, stale revision, or the alternate Cloud Run URL were unproven. The repeated generic error did not establish any of those claims. A successful direct Meta silent-WAV request only verified that particular request, not our budget-backed pipeline.
- Fixed application logging for blocked speech by using WARNING with fixed stage/reason codes. INFO logs had been suppressed by default configuration. No exception bodies, transcripts, recordings or secrets are logged.
- Cloud deployment and a subsequent person-driven speech test remain required. This is a backend-only fix; no new APK is needed. Local workstation has no gcloud command configured, so no cloud deployment is claimed.

## 2026-11-03 — Companion feedback and respectful narration, Candidate 1.5.43/code 52

- User confirmed cloud transcription works after the Firestore fix. This is user-reported real-phone evidence, not an automatically measured full-task pass.
- Added finite companion gestures and distinct poses for ready/listening/thinking/guidance/questions/completion/pause. Reuses the Help icon and a 40dp icon beside microphone status. No extra window or decorative repeating timer. Reduced motion and system-disabled animators suppress motion; removal cancels it.
- Vietnamese model narration now normalizes self-reference to the saved assistant choice (Tôi/Cháu/Con/Trợ lý) and uses the saved elder address in additional system phrases. GuideEngine applies the same wording to card and TTS. Quoted text and protected task labels stay literal; editable transcript is not rewritten.
- Regression checks cover pronoun conversion, idempotence, protected names inside quotes, and existing card/speech agreement. Design contract: docs/companion-interaction-2026-11-03.md.
- ADB currently lists no devices. The APK has not been installed for this change. Real-phone visual/motion, large-text, and anchoring review remain pending; reconnect Xiaomi to install Candidate only. No cloud redeploy is required for this Android change.

## 2026-11-03 — Warm surface finish, Candidate 1.5.44/code 53

- User approved restrained glass styling: Help now has an opaque green gradient with inner/outer highlights. Floating guide and microphone card keep solid cream behind instruction text with light-catching edges. Home/Settings use a soft stationary backdrop, cream surfaces and shallow card elevation.
- No blur/capture, new timer or change to ring geometry, Help/card anchoring, target sizes or typography. Native drawable backgrounds explicitly decline padding changes to preserve existing layout.
- Measured contrast: instruction/card 13.26:1; Help text against its brightest gradient endpoint 8.36:1.
- Xiaomi is disconnected, so Candidate 1.5.44 has not been installed on the phone. Native visual review attempted in a read-only emulator failed because Android System UI entered ANR before showing the app; emulator was stopped without snapshot save. Do not claim visual or phone pass. See docs/warm-surface-design-2026-11-03.md.

## 2026-11-03 — Candidate 1.5.45/code 54 Android regex compatibility fix

- Xiaomi reconnected. Installing 1.5.44 exposed an actual service crash: Android ICU rejects Java's embedded `(?U)` regex flag with PatternSyntaxException during respectful-wording initialization. JVM unit tests had accepted it.
- Replaced the flag and word boundaries with explicit Unicode letter/number boundaries supported by Android. Existing respectful-language tests pass, including Vietnamese accents, idempotence and literal-label preservation. No backend change is required.
- Installed Candidate 1.5.45/code 54 side-by-side on Xiaomi. Read-only phone smoke check passed: Accessibility enabled and not crashed, no new fatal exception, idle overlay stable. Evidence: build/outputs/phone-smoke/phone-smoke-20261103-231408Z.json and safe-screen-20261103-231408Z.png. This verifies installation/service health, not a user-driven speech or target-app task pass. Android unit tests/lint/assemble and 93 backend tests passed.

## 2026-11-03 — Candidate 1.5.46/code 55 custom forms of address

- Added CUSTOM presets for user and assistant, storing each chosen label independently of the preset. Vietnamese Settings shows single-line inputs, live preview and automatic saving. Empty labels fall back to Bác/Tôi; labels are NFC-normalized, bounded to 32 characters and never compiled as regex. Existing preset keys remain compatible.
- The left Home figure follows the chosen preset with changes to hair color/style. Bác/NONE/custom use the neutral existing illustration rather than inferring gender/age from a typed name. The right figure and illustration footprint remain unchanged.
- Home tagline uses the selected labels. Engine narration continues to share wording between guide and TTS, preserving literal task content. New tests cover custom labels, blank fallback and repeated formatting. See docs/custom-address-portrait.md.
- Installed 1.5.46/code 55 on Xiaomi. An initial smoke observation coincided with a microphone-window open (not an idle interval); after opening Candidate Home, read-only smoke passed with no new fatal error, Accessibility connected and stable idle overlay. Evidence: build/outputs/phone-smoke/phone-smoke-20261103-232357Z.json and corresponding safe-screen PNG. Automated checks: 121 Android tests, lint, Candidate assembly and 93 backend tests pass. User-driven custom-label selection and spoken playback still need confirmation.

## 2026-11-03 — Candidate 1.5.47/code 56 role-aware controls

- User screenshots showed “Giúp cháu”, “Bà cần tôi…” and “Bà, nhập chữ ạ.” on action controls. Generic narration rewriting had incorrectly been applied to labels. UI translation now preserves literal button labels; narration is formatted explicitly. Help names its beneficiary via the selected user label, while the microphone question uses both selected roles. Transcript headers format the prefix separately from the transcript.
- Added the automatically saved “Chỉ hiện nhân vật / Character only” option under Companion. Icon-only Help keeps the 72dp touch minimum, drag/click behavior and state-dependent screen-reader description. Render identity includes this preference so toggling it updates the existing anchored card/bubble.
- Replaced the three abrupt Home fragments with one connected sentence at 24sp/32sp line height. Added tests for help label, microphone question, Home sentence and custom-name casing.
- Verification: 122 Android tests, lint and Candidate assembly passed; 93 backend tests passed. Installed Candidate 1.5.47/code 56 on Xiaomi. Accessibility remains enabled/not crashed with no new fatal exception. The phone was asleep/locked during post-install observation, so the smoke report is not an overall phone pass and visual review is pending. Evidence: build/outputs/phone-smoke/phone-smoke-20261104-020108Z.json. No lock bypass or target-app input was used.

## 2026-11-03 — Persistent development and documentation instructions

- Added root AGENTS.md as the entry point for agents and docs/current-development-state.md as the maintained continuation snapshot. Agents must review README after relevant changes, update affected docs/component maps, record installed/deployed state separately from source, and append evidence/blockers/next steps.
- META_DEVELOPMENT.md now links these instructions, reflects 1.5.47/code 56 and current checks, maps new UI/Firestore components, and shows safety before transcription in its diagram. README updated only to expose the agent instructions/current-state links.
- Documentation-only change: validated local links and git diff --check. No new Android build, phone installation, provider call or cloud deployment. Prior code-test results remain attached to the recorded feature build.

## 2026-11-03 — Candidate 1.5.48/code 57 intake cancellation

- Explicit Hủy/Cancel closes recording/input and returns to the Help bubble without the former “Listening window closed” notice card. Local draft is cleared; the task remains available. Safety pause takes precedence, and a new intake resets dismissal so later guidance can be shown normally.
- Typing receives remaining horizontal space instead of half the row; Cancel wraps its label with an 80dp minimum and keeps the 48dp touch-height minimum. Short literal “Gõ chữ” label from 1.5.47 remains.
- README reviewed/updated for source version; META_DEVELOPMENT updated for cancellation behavior. Native Cancel interaction and large-font layout still need user confirmation; deterministic tests do not constitute that evidence.
- Verification: 122 Android tests, lint and Candidate assemble passed; 93 backend tests passed. Installed 1.5.48/code 57 on Xiaomi. Read-only smoke passed with Accessibility connected, no new fatal exception and stable idle overlay; evidence build/outputs/phone-smoke/phone-smoke-20261104-022336Z.json. No user-driven Cancel or typing interaction pass is claimed.

## 2026-11-03 — Candidate 1.5.49/code 58 companion transcript review

- The supplied phone video showed transcript confirmation opening at its final lines. The cause was an explicit `fullScroll(FOCUS_DOWN)` for confirmation states. Reviews now always open at the first line; fixed Use/Edit/Stop actions remain outside the scroll lane.
- The selected companion now appears beside a role-correct review lead such as “Cháu nghe bà nói:”. Android offline TTS at 0.85 reads the complete transcript once and asks for confirmation. This local playback does not call Meta or the backend. Dictated text remains verbatim, and missing TTS does not block visible review.
- Acceptance contract: `docs/transcript-review-companion-2026-11-03.md`. README, META_DEVELOPMENT and current-state snapshot were updated for the public behavior and component contract. Cloud backend was not changed or redeployed.
- Verification: 122 Android tests, Candidate lint and assemble passed; 93 backend tests and deterministic evaluation passed. Installed Candidate 1.5.49/code 58 on Xiaomi. Read-only smoke found the phone awake/unlocked, Accessibility enabled/not crashed, no new fatal exception, and stable idle overlay: `build/outputs/phone-smoke/phone-smoke-20261104-023854Z.json`. A person-driven long-transcript visual and one-time playback check remains required; no interaction pass is claimed.

## 2026-11-04 — Candidate 1.5.50/code 59 short voice retry

- The empty-recording (`no_transcript`) and unknown-failure (`else`) voice notices in `GuidingHandService` now share one short canonical line: “Tôi chưa nghe rõ. Bác nói lại giúp tôi nhé.” / “I didn't catch that. Please say it again for me.” It replaces the misleading “Chưa kết nối được…” network wording the user saw after tapping Chạm để nói without speaking.
- The line flows through the existing `RespectfulLanguage.message` pipeline, so saved addresses render it correctly (Bà/Cháu → “Cháu chưa nghe rõ. Bà nói lại giúp cháu nhé.”); nothing is hardcoded. Specific failures keep their own guidance: session expiry, bad recording, server key, busy, provider failure, and network.
- No state-machine, safety, provider, or model-contract change. Conversion coverage already exists in `RespectfulLanguageTest`; no new maintained test was added for this copy-only change.
- Candidate identity: `vn.guidinghand.candidate`, version `1.5.50-candidate`, code `59`. APK SHA256: `145f305e18ffe5c853fd0fff9659e29e1bb4d0c093a815514a10af0f04153447`.
- Verification: 93 backend tests; 122 Android unit tests (0 failures/errors from test-result XML); Android lint and Candidate assemble passed; deterministic evaluation regenerated; `git diff --check` clean. Installed side-by-side with `adb install -r` via the read-only smoke script; smoke passed (awake/unlocked, Accessibility enabled and not crashed, no new fatal exception, stable idle overlay). Foreground was Instagram, so no safe screenshot was taken: `build/outputs/phone-smoke/phone-smoke-20261104-041151Z.json`.
- Still requires person-driven evidence: tap Chạm để nói, stay silent or trigger a failure, and confirm the short retry line with the saved address. No speech or task pass is claimed.

## 2026-11-04 — Candidate 1.5.51/code 60 safe-group latency

- Event coalescing 120→80ms with the `tryReceive` drain loop kept ([GuidingHandService.kt](E:/Hackathon/GuidingHand/app/src/main/java/vn/guidinghand/GuidingHandService.kt):78). Settings debounce 450→250ms before model reload ([MainActivity.kt](E:/Hackathon/GuidingHand/app/src/main/java/vn/guidinghand/MainActivity.kt):444); auto-save behavior unchanged.
- Backend decodes the ≤1MB step image once per step instead of ~9 times. `validate_snapshot` memos decoded bytes on the per-step frame, keyed by identical image object plus dimensions, so any mutation re-decodes with byte-identical error behavior ([core.py](E:/Hackathon/GuidingHand/backend/core.py):369). The deleted direct `image_info` call in `SamSegmenter.segment` was provably redundant (validated inputs, no interleaving mutation). No signature changed, so planner/segment doubles keep working; no cross-request state, nothing logged.
- Busy cards (READING/PLANNING/VERIFYING/RECOVERING) now show “Đã chờ X giây…” / “Waited Xs…” at 20sp under the instruction. The line is not a live region, so TalkBack stays silent on ticks; the timer resets per busy phase and stops on any terminal path, and layout stays in one card with text-only updates ([GuideOverlay.kt](E:/Hackathon/GuidingHand/app/src/main/java/vn/guidinghand/GuideOverlay.kt), [BusyElapsed.kt](E:/Hackathon/GuidingHand/app/src/main/java/vn/guidinghand/BusyElapsed.kt)).
- Debugging note: the first cache attempt threaded a dict through `plan`/`segment` signatures and broke two duck-typed planner doubles plus miscounted its own test (fixture decode counted inside the patch). Root-caused via stack-trace probe, then replaced with the frame-keyed memo and fixed the test to build fixtures outside the patch. No provider, gate, or contract change at any point.
- Candidate identity: `vn.guidinghand.candidate`, version `1.5.51-candidate`, code `60`. APK SHA256: `7342f066707a9a5df4aac539d862a36b6e20d15dc7bf98814f47cb902188a8dc`.
- Verification: 96 backend tests (3 new decode-cache tests); 125 Android unit tests (3 new `BusyElapsedTest`, 0 failures/errors from test-result XML); Android lint and Candidate assemble passed; deterministic evaluation regenerated; `git diff --check` clean. Installed side-by-side with `adb install -r` via the read-only smoke script; smoke passed (awake/unlocked, Accessibility enabled and not crashed, no new fatal exception, stable idle overlay). Foreground was Instagram, so no safe screenshot was taken: `build/outputs/phone-smoke/phone-smoke-20261104-045751Z.json`.
- Still requires person-driven evidence: run one guided step and confirm the elapsed line counts up and disappears when guidance arrives, without extra TalkBack announcements. No latency, ring-accuracy, or usability pass is claimed.

## 2026-11-04 — Candidate 1.5.52/code 61 USB loopback endpoint

- `FamilyPreferences.resolveBackendUrl` honors a stored `http://127.0.0.1` loopback URL on every flavor only together with the explicit dev-USB-bridge flag. Without the flag, a Candidate build ignores a stale loopback pairing (left by an earlier cloud session) and uses the cloud default instead of failing with a 401 “phiên hết hạn”. Cloud default behavior is unchanged.
- Coverage: `BackendUrlResolveTest` (6 tests: empty/stale-loopback/cloud/exact-loopback cases). No safety, state-machine, provider, or model-contract change.
- Verification: backend suite and Android suite green at the time; Candidate installed side-by-side and USB re-paired (`url=http://127.0.0.1:8768`, `bridge=true`). No interaction pass claimed.

## 2026-11-04 — Candidate 1.5.53/code 62 video-bug-fix sprint (Batch A+B)

- Verification timeout: `VERIFY_TIMEOUT_MS=15s` + `shouldConcludeVerify`; `GuideEngine.onTap`/`onScreen` conclude a stuck VERIFYING with an explicit ask naming the retry. Clock injected via `nowElapsed` (default `monotimeMs`, unit-test safe); pure logic covered by `VerifyTimeoutTest` (3 tests). The first attempt called `SystemClock` directly and broke 12 engine tests (not-mocked); fixed without touching test expectations.
- Single confirmation: `submitEditedVoiceText` now calls `confirmVoiceDraft` directly — the in-activity tap is the confirmation; the overlay second ask is removed. Safety preserved: exact reviewed text only, no auto-send.
- Transcribe busy copy sets a time expectation (“typically a few seconds”); review field gets `imeHintLocales` so the keyboard opens in Vietnamese; review header prefixes dictation with the saved address in quotes without rewriting words (`RespectfulLanguage.reviewLead/reviewPrompt` + tests).
- One silent capture retry after 300ms before the safe-screenshot notice. Spark prompt gains one explicit-delegation sentence (backend `test_prompt_delegation.py`); no other model-contract change.
- Single confirmation, timeout ask, and delegation answers were shown to the user as acceptance criteria before implementation (single confirm, no silent >15s wait, one named app on delegation).
- Candidate identity: `vn.guidinghand.candidate`, version `1.5.53-candidate`, code `62`. APK SHA256: `02A904BD90665FD99A63C4BAF88764774CCC787BCF2B1D9DF5C992C526E10022`.
- Verification: 97 backend tests; 134 Android unit tests (0 failures/errors from test-result XML); Android lint and Candidate assemble passed; deterministic evaluation regenerated; `git diff --check` clean. Installed side-by-side via the read-only smoke script; smoke passed (awake/unlocked, Accessibility enabled and not crashed, no new fatal exception, stable idle overlay) with safe screenshot: `build/outputs/phone-smoke/phone-smoke-20261104-063000Z.json`. USB re-paired for measurement.
- README reviewed; no public-facing fact changed (product-level description does not document these flows at copy granularity). Demo script added: `docs/demo-script-3-diem-bat-buoc.md`.
- Still requires person-driven evidence: single-tap confirm, 15s timeout ask, delegation naming, ring/card/overlap visuals from the 3 user videos (no code fix claimed for visual items without video), and the 20-step USB latency run (median/p95 per stage).

## 2026-11-04 — Candidate 1.5.54/code 63 minimal reasoning and animated-screen freeze

- Muse Spark 1.3 requests in the production core and temporary development gateway now use
  `reasoning_effort=minimal` instead of `low`. Muse remains the sole planner. No provider call was
  made, so speed and quality changes remain `TBD`; Cloud Run is not deployed by a source commit.
- The bounded capture gate now freezes its second safety-checked frame when pixels move but the app,
  window, actionable Accessibility tree, and interaction generation remain bound to the expected
  screen. Click, selection, text edit, scroll, window/tree change, lock, or sensitive state invalidates
  the frame. Spark and SAM still receive identical bytes, and live-node checks still reject stale AI
  results. Contract: `docs/animated-screen-capture-2026-11-04.md`.
- Verification: 97 backend tests; 134 Android unit tests with zero failures/errors; Candidate lint and
  assemble passed; deterministic evaluation regenerated; `git diff --check` clean. Candidate 1.5.54
  was installed side-by-side on Xiaomi and its read-only smoke passed (awake/unlocked, Accessibility
  enabled and not crashed, no new fatal exception, stable idle overlay):
  `build/outputs/phone-smoke/phone-smoke-20261104-071256Z.json`. APK SHA256:
  `F7F58A1000DE3A03591443A643B4D3654DEDEEC33300C3F67F7E0A87F94E0116`. Cloud is not deployed.
  Real animated-screen behavior and latency are TBD.
- README and META_DEVELOPMENT were updated for the public/current architecture. Unrelated untracked
  `output/` and the existing transcript-review contract were preserved.

## 2026-11-04 — Candidate 1.5.55/code 64 return to Cloud transport

- Added a nonce-authenticated, debug-only `--cloud` path to `backend/pair_phone.py`. It removes the
  saved loopback URL, disables the USB bridge and reloads the model without clearing the app-session
  token, language, consent, family setup, or accessibility preferences. It never reads or transfers
  the Meta provider key.
- Verification: 97 backend tests; 134 Android unit tests with zero failures/errors; Candidate lint and
  assemble passed. Candidate 1.5.55 was installed side-by-side and the read-only phone smoke passed:
  `build/outputs/phone-smoke/phone-smoke-20261104-073405Z.json`.
- The Xiaomi now resolves the default HTTPS Cloud Run URL and `devUsbBridge=false`. An authenticated
  capabilities request returned HTTP 401 because the retained value was the temporary local gateway
  token, not the Cloud app-session token. Cloud guidance remains blocked until the user replaces that
  field in Settings. No token value was printed or committed; no phone interaction pass is claimed.

## 2026-11-04 — Candidate 1.5.56/code 65 no-swipe transcript review

- The supplied phone video showed a long transcript inside a very short scroll lane. Review now
  divides long text into short numbered pages with fixed Back/Next controls. It starts at page 1,
  never auto-advances, and exposes Use this only on the final page. Edit and Stop stay available.
- Page size responds to system font scale by creating more pages, not smaller text. Paging changes
  presentation only; the task draft remains unchanged. The separate editor now opens with its caret
  and viewport at the first character rather than deliberately jumping to the end.
- Acceptance contract: `docs/transcript-review-companion-2026-11-03.md`. README and
  `META_DEVELOPMENT.md` were updated for the current public behavior and component contract.
- Deterministic verification: 97 backend tests; 136 Android unit tests; Candidate lint and assemble
  passed; deterministic evaluation regenerated; `git diff --check` clean. Candidate 1.5.56/code 65
  was installed side-by-side and its read-only smoke passed while Xiaomi Home was foreground:
  `build/outputs/phone-smoke/phone-smoke-20261104-084948Z.json`. APK SHA256:
  `C8C704C6F1043D07A54A67184A2C9E1D7B30AADB78F1EFDFFCB6B1C08562E550`.
  A person-driven long-transcript paging/readability check remains pending; no usability or
  interaction pass is claimed.

## 2026-11-04 — README purpose clarification

- README now explains GuidingHand's two horizons: immediate one-step help and longer-term confidence
  and phone-use intuition, while preserving the rule that the person performs every action.
- No app code, version, model behavior, or evidence claim changed. README reviewed and updated.

## 2026-11-04 — Candidate 1.5.57/code 66 same-package motion capture

- The phone screenshot showed a harmless animated Meta screen being reported as an unsafe capture.
  During display-capture fallback, an active overlapping window from the same target package is now
  allowed; a different package still blocks capture. This preserves the safety gate while avoiding
  false stops from an app's own popup/WebView window.
- Snapshot-unavailable copy now explains that motion is the reason and that the elder does not need
  to tap. Sensitive screens and unreadable trees remain hard-stopped.
- Candidate 1.5.57/code 66 was installed side-by-side and read-only smoke passed while Instagram was
  foreground: `build/outputs/phone-smoke/phone-smoke-20261104-094659Z.json`. APK SHA256:
  `5693421547197DC57E93CDA09020B58C965F0CD2F8E9DB9B8C33883A4336D207`.
  Person-driven motion capture verification remains pending; no safety or interaction pass is claimed.

## 2026-11-04 — Candidate 1.5.58/code 67 distinct screen-block explanations

- The user saw “màn hình chưa an toàn” on an ordinary Meta screen. The model boundary used one
  generic phrase for locked, sensitive, unreadable, and sensitive-goal states. It now explains the
  actual reason: privacy pause for password/OTP/payment, unlock required, or controls not readable.
  No safety gate was relaxed; the change only prevents a misleading warning.
- Candidate 1.5.58/code 67 was installed side-by-side and read-only smoke passed while Messages was
  foreground: `build/outputs/phone-smoke/phone-smoke-20261104-095745Z.json`. APK SHA256:
  `D3CAD61923D58330A89E8F24BC5E44BCED59EAED2F725A18BEBDBA4C72A02107`.
  Person-driven screen-block verification remains pending; no safety or interaction pass is claimed.

## 2026-11-04 — Candidate 1.5.59/code 68 same-app window stability

- Instagram could recreate an internal Accessibility window without any elder interaction. The
  strict planning fingerprint still retains that window ID for state verification, while a separate
  capture fingerprint ignores only same-package window recreation when package, actionable geometry,
  safety state, and explicit interaction generation remain unchanged.
- Added a JVM regression test proving same-package window recreation stays stable while a click or
  navigation-generation change invalidates the planning frame. Candidate 1.5.59/code 68 is built in
  source; read-only Candidate installation passed in `build/outputs/phone-smoke/phone-smoke-20261104-101548Z.json`.
  APK SHA256 is `372B4DBD966BE228C6C7B8C590E0F5E6745249960B9227328C629056BC9837C5`.
  Person-driven timing verification remains pending; no interaction pass is claimed.
