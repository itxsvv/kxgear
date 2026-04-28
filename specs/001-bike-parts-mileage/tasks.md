# Tasks: Bike Parts Mileage Management

**Input**: Design documents from `/Users/itx/Projects/Karoo/kxgear/specs/001-bike-parts-mileage/`
**Prerequisites**: `/Users/itx/Projects/Karoo/kxgear/specs/001-bike-parts-mileage/plan.md`, `/Users/itx/Projects/Karoo/kxgear/specs/001-bike-parts-mileage/spec.md`, `/Users/itx/Projects/Karoo/kxgear/specs/001-bike-parts-mileage/research.md`, `/Users/itx/Projects/Karoo/kxgear/specs/001-bike-parts-mileage/data-model.md`, `/Users/itx/Projects/Karoo/kxgear/specs/001-bike-parts-mileage/contracts/karoo-ride-input-contract.md`, `/Users/itx/Projects/Karoo/kxgear/specs/001-bike-parts-mileage/contracts/json-storage-contract.md`, `/Users/itx/Projects/Karoo/kxgear/specs/001-bike-parts-mileage/quickstart.md`

## Phase 1: Local Bike Lifecycle

- [X] Implement local bike add, edit, delete, view, and activate flows through
  `BikeLifecycleService`.
- [X] Render each bike list panel with name/mileage, View/Edit/Delete actions,
  active state, and a full-width Add Bike button.
- [X] Confirm the first locally added bike becomes active only when no active
  bike is already selected.
- [X] Confirm deleting the active bike clears active selection and deleting an
  inactive bike preserves the current active bike.
- [X] Remove runtime Karoo bike catalog sync wiring.
- [X] Delete unused Karoo bike catalog adapter, controller, service, contract,
  and tests.

## Phase 2: Parts

- [X] Keep parts attached to local bike files across bike rename and mileage
  edits.
- [X] Allow duplicate part names; part lifecycle operations use stable part IDs.
- [X] Use ridden mileage only for parts; no separate manual mileage offset.
- [X] Keep add, edit, archive, delete, and replace part flows working on local
  bikes.
- [X] Extend part persistence with alert configuration fields and any required
  per-part alert progress state.
- [X] Add Edit Part alert button behavior, alert dialog fields, validation for
  empty alert mileage, and Remove alert action.

## Phase 3: Ride Processing

- [X] Process Karoo ride distance only while ride state is `RECORDING`.
- [X] Reset the active bike ride cursor when a new recording ride starts.
- [X] Apply accepted ride deltas to installed parts and bike mileage.
- [X] Persist ride updates every 100 meters or when recording stops.
- [X] Document that the Karoo distance stream may remain subscribed outside
  recording if non-recording events are ignored before persistence.
- [X] Accumulate per-part `curAlertMileage` from accepted ride deltas while a
  non-zero `targetAlertMileage` is configured.
- [X] Emit one alert when `curAlertMileage` reaches or exceeds
  `targetAlertMileage`, then reset `curAlertMileage` to `0`.
- [X] Integrate Android notifications so maintenance alerts are visible while
  kxgear is backgrounded and another app is active.

## Phase 4: Verification

- [X] Keep repository, lifecycle, part, restart, and ride-processing tests
  aligned with local bike management.
- [X] Validate focused Kotlin compilation and JVM unit tests.
- [X] Add tests for alert persistence, Edit Part alert configuration,
  `curAlertMileage` accumulation, reset behavior, and zero-target behavior.
