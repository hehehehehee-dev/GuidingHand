# GuidingHand

A floating Android companion that helps someone use their own phone, one step at a time. Kotlin + Jetpack Compose, Android 11+. English is the default; **Tiếng Việt** is an explicit, saved option.

## Current build: 1.0

This repository contains GuidingHand **1.0**, including its Android source, branding, build instructions and verification records.

GuidingHand 1.0 includes:

- One interface language at a time across Home, preferences, the floating guide, microphone states/consent, recovery, accessibility descriptions and spoken instructions. Language names are localized too: English/Vietnamese or Tiếng Anh/Tiếng Việt. Changing language stops old speech/recording and invalidates old guidance; an expanded guide returns to its newly localized help menu. Android app resources follow the saved selection, and unsaved family fields survive the locale change. Speech-recognition language remains a separate explicit preference. App/provider names and user-supplied names/content retain their spelling. Common external control labels are translated locally; unknown controls use numbered localized labels and a highlighted-control instruction rather than copying foreign text or guessing a translation. This does not translate the underlying third-party app or system-owned Settings screens.

- Launcher guidance follows manual swipes instead of going idle after the first Home page. The default is left to the next page; Other way cycles right, up, then left. Downward swipes are omitted because they open a system panel on the tested Xiaomi. A thin translucent track and small moving circle demonstrate the direction without blocking touches. Reduced-motion settings keep the circle still. Swipe for me performs one 420ms stroke only after an explicit tap, a fresh complete safe tree and an immediate exact-Home foreground check; both interactive overlays are removed while the non-touchable circle travels with the stroke. Search/app icon targets are read again afterward, including Xiaomi App finder. Gesture completion never counts as finding/opening an app. No automated app taps or typing; TalkBack/magnification, locked, sensitive, unknown and non-launcher contexts retain manual guidance.

- More forgiving local voice intake: the speech language is saved separately from the UI language and can be switched EN/VI inside the microphone dialog. The recognizer requests up to five final alternatives and partial feedback, when supported; partial text never launches a task. Choose instead always offers a path using buttons. Installed-app matching recognizes curated pronunciation variants and family-defined Zalo/Instagram names, then asks for confirmation using real app icons. Ambiguous results remain choices. Message goals without an app ask which supported installed app to use; generic call requests ask the call type. None of this substitutes a cloud model or establishes measured speech accuracy.

- Lock-screen suppression: screen-off or a showing keyguard removes all GuidingHand overlay windows, stops speech/microphone, cancels pending steps and drops cached screen state. Tree reads and screenshots are guarded while locked. Waking the display alone does not restore the bubble; after unlock only the bubble returns, respecting the saved Hide preference. No prior task automatically resumes.

- A minimal chat-style floating root: short prompt, a real tap-to-speak microphone, and a horizontal strip of suggestion chips (This screen, Find an app, Hide). The warm panel background is translucent; text and controls stay opaque. The root no longer stacks a brand header, reassurance paragraph and four large buttons. Substeps retain Help menu and minimize, with reduced typography and spacing. Tap targets remain at least 48dp for the microphone and suggestion controls; system font scaling is respected.

- A family-centered visual identity: warm ivory, forest green and apricot, a native vector illustration of two generations holding hands, a calm serif headline, high-contrast action buttons and patient bilingual copy. The family card opens the dialer for the saved number, or family setup when no number is saved. It does not imply a relative is online or speaking through the assistant.
- A draggable 52dp accessibility overlay bubble with a pointing-hand/waypoint logo. Drag to reposition; release to dock at the nearest edge; a tap opens the card. Questions, choices, contact confirmation and coaching now render in native overlay cards; tapping the bubble does not launch the GuidingHand app or a large assistant sheet. Close/tap the bubble to minimize and restore the current card. **Hide** stops guidance and removes the app's overlays.
- A compact guide card, at most 300dp wide, with one instruction and Repeat/Stop. The card stays 6dp above/below the hand bubble, aligns to its edge, and follows dragging. The bubble/card move as a group to avoid the highlighted target. Subcards and guidance expose a persistent Help menu action that cancels pending guidance and returns to the root question over the current app. The ring has its own non-touchable window. A minimized card also disables window touch interception.
- Conversational entry: **Find an app → point out its real launcher icon → verify the actual package opened → continue in an overlay on that app**. Zalo and Instagram offer a recipient question and semantic messaging steps. Other apps offer accessible controls from the actual screen. There is no typed goal field.
- **This screen**: choose a uniquely labeled accessible control or say its name. This operates across app packages without per-app coordinates. After a screen change, the assistant asks what to inspect next; it does not falsely claim an arbitrary goal is complete.
- Tap-to-talk starts with offline language checking. An installed model, a downloadable model, a pending download and unsupported language are distinct states. A user may request a one-time language download where the provider supports it; downloading does not automatically start recording. Online speech is a separate per-utterance opt-in: the dialog identifies the current system provider and explains that audio leaves the phone; only confirmation starts that recognizer, explicitly bound to the disclosed provider. No silent network fallback, background listening or app-owned speech server/API key. The system provider uses its own network access; GuidingHand still has no INTERNET permission. Unsupported/offline-only devices retain button choices.
- The existing local mock planning seam, target verification, and password/payment safety stop.

