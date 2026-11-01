# Send-photo real-phone test

**Status: TBD — no passing real-phone run has been recorded for this candidate.** This is a procedure and an empty evidence sheet, not a test result. Unit tests, fixture Meta responses and earlier phone builds do not satisfy it.

## Installation gate and privacy

- Keep the current phone build. Test a separately identified candidate alongside it if required; do not replace the existing build before the send-photo gate passes. Record the actual candidate package, APK SHA-256, branch/commit or worktree state and build version.
- Use a consenting test recipient and a harmless test image. The elder/tester performs every target-app tap, swipe, text entry and Send; no ADB input injection or scripted app actions.
- A human grants Accessibility, microphone and sharing permissions in Android UI. Never change protected permissions through a shell shortcut.
- For password, OTP and payment hard-stop checks, **open the screen only**. Never enter a real password, code, card number, account detail or payment amount; never confirm a payment.
- Keep screen images, message contents, names, audio, transcripts, session tokens and provider keys out of logs. Use aliases such as Contact A in the report. The API key remains in the server environment, excluded from Git and the APK.
- Read persistent budget usage before the run. The project cap is **150 USD including prior usage**; stop at the cap. Do not clear reservations or retry a failed model call indefinitely.

## Setup record

| Field | Actual observation |
| --- | --- |
| Date/time and tester | TBD |
| Candidate APK/version/package/hash | TBD |
| Branch and source revision/worktree state | TBD |
| Device model / Android / Zalo version | TBD |
| Screen size, density, system font scale | TBD |
| Network / own backend / temporary USB transport if used | TBD |
| Installed offline TTS voice and rate 0.85 | TBD |
| Budget spent + reserved before/after | TBD |

Use Vietnamese for the principal send-photo run. Repeat the control/readability checks at the largest supported system text size. A separate English check must keep every label, question and spoken output in English; proper names remain unchanged.

## Run procedure

1. Start in an ordinary app such as a browser or Facebook, not GuidingHand or Zalo. Tap Help and speak a natural request to send the test image. Finish recording. Check that the mic window closes and the service reads the actual application again before transcription/model upload.
2. Leave one detail genuinely ambiguous. Answer the assistant's one clarification question by voice. Record whether it clarified intent, recipient or content; do not supply a canned phrase just to satisfy a matcher.
3. Follow guidance to Home and the real launcher search, folder or icon. The tester performs the navigation and any search typing. No default auto-launch is permitted. Check the Zalo icon ring, manually tap it and verify `com.zing.zalo` is foreground. If the tester chooses **Mở giúp tôi**, it must be an explicit launcher-only action; the control must disappear inside Zalo.
4. Open the intended conversation. Verify the recipient in the actual conversation title/header, not a name mentioned inside a message.
5. Deliberately take one wrong turn, such as another conversation or another tab. Check that the ring clears/rebinds, the assistant recognizes the new screen and guides from there while preserving the goal, recipient and verified history. It must not restart from Home.
6. Return to the correct conversation. Follow the photo/attachment, gallery selection and Send instructions, one step at a time. Before Send, confirm both the intended title and the selected image. The tester alone presses Send.
7. Record a new outgoing photo message in that same verified conversation and the associated delivery evidence. Closing the gallery alone does not pass. Moving/recycling old messages does not pass. If the UI does not expose sufficient reliable evidence, report **unverified**, not success.
8. Repeat the cross-app entry from Home. Check Repeat, Stop and collapse/expand without losing the current task. If a second source app is available, repeat entry from it.
9. Open each empty password, OTP and payment screen in a controlled environment. Verify hard stop, cleared ring and zero provider calls after detection. Choose Call family: only the dialer opens; no call starts. Close the screen without entering data.
10. Exercise one unreadable/unlabeled or cluttered screen. It must ask/stop without pretending that it detected a password/payment/OTP screen and without sending the uncertain screen to a model.

## Per-button evidence

The expected target is identified by the human from the actual phone UI, independently of the model result. A target is a hit only when the ring points to the intended control and the user's actual tap opens/changes the expected state. Report each control separately; do not invent an aggregate accuracy percentage.

