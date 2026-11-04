# Current development state

Maintain this as a concise snapshot, not a second chronological changelog. Update affected fields after meaningful work; dated details belong in the continuation handoff.

## Snapshot — 2026-11-03, America/Indianapolis

| Area | Last established fact |
| --- | --- |
| Branch | main; preserve unrelated work (an untracked output/ directory existed at handoff) |
| Source | 1.5.47, version code 56; feature commit 3da2d1c |
| Installed Xiaomi Candidate | vn.guidinghand.candidate, 1.5.47/code 56 |
| Original phone app | vn.guidinghand remains separate; do not replace/uninstall it |
| Backend | Cloud Run with Firestore; cloud transcription confirmed by the user after the Firestore fix cd9febf. Current cloud revision/hash has not been freshly queried for this snapshot. A local commit/push does not deploy Cloud Run. |
| Latest deterministic verification | 122 Android unit tests, Candidate lint and assemble; 93 backend tests passed for the 1.5.47 change |
| Latest phone observation | Candidate installed, Accessibility enabled and not crashed, no new fatal exception; phone asleep/locked, so the overall smoke report was not a pass |
| Phone evidence | build/outputs/phone-smoke/phone-smoke-20261104-020108Z.json (local generated output, may not exist in a fresh clone) |
| Public demo, measured ring accuracy/latency, older-adult usability | TBD; do not turn developer feedback into measured claims |

## Latest behavior

- Help label names the person receiving help, e.g. “Giúp bà”. The microphone question uses both selected roles, e.g. “Bà cần cháu giúp gì ạ?”.
- Action labels remain literal: “Gõ chữ”, “Hủy”, “Tiếp tục”. Never run those through narration rewriting.
- Companion settings can hide the bubble text with “Chỉ hiện nhân vật / Character only”; retain accessible descriptions and a 72dp target.
- Custom Vietnamese user/assistant forms of address auto-save and preview. Preserve dictated content and protected names. Custom labels do not imply age or gender.
- Home has one connected sentence instead of three abrupt fragments. The left figure follows the preset address; arbitrary custom labels keep a neutral portrait.
- Cream guide surfaces remain opaque; Help has restrained light-catching green surfaces. The card stays attached to Help.

## Next concrete verification

When Xiaomi is unlocked, have the user choose Bà/Cháu, tap Help, and verify “Giúp bà”, the microphone question, and short action labels. Toggle Character only and drag Help with the card open. Review Home sentence wrapping, custom address persistence and spoken output. Record observations for this installed build separately from earlier screenshots.

Do not bypass the screen lock or Xiaomi's INJECT_EVENTS restriction. If disconnected or locked, continue useful bounded code/docs work and leave phone verification pending.

## Known regressions to avoid

- Java's embedded regex flag (?U) passed JVM tests but crashed Android ICU. Use the existing explicit Unicode boundaries; phone evidence must cover Android runtime behavior.
- Firestore Transaction.get(document) returns an iterator in SDK 2.21.0. Use DocumentReference.get(transaction=tx) for a snapshot. Retain the real-SDK regression tests.
- Generic speech errors can hide budget-storage errors before a Meta call. Check fixed stage/reason codes before changing providers or parser formats.
- A saved backend URL can override a changed default. Verify endpoint state and deployment independently; the project's alternate Cloud Run hostnames alone do not establish a stale deployment.

## Handoff fields for future changes

Record: purpose and acceptance criteria; affected components; source version/commit; installed package/version or “not installed”; cloud deployment/revision or “not deployed/unknown”; exact checks and evidence type/path; unresolved blocker; next action; README reviewed/updated and other documents updated. Do not guess identifiers or results.
