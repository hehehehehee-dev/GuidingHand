# Animated-screen capture contract

## Problem

Video, advertisements, carousels, and other decorative motion can change screenshot pixels while the
elder does nothing. The earlier two-frame gate rejected every such frame. Guidance then remained in
the reading state even when the actionable Accessibility controls were unchanged.

## Approved behavior

1. The native lock and safety gate run before every capture and provider call.
2. GuidingHand reads the current Accessibility tree before capture.
3. It takes at most two bounded frames back-to-back, with no fixed settling delay. The second frame
   is the only image sent to providers.
4. If both sampled frames are visually stable, use the second frame as before.
5. If pixels move but the safe app, window, actionable tree, and interaction generation remain bound
   to the expected screen, freeze and use the second frame.
6. A click, selection, text edit, scroll, window change, unsafe state, or actionable-tree change makes
   the capture stale. Do not upload it.
7. Spark and SAM receive the exact same frozen bytes. Native node and region fusion is still required.
8. Re-read the live tree before upload and after the provider result. Discard stale results while
   keeping the current goal.

This is not continuous capture. It does not infer that motion is harmless from pixels alone. The
stable Accessibility binding and absence of a new interaction are required before moving motion can
be frozen.

## Acceptance checks

- A stable screen continues to use the second frame.
- Background pixel motion with an unchanged safe screen returns a frozen second frame.
- A size mismatch, unsafe screen, interaction, window change, or actionable-tree change is rejected.
- Provider payloads use `reasoning_effort=minimal` for Muse Spark 1.3.
- Sensitive-screen behavior, stale-result rejection, same-image Spark/SAM binding, and elder-operated
  target-app actions remain unchanged.
- Real-phone behavior and latency remain `TBD` until the user performs an animated-screen flow.
