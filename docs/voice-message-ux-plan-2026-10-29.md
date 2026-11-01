# Voice message UX plan — October 29, 2026

Status: **Candidate 1.5.20 installed; elder-initiated visual check pending**. The separate
tap-to-start/tap-to-stop mic sheet, batch transcript review and typed fallback are covered by
deterministic tests/build validation. The private mic Activity has not yet been opened by the
elder on the installed phone build.

## Product outcome

An older adult can say the content they want to write without opening GuidingHand or
typing it. GuidingHand transcribes the completed utterance, shows a large readable
draft, and asks for one explicit confirmation. The elder still performs every action
inside the target app, including paste and Send.

Muse Voice Transcribe is batch speech-to-text, not live streaming. The UI must say
"Đang nghe" while recording and reveal the words after the elder stops speaking. It
must never animate fake live words.

## Non-negotiable interaction rules

- The planner declares a structured speech purpose (`general`, `recipient`, or
  `message_content`). Android must never infer this from question keywords.
- GuidingHand never taps, types, pastes, sends, confirms, calls, or swipes inside the
  target app.
- A message draft stays in memory only. It is never written to logs, traces, analytics,
  screenshots, filenames, or task reports.
- Copying a confirmed draft is an explicit elder action, not an automatic side effect.
  Paste and Send remain elder actions in the target app.
- Stop, lock, a sensitive screen, or starting a new goal clears the in-memory draft.
- Password, payment, OTP, PIN, or CVV content hard-stops before transcription or copy.
- One screen presents one decision. Do not show Confirm, Copy, Retry, Repeat, Stop, and
  launcher actions together.

## Proposed elder flow

1. **Ask for content**
   - The planner asks one short question and returns `speech_purpose=message_content`.
   - The card's single primary action becomes **Nói nội dung / Dictate message**.
   - Repeat and Stop remain secondary actions.

2. **Record**
   - The separate mic sheet says **Đang nghe… / Listening…** and identifies the purpose:
     **Nói nội dung tin nhắn / Say the message**.
   - The only actions are **Đã nói xong** and **Hủy**.
   - No screenshot or screen-tree request occurs while the mic window owns focus.

3. **Review the transcript**
   - After recording stops, Muse returns one transcript and the mic window closes.
   - A solid guide card shows **Bác muốn viết:** followed by the draft in quotation marks.
   - The primary action is **Đúng rồi / Use this**.
   - The secondary action is **Nói lại / Record again**; Stop remains available.
   - The assistant does not read the private draft aloud automatically. Repeat reads the
     question, not the message, unless the elder explicitly requests playback later.

4. **Prepare the draft for the target app**
   - After **Đúng rồi**, a second single-decision card explains that the message will be
     copied temporarily and asks for **Sao chép nội dung / Copy message**.
   - Copy happens only on that explicit tap. The card confirms with text and an icon;
     color alone is never the signal.
   - The clipboard is cleared on Stop and after a short timeout only if it still contains
     GuidingHand's exact draft. The timeout is a design requirement; its final duration is
     TBD after phone testing.

5. **Guide paste and send**
   - Spark and SAM guide the elder to the real message field, then to the actual Paste
     affordance if it is visible and readable.
   - GuidingHand waits for the elder to paste and then points to Send only after verifying
     the correct conversation title and the presence of the expected draft in the composer
     when Android exposes it.
   - If Android cannot expose the composer text or Paste control, stop and ask; never guess.
   - GuidingHand never performs Paste or Send.

6. **Recover**
   - If the elder moves to another screen, preserve the confirmed draft in RAM and replan
     from the new safe state.
   - If the elder selects the wrong conversation, keep the goal and draft, but do not point
     to Send until the intended recipient is verified in the conversation title.

## Visual direction

The experience should feel like a calm family note, not a developer console or chatbot.

- Keep the existing cream card (`#FFFCF5`) and dark green text (`#18332A`). Their measured
  contrast is 13.258:1, above the 7:1 instruction-text requirement.
