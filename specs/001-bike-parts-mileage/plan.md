# Implementation Plan: Bike Parts Mileage Management

**Branch**: `001-bike-parts-mileage` | **Date**: 2026-04-27 | **Spec**: `/Users/itx/Projects/Karoo/kxgear/specs/001-bike-parts-mileage/spec.md`
**Input**: Feature specification from `/Users/itx/Projects/Karoo/kxgear/specs/001-bike-parts-mileage/spec.md`

## Summary

Keep local bike and part management, and extend part records with a persisted
creation date and optional recurring maintenance alert configuration. Alert
settings are managed from Edit Part, stored per part, and used to trigger
Android notifications whenever ridden mileage crosses recurring alert
thresholds.

## Technical Context

**Language/Version**: Kotlin 2.0
**Primary Dependencies**: `io.hammerhead:karoo-ext`, Jetpack Compose Material 3, kotlinx serialization, kotlinx coroutines
**Storage**: One JSON file per local bike plus shared metadata JSON
**Testing**: JUnit 4 JVM unit and repository tests with focused integration tests
**Target Platform**: Android 28+ on Karoo-supported devices
**Project Type**: Single Android application + Karoo extension
**Performance Goals**: Local bike and part updates should appear during app runtime without restart; ride persistence writes occur every 100 meters or when recording ends; part creation dates must be available immediately after Add Part and Replace Part saves; maintenance alerts must be emitted when threshold crossings are detected during ride processing
**Constraints**: Karoo bike catalog sync is not used; one JSON file per bike; preserve local part history during rename and activation; delete removes the local bike record; no SQL storage; persist mileage in meters and display mileage in meters; parts persist a creation-date timestamp and optional alert settings; alert mileage is configured in kilometers while internal ride and part mileage remain meter-based; alert processing must treat skipped exact thresholds as valid crossings and emit only one alert for the highest threshold crossed by a single mileage update; Android alerts must be visible while kxgear is backgrounded and another app is active; process ride distance only during recording and reset the ride-distance baseline when a new recording ride starts; keeping the Karoo distance stream subscribed while idle or paused is acceptable if ignored events do not mutate mileage or trigger disk writes; bike and part mutations are UI-enabled only while ride state is idle, while View Bike and Back remain enabled
**Scale/Scope**: Single-user, device-local bike and part catalog with low bike counts and frequent ride distance events

## Constitution Check

- **Platform and Integration Boundaries**: PASS — design stays within Android +
  `karoo-ext` for ride distance input while bike and part lifecycle state
  remains local.
- **Dependency Preference and Explicit Behavior**: PASS — created-date and
  alert-threshold handling use existing Kotlin/Java time and Android
  notification APIs while keeping local lifecycle rules explicit in service and
  UI layers.
- **Testable Business Logic**: PASS — part creation-date assignment, alert
  threshold crossing, legacy fallback, and UI mapping remain unit-testable.
- **Data Integrity, Persistence, and Recovery**: PASS — local files remain
  atomic per-bike JSON records, new parts receive a creation date and optional
  alert settings at save time, and older parts can recover missing creation
  dates from the persisted creation timestamp.
- **Code Quality, Simplicity, and Separation of Concerns**: PASS — lifecycle,
  serialization, threshold detection, notifications, formatting, and Edit Part
  UI rendering stay in separate components.

## Project Structure

### Documentation

```text
specs/001-bike-parts-mileage/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    ├── karoo-ride-input-contract.md
    └── json-storage-contract.md
```

### Source Code

```text
app/src/main/kotlin/kxgear/bikeparts/
├── domain/
│   ├── model/
│   └── service/
├── data/
│   ├── repository/
│   └── serialization/
├── integration/karoo/
├── ui/bikes/
├── ui/common/
└── ui/parts/
```

**Structure Decision**: Keep the existing domain/repository/integration/UI split
and implement part creation-date handling in the domain model and lifecycle
service, carry it through JSON serialization, then render it in the existing
part list UI with a small formatter helper.

## Phase 0: Research

