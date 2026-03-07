# AGENTS.md

## Purpose

This file guides coding agents working in this repository.
It is intentionally principle-based so small restructures do not require frequent updates.

## Canonical Policy

AGENTS.md is the canonical policy for this repository.
If a repo skill and AGENTS.md ever conflict, follow AGENTS.md.
Repo skills should contain task workflow/checklists and avoid duplicating policy text from this file.

## Domain Context

For AMAN operational background and controller workflow context, refer to docs/domain/aman-concept.md.
This context document informs feature behavior and terminology, but it does not override the rules in AGENTS.md.

## System Intent

This project implements an AMAN/DMAN system for virtual ATC operations.
The overall application style is Model-View-Presenter (MVP).

Core responsibilities should stay separated:

- Model/domain: sequencing, trajectory prediction, planning rules, timeline behavior.
- Presenter: orchestration between model and view, UI-facing state/flows.
- View: rendering and user interaction only.
- Integrations/infrastructure: external APIs, protocol adapters, persistence, config loading.
- Application wiring: startup and dependency composition.
- Cross-cutting shared code: small, domain-agnostic utilities/types used across layers.

If folder/module names change, preserve these boundaries.

## Long-Term Direction

Design core/domain logic so it can be moved to a remote service in the future with minimal rewrite.
This implies:

- Keep client-side domain logic independent from UI and framework details.
- Keep low coupling between domain and infrastructure.
- Prefer explicit interfaces/ports at boundaries.
- Keep ATC client integration swappable: design against a stable AtcClient-style interface so different ATC clients can replace the current client in the future with minimal core rewrite.
- If core logic is moved to a server, ATC client plugins should integrate with the server API rather than coupling directly to the Swing app process.
- Keep the client thin where practical.

## Architecture Rules

- Keep domain logic deterministic where practical.
- Do not let UI concerns leak into domain logic.
- Keep external I/O and adapter code outside the domain layer.
- Prefer package-by-feature over package-by-technical-layer where practical.
- VACCs define relevant airports and operational setup through YAML config files, so AMAN/DMAN core rules must be configurable rather than hardcoded per deployment.
- Preserve backward compatibility for inter-process/API contracts unless intentionally versioned.

## Package-By-Feature

Organize code around business capabilities (features) first, then internal technical split inside each feature if needed.

- Good examples: `planning`, `timeline`, `weather`, `airport`, `sharedstate`.
- Avoid cross-feature catch-all packages like `utils`, `managers`, or `services` when feature intent is unclear.

## `common` vs Core Rule

- Keep `common` (or equivalent shared module) separate for domain-agnostic code reused across layers.
- Do not move `common` into core/domain by default.
- If code expresses AMAN/DMAN behavior, place it in the core/domain module.
- Only merge/remove the shared module if it becomes effectively unused.

## Domain Modeling Principle

Make illegal states unrepresentable.

- Prefer type-safe models where invalid domain combinations cannot be constructed.
- Use sealed hierarchies/discriminated variants for mutually exclusive cases.
- Avoid nullable fields that represent a different domain type.
- Prefer composition for shared parts, and use inheritance/sealed parents to model valid alternatives.

Example direction:

- Avoid: one Aircraft type with nullable wingspan for helicopters.
- Prefer: sealed Aircraft variants like FixedWingAircraft and Helicopter, each with only valid fields.
- Share reusable data through composed value objects where appropriate.

## Testability And Responsibility Rules

For core/domain changes, agents must evaluate testability before finishing and add or update tests for behavior changes unless skipping is explicitly justified in the final handoff.

- Classes should have limited responsibility.
- If testing a small behavior requires many unrelated mocks/stubs, treat it as a design smell.
- Prefer refactoring toward smaller collaborators and clearer boundaries.
- Constructor dependencies should mostly match the class's direct responsibility.

## Naming Conventions (Human-Friendly)

Prefer names normal developers/controllers can understand quickly.
Avoid fancy, academic, or enterprise-heavy terms.

- Prefer `SequenceService` over `SequencingOrchestratorFacade`.
- Prefer `ArrivalPlanner` over `InboundFlowOptimizationEngine`.
- Prefer `updateRunwayMode` over `synchronizeOperationalConfigurationState`.
- Prefer `latestClientVersion` over `artifactCompatibilityDescriptor`.
- Prefer `AirportData` over `AirportAggregateContext`.

## Coding Conventions

### General

- Optimize for clarity and operational realism over cleverness.
- Document behavioral intent when changing sequencing/trajectory behavior.
- Keep changes scoped to one logical concern when possible.

### Kotlin/JVM

- Use official Kotlin style.
- Prefer immutability (`val` over `var`) and pure functions where practical.
- Prefer functional-style transformations when they improve clarity.
- Keep domain code pure/testable.
- Do not over-engineer: favor straightforward code that OO-oriented contributors can follow.
- Add or update tests for behavior changes in planning, sequencing, navigation, weather, or timeline logic; if skipped, include explicit justification in the final handoff.

### TypeScript/Node

- Keep strict typing enabled.
- Validate external input early (headers, params, payloads).
- Run lint/format checks before finalizing.

### C++ plugin/bridge

- Keep message/protocol compatibility with client expectations.
- Make conservative, focused changes to lifecycle/event hook code.

## Frontend Thread-Safety

For view/frontend code, model updates and UI updates must be thread-safe.

- Perform Swing/UI mutations on the UI event thread.
- Do not block the UI thread with network, file, or heavy computation work.
- Marshal background/model results back to the UI thread through the existing dispatcher abstractions.
- Keep thread ownership explicit at boundaries between presenter/model/view.

## Dependency Policy

- Do not introduce new dependencies by default.
- If a new dependency increases build, runtime, or maintenance complexity, avoid it.
- Add a dependency only when there is clear net benefit and not adding it would be bad practice (for example security, correctness, protocol compliance, or major maintainability concerns).
- Prefer existing standard library/project utilities first.
- When adding a dependency, document why alternatives were rejected.

## Build And Verification

Use the project-local scripts/wrappers for each component.
Typical commands include:

- Kotlin/Gradle: test, build, and packaging tasks via Gradle wrapper.
- Server/Node: install, dev, build, start, lint, and format scripts via npm.
- C++ bridge: build/debug via the solution in Visual Studio.

When in doubt, prefer existing scripts over ad-hoc commands.

## Change Checklist For Agents

Before finishing:

- Confirm architecture boundaries are still respected.
- Include a short `Testability note` in the final handoff: changed responsibilities, seam quality, mock/stub burden, and any design smell found.
- Confirm affected tests/lint/build steps were run (or explicitly note if not run).
- Call out any contract changes (API/schema/protocol) and migration impact.
- Avoid incidental refactors unrelated to the requested change.
