# Phase 1 development archive

Historical local matching, mixed task experiments, hand-pointer UI and launcher swipes live here for reference only. These archived files are outside Android source sets. The current Phase 1 `MockModel` is restored under `app/src/debug`, behind the same model interface and selected once, never a parallel fast path. Current speech output is Android system offline TTS at 0.85; Muse Voice Transcribe is input only.

The production implementation is under `app/src/main` and `backend/core.py`. Archived tests describe historical behavior; they are not evidence for the new core loop.