See `/Users/itx/Projects/Karoo/kxgear/specs/001-bike-parts-mileage/research.md`
for prior ride-processing decisions. No new external research is required for
part creation dates and recurring alert behavior because the change stays within
existing Kotlin, Compose, Android notification, and JSON patterns.

## Phase 1: Design & Contracts

### Data Model Changes

- Keep `Bike` as the local bike record with local display name and stored bike
  mileage.
- Keep one `BikeFile` per local bike record with parts and ride cursor.
- Allow multiple parts in the same bike file to share the same display name;
  part identity remains based on the part record identifier.
- Add `createdDate` to each part and set it when a part is created from Add
  Part or Replace Part.
- Add optional `alertMileage` in kilometers and `alertText` to each part.
- Keep persisted mileage values in meters for bike mileage, ridden part
  mileage, and ride cursor state.
- For older persisted parts that do not yet store `createdDate`, derive it from
  the existing persisted creation timestamp during load.
- Track enough per-part alert state to avoid duplicate alerts for the same
  threshold after persistence or app restart.
- Treat `RideCursor` as the cumulative-distance baseline for the current
  recording ride only; reset it when a new recording ride starts so Karoo
  distance can restart from zero without being treated as invalid.
- Display bike mileage values in meters at UI display boundaries.
- Display part creation date at the top of Edit Part using `DD.MM.YY`.
- Display alert state on Edit Part as a button labeled `Alert disabled` or
  `Alert every Nkm`, and configure alerts from a dialog with alert text, alert
  mileage, and Remove alert action.
- Keep `SharedMetadata` as the local active-bike and local bike index only.

### Integration Contracts

- Keep bike lifecycle behavior as a local UI/domain contract: Add Bike creates
  a local bike file, Edit renames the local bike, Delete removes the local bike
  file and clears active selection when needed, View Bike opens the bike, and
  Activate updates active selection.
- Extend the local part UI contract so Edit Part exposes the persisted creation
  date separately from mutable ridden mileage and alert configuration.
- Add a part alert contract: Edit Part can create, update, or remove alert
  settings; saving alert configuration requires non-empty alert mileage; ride
  processing emits one Android notification for the highest threshold crossed
  by a single mileage update.
- Keep ride distance contract focused on cumulative distance deltas and the
  every-event update / every-100m persistence split.
- Include ride-state handling in the ride input contract: accept distance only
  while recording, flush pending state when recording pauses or stops, and
  reset the active bike ride cursor on a new recording session.
- Publish Karoo ride-state changes to UI so the app can disable mutating bike
  and part actions outside idle.
- Keep View Bike and Back as navigation actions that remain enabled regardless
  of ride state.
- Document that the distance stream may remain subscribed outside recording;
  this is acceptable only because non-recording events are filtered before
  domain processing and persistence.
- Update JSON contract for locally managed bikes and preserved local part
  files, including `createdDate`, alert configuration, and any persisted
  anti-duplicate alert state.

### Agent Context Update

- Run `.specify/scripts/bash/update-agent-context.sh codex` after artifact
  updates to keep agent context aligned.

## Phase 2: Implementation Strategy

1. Extend the `Part` domain model and serialized DTO with persisted
   `createdDate`, `alertMileage`, `alertText`, and any required alert progress
   state.
2. On Add Part and Replace Part, assign `createdDate` from the current clock
   time at the same moment the new part record is created.
3. Preserve existing `createdDate` values when editing, archiving, deleting,
   or applying ride mileage updates to a part.
4. Add legacy load behavior so older persisted parts without `createdDate`
   reuse `createdAt`.
5. Add Edit Part UI state and dialog behavior for alert configuration,
   validation, and Remove alert.
6. Add threshold-crossing detection so alerts fire at `N`, `2N`, `3N`, etc.,
   including skipped exact thresholds, while emitting only one alert for the
   highest threshold crossed by a single mileage update.
7. Integrate Android notifications so alerts remain visible when kxgear is in
   the background and another app is active.
8. Update unit and repository tests for Add Part, Replace Part, alert
   persistence, validation, threshold crossing, duplicate prevention, and
   background alert behavior where testable.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | — | — |
