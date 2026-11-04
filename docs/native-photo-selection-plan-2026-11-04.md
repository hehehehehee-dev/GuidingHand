# Native photo-selection evidence

## Purpose

An elder may tap the highlighted Instagram image and see a small selection mark that is hard
for the assistant to read. Use the Android selection event as additional native evidence so
GuidingHand can proceed without asking the elder to repeat an already successful selection.

## Acceptance criteria

1. A matched click alone never proves selection. The source node must report selected/checked
   after the event, and the following safe Accessibility tree must show a new bottom preview.
2. Bind this evidence to the same package, window, and target. A later click, edit, scroll,
   window change, deselection, missing preview, or unsafe screen discards it; ordinary duplicate
   selection events must not invalidate an otherwise current receipt.
3. Use this only for selecting media in a general app. The Send step still requires a verified
   conversation recipient, a real Send node, and the normal Spark/SAM target checks; the elder
   performs the send. Do not treat a tap as proof of delivery.
4. Existing selected-node evidence keeps working. Add deterministic tests for positive and
   negative cases; run backend tests, Candidate Android unit tests, lint, assemble and diff checks.
5. Record this as a reliability fallback, not a measured latency improvement or a real-phone pass.
