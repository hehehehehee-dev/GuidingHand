# Demo regression plan — 2026-11-02

## Goal

Fix the failures visible in the 2026-11-02 phone demo without adding app-specific keyword scripts or target-app automation. The elder keeps the goal while temporary dialogs, ads, and wrong taps change the screen.

## Evidence

- The saved preference was English, but several Spark questions and steps appeared in Vietnamese.
- The long transcript review showed its lower portion, with no clear instruction that the text could scroll.
- Chrome changed while the planner was working. The trace recorded nine stale results, including three completed backend calls discarded after upload.
- Spark asked repeated app-choice questions instead of using the device's default browser when the elder had no preference.
- A wrong tap opened a context menu and recovery worked, but a popup and ad pulled the guidance away from the original search goal.

## Checklist and acceptance criteria

- [x] **Lock output to the selected language at both boundaries.**
  Backend and Android reject mixed-language model text. Proper names and short app labels remain valid.
  Acceptance: an English preference cannot display a Vietnamese planner sentence, even if the backend returns one.

- [x] **Wait for a visually stable screen before a paid model call.**
  Use two bounded captures, then send only the final safety-checked image to both Spark and SAM.
  Acceptance: a changing screen schedules another automatic read without showing a manual retry or spending a model call.

- [x] **Make long transcript review obvious and readable.**
  Keep the fixed actions visible and show a localized scroll hint only for a long transcript.
  Acceptance: the elder sees how to read the full transcript before confirming it.

- [x] **Supply the default browser as device context.**
  Spark may choose it after the elder says any browser is acceptable. The app does not infer intent from keywords.
  Acceptance: the prompt has a grounded default browser package and tells Spark not to repeat the same app question.

- [x] **Treat popups and ads as temporary obstacles.**
  Spark may point to a real dismiss control, then must continue the bound goal from the new screen.
  Acceptance: an obstruction never replaces the goal or becomes proof that the task is complete.

## Verification

- Backend deterministic tests.
- Android Candidate unit tests, lint, and assemble with `-PcandidateBuild=true`.
- Diff and secret checks before commit.
- Install only `vn.guidinghand.candidate`, then run the read-only phone smoke check.
- Real speech, Chrome recovery, ring accuracy, and latency remain `TBD` until a person performs the flow.

## Verified implementation result

- Backend: 84 deterministic tests passed without provider calls.
- Android: 101 Candidate unit tests, lint, and assemble passed.
- Phone: Candidate `1.5.31-candidate` / code `40` installed side-by-side. Read-only package, unlock, Accessibility, crash, and idle-overlay checks passed.
- Static phone capture: the Candidate home screen rendered without clipping at the current device font scale.
- User-driven English-only output, long transcript scrolling, Chrome visual-settle behavior, popup recovery, ring accuracy, and end-to-end latency remain `TBD` until the next real run.
