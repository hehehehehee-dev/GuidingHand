# Transcript review as a companion conversation

## Problem

The transcript review card can be taller than its visible text lane. It currently opens at the
bottom of the scroll area, so the beginning of the sentence is hidden. An older adult must infer
that the text can scroll before they can review what GuidingHand heard.

## Approved outcome

- Open every transcript review at the first line. Never jump to the newest or last line.
- Split a long transcript into short, numbered pages. The elder uses large **Back / Next**
  controls; reviewing the transcript must not require discovering a swipe gesture.
- Never advance a transcript page automatically. Show **Use this** only on the final page so a
  partial reading cannot be mistaken for the complete sentence.
- Keep **Back / Next / Use this / Edit this / Stop** outside the transcript lane and visible.
- Present the review as the fictional assistant speaking: show the selected companion beside a
  role-correct line such as **“Cháu nghe bà nói:”**.
- Read the complete review once with Android offline TTS at `0.85` speed, ending with a short
  confirmation question. This is local speech output, not a Meta/provider call.
- Preserve the dictated transcript verbatim. Respectful-language rewriting may affect only the
  surrounding assistant sentence.
- A missing offline voice must not block review. The visible transcript and fixed actions remain.
- Do not add automatic tapping, typing, scrolling in the target app, confirmation, or sending.

## Compact-screen contract

Short transcripts use one page. Long transcripts use as many short pages as needed, with no
ellipsis and no hidden review text. Page size adapts to system font scale and each page starts at its
first line. The complete dictated value remains unchanged in task state; paging is presentation only.
Large text may create more pages, never smaller text or targets. The separate editor may scroll for
correction, but it must open with its caret and viewport at the beginning rather than at the end.

Instruction text remains at least 24sp, button labels at least 22sp, primary targets at least 72dp,
and secondary targets at least 48dp. A page never changes on a timer or because TTS finishes.

## Verification

1. A short transcript is one page; a long Vietnamese or English transcript opens on page 1.
2. Back/Next preserve word order, expose every word, and never auto-advance.
3. Use this appears only on the final page; Edit and Stop remain available.
4. The selected assistant and user forms of address appear in the review sentence.
5. The complete transcript is spoken once and a repeated render does not replay it.
6. Editing opens at the first character and returns the edited value for explicit confirmation.
7. Confirm, edit, cancel/stop, safety pause, and device lock stop review speech.
8. Android unit tests, Candidate lint, Candidate assembly, and a read-only phone smoke check pass.
9. Actual readability and speech timing remain pending a person-driven phone test.
