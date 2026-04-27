<!--
Sync Impact Report
- Version change: 1.0.0 -> 1.1.0
- Modified principles:
  - I. Platform and Integration Boundaries -> I. Platform and Integration Boundaries
  - II. Deterministic Domain Behavior -> II. Dependency Preference and Explicit Behavior
  - III. Testable Mileage Integrity -> III. Testable Business Logic
  - IV. Reliable Persistence and State Safety -> IV. Data Integrity, Persistence, and Recovery
  - V. Simplicity and Separation of Concerns -> V. Code Quality, Simplicity, and Separation of Concerns
- Added sections:
  - None
- Removed sections:
  - None
- Templates requiring updates:
  - ✅ updated /Users/itx/Projects/Karoo/kxgear/.specify/templates/plan-template.md
  - ✅ updated /Users/itx/Projects/Karoo/kxgear/.specify/templates/spec-template.md
  - ✅ updated /Users/itx/Projects/Karoo/kxgear/.specify/templates/tasks-template.md
  - ⚠ pending /Users/itx/Projects/Karoo/kxgear/.specify/templates/commands/*.md (directory not present)
- Follow-up TODOs:
  - None
-->
# KXGear Constitution

## Core Principles

### I. Platform and Integration Boundaries
All production code MUST run on Android versions supported by Karoo devices and
MUST integrate through the Karoo Extension SDK. Kotlin is the default
implementation language for application and domain code. The project MUST NOT
depend on the ANT+ SDK or deprecated Karoo integration paths. These boundaries
keep delivery aligned with the supported device environment and avoid
unsupported integration paths.

### II. Dependency Preference and Explicit Behavior
The system MUST prefer well-established external libraries over custom
implementations when they provide reliable and maintainable solutions. Custom
implementations for common patterns MUST be introduced only when existing
libraries or platform capabilities are insufficient, and that decision MUST be
explicitly justified. System behavior and state transitions MUST remain
explicit, predictable, and free of magic values or implicit side effects.

### III. Testable Business Logic
Core business logic MUST be testable in isolation. Mileage calculation and any
logic that derives ridden distance, applies manual adjustments, or guards
against duplicate and out-of-order updates MUST have unit test coverage.
Critical edge cases named in the specification MUST be verified in tests so
data correctness is demonstrable rather than assumed.

### IV. Data Integrity, Persistence, and Recovery
The system MUST ensure data consistency at all times. Writes MUST be safe
against duplication, corruption, and partial application, and state changes
MUST be deterministic. Persistence MUST remain reliable across device restarts
and interrupted operations, with recovery behavior that preserves the last valid
state rather than silently accepting corruption or mixed results.

### V. Code Quality, Simplicity, and Separation of Concerns
All code MUST be clear, readable, and maintainable. Complex logic MUST be
encapsulated in well-defined components, and unnecessary layers or premature
optimizations MUST be avoided. Business logic, persistence, and external
integrations MUST remain clearly separated so each concern can evolve, be
tested, and be reviewed independently while preserving consistent behavior
across all features.

## Operational Constraints

The system MUST avoid unnecessary writes, polling, or other heavy operations
that waste device resources. Distance updates and other frequently triggered
workflows MUST be processed efficiently within the limits of Karoo-supported
Android devices. Invalid or inconsistent input MUST be handled safely and MUST
NOT leave the system in a broken or partially updated state. Behavior across
features, supported Android versions, and device restarts MUST remain
consistent and explainable from recorded state.

## Delivery Workflow and Quality Gates

Every specification, plan, and task list MUST include an explicit constitution
check. Specifications MUST call out critical state transitions, integrity rules,
and edge cases when the feature affects business data. Plans MUST identify how
the design preserves Android compatibility, Karoo integration boundaries,
dependency-selection rationale, deterministic state handling, safe invalid-input
behavior, and isolated testability. Task lists MUST include the work needed to
verify business logic, data-integrity behavior, invalid-input handling, and
constitution-required tests; such testing cannot be omitted as optional work.

## Governance

This constitution supersedes conflicting local project conventions. Amendments
require a documented update to this file, a review of dependent templates under
`.specify/templates/`, and a version change that follows semantic versioning:
MAJOR for removed or redefined principles, MINOR for new principles or
materially expanded governance, and PATCH for clarifications that do not change
project obligations.

Compliance review is required for every feature plan and implementation review.
Reviewers MUST verify platform constraints, dependency choices, readability and
separation of concerns, deterministic state transitions, persistence safety,
invalid-input handling, and required tests for core business logic. Any
intentional deviation MUST be documented in the relevant plan under Complexity
Tracking and explicitly justified before implementation proceeds.

**Version**: 1.1.0 | **Ratified**: 2026-04-07 | **Last Amended**: 2026-04-12
