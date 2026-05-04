# Research: Bike Parts Mileage Management

## Decision: Manage bikes locally in kxgear with startup Karoo discovery

- **Decision**: Keep local bike add, rename, delete, view, and activate flows
  in kxgear, but on app startup read the Karoo SDK `Bikes` event once to find
  missing bikes by name and add them automatically.
- **Rationale**: The app needs predictable local part history and user-controlled
  bike management, but startup discovery can reduce duplicate data entry and
  lets local bikes retain a persisted `karooBikeId`.
- **Alternatives considered**:
  - Use the Karoo bike catalog as the source of truth: rejected because bike
    add/remove/rename behavior must be controlled from kxgear.
  - Ignore the Karoo bike catalog entirely: rejected because the new
    requirement explicitly needs startup discovery and import.
  - Infer bikes from ride-only signals: rejected because ride signals do not
    define the user's bike catalog.

## Decision: Match startup bikes by name and backfill `karooBikeId`

- **Decision**: Compare Karoo bikes and local bikes by bike name on startup;
  when a name matches, store the Karoo bike ID on the existing local bike
  instead of creating a duplicate add.
- **Rationale**: The requirement defines bike name as the matching key, and
  backfilling `karooBikeId` makes future local state aware of the Karoo
  identity without changing local bike ownership.
- **Alternatives considered**:
  - Prompt for every Karoo bike regardless of name: rejected because it would
    create duplicate local bikes for already-existing names.
  - Match only by `karooBikeId`: rejected because manually created local bikes
    start with `karooBikeId = null`.

## Decision: Use the official Karoo `Bikes` event on startup

- **Decision**: Fetch startup Karoo bikes from the official `Bikes` event
  (`Bikes.Params`) and map each SDK bike to a local snapshot of bike ID, name,
  and whole-meter odometer.
- **Rationale**: The official `karoo-ext` docs expose `Bikes` as a supported
  observable event whose `Bike` payload already includes `id`, `name`, and
  `odometer`, which is exactly the startup-sync input shape needed here.
- **Alternatives considered**:
  - Reconstruct bikes from ride profiles or ride state: rejected because those
    APIs do not represent the saved bike catalog.
  - Keep a deleted custom adapter: rejected because a focused one-shot adapter
    is enough for startup sync.

## Decision: Keep local active-bike selection

- **Decision**: Continue storing the selected active bike in local metadata.
- **Rationale**: The current feature set depends on a local active-bike concept
  for part ride processing.
- **Alternatives considered**:
  - Remove active-bike selection entirely: rejected because ride processing
  still needs one chosen bike.
  - Infer active bike from unrelated bike signals: rejected because there is no
    in-scope catalog-driven active-bike signal for this change.

## Decision: Default active selection from the first locally added bike

- **Decision**: When no active bike is selected and the user adds a bike, select
  the newly added local bike.
- **Rationale**: This gives ride processing a deterministic active bike without
  overriding an existing valid user selection.
- **Alternatives considered**:
  - Always select the newest bike: rejected because it would overwrite an
    existing active bike.
  - Leave active selection unset until the user chooses: rejected because the
    first local bike should be ready for ride processing.

## Decision: Continue every-event ride updates with every-100m persistence

- **Decision**: Keep ride mileage updates applied on every distance event while
  persisting to disk only every 100 meters or when the ride ends.
- **Rationale**: This preserves accurate part mileage behavior while limiting
  write frequency on-device.
- **Alternatives considered**:
  - Persist every distance event: rejected because it causes unnecessary writes.
  - Only update mileage when persisting: rejected because the UI would lag
    behind the latest accepted ride event.

## Decision: Display and enter mileage in meters

- **Decision**: Store all mileage and ride cursor values in meters and keep bike
  and part mileage UI in meters.
- **Rationale**: Ride events provide meter-based values, and current UI
  requirements prefer direct meter values.
- **Alternatives considered**:
  - Store kilometers directly: rejected because it would lose alignment with
    ride event inputs.
  - Display kilometers: rejected by the current UI requirement.