| Step/control | Human expected target | Ring correct? | Panel coverage | Step advance after elder action | Actual result |
| --- | --- | --- | --- | --- | --- |
| Help / microphone | Floating Help | TBD | TBD | TBD | TBD |
| Launcher navigation/search | Actual control on this launcher | TBD | TBD | TBD | TBD |
| Zalo launcher icon | Zalo icon, including search/folder result | TBD | TBD | TBD | TBD |
| Recipient | Contact A in conversation list | TBD | TBD | TBD | TBD |
| Wrong-tap recovery | Actual return/next control from the new screen | TBD | TBD | TBD | TBD |
| Photo/attachment | Actual image picker control | TBD | TBD | TBD | TBD |
| Chosen image | Consented test image | TBD | TBD | TBD | TBD |
| Send | Send in the verified Contact A conversation | TBD | TBD | TBD | TBD |
| Completion | New outgoing image and delivery evidence | Not a tappable ring | TBD | TBD | TBD |

Panel coverage is `intersection(panel, padded pointed area) / area(padded pointed area)`. Passing coverage is **0**; use the geometry logged by `GuidingHandOverlay`, and manually confirm the panel does not obscure the actual target. If the card cannot fit, it must collapse rather than cover the pointed area. Record normal and enlarged font sizes separately. A navigation instruction without a real tappable target must not fabricate a SAM ring.

## Latency log

Use monotonic durations, not subtraction of unrelated device/server wall clocks. Native `GuidingHandTrace` emits sequence, uptime, event, duration, safe code and request ID; debug `files/guide-trace.jsonl` stores the same restricted data. `GuidingHandOverlay` emits geometry, coverage and font scale. Backend `/core/step` returns `timings_ms` for Spark, SAM and total; input transcription returns its measured duration. These numbers come from the tested request, not model estimates.

| Run/step/request ID | Screen read | Capture + encode | Transcribe | Spark | SAM | Backend roundtrip | Ring visible / step advance | Result |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD |

Do not fill a stage with zero when it was not measured; use **TBD** or **not applicable**. Separate elder thinking/tapping time from system latency. Record failures, stale-result discards and retries as their own rows. Do not publish median, tail latency or success rates until the underlying runs exist.

## Hard-stop and negative evidence

| Check | Ring cleared | Provider calls after detection | Outcome |
| --- | --- | --- | --- |
| Empty password screen | TBD | TBD | TBD |
| Empty OTP screen | TBD | TBD | TBD |
| Empty payment screen | TBD | TBD | TBD |
| Unreadable/cluttered screen | TBD | TBD | TBD |
| Gallery closed without Send | TBD | Not applicable | Must remain unverified — TBD |
| Old messages shift position | TBD | Not applicable | Must remain unverified — TBD |
| New photo in wrong recipient's conversation | TBD | Not applicable | Must not count as sent — TBD |
| Screen changes during Spark/SAM thinking | TBD | Existing response discarded | Task preserved — TBD |

## Gate result

### Observed blocker and Candidate.4 correction (2026-10-28)

Candidate.3 did not pass: the elder reported `UNSUPPORTED` after confirming Zalo.
The last captured request in `build/phone-runs/20261028T083048Z/trace.json`
recorded a SAFE screen read, capture/encode 412ms, Spark 6141ms, backend
roundtrip 6187ms, then native UNSUPPORTED. This is a planning/scope rejection;
the trace does not establish why the model interpreted the goal that way.

Candidate.4 fixes a confirmed native context-loss defect: speaking after
UNSUPPORTED now refines the original goal, retaining the paired explanation and
answer, rather than creating a new goal. Unsupported model options remain
answerable. No automatic retry, launch or target-app action was added.
Spark now classifies intent separately from missing details and navigation;
contradictory scope decisions stop and ask without invoking SAM. Clear tasks
outside photo sharing remain unsupported, with a spoken explanation inviting
correction. Visible conversation titles alone do not authorize a recipient.

The synthetic live Spark scope checks are in
`build/outputs/intent-live-regression.json`: five cases passed, one encountered
a provider/validation error. That single case passed on one explicit rerun
(`intent-live-regression-choose_recipient_in_app.json`). These checks use an
owned artificial UI, not private phone screens, and do not establish ring
accuracy or send-photo success. All 62 Android and 62 backend unit tests pass;
Candidate debug build and lint pass. Candidate.4 is installed side by side;
the original 1.3 is retained. The actual Candidate.4 send-photo run remains TBD.

**TBD / NOT PASSED.** A pass requires the complete real send-photo session, correct recipient/title and new-image evidence, per-button ring and coverage observations, wrong-tap recovery, hard stops and measured per-step logs. Keep this status until those observations are recorded. A passing build or fixture test alone cannot authorize replacing the current phone build.