The interaction takes inspiration from [HeyClicky's on-demand floating companion](https://www.heyclicky.com/). The user chose to continue local development without API access. This is **not** a universal AI planner: it can point to uniquely labeled controls and open installed apps, but cannot infer every goal or understand unlabeled graphics. Screen matching remains local, not a Meta/Google/OpenAI backend. The app never taps, types, sends, or confirms for the user.

## Verified on 2026-10-26

The 1.0 APK builds successfully and its packaged manifest and installed Xiaomi app both report `versionName=1.0`. The internal Android upgrade code is 14 so the existing installation can be updated without removing its data. Accessibility remains connected.

`assembleDebug testDebugUnitTest lintDebug` passed for the application logic: **86 JVM tests, zero failures; lint zero errors**. The user physically confirmed correct language in both interface modes after the EN→VI→EN guide/microphone-dialog check. This does not establish every speech-provider/error state, audible TTS or language-change lifecycle variant. It has `RECORD_AUDIO` and `VIBRATE`, but no `INTERNET`, call, or additional gesture permissions; the Accessibility service declares canPerformGestures for the explicit launcher-only swipe action. The user physically confirmed the moving cue is easy to see and swipes correctly on this Xiaomi. Individual lifecycle/reduced-motion variants and other devices remain unverified.

Physical checks on the Xiaomi confirmed bubble opening, attached-card dragging, return to the help menu, the compact chat bar, lock-screen hiding/restoration, correct swiping and interface language consistency. Current-app messaging requests preserve the current page. Complete Instagram messaging and additional voice/search variants remain pending. No personal app screenshots were exported.

## Use on the phone

1. Open GuidingHand. Choose **English/Vietnamese**, or **Tiếng Anh/Tiếng Việt** when the interface is Vietnamese.
2. If needed, choose **Enable floating guide → Open Accessibility settings** and enable **GuidingHand** in downloaded services/apps. This is an explicit system permission enabled by the user.
3. In **Preferences**, save a family phone number and, optionally, an exact Zalo contact name. A saved name is a one-tap alternative to speaking a name. Network and photo labels remain optional, task-specific setup.
4. Tap **Guide** over the app being used. **This screen** displays readable controls from that app. **Speak** lets you name a visible control or request a known task. Grant microphone permission only if using voice.
5. **Find an app** lists actual installed launcher apps, two at a time with **More** and **Speak**. Choose one; the app opens the launcher and points to its uniquely visible icon or an accessible search field. The user performs the taps and any search typing. If no icon/search target is readable, a subtle moving circle demonstrates a leftward workspace swipe. **Other way** cycles right, up, then left; no downward swipe is offered. Manual swipes refresh guidance automatically; **Swipe for me** performs one user-requested stroke after launcher-only checks, with the circle illustrating that stroke. The circle does not track the user's finger during a manual swipe. Launcher layouts vary, so success still depends on actual Search/result/app evidence.
6. Zalo/Instagram opening continues to a recipient question in the same overlay, without returning Home. Say or choose a saved contact, confirm it, then choose **Guide me**. The user still performs all chat lookup and composition actions.
7. **Close** or the bubble minimizes the current card; tap the bubble again to restore it. **Stop** ends the current guide. **Hide** in the assistant removes the bubble. Open GuidingHand and use the primary action to restore it.

If Xiaomi adds its own separate accessibility shortcut, that system shortcut is independent of GuidingHand's **Guide** bubble. It can be configured separately in the phone's accessibility settings.

## Build and install

Open this folder in Android Studio, use its bundled JDK, and build:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The deliverable is also copied to `build/outputs/GuidingHand-debug.apk`. If Xiaomi blocks USB installation, copy that file to the phone's Downloads and open it with File Manager to install/update it. On the latest attempt, normal `adb install -r` succeeded.

This workstation's Avast TLS certificate is Windows-trusted but absent from Java's default trust store. An ignored, project-local copy of that store includes the certificate; certificate verification remains enabled and the system JDK is unchanged. Terminal builds here use:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Users\Admin\AppData\Local\Android\Sdk'
$env:JAVA_TOOL_OPTIONS = '-Djavax.net.ssl.trustStore=E:\Hackathon\GuidingHand\.build-tools\cacerts'
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

## Architecture and limits

`GuideModel` remains the backend seam. `MockModel` supplies semantic targets and bilingual canned instructions; system TTS chooses an offline voice at 0.85 speed. `GuideEngine` verifies expected screen changes, protects against duplicate taps/stale responses, and saves verified familiar paths. Generic screen changes instead emit `NEEDS_INPUT`, without success/history claims. `LocalScreenGuide` grounds labels in any current app's nodes and refuses ambiguity. `FloatingConversation` owns inline questions. `OfflineVoiceInput` uses a small, separate-task foreground microphone window only on Speak; it never brings Home to the foreground and invalidates late results. No screenshot, tree or recording is persisted by the app.

The service does not inspect screens while the bubble is idle. During requested guidance it reads accessibility nodes, optionally captures a local bitmap, and skips screenshot capture for detected sensitive screens. Password fields and password/payment/OTP text stop guidance before planning. **Call family** opens `ACTION_DIAL`, never an automatic call.

The mock is deliberately limited by what each launcher/Zalo version exposes. Ambiguous names, unlabeled icons/photos, inaccessible scan results, slider-only font controls, and missing expected state evidence stop rather than fabricate success. Photo delivery requires a newly exposed outgoing-photo description plus new delivery evidence. A QR result leading to payment triggers the hard stop. On Android 11–13, local screenshots may include guide overlays; Android 14+ uses window capture.

The bubble lasts while the accessibility service is enabled and allowed to run; it cannot survive force-stop or service disable. OEM lifecycle behavior, TalkBack speech, offline voice languages, app icons/search, and complete Zalo flows require real-device verification. Phase 2 Meta APIs and screenshot eval datasets are not implemented.