- Keep the black/yellow non-touchable ring exclusively for the target. Do not reuse bright
  yellow for ordinary buttons; this preserves its meaning as “look here.”
- Instruction and transcript: at least 24sp. Button labels: at least 22sp. Supporting text:
  at least 20sp. Primary target: at least 72dp; secondary targets: at least 48dp.
- Card remains solid and at most 42% of the safe screen height unless accessibility minima
  require more. It must avoid the ring, keyboard, cutout, and system bars and remain
  collapsible.
- Show at most one filled dark-green primary button. Secondary actions use cream with a
  dark outline. Avoid red for correction and never rely on color alone.
- Transcript uses a quiet quotation treatment and generous line spacing, not a chat bubble
  stack. Maximum visible height is four large-text lines; longer text scrolls inside the
  content area while actions remain fixed.
- Use a microphone icon plus a text label, never an unlabeled icon. Motion is limited to a
  slow listening pulse that also has the text **Đang nghe**.
- Tone is respectful and warm: “Bác muốn viết…” / “Would you like to use this message?”
  The assistant never claims to be a child or grandchild.

## Current design assessment

### What already fits older adults

- Typography, target sizes, solid card, measured contrast, offline 0.85-speed speech, and
  the fixed footer are appropriate foundations.
- The card can avoid the pointed region and collapse when space is insufficient.
- The ring has two high-contrast borders and does not intercept touches.
- The elder controls every target-app action, and sensitive screens latch a hard stop.

### What does not yet fit

- **Speak** is generic; it does not tell the elder whether they are stating a goal, a name,
  or message content.
- The current mic sheet gives no persistent purpose label, so the elder can forget what to
  say after the guide card disappears.
- A transcript is treated as an immediate planner answer. There is no large-text review or
  correction step, which is unsafe for names and message content.
- The visual system is accessible but still utilitarian: too many similarly weighted
  rectangular controls can make the card feel clunky.
- Long instructions and option buttons can make the card tall. Message review would worsen
  this unless the content scrolls while actions remain fixed.
- There is no private-draft lifecycle or clipboard cleanup yet.

### Design verdict

**Not ready to build unchanged.** The concept is appropriate for older adults only with a
separate review step, one-primary-action hierarchy, explicit copy consent, private-draft
lifecycle, and a persistent purpose label in the mic sheet. With those changes, the flow
is clearer than asking the elder to type and remains consistent across apps.

## Build checklist

- [x] **1. Lock the state contract**
  What to build: Define structured speech purpose and a private draft state without UI.
  Acceptance: No keyword inference; draft is RAM-only and cleared by Stop, lock, sensitive
  pause, and new goal.
  Verify: Unit tests for every state transition and cleanup path.
  Evidence: `VoiceDraftStateTest` covers structured purpose, review/copy transitions,
  replacement, cleanup and sensitive/invalid drafts. Service clears the draft on Stop,
  lock/screen-off, sensitive pause, new goal and service destruction.

- [x] **2. Prototype the three card states**
  What to build: Static previews for Ask, Review, and Copy-consent states using real font
  scale and screen dimensions.
  Acceptance: One filled primary action, no target overlap, no clipped text at font scales
  1.0, 1.3, and 1.5.
  Verify: Rendered screenshots and panel-coverage report; human visual review before wiring.
  Evidence: the interactive `Quiet note` prototype covers Ask, Listening, Review,
  Copy-consent and Paste guidance. Automated browser inspection confirms every primary
  action remains inside the card at simulated font scales 1.0, 1.3 and 1.5; large-text
  mode may exceed the 42% cap because accessibility minima take priority. The ring is
  absent during speech/review and appears only for the target-app step.

- [x] **3. Add structured planner output**
  What to build: Extend the Spark/backend contract with a required speech-purpose enum.
  Acceptance: Invalid or missing purpose stops safely; message purpose is not inferred from
  localized copy.
  Verify: Backend contract tests; no provider call required.
  Evidence: the strict Spark schema requires `general`, `recipient`, or
  `message_content`; missing/unknown values fail closed. Backend tests pass 34/34, and
  native engine tests verify the purpose reaches the view state without copy inference.

