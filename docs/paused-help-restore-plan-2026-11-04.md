# Restore Help after a safety pause

## Evidence and acceptance criteria

- On Candidate 1.5.68, Accessibility remains enabled on the unlocked Xiaomi Home screen, but no Help window is present. The content-free trace records `SAFETY_PAUSED`, then repeated safe screen reads, plus Help attach/detach pairs about 150 ms apart.
- Events emitted by GuidingHand's own Accessibility overlay must never hide that overlay. The foreground setup and microphone activities still suppress it through their lifecycle and voice lease.
- On a safe external screen, a latched safety pause must display its paused Help/card if hidden. It must not clear the pause, restart planning, capture a screenshot, call a provider, or provide a target ring.
- On the lock screen or a sensitive event, Help remains hidden and the safety gate takes precedence.
- Verify with deterministic gates and a Candidate-only read-only phone smoke. A visible phone window is evidence of restoration, not of an interaction pass.
