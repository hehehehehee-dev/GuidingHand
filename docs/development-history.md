# Development history

This document summarizes earlier architecture decisions. It is historical context, not a description of every current UI choice. See the root README and actual code for current behavior.

## Core redesign decisions

The redesign retained Accessibility tree reads, event-driven observation, lock-screen suppression, person-operated target-app actions, family preferences, and task state across wrong taps. Zalo photo guidance retained conversation-title recipient checks and strict new-photo evidence.

It removed launcher swipe-for-me, automatic target-app input, default app launching, and parallel planner shortcuts. An explicit launcher-only Open for me action remained. General model-planned guidance later replaced the earlier Zalo-only task restriction.

MockModel was retained as an explicit development stand-in behind one model interface. Production uses Spark and SAM through the backend. Muse Voice Transcribe handles speech input; Android offline TTS handles output. Earlier plans referring to Muse Voice as mandatory speech synthesis did not match the available API and were corrected.

The guide uses one safety-checked screenshot for Spark and SAM. The engine retains goals and dialogue, verifies screen changes, and replans from unexpected screens. Safety pauses latch until explicit recovery. Unreadable content and sensitive content are separate conditions.

## Subsequent product refinements

- Help became a movable anchor for the guide card rather than a separate handle above a fixed bottom panel.
- Editable transcript review, user-selected card sizes, vision profiles, respectful forms of address, and an optional fictional young helper were added.
- The backend was deployed on Cloud Run with a persistent Firestore budget ledger. A Firestore SDK iterator/snapshot mismatch blocked requests before Meta; the fix added regression coverage against the real SDK with mocked network calls. The developer subsequently confirmed real-phone cloud transcription.
- Companion poses and restrained light-catching surfaces were added in source builds 1.5.43 and 1.5.44. Those latest visual changes still require real-phone review.

Dated implementation details, verification limits, and continuation notes are recorded in [the handoff](continuation-handoff-2026-10-28.md).
