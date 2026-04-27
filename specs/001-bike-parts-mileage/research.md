# Research: Bike Parts Mileage Management

## Decision: Manage bikes locally in kxgear

- **Decision**: Do not observe or sync the Karoo bike catalog. Create, rename,
  delete, view, and activate bikes from the kxgear bike list screen.
- **Rationale**: The app needs predictable local part history and user-controlled
  bike management independent from Karoo Settings.
- **Alternatives considered**:
  - Use Karoo bike catalog events as the source of truth: rejected because bike
    add/remove/rename behavior must be controlled from kxgear.
  - Infer bikes from ride-only signals: rejected because ride signals do not
    define the user's bike catalog.

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
