# GuidingHand elder UX regression checklist

Use the current Candidate on a real phone. Record the whole screen. Mark **TBD** until a person reviews the evidence; deterministic tests are not older-adult usability evidence.

## One short end-to-end run

1. Start in Chrome or another ordinary app and tap **Help**.
2. Say a request containing an app and a person name, but make the relation ambiguous once (for example, wanting to talk “about” Minh).
3. Confirm that GuidingHand asks one understanding question instead of guessing.
4. At transcript review, choose **Edit this**, correct one word, then choose **Use edited text** and **Use this**.
5. Follow one ring, intentionally tap the wrong nearby control once, and verify the goal is retained from the new screen.
6. Open a password/OTP/payment screen without entering any private value and verify the ring clears and only **Call family** / **Stop** remain. Do not place a call.

## Observable pass criteria

- Help starts from the app already in use; GuidingHand does not require opening its home screen.
- No second translucent Listening/Looking bubble appears under the mic/editor.
- Busy copy is stable and shows only Stop; no flashing retry button and no duplicate request trigger.
- Transcript, Use this, Edit this, and Stop are fully visible or reachable with an obvious scroll; no button is a clipped sliver.
- The selected card size persists after Save and after relaunch.
- English mode contains English guidance chrome and model output; Vietnamese proper names remain unchanged.
- The ring is one non-touchable black/yellow double border with an optional hand, and does not blink on unrelated screen events.
- Wrong tap recovery continues from the actual new state; it does not restart from Home.
- Sensitive stop happens before any provider guidance, clears the ring, and never auto-calls.
- The elder performs every target-app tap, swipe, type, paste, send, and confirmation.

## Evidence to capture

- Candidate package/version from the phone.
- One continuous video and its date.
- Privacy-safe trace file and SHA-256.
- Per-step latency report, including `goal_to_first_guidance` when available.
- Human-reviewed notes for ring correctness and panel coverage. Do not infer these from a DONE state.

