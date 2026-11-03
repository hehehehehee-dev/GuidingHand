# Companion gestures and chosen forms of address

## Experience and acceptance criteria

The existing young-helper option represents GuidingHand's fictional assistant. The elder keeps the current Help anchor, guide card, ring, and primary action. The illustration must explain a status without taking more screen space or implying that an operation completed before verification.

- Microphone sheet: ready pose, listening head tilt with sound strokes, thinking pose after recording, questioning pose on an error.
- Help: thinking for reading/planning/verification, pointing for the elder's next operation, a questioning pose for clarification, a smile/check only at DONE, and a calm still pose for safety or unavailable states.
- Gestures last 500ms (700ms for completion) once per mood change. Repeated identical renders do not restart them. No repeating decorative animation, layout changes, extra overlay, or new timer window.
- Reduced motion or disabled system animators show the final static pose immediately. Existing readable status text remains; the illustration is decorative.
- Vietnamese instructions and speech use the selected elder address and assistant self-reference. Examples: “Tôi chưa nghe rõ. Bác nói lại giúp tôi nhé.” becomes “Cháu chưa nghe rõ. Bà nói lại giúp cháu nhé.” for Bà/Cháu.
- Preserve protected recipient names, filenames, task content, and quoted labels. Never rewrite the editable transcript itself. English remains English; Vietnamese kinship terms are not inserted into English copy.

## Implementation and review

Reuses GuideOverlay, VoiceActivity, RespectfulLanguage, existing typography/target sizes, and existing mascot/reduced-motion preferences. CompanionDrawable animates only its drawing inside a fixed 40dp icon. It cancels animation when its owner disappears. GuideEngine already sends its formatted instruction to both the card and Android TTS; updated formatting covers model self-references and system notices.

Scope is component feedback and copy. Navigation, consent, speech providers, ring geometry and task execution are unchanged. The same existing card layout handles compact screens and large fonts; no extra vertical row is added. The new voice icon occupies 40dp horizontally beside the status, so large-text wrapping remains a phone-review item.

Sources: actual GuideOverlay/VoiceActivity/RespectfulLanguage/GuideEngine implementations and existing RespectfulLanguageTest. No new usability research or measured elder usability is claimed. Device visual and motion checks require a connected phone; build and deterministic tests alone cannot establish their appearance.

Manual review: select young helper; observe ready/listening/planning/question/guidance; verify Bà/Cháu or Ông/Con in card and spoken output; repeat with reduced motion; check font scaling and card anchoring. Do not send a real message as part of this check.
