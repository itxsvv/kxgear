# CR-001: mileage-alerts

## Status
Proposed

## Reason
Users need a way to be notified when a bike part reaches a maintenance interval.
Currently the app tracks part mileage, but it does not proactively inform the user
that a service threshold has been reached. This makes it easy to miss scheduled
maintenance, especially when the app is not open.

## Current Behavior
Each bike part stores ridden mileage and accumulates distance from rides.
The system shows current mileage in the UI, but it does not store any alert
threshold and does not generate notifications when a mileage interval is reached.

## Proposed Change
Add an `alertMileage` value to each bike part, expressed in kilometers, and an
`alertText` value used in the notification content.

When the accumulated ridden mileage reaches a multiple of the configured alert
mileage, the system must show an Android alert/notification visible even if
kxgear is in the background and another application is active.

The alert is cyclical:
- if alert mileage is `N`, alerts are triggered whenever ridden mileage crosses a multiple of `N`
- alerts must be generated even if the reported mileage skips past the exact threshold value
- if multiple thresholds are crossed in a single mileage update, the system shows only one alert for the highest crossed threshold
- the alert must not be one-time only after the first threshold is reached

UI behavior:
- alert configuration is managed from the Edit Part screen
- the Edit Part screen must show an alert button
- if no alert is configured, the button text must be `Alert disabled`
- if alert mileage is configured, the button text must be `Alert every Nkm`
- tapping the alert button opens a dialog
- the dialog contains two editable fields:
  - alert text
  - alert mileage
- alert mileage is required when saving an alert configuration
- empty alert mileage must be treated as a validation error
- the dialog must include a `Remove alert` button
- tapping `Remove alert` removes the alert configuration for that part

## Spec Changes
- Add:
  - Per-part `alertMileage` field in kilometers
  - Per-part `alertText` field for notification content
  - Notification behavior for maintenance alerts
  - Cyclical alert rules based on repeated mileage intervals
  - Edit Part UI rule for displaying current alert state as a button
  - Alert configuration dialog with alert text field, alert mileage field, and `Remove alert` action
- Change:
  - Part data model to include alert configuration
  - Ride-processing behavior so threshold crossings are detected during mileage updates
  - Part edit UI to allow configuring, updating, and removing alert settings
- Remove:
  - None

## Acceptance Criteria
- [ ] A user can configure alert settings for a bike part from the Edit Part screen
- [ ] The Edit Part screen shows an alert button for each editable part
- [ ] If a part has no alert configured, the button text is `Alert disabled`
- [ ] If a part has alert mileage configured, the button text is `Alert every Nkm`
- [ ] Tapping the alert button opens a dialog with editable fields for alert text and alert mileage
- [ ] Saving an alert with empty mileage is rejected as a validation error
- [ ] The dialog includes a `Remove alert` button
- [ ] Tapping `Remove alert` clears the alert configuration for that part
- [ ] Alert mileage and alert text are stored per part and remain available after app restart
- [ ] When a part reaches its configured mileage interval, the system shows an Android alert/notification
- [ ] The notification is visible even when kxgear is in the background and another app is active
- [ ] Alerts repeat every `N` kilometers for a part configured with alert mileage `N`
- [ ] The system does not emit duplicate alerts for the same threshold crossing
- [ ] Parts without alert mileage configured do not generate alerts

## Impact
- API:
  - No external API changes expected
- DB:
  - Local persisted part schema must be extended with `alertMileage`
  - Local persisted part schema must be extended with `alertText`
  - Notification progress/state may require persisted tracking to avoid duplicate alerts
- Performance:
  - Minimal additional processing during ride mileage updates to detect threshold crossings
- Risks:
  - Duplicate alerts if threshold state is not tracked correctly
  - Missed alerts if background notification flow is not integrated with Android lifecycle constraints
  - Unit mismatch risk because alert mileage is entered in kilometers while internal mileage is stored in meters

## Follow-up
- Update spec.md
- Regenerate plan.md
- Regenerate tasks.md
