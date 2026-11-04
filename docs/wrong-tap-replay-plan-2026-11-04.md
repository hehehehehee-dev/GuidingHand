# Wrong-tap step replay

## Purpose

When the elder taps a different control and returns to the same safe screen, replay the last
nonconsequential instruction instead of spending another Spark/SAM request. Guidance on the wrong
screen can still use the normal planner to find a safe way back; Back is never automated.

## Acceptance criteria

1. Save a replay candidate only after an observed wrong tap and a meaningful screen transition.
   It expires after 30 seconds and is cleared by Stop, a new goal, spoken update, safety pause,
   task change. A recovery step on the wrong screen may remain visible while the elder navigates
   back; a successful replay consumes the candidate once.
2. Replay only when package, window, visible node details, target identity, target bounds, task,
   and user intent match the earlier screen. Ignore only the interaction counter. Run the native
   safety gate and live-node validation again before presenting the ring.
3. Never replay send, call, post, purchase, delete, permission, or confirmation controls, or a
   selected-media target. No target-app action is performed by GuidingHand.
4. A match shows the prior instruction and ring with a new screen binding and Android offline TTS,
   without a Spark/SAM call. A mismatch follows normal recovery. A stale or rejected replay is
   discarded rather than looped.
5. Deterministic tests cover a matching return, changed target, changed window, changed task,
   consequential step and expiry. Measure any real speed claim separately on the phone.
