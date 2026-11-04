# Current development state

Maintain this as a concise snapshot, not a second chronological changelog. Update affected fields after meaningful work; dated details belong in the continuation handoff.

## Snapshot — 2026-11-04, America/Indianapolis (source Candidate 1.5.69/code 78)

| Area | Last established fact |
| --- | --- |
| Branch | main; preserve unrelated untracked output/ and user screenshot |
| Source | 1.5.69, version code 78; Help overlay events no longer hide Help, and a latched safety pause restores its controls after a fresh safe external screen read. The pause stays latched. Wrong-tap replay and photo Send guards remain as in 1.5.68. The Precise ring (SAM) setting defaults on; node bounds are an estimated ring only for nonconsequential actions when it is off. |
| Installed Xiaomi Candidate | vn.guidinghand.candidate, 1.5.69/code 78; read-only installation smoke passed at 2026-11-04 17:39:47 UTC. WindowManager showed one visible Accessibility overlay on Xiaomi Home; content-free trace showed a Help attach with no subsequent detach during the observation. User-driven Help and safety-pause return remain unverified. |
| Original phone app | vn.guidinghand remains separate; do not replace/uninstall it |
| Backend | Cloud Run with Firestore; the user reported deploying commit 0b065a2. The new SAM switch, next-step clarification rule, and compact Spark app list are source only until a new Cloud Run deployment. |
| Latest deterministic verification | Candidate 1.5.69: 107 backend tests and 148 Android unit tests, lint, assemble and deterministic evaluation passed. No phone interaction pass is claimed. |
| Latest phone observation | On 1.5.68, Help was absent on unlocked Xiaomi Home despite enabled Accessibility. Trace showed a latched safety pause and Help attach/detach pairs ~150 ms apart. After installing 1.5.69, WindowManager showed a visible Help overlay on Home with no detach in the read-only sample. No elder tap or return-from-sensitive-screen pass yet. |
| Phone evidence | Local 2026-11-04 17:39:47 UTC phone smoke report, WindowManager dump and content-free GuideTrace (generated output may not exist in a fresh clone) |
| Public demo, measured ring accuracy/latency, older-adult usability | TBD; do not turn developer feedback into measured claims |

The 2026-11-04 Instagram conversation trace shows a successful 736×1600 snapshot followed by about 7.1 s in Spark and no SAM invocation for the first `cannot_read` result. This identifies Spark uncertainty, not an Android screenshot failure, for that specific screen. The new source distinguishes it from SAM ambiguity and asks one spoken clarification instead of looping on Check again. This behavior still needs a real-phone interaction check after installation and Cloud Run redeployment.

After the user redeployed that backend and retried, the real-phone trace advanced through photo selection. Two later 736×1600 captures returned Spark/SAM targets; the native general-app text-content guard rejected a media Send step as `message_content`, and an exact-screen cache hit repeated it. A user screenshot shows a single white-duck photo selected, “Quang Minh” in the conversation title, and Send at the lower right. A read-only node dump confirmed that Instagram represented the selected image as a checked `CheckBox`, with a separate preview `ImageView`; Candidate 1.5.64 still rejected this because Spark did not fill the photo slot. Candidate 1.5.65 checks the actual native media selection and recipient title without requiring typed text or a model photo description. It does not prove delivery or automate Send. The user-driven post-install result is pending.

## Latest behavior

- Settings has a Precise ring (SAM) switch, default on. When off, the app sends `allow_sam=false` and the exact-screen cache is keyed per setting; the backend skips SAM and returns the vetted node bounds as `region_source=accessibility-node`. Consequential steps (send, call, payment, delete, confirm) still require SAM or stop.
- A matched gallery click plus a native checked/selected event and newly visible bottom preview can confirm selection when a later Accessibility tree omits the tile's selected flag. A click by itself never suffices; subsequent interaction or a changed/unsafe window clears the receipt. The next target still needs its normal model and ring checks; delivery remains separately verified.
- After an observed wrong tap, the engine keeps the prior safe navigation step for up to 30 seconds. If the elder navigates back to the same complete native screen and unchanged task, it rebinds the old ring to the live node and uses local TTS without a new Spark/SAM request. Changed window, node, task, language, safety state, or consequential target uses normal recovery instead.
- With a fixed task and no new spoken update, Spark receives only the bound app from the validated installed-app list. A newly spoken task update still receives the full list. A failed optional second screenshot is backed off for 30 seconds on the same window; the first frame is safety-checked before use. Help/card placement excludes visible Send/Next-type controls as well as the ring.

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

- Transcript review starts at the first words and divides long text into short numbered pages with
  large Back/Next controls. It never advances on a timer, and confirmation appears only on the final
  page. Edit and Stop stay available; the editor opens at the first character. System font scaling
  creates more pages rather than smaller text. The selected companion introduces the transcript and
  Android offline TTS still reads the full sentence once; task text is not rewritten.
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

Have the user record a long request. Verify page 1 begins with the first words, Next/Back expose the
whole sentence without a swipe, no page advances by itself, and Đúng rồi appears only on the last
page. Confirm the young helper appears beside “Cháu nghe bà nói”, TTS reads the complete transcript
exactly once, and Sửa câu này opens at the first character.
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
