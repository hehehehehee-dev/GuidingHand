# Meta connection setup

The owner has authorized up to USD150 in Meta credit and now requests normal use, with no further trials. In-app AI opt-in discloses the requested goal/UI information sent to Meta through the paired computer. Password/payment/OTP checks still pause guidance. Do not publish or push changes without a new request.

## Key

1. Open the [Meta Model API dashboard](https://dev.meta.ai/), then **API keys → Create API key**.
2. Open the local `backend/.env` file and paste the key after `MODEL_API_KEY=`. Keep it on one line, without quotes or extra spaces.
3. Save the file and tell the coding assistant the file is ready. Do not paste the key in chat.

The `.env` file is ignored by Git. `.env.example` contains no credentials. The key belongs in the backend, never in the APK, Android resources or logs. Configuration checks should report only whether a key exists.

## Initial model choices

- Planner: `muse-spark-1.3`, Standard mode, against `https://api.meta.ai/v1`. Contributor variants are not selected because their content may be used for model training.
- Region refinement: `sam-3.1`, after the planner and local verification boundary work. Accessibility bounds remain available as a conservative local fallback.
- Recognition: `muse-voice-transcribe-1.0`, after verifying Vietnamese support and the recording/consent flow. This is speech-to-text, not text-to-speech; keep Android TTS for spoken instructions.

The application release stays **1.0**. Model names use the provider's own versions, independently of the app version.

## Verification order

Check configuration locally without making requests. Then validate a small text request, a synthetic screenshot, and finally a consenting test Zalo screen. Report actual responses and spend; do not mark a model implemented or a test successful from a screenshot of the model catalog alone.

The gateway enforces the lower of `GUIDINGHAND_PROJECT_BUDGET_USD` and `GUIDINGHAND_SESSION_BUDGET_USD`. The owner's live session now uses the previously authorized USD150 ceiling; the existing usage ledger is preserved. Before each paid request it reserves a conservative worst-case context/output cost; valid usage settles it. Uncertain failures retain reservations across restarts. This ledger covers this gateway, not other uses of the Meta account, and does not change Meta's billing settings.

## Run the local debug connection

Python 3.10+ is sufficient; there are no third-party backend dependencies. From the repository root:

```powershell
python backend/gateway.py --check
python -m unittest discover -s backend -p 'test_*.py' -v
python backend/gateway.py
```

Keep the gateway running. It listens only on `127.0.0.1:8766`. In a second terminal, after installing the debug APK and unlocking the USB-connected phone:

```powershell
python backend/pair_phone.py --serial YOUR_ADB_DEVICE_SERIAL
```

The helper creates the ADB reverse tunnel and configures a separate local pairing token. It does not read or send the Meta API key to Android. Repeat pairing after USB reconnect. Use `--language vi` to configure Vietnamese interface, speech and instructions on the paired phone. In **Preferences → AI guidance**, choose **Enable AI guidance → Allow AI guidance** when you want remote reasoning. Requested goals, app names and UI information go to Meta through the computer; images are sent only when needed. Turn it off in the same place. After the current disclosure is accepted, that choice is saved; service reconnection does not restore an active task or start observing screens automatically. The current installed APK uses the paired debug connection; it is not a standalone hosted backend.

Explicit app names, common capabilities and known controls use local lookup. The private app catalog persists and refreshes on package changes. From any app, choose **Tell me your goal** or **Everyday tasks**. Destination confirmation starts Home/launcher guidance, not target-app launching. The real visible icon is ringed and one instruction is spoken; the elder taps and the actual package is verified. Only **Open for me / Mở giúp tôi** on the launcher card directly opens the selected app, with package verification afterward. This opt-in never applies to in-app taps, typing, sending or confirmation. Suggested search queries are displayed for the person and are not dispatched as Android search intents. Original goals and recipients continue after the package check.

Next-step planning starts with the accessibility tree and bounded observations. Only a `needs_image` response escalates to transient pixel capture. A compact hand points to real accessibility bounds. Checked target memory stores at most64 hashed context/node bindings and normalized bounds, and requires the actual current node plus goal/recipient/language/package/screen to match. It never taps a saved coordinate. **Forget learned steps** clears it. General mode offers specific typing, scrolling and final-action handoffs, clarification and user result review. No final sending, calls, deletion, publishing, permissions or financial confirmation is performed for the user. SAM and Muse Voice remain planned.

The app's trial labels and Calculator demonstration have been removed for the owner's real use. Everyday task suggestions remain YouTube, reading news, messaging family and finding an app. Earlier direct-search behavior was demonstrated on Xiaomi, then superseded by the owner's point-first instruction. No additional user trials, paid API smoke requests or automated functional tests are run after the owner requested testing to stop. Final changes are compiled and statically checked, then installed.

Dynamic-page checking uses navigation, actual controls, focused fields and top-of-screen identity, rather than requiring every read-only view counter or timestamp to remain identical. A chosen node is rebound to its actual current identity/label/bounds. Recipient changes, sensitive content, incomplete reads, package changes and substantial layout changes still reject the old result. Ordinary changes cause up to three re-reads before asking the user to let the page settle. Stable calculator values remain meaningful state. Diagnostics record counts, timing and fixed status/error codes only, not UI text or screenshots.

## Recorded checks

- Real text API request: succeeded.
- Real structured vision request on a synthetic chat screen: selected the expected Photos choice.
- Conservative combined token cost for those two requests: USD0.00400875; not a benchmark or a billing receipt.
- Backend: 22 deterministic tests passed, including budget, consent, sensitive-screen rejection, bounded observations, malformed output, app whitelists, routing cache and tree-only/image escalation. Native checks: 119 JVM tests passed; Android lint has zero errors.
- Initial phone check: after enabling Meta test guidance and following the Calculator test instructions, the user reported a ring on the correct control. The gateway ledger increased by three settled requests, with no pending reservations; conservative cumulative cost was USD0.01425675. Request contents are not logged, so this corroborates API activity without independently identifying the control or measuring accuracy.
- Full Zalo photo-task, lifecycle and other-device acceptance: **TBD**. The earlier control check and the Calculator result below are not held-out benchmarks.
- New goal planner check: six synthetic Calculator screen states produced the expected choices 2 → 4 → Multiply → 7 → Equals, then `review` for 168. These were six independent real API calls with bounded observation history, not a physical phone task. Conservative cumulative gateway cost after these checks: USD0.03778975, with no pending reservations. These small fixtures do not establish general accuracy or phone latency.
- New phone flow: the user confirmed the app picker opened Calculator and showed the example. They then reported the pointing-hand steps reached 168 and the final confirmation question appeared. Seven additional gateway requests settled after the synthetic checks; conservative cumulative estimate was USD0.06988625 with no pending reservations. These are user observations plus usage counters, not independently recorded target traces, held-out accuracy or measured phone latency.
- Cross-app API smoke checks: synthetic news, YouTube and Instagram requests selected expected installed-app IDs; a calculator screen with a messaging goal produced `open_app` for Instagram. Tree-only and image fixtures both selected the expected Calculator key. Three repeated route requests used the in-memory cache without another paid request. Cumulative estimate after these checks: USD0.09067525. One small pair measured tree1625ms and image1375ms, so it does **not** establish that tree-only inference is faster. Native fast routing removes whole calls instead; end-to-end p50/p95 remain TBD.
- Phone diagnostics confirmed a persisted catalog of82 apps, a matching local pairing token and working USB reversal. The user confirmed direct YouTube results and YouTube → browser news search. Two subsequent API requests completed, but the dynamic-page guidance initially fell into recovery; it was not a USB disconnection. The dynamic-state repair's physical result is pending. Cumulative estimate at that check: USD0.10513000, no pending reservations.

## References

- [Quickstart and API keys](https://dev.meta.ai/docs/quickstart)
- [Pricing, Standard and Contributor modes](https://dev.meta.ai/docs/pricing-rate-limits)
- [Image understanding](https://dev.meta.ai/docs/overview)
- [Media segmentation](https://dev.meta.ai/docs/media-segmentation)
- [Speech to text](https://dev.meta.ai/docs/speech-to-text)
- [Android launch and search intents](https://developer.android.com/guide/components/intents-common)
