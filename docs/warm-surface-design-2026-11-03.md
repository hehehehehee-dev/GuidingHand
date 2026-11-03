# Warm surfaces with restrained light-catching edges

Approved direction: warm cream and dark green, a subtly glass-like Help handle, opaque instruction cards, and decorative depth on Home/Settings. This is a visual change, not a new screen or navigation flow.

## Design contract

- Help uses an opaque diagonal green gradient (#315344 to #18332A), a quiet outer edge and a light inner edge. Pressed/focused/disabled states retain distinct existing feedback and readable text. No live blur, screenshot, or new animation is used for the finish.
- Guide and microphone cards keep a uniform #FFFCF5 fill, #18332A instruction text, 24dp corners, a neutral outline and a white inner highlight. Decoration cannot set view padding or move the Help/card anchor. Ring appearance and touch behavior are unchanged.
- Home and Settings use a pale green/cream background with a stationary white glow, behind opaque cream cards. Cards use shallow elevation and restrained borders. Selected options keep their checkmark and strong border.
- Instruction >=24sp, label >=22sp, body >=20sp, primary >=72dp and secondary >=48dp remain unchanged. Large text can wrap and scroll. Motion preferences continue to apply to the companion and existing feedback.

## Contrast measured with sRGB luminance calculation

| Text | Background | Ratio |
|---|---|---|
| #18332A | #FFFCF5 card | 13.26:1 |
| #FFFCF5 | #315344 lightest Help gradient endpoint | 8.36:1 |
| #FFFCF5 | #18332A darkest Help gradient endpoint | 13.26:1 |

The Help gradient interpolates between those endpoints. Its border is outside the text area. Decorative outlines are not the sole indication of selection or status.

## Verification scope

Reuses GuideDesign, GuideOverlay, VoiceActivity and existing Compose components. GuideSurfaces shares native backgrounds between Help, controls, guide cards and microphone cards. Candidate identity is 1.5.44/code 53. Backend deployment is unnecessary for these Android changes.

Native screenshot review was attempted on the read-only Medium_Phone emulator. The emulator's System UI entered ANR during startup and remained over the app; no usable app screenshot was obtained. The temporary emulator was stopped without saving its snapshot. Xiaomi overlay/microphone review requires reconnection, including Help anchoring, large fonts and both light/dark underlying apps. Visual review remains pending and is not replaced by compilation or contrast calculation.
