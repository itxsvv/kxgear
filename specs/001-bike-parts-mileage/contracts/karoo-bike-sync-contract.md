# Contract: Karoo Bike Startup Sync

## Purpose

Define how kxgear reads the Karoo SDK bike catalog on app startup without
making Karoo the source of truth for ongoing bike lifecycle actions.

## Input Shape

- `karooBikes`: list of Karoo SDK bikes, each with:
  - `id`
  - `name`
  - `odometer`
- `localBikes`: current persisted local bikes loaded from bike JSON files

## Processing Rules

1. Fetch the Karoo SDK bike list once when the app first loads the bike list
   during an application startup.
2. Normalize bike names for comparison using the same trimming rules used for
   local bike names.
3. For each Karoo bike whose name matches an existing local bike, update the
   local bike's stored `karooBikeId` if needed and do not create a duplicate
   local bike.
4. For each saved local bike whose name does not appear in the current Karoo
   bike list, clear the stored `karooBikeId` to `null`.
5. For each Karoo bike whose name does not match any local bike, create a new
   local bike with:
   - a generated local `bikeId`
   - the Karoo bike name
   - mileage initialized from the Karoo odometer converted to whole meters
   - persisted `karooBikeId`
6. Manual bike creation from local bike lifecycle code always persists
   `karooBikeId = null`.

## Guarantees

- Startup sync never deletes local bikes.
- Startup sync never renames local bikes.
- Startup sync never changes the active bike unless the import creates the
  first local bike while no active bike was selected.
