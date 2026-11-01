# Core-loop contract

GuidingHand 1.2 has one executable task in this redesign: `send_photo_zalo`. Goal understanding can remain general without expanding that execution allowlist.

Everyday entry is voice-first: tap the floating bubble or card microphone, speak in a small sheet over the current app, then submit the bounded recording. Muse Voice Transcribe runs through the authenticated backend; its transcript becomes the original goal or a clarification answer. Questions are spoken with Android offline TTS and can be answered from the same microphone. Keyboard entry is an optional alternative, not a required step. Microphone access and audio sharing require explicit on-device consent; prior screen-sharing consent alone does not authorize audio upload.

## Goal understanding

Spark receives the original goal, the actual safe screen and prior clarification answers. It may ask what the elder means, which mode of an activity is intended, or which recipient or item is missing. Alternatives are returned as data, not hardcoded phrase branches.

A request for another task can be clarified but must then be reported as unsupported. It is never converted into sending a photo. No additional chat, calling or other workflow is enabled merely because Spark can understand it.

## One session, one interface

The engine retains the original goal, answers, recipient and photo slots, verified step IDs and bounded transition observations. Each observation distinguishes a real highlighted-target tap from an unrelated tap and records a screen change without treating that change as success.

Wrong-tap recovery discards the stale target and reads the resulting state. It preserves the task and verified progress. Recovery must not call a new-task reset.

The production model interface accepts screen plus goal/session data and returns one typed decision. It does not expose tap, type, send, confirm, arbitrary intent or gesture tools.

For the November 4 build, the service selects one GuideModel implementation for the session: the production backend model, or the clearly marked Phase 1 MockModel for development. It never calls MockModel first and Spark second as parallel fast paths. Mock regions may use node bounds for development only and must not claim SAM provenance.

| Stage | Input | Accepted output |
| --- | --- | --- |
| Screen read | Accessibility events | Complete live tree |
| Safety gate | Tree, device visibility and freshness | Proceed or latched pause/stop |
| GuideEngine | Safe state and retained session | Verified progress and next-step request |
| Muse Voice Transcribe 1.0 | Safe, consented speech input through the backend | Goal or clarification transcript; no synthesized speech |
| Muse Spark | Screen facts, goal, answers and task progress | One Vietnamese step and actual target choice, or clarification/abstention |
| SAM 3.1 | Selected target and on-demand image of that same safe screen | One segmentation region with source and frame provenance |
| Android system TTS | The short Vietnamese instruction | Offline Vietnamese speech at rate 0.85 |
| Presentation | Validated step and production SAM region, or explicitly marked development region | One non-touchable ring and one bottom card |
| Verification | Before/after screen states and elder tap | Evidence-based progress, waiting or recovery |

Accessibility bounds may seed production segmentation but cannot create a ring without a valid SAM result. When a real control touches a display edge, native code may constrain the visible ring to that exact clickable node only after the SAM region overlaps it by at least 50%; the raw SAM region and provenance are retained. Development MockModel regions are separate, explicitly labelled node-bound regions, never SAM results. A successful production Spark response without valid SAM grounding is not a complete guide step. Speech output uses Android system TTS and does not depend on a Muse Voice synthesis API.

## Backend exchange

The app calls `POST /core/step` through the project's own HTTPS endpoint using app-session authentication. The request carries request ID, screen fingerprint, goal and answers, safe current screen facts, optional photo-task slots and verified step IDs. Consent, unlocked-device and complete-tree checks precede any provider call.

The first request contains no screenshot. A `needs_image` response may return a short-lived plan token and the chosen target. The app rechecks safety and screen freshness before capture and submits that same plan token with the image. The backend rejects stale, replayed, mismatched or uncertain plans.

| Response | Meaning |
| --- | --- |
| `clarify` | One Vietnamese question and clarification alternatives |
| `unsupported` or `blocked` | Explanation; no target region |
| `needs_image` | Capture for the SAM stage; no usable production ring yet |
| `step` | One instruction, actual target identity and SAM 3.1 region bound to the request and screen; Android TTS speaks locally |

All provider credentials remain server-side. An app-session token authenticates the client to this backend; it is not a Meta provider key. No deployed production URL is claimed.

## Evidence and recovery

Every transition requires complete, safe before/after screens and an actual diff. Elapsed time, a model's completion suggestion and the elder's acknowledgement cannot by themselves verify progress.

| Photo stage | Evidence requirement |
| --- | --- |
| Launcher | The foreground package becomes Zalo; seeing its icon alone is insufficient |
| Recipient | The intended recipient is evidenced in the current conversation context |
| Attachment | The state changes to an appropriate attachment/photo-selection screen |
| Photo | The intended photo identity is selected |
| Send | Recipient and selected-photo checks still hold before guiding the elder's tap |
| Delivery | New outgoing-photo evidence and new delivery evidence appear; gallery closure alone is insufficient |

When available facts cannot prove a transition, stop and ask. A wrong tap uses the new screen as a recovery input while keeping prior verified evidence. The task includes recovery as data; it does not restart a canned sequence from Home.

## Safety and platform actions

The safety gate runs before every model call, capture request and speech output. Password, payment or OTP evidence latches a pause, clears the ring and stops audio. Unknown or incomplete state stops and asks. A later safe-looking frame does not resume automatically.

Repeat rechecks the current safe screen and speaks the accepted instruction with the same Android system offline TTS voice at rate 0.85. TTS is the output implementation, not a fallback for Muse Voice. Stop and lock invalidate pending plans, segmentation, ring and speech. An asynchronous response is accepted only for the still-current safe screen.

Only two explicit platform actions are permitted:

1. **Mở giúp tôi:** on the launcher step only, after the elder presses it and fresh safe-launcher checks pass, open Zalo's real launcher activity. Verify the resulting foreground package. This exception does not apply inside Zalo.
2. **Call family:** after the elder selects it from a paused state, open `ACTION_DIAL` for the configured family number. Do not use `ACTION_CALL` or place a call.

Gesture dispatch, swipe-for-me, automatic search intents and all target-app input automation are excluded. The ring is always a non-touchable black and bright-yellow double border. The bottom card normally has one instruction, Repeat and Stop; launcher-only open and sensitive-pause family choices are explicit exceptions.

## Wiring truth

Spark and SAM have server adapter paths in the redesign; contract/fixture verification is separate from complete live-session evidence. SAM must be called through its documented API. The parser rejects no matches, ambiguous matches, malformed output, incorrect image dimensions and stale screen provenance. Accessibility may validate and edge-bound a returned SAM region, but never replaces a missing or rejected SAM result.

**Muse Voice Transcribe 1.0 is core speech input only.** Published `muse-voice-transcribe-1.0` recognizes speech and does not synthesize speech. The November 4 build uses Android system offline Vietnamese TTS at rate 0.85 for output; there is no Muse Voice output dependency or unavailable-output blocker. Speech-input wiring and live verification must be reported separately from Spark planning, SAM grounding and local TTS output. [Meta models](https://dev.meta.ai/docs/models), [SAM segmentation](https://dev.meta.ai/docs/sam/segmenting), [SAM output grammar](https://dev.meta.ai/docs/sam/reading-segmentation)

MockModel remains a Phase 1 development stand-in behind the same GuideModel interface, selected once for development rather than mixed with production planning. Historical extra tasks, local fast-path experiments and launcher experiments remain archived outside the core loop.

## Change record

| Kept | Cut | Changed |
| --- | --- | --- |
| Tree-first observation and lock suppression | Swipe-for-me and gesture capability | One retained task state machine |
| Elder performs target-app actions | Default app opening and automatic searches | One mandatory backend model interface |
| Family setup and dialer handoff | Extra executable tasks and launcher menus | Generic Spark clarification |
| Conservative screen-state verification and development MockModel | Parallel MockModel/Spark fast paths | Production SAM region; explicitly labelled development bounds |
| Demand-driven image capture | Hand-only pointer UI | One bottom instruction card |

Candidate 1.5.27/code36 passes 91 Android unit tests, lint and assemble locally. Backend checks pass 79 tests with fixture transports, without live provider requests. The deterministic suite contains 24 safety and eight fusion-geometry cases; these are not human-reviewed ring accuracy. The development photo task calls the speech-output port in its tested complete flow, which does not prove physical Android audio playback. Complete live Candidate Spark → SAM → Android TTS and Muse Voice Transcribe evidence remains separate from source verification. The phone build was not replaced. The production backend remains unconfigured here; source adapters are not proof of production readiness. Held-out ring-hit rate, guidance accuracy, device coverage and the final demo remain **TBD**.


## 1.3 overlay and microphone correction

Version 1.3 (Android code 4) was installed on the connected Xiaomi; Accessibility was bound with no crashed service, and the temporary USB development backend connection was paired. The guide uses a narrower 18sp card with 48dp actions, a collapse control and draggable guide windows. The black/yellow non-touchable ring is unchanged.

Speech intake now gates the actual application window beneath an accessibility overlay, permits the setup app as speech context without sending it to the planner, and waits for microphone-window focus before recording. Empty trees remain incomplete and blocked. Retry restores the recording finish button. No target-app gestures or input execution were introduced.

Validation: debug build, 34 unit tests (zero failures/errors) and lint (zero errors) passed. Opening the microphone and a live transcription on this phone still require manual confirmation; installation and service binding alone do not verify voice input. No GitHub push was performed.
