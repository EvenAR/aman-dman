---
name: aman-domain-change-guard
description: Enforce safe behavior changes in AMAN/DMAN domain code in this repository. Use when editing sequencing, planning, trajectory prediction, timeline behavior, navigation, weather, presenter-model boundaries, or shared-state contracts.
---

# AMAN Domain Change Guard

Always apply repository policy from `AGENTS.md` first. Do not duplicate or override it here.

## Workflow

1. Classify changed files by boundary before editing:
- Domain/core (`aman-dman-client/model-core`)
- Presenter (`aman-dman-client/presenter`)
- View (`aman-dman-client/view`)
- Infrastructure (`aman-dman-client/model-infra`, `server`, `euroscope-bridge`)
- App wiring (`aman-dman-client/app`)
- Shared (`aman-dman-client/common`)

2. Keep boundaries strict:
- Keep UI and thread concerns out of domain/core.
- Keep external I/O and adapters out of domain/core.
- Preserve feature intent; avoid catch-all modules.

3. For domain behavior changes, document intent briefly:
- Explain operational reason for sequencing/planning/timeline behavior changes in code comments where needed.
- Prefer models that make illegal states unrepresentable.

4. For behavior changes in planning, sequencing, navigation, weather, or timeline:
- Add or update tests under `aman-dman-client/model-core/src/test/kotlin`.
- If no tests are added, include explicit justification in final handoff.

5. Verify using project-local commands:
- `aman-dman-client/gradlew.bat :model-core:test`
- Run broader checks only when change crosses module boundaries.

6. Final handoff must include:
- `Testability note` with changed responsibilities, seam quality, mock/stub burden, and design smells.
- What validation commands were run (or not run).
- Any API/schema/protocol contract impact.