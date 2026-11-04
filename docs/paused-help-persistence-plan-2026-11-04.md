# Keep paused Help visible during repeated sensitive events

## Evidence

Candidate 1.5.69 briefly attaches Help, then detaches it on an external app. Content-free trace records a fresh `SENSITIVE` screen and repeated `SAFETY_PAUSED` states. The service hides the overlay on every sensitive Accessibility event, while the engine intentionally emits the safety pause only once. Later sensitive events therefore leave no paused controls visible.

## Acceptance criteria

- The first sensitive event stops active work and clears guidance before any provider call, then shows only the paused Call family / Stop controls.
- Later sensitive events do not detach those paused controls. No ring, capture, transcription, model call or automatic action resumes while paused.
- Lock screen, own setup UI and microphone window still hide the overlay. A fresh safe external screen may restore paused controls, without clearing the pause.
- Verify the Candidate with deterministic gates, install only the side-by-side package, and observe the overlay on the real phone for longer than the previous brief sample. Do not claim a user interaction pass from a read-only observation.
