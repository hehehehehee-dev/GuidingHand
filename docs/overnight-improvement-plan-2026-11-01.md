# GuidingHand overnight improvement plan — 2026-11-01

## Objective

Increase hackathon readiness without adding fragile demo-only features. Improve the real elder experience, measured reliability, and evidence while preserving the product rules: safety before models, Spark/SAM as the production core, one step at a time, and no target-app automation.

## Evidence and priorities

- Historical phone trace: screen read median 26 ms; transcription median 1.9 s; backend/Spark median about 4.7 s and P95 about 8.1 s.
- Recent phone videos: duplicate/transient busy surfaces, flicker at the bottom of the screen, clipped transcript actions, and weak feedback during long provider waits.
- Existing strengths to preserve: RAM-only transcript review, explicit correction and confirmation, exact-screen decision cache, stale-result rejection, safety suite, target fusion, non-touchable ring, and strict elder-performs-actions policy.

## Build preferences

- Build mode: autonomous, as explicitly requested by the participant.
- Verification pauses: none while the participant sleeps; do not install or claim phone success without real device evidence.
- Git: one focused commit per verified improvement; push `main` after the full relevant test gate passes.
- Provider spend: no repeated paid fixtures; prefer deterministic tests and existing traces.

## Sequenced checklist

- [x] **1. Stabilize busy-state rendering**
  What to build: Make identical overlay states idempotent so repeated Accessibility events cannot rebuild or blink the card/bubble.
  Acceptance: Re-rendering the same state performs no window/content rebuild; hide/unlock and changed content still render.
  Verify: Android unit tests, lint, assemble; inspect overlay render-key logic.

- [x] **2. Replace misleading retry during active work**
  What to build: Add a dedicated non-actionable working presentation for transcription/planning. Keep only Stop visible while an in-flight operation owns the session.
  Acceptance: “Understanding what you said…” never shows Check again; no duplicate provider request can be triggered from the busy card.
  Verify: deterministic state test plus Android build/lint.

- [x] **3. Make waiting understandable for an older adult**
  What to build: Use short stage-specific copy (listening, transcribing, reading, finding) and a stable visual progress cue that respects Reduce motion and never flashes.
  Acceptance: one sentence, >=24sp, no animation when Reduce motion is enabled, no color-only meaning.
  Verify: static UI inspection and Android accessibility lint.

- [x] **4. Audit and prevent duplicate model work**
  What to build: Confirm exact-screen cache and settled-screen gating cover repeated events; add trace reason for avoided work where evidence is missing.
  Acceptance: identical screen+goal+dialogue cannot start two simultaneous model calls; changed screen still invalidates old work.
  Verify: concurrency/cache unit tests and privacy-safe trace assertions.

- [x] **5. Improve perceived-latency evidence**
  What to build: Extend the evidence report with time-to-first-feedback and cache-hit/duplicate-work counters using content-free trace events.
  Acceptance: no screen text, transcript, goal, recipient, token, image, or audio enters traces; missing phone evidence remains TBD.
  Verify: evidence generator tests and a fixture-only report.

- [x] **6. Run elder UX regression audit**
  What to build: A deterministic checklist for transcript correction, card sizes, wrong-tap recovery, sensitive stop, language consistency, and busy-state behavior.
  Acceptance: each item names observable pass/fail evidence and never treats simulation as phone proof.
  Verify: generated checklist reviewed against current product rules.

- [x] **7. Full verification and candidate handoff**
  What to build: Run backend tests, Android unit tests, lint, assemble, diff check, and secret scan; update the concise handoff.
  Acceptance: all gates pass; APK identity remains Candidate; no key or provider content is committed.
  Verify: command outputs and GitHub CI after push.

- [x] **8. Add bounded real-phone smoke evidence**
  What to build: Add a reusable read-only phone check for Candidate identity, lock/power state, Accessibility health, crashes, and idle overlay churn.
  Acceptance: never inject input or claim a user interaction; never record screen text, speech, goals, recipients, credentials, provider content, or device serial; screenshot capture is opt-in and limited to Home/Candidate.
  Verify: run against the connected Xiaomi and preserve only the content-free JSON result under the ignored build directory.

