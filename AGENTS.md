# GuidingHand repository instructions

Applies to coding agents working in this repository, including Codex and Meta-based agents.

## Start every development session

1. Read `META_DEVELOPMENT.md` completely for architecture, safety, implementation boundaries and commands.
2. Read `docs/current-development-state.md` for the latest source/installed/deployed state, blockers and next checks.
3. Read the latest relevant entries in `docs/continuation-handoff-2026-10-28.md`, then `README.md`.
4. Check `git status --short --branch` and the actual relevant source/tests. Preserve existing user work. Resolve documentation conflicts against current source and observed evidence; do not assume a note is still current.

## Documentation is part of finishing a change

- Review README for every product, UI, configuration, model, architecture, version or evidence change. Update affected sections in the same commit. If no public-facing fact changed, record “README reviewed; no public-facing change” in the handoff rather than editing it for activity.
- README describes the current product: what it is, why it matters, how to use it, accessibility needs served, architecture, setup, evidence limits and license. Keep development chronology and kept/cut decisions in `docs/`.
- Explain which needs an accessibility feature is designed to support. Do not present design intent or contrast calculation as proof of older-adult usability.
- Update `META_DEVELOPMENT.md` whenever components, preferences, model roles, safety rules, commands or development workflow change.
- Update `docs/current-development-state.md` after a meaningful change: source version, installed version, deployment status, evidence, unresolved issues and next concrete action. “Built”, “installed”, “cloud deployed”, and “user confirmed” are separate facts.
- Append a concise entry to the dated continuation handoff with the reason, changed behavior, checks and evidence paths. Do not rewrite historical results as if they refer to the newest build.
- Update affected setup/design/protocol documents. Keep root links valid. Use TBD for unmeasured metrics and unknown deployment/phone results. Never copy credentials or private user content into documentation.

## Verification and delivery

For code changes, run the relevant gates in META_DEVELOPMENT.md; Candidate Android gates must use `-PcandidateBuild=true`. Do not claim phone interaction from unit tests or installation.

For documentation-only changes, check factual claims, relative links and `git diff --check`; no APK build or cloud deployment is needed.

Follow the user's active authorization for committing, pushing, installing or deploying. These instructions do not grant new permission for external writes, provider spending, device input or sending user content. The app's safety and elder-operated-action rules always remain in force.
