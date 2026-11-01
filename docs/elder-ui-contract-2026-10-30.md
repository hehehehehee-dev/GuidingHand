# GuidingHand elder UI contract

## Context

- Primary user: an older adult with limited Android confidence, possible low vision, and possible hand tremor.
- Entry: the floating Help control from any unlocked, non-sensitive app.
- Primary task: hear one short instruction, find one marked target, and tap it personally.
- Recovery: keep the goal and guide from the current screen after a wrong tap or unreadable state.
- Product tone: calm, respectful, direct, and visibly reliable.

## Interface rules

1. Show one instruction and one target per step.
2. Keep the guide card solid, movable, collapsible, and outside the target area.
3. Use at least 24sp for instructions, 22sp for labels, and 20sp for body text.
4. Use at least 72dp for the main target and 48dp for every secondary target.
5. Keep error recovery visible. Never rely on color, motion, or an icon alone.
6. While the app reads or plans, show status text and Stop only. Do not invite duplicate speech input.
7. Announce safety pauses and recoverable errors immediately to TalkBack.

## Existing tokens and components

- `GuideDesign`: type, target, ring, surface, and contrast tokens.
- `GuideOverlay`: floating Help control, solid guide card, fixed footer, double-border ring, and pointing hand.
- `GuidePanelLayout`: safe-area placement and target avoidance.
- `GuideViewState`: explicit reading, planning, instruction, recovery, error, and safety states.

## Verification

- Small and standard Android phone widths.
- Android font scaling up to 200 percent without hidden actions.
- TalkBack labels and state announcements.
- Default and reduced-motion settings.
- Idle, reading, planning, waiting, retry, unavailable, and safety-paused states.
- Real-device screenshots and overlay attach/detach trace before claiming visual success.

Official Android guidance recommends at least 48dp touch targets and testing layouts at 200 percent font scaling:

- https://developer.android.com/guide/topics/ui/accessibility/views/apps-views
- https://developer.android.com/about/versions/14/features#200-percent-font-scaling
