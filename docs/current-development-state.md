# Current development state

Maintain this as a concise snapshot, not a second chronological changelog. Update affected fields after meaningful work; dated details belong in the continuation handoff.

## Snapshot — 2026-11-04, America/Indianapolis (source Candidate 1.5.55/code 64)

| Area | Last established fact |
| --- | --- |
| Branch | main; preserve unrelated work (an untracked output/ directory existed at handoff) |
| Source | 1.5.55, version code 64; adds an authenticated debug-only switch from the temporary USB transport to the default Cloud URL without clearing user setup |
| Installed Xiaomi Candidate | vn.guidinghand.candidate, 1.5.55/code 64 |
| Original phone app | vn.guidinghand remains separate; do not replace/uninstall it |
| Backend | Cloud Run with Firestore; cloud transcription confirmed by the user after the Firestore fix cd9febf. Minimal reasoning is changed in source but not deployed to Cloud Run. A local commit/push does not deploy Cloud Run. |
| Latest deterministic verification | 134 Android unit tests (0 failures/errors from test-result XML), Candidate lint and assemble; 97 backend tests passed for 1.5.55 |
| Latest phone observation | Candidate 1.5.55 installed; read-only smoke passed. The stored loopback URL was removed and USB bridge disabled, so the effective URL is the default Cloud Run service. The retained local gateway token received HTTP 401 from Cloud and must be replaced with the Cloud app-session token before cloud guidance can work. No interaction pass claimed. |
| Phone evidence | build/outputs/phone-smoke/phone-smoke-20261104-073405Z.json (local generated output, may not exist in a fresh clone) |
| Public demo, measured ring accuracy/latency, older-adult usability | TBD; do not turn developer feedback into measured claims |

## Latest behavior

- An authenticated debug action can leave the temporary USB bridge and restore the default Cloud Run
  URL while preserving language, accessibility preferences, consent, family setup, and the saved
  app-session token. It does not convert a local token into a Cloud token; Cloud correctly returns 401
  until the Cloud app-session token is entered in Settings.

- Muse Spark 1.3 requests use `reasoning_effort=minimal`. Provider quality and latency on this build
  remain unmeasured until the backend is deployed or an explicitly authorized live check runs.
- The bounded two-frame capture no longer rejects a screen only because video, an advertisement, or
  a carousel changes pixels. When the screen remains safe and the app, window, actionable tree, and
  interaction generation are unchanged, the second frame is frozen and shared byte-for-byte with
  Spark and SAM. A new click, selection, edit, scroll, window/tree change, or unsafe state still makes
  the capture stale. See `docs/animated-screen-capture-2026-11-04.md`.

- Transcript review now starts at the first line instead of jumping to the bottom. The selected
  companion introduces the transcript with the saved roles and Android offline TTS reads the full
  sentence once. Fixed review actions remain visible; dictated words are not rewritten.
- Cancel in the intake returns to Help without a notice card, clears the local draft and preserves task state. Safety pause takes precedence. A new intake permits normal guidance again.
- Typing gets flexible row width; Cancel uses its label width with an 80dp minimum and 48dp minimum touch height.

- Help label names the person receiving help, e.g. “Giúp bà”. The microphone question uses both selected roles, e.g. “Bà cần cháu giúp gì ạ?”.
- Action labels remain literal: “Gõ chữ”, “Hủy”, “Tiếp tục”. Never run those through narration rewriting.
- Companion settings can hide the bubble text with “Chỉ hiện nhân vật / Character only”; retain accessible descriptions and a 72dp target.
- Custom Vietnamese user/assistant forms of address auto-save and preview. Preserve dictated content and protected names. Custom labels do not imply age or gender.
- Home has one connected sentence instead of three abrupt fragments. The left figure follows the preset address; arbitrary custom labels keep a neutral portrait.
- An empty recording and an unknown speech failure now show one short retry line (“Tôi chưa nghe rõ. Bác nói lại giúp tôi nhé.” / “I didn't catch that. Please say it again for me.”). It passes through the saved forms of address, so Bà/Cháu settings render “Cháu chưa nghe rõ. Bà nói lại giúp cháu nhé.” Specific failures (network, server key, busy, session expiry) keep their own guidance.
- Safe-group latency (no safety-invariant change): event coalescing 120→80ms with the drain loop kept; Settings debounce 450→250ms; the backend decodes the ≤1MB step image once per step (frame-keyed memo, same errors on any mutation, never shared across requests or logged); busy cards show a non-announced “Đã chờ X giây…” line at 20sp that resets per phase and stops when busy ends.
- Cream guide surfaces remain opaque; Help has restrained light-catching green surfaces. The card stays attached to Help.
- Single voice confirmation: the in-activity tap on the review action confirms the exact text; the overlay no longer asks a second time. The transcribe busy line sets a time expectation and the review field opens a Vietnamese keyboard (`imeHintLocales`).
- One silent capture retry after 300ms precedes the safe-screenshot notice, so a transient failure does not cost a full settle cycle. The review header prefixes dictated words with the saved address (“Bác vừa nói: …”) without rewriting them.
- VERIFYING past 15s without a screen change ends with an explicit ask naming the retry. The Spark prompt now handles explicit delegation: when the elder accepts any app, it chooses exactly one suitable installed option and states it, still correctable.
- Demo script covering the 3 mandatory scenarios (wrong-tap recovery, hard stop, pre-send check) lives at `docs/demo-script-3-diem-bat-buoc.md`; all three are existing mechanisms, undemonstrated on video so far.

## Next concrete verification

Tap Chạm để nói, stay silent or force a failure, and confirm the short retry line appears with the saved forms of address. Then have the user record a long request. Verify that the visible review begins with the first words, the
young helper appears beside “Cháu nghe bà nói”, and local TTS reads the complete transcript exactly
once before the user chooses an action.
For 1.5.53 also confirm: one tap confirms (no second overlay ask); a 15s-stuck verification asks again instead of looping silently; an “any app is fine” answer yields one named app. Then run the user-driven 20-step USB latency measurement and record median/p95 per stage. Then test Help → Cancel, the wider typing action, large fonts,
Bà/Cháu wording, Character only, card dragging, Home wrapping and custom address persistence. Record
observations for this installed build separately from earlier screenshots.

Do not bypass the screen lock or Xiaomi's INJECT_EVENTS restriction. If disconnected or locked, continue useful bounded code/docs work and leave phone verification pending.

## Known regressions to avoid

- Java's embedded regex flag (?U) passed JVM tests but crashed Android ICU. Use the existing explicit Unicode boundaries; phone evidence must cover Android runtime behavior.
- Firestore Transaction.get(document) returns an iterator in SDK 2.21.0. Use DocumentReference.get(transaction=tx) for a snapshot. Retain the real-SDK regression tests.
- Generic speech errors can hide budget-storage errors before a Meta call. Check fixed stage/reason codes before changing providers or parser formats.
- A saved backend URL can override a changed default. Verify endpoint state and deployment independently; the project's alternate Cloud Run hostnames alone do not establish a stale deployment.

## Handoff fields for future changes

Record: purpose and acceptance criteria; affected components; source version/commit; installed package/version or “not installed”; cloud deployment/revision or “not deployed/unknown”; exact checks and evidence type/path; unresolved blocker; next action; README reviewed/updated and other documents updated. Do not guess identifiers or results.
