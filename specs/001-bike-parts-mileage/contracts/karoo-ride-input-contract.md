# Contract: Karoo Ride Input

## Purpose

Define how Karoo distance updates are mapped into ride cursor changes and part
mileage updates for the selected local bike.

## Identity Constraint

- The Karoo Extension application identity MUST be `kxgear`.
- The extension service MUST construct `KarooExtension(...)` with `kxgear` as
  the extension ID.
- No alternate IDs are permitted.

## Input Shape

- `bikeContext`: implicit currently active local bike in app state
- `rideMetricValue`: non-negative whole-number cumulative distance from Karoo
- `receivedAt`: timestamp captured by the app

## Preconditions

- The integration layer uses only the official Karoo extension library.
- The app may receive ride data when no bike is active.
- Incoming metric values are treated as cumulative totals, not deltas.
- The Karoo distance stream may remain subscribed while ride state is idle or
  paused; this is expected behavior and is not by itself a mileage update.

## Processing Rules

1. If no local bike is active, ignore the event and do not persist state changes.
2. If the incoming cumulative value is less than the last accepted value for
   the active bike, reject the update and do not persist state changes.
3. If the incoming cumulative value is equal to the last accepted value, treat
   the update as a no-op and do not change part mileage.
4. If the incoming cumulative value is greater than the last accepted value,
   derive `delta = incoming - lastAccepted`.
5. Apply `delta` immediately to every installed part on the active bike.
6. For each installed part whose `targetAlertMileage` is greater than `0`, add
   `delta` to `curAlertMileage`.
7. If `curAlertMileage` reaches or exceeds `targetAlertMileage`, emit one
   maintenance alert and reset `curAlertMileage` to `0`.
8. Persist the updated bike file when 100 additional meters have accumulated
   since the last persisted ride state, when an alert is emitted, or when the
   ride ends.
9. If there is no active bike, the active bike file is missing, the value is
   duplicate, or the value decreases, do not persist a bike file update.
10. If ride state is not `RECORDING`, ignore distance stream events before
   domain ride processing; ignored non-recording events must not mutate mileage
   or trigger disk writes.

## Guarantees

- Archived parts never receive ride increments.
- Non-active bikes never receive ride increments.
- Duplicate or decreasing updates do not mutate persisted mileage.
- Distance events received while the ride is idle or paused do not mutate
  mileage and do not write bike files, even if the distance stream is
  subscribed.
- Every accepted ride event updates the in-memory bike state before the next
  event is processed.
- The domain layer remains independent from Karoo integration types.
