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

