# Transcript review as a companion conversation

## Problem

The transcript review card can be taller than its visible text lane. It currently opens at the
bottom of the scroll area, so the beginning of the sentence is hidden. An older adult must infer
that the text can scroll before they can review what GuidingHand heard.

## Approved outcome

- Open every transcript review at the first line. Never jump to the newest or last line.
- Keep **Use this / Edit this / Stop** outside the scrolling text lane and visible.
- Present the review as the fictional assistant speaking: show the selected companion beside a
  role-correct line such as **“Cháu nghe bà nói:”**.
- Read the complete review once with Android offline TTS at `0.85` speed, ending with a short
  confirmation question. This is local speech output, not a Meta/provider call.
- Preserve the dictated transcript verbatim. Respectful-language rewriting may affect only the
  surrounding assistant sentence.
- A missing offline voice must not block review. The visible transcript and fixed actions remain.
- Do not add automatic tapping, typing, scrolling in the target app, confirmation, or sending.

## Compact-screen contract

The transcript may still scroll when 24sp text cannot fit safely. Scrolling becomes an optional
way to inspect the text because the assistant reads the complete sentence. The first line and the
primary decision are visible without requiring a gesture. Large text keeps the fixed footer and may
reduce only the transcript viewport, never the text size or target size.

## Verification

1. A long Vietnamese transcript initially shows its beginning, not its ending.
2. The selected assistant and user forms of address appear in the review sentence.
3. The complete transcript is spoken once and a repeated render does not replay it.
4. English mode remains English.
5. Confirm, edit, cancel/stop, safety pause, and device lock stop review speech.
6. Android unit tests, Candidate lint, Candidate assembly, and a read-only phone smoke check pass.
7. Actual readability and speech timing remain pending a person-driven phone test.