- [ ] **4. Make the mic purpose explicit**
  What to build: Pass the structured purpose to the separate mic sheet and change its title
  and button copy.
  Acceptance: Screen capture remains suspended while the mic is visible; no background
  recording; English and Vietnamese never mix.
  Verify: Android lifecycle tests plus manual mic-window inspection.
  Implementation evidence: the structured purpose is passed to the separate mic activity;
  Vietnamese and English use separate purpose-specific copy, and no screen request is made
  while the mic activity owns focus. Manual phone inspection is still pending.

- [ ] **5. Add transcript review**
  What to build: Show the transcript in a large-text confirmation card with Use this,
  Record again, and Stop.
  Acceptance: No planner step runs before explicit confirmation; Record again replaces,
  rather than appends to, the draft.
  Verify: Engine/service tests with stale ASR result, screen change, lock, and Stop.
  Implementation evidence: a completed `message_content` transcript enters an in-memory
  Review state and cannot reach the planner until **Use this** is tapped. **Record again**
  replaces the draft. Lock, Stop, sensitive pause, a new goal, and service destruction clear
  it. Device lifecycle observation is still pending.

- [ ] **6. Add explicit temporary copy**
  What to build: Copy only after a dedicated elder tap and clear only GuidingHand's unchanged
  clipboard entry on Stop/timeout.
  Acceptance: No automatic paste or target-app input; draft never appears in logs.
  Verify: Clipboard ownership and cleanup tests on supported Android versions.
  Implementation evidence: copy is available only after review confirmation and a second
  explicit tap. Deterministic tests verify timeout/Stop cleanup clears only an unchanged
  GuidingHand-owned clipboard value. Android-version phone verification is still pending.

- [ ] **7. Guide composer, Paste, and Send**
  What to build: Let Spark/SAM point at real exposed controls while native validation gates
  recipient title and Send.
  Acceptance: Wrong conversation cannot expose Send guidance; unreadable Paste stops and
  asks instead of guessing.
  Verify: Deterministic screen fixtures, then real-phone observation without sending content.
  Implementation evidence: deterministic verification now requires both the intended
  recipient in the conversation title and the exact expected draft exposed in the composer
  before Send guidance is eligible. Real Spark/SAM and phone observation remain pending.

- [ ] **8. Visual and accessibility QA**
  What to build: Refine spacing, quotation treatment, button hierarchy, and listening pulse.
  Acceptance: Contrast stays at least 7:1; required text/target minima hold; panel coverage of
  the pointed area is zero.
  Verify: Automated geometry/contrast checks and screenshots at three font scales in both
  languages.
  Static evidence: the Quiet note prototype passes action containment at simulated font
  scales 1.0, 1.3 and 1.5. Runtime uses one filled primary action, an outlined correction
  action and a dedicated 24sp quotation surface. Runtime phone screenshots in both languages
  are still pending.

- [ ] **9. Candidate build and safe phone test**
  What to build: Versioned side-by-side Candidate; never replace the original app.
  Acceptance: Dictate, review, record again, copy consent, wrong-screen recovery, safety
  stop, and app-switch continuity are observed. No real message is sent.
  Verify: Phone evidence log with per-step latency marked TBD until measured.

- [ ] **10. Decide whether to ship the feature**
  What to build: Compare observed confusion, panel coverage, recognition corrections, and
  privacy behavior against this plan.
  Acceptance: Ship only if the elder always knows what to say, can correct the transcript,
  and never mistakes Copy for Send.
  Verify: Written go/no-go decision; no invented success rate.

## Overnight execution rule

Work may proceed autonomously in checklist order after this plan exists. Do not skip the
static visual checkpoint, and do not install a phone build until deterministic tests,
build, lint, and the visual review pass. If the phone remains disconnected, stop at the
phone-test boundary and leave an honest handoff rather than simulating success.