- [x] **9. Keep the guide card attached during a safe Help drag**
  Evidence: the bubble drag path updates the bubble window inside the safe area but records its new anchor and repositions the card only in the rejected target-overlap branch. This can leave the card behind or save an invalid anchor.
  What to build: accept a drag only when it stays clear of the pointed area; on an accepted Help drag, persist the Help bounds and re-layout the attached card in the same frame. A rejected drag changes neither anchor.
  Acceptance: accepted Help drag updates both bubble anchor and card placement; target-overlapping drag is rejected; dragging GuidingHand never injects an action into the target app.
  Verify: pure geometry tests for accepted/rejected candidates, Android unit tests, lint, assemble, and manual phone status remains `TBD` until the user drags it.

- [x] **10. Apply language and Reduce motion immediately to an unchanged overlay**
  Evidence: the idempotent render identity omits language and Reduce motion. Saving either preference while the guide state is unchanged can return early, leaving the Help label/action labels in the old language or old ripple behavior until another state transition.
  What to build: include both preferences in the render identity so a preference save performs exactly one intentional rebuild while duplicate Accessibility events remain no-ops.
  Acceptance: changing language refreshes Help and fixed action labels even in IDLE; changing Reduce motion rebuilds action backgrounds without waiting for a new guide step; unchanged renders remain idempotent.
  Verify: Android Candidate unit tests, lint, assemble, read-only phone smoke, and manual visual confirmation remains `TBD` until the user changes each setting.

- [x] **11. Keep the Speak action meaningful for screen readers across dialogue turns**
  Evidence: the overlay's duplicate-render identity omits `speechPurpose`, even though that field changes the Speak button description between a general request and dictating message content. A purpose-only planner update can therefore leave assistive technology announcing the wrong action.
  What to build: include structured speech purpose in the content-free render identity; do not infer purpose from question text.
  Acceptance: identical purpose remains idempotent; GENERAL → MESSAGE_CONTENT triggers one refresh and announces the message-dictation action; no transcript or question text enters diagnostic traces.
  Verify: deterministic identity regression test, Android Candidate unit tests, lint, assemble, and read-only phone smoke.

- [x] **12. Give an accurate recovery step when offline speech output is unavailable**
  Evidence: Android system TTS is deliberately offline, but the Vietnamese repeat-failure copy tells the elder to check a connection. That recovery cannot fix a missing offline voice and conflicts with the English copy.
  What to build: replace the misleading connection instruction with one short Vietnamese sentence that names the installed offline voice and asks a family member for setup help. Keep the task and on-screen state intact.
  Acceptance: no offline-TTS failure message asks the elder to check a network connection; Vietnamese and English both identify the offline voice; the unavailable state still exposes the existing recovery actions and never falls back to cloud speech.
  Verify: deterministic GuideEngine copy regression, Android Candidate unit tests, lint, assemble, diff/secret checks, and read-only phone smoke. Rendered elder interaction remains `TBD` until a person triggers the failure.

## Deferred deliberately

- Cloud deployment and paid live benchmarks: final phase, after behavior stabilizes.
- New task-specific scripts: they would inflate scope without proving general understanding.
- Automatic tap/type/swipe/send/call: prohibited by the product safety model.
- Invented latency or usability claims: remain TBD until measured on the current phone build.

## Verified outcome

- Commit: `04204b5` on `main`.
- Local: 79 backend tests passed; Android unit tests, lint, and Candidate assemble passed; diff and secret checks passed.
- GitHub Actions: run `41286612609` passed the backend, Android, and container jobs.
- Phone status: Candidate was updated after explicit user authorization. The read-only smoke test passed package, unlocked/awake, Accessibility, crash, and idle-overlay checks. User interaction, speech, ring accuracy, and target-app latency remain `TBD` until a person performs the flow.

