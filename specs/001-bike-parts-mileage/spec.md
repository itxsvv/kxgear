# Feature Specification: Bike Parts Mileage Management

**Feature Branch**: `001-bike-parts-mileage`  
**Created**: 2026-04-07  
**Status**: Draft  
**Input**: User description: "As a cyclist, I want kxgear to manage bikes locally from the bike list screen while using Karoo ride distance to track part mileage."

## Clarifications

### Session 2026-04-15

- Q: What happens during rides? → A: Ride distance updates part mileage on every
  accepted event, but persistence occurs every 100 meters of additional distance
  or when the ride ends.

### Session 2026-04-17

- Q: How is Karoo ride distance interpreted across ride starts and stops? → A:
  Karoo distance is treated as a cumulative meter value within the current
  recording ride only. Starting a new ride resets the local ride-distance
  baseline so the new ride can begin from zero without being rejected as a
  decreasing value.
- Q: Who manages bike existence and names? → A: kxgear manages bikes locally
  from the bike list screen. Karoo bike catalog sync is not used for adding,
  removing, renaming, or listing bikes.
- Q: What does the bike list show? → A: Each bike appears as a panel with bike
  name on the left and bike mileage on the right, View Bike/Edit/Delete actions
  on the second row, and Activate or Active status on the third row. A full
  width Add Bike button appears at the bottom of the list.

### Session 2026-04-18

- Q: Is it acceptable for the Karoo distance stream to stay subscribed while no
  ride is recording? → A: Yes. A continuously subscribed distance stream is
  acceptable because distance events are ignored unless Karoo ride state is
  `RECORDING`; such ignored events must not mutate mileage or trigger disk
  writes.

### Session 2026-04-20

- Q: Which UI actions are allowed while a ride is not idle? → A: Mutating
  actions are disabled unless Karoo ride state is idle. View Bike, Back, and
  system back remain enabled as navigation-only actions.
- Q: How should the app communicate the disabled state? → A: The top bar shows
  "Disabled while riding" on a red background whenever ride state is not idle;
  when ride state is idle, the normal title is shown on a black background.

### Session 2026-04-27

- Q: How should part creation time be tracked and shown? → A: Each part stores
  a `createdDate` timestamp set when the user creates a part from Add Part or
  Replace Part. The Edit Part screen shows that date at the top in `DD.MM.YY`
  format.
- Q: How should recurring maintenance alerts work for parts? → A: Each part
  stores `curAlertMileage`, `targetAlertMileage`, and `alertText` for
  notification content. `curAlertMileage` starts at `0`, increases with
  accepted ride deltas while the alert is enabled, and resets to `0` after an
  alert is emitted because the current value reached or exceeded
  `targetAlertMileage`. Alert settings are managed from the Edit Part screen by
  an alert button that opens a dialog with alert text and target alert mileage
  fields plus a Remove alert action.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Manage Local Bikes in kxgear (Priority: P1)

As a cyclist, I can add, rename, delete, open, and activate bikes directly from
the kxgear bike list so I can manage the bike catalog without changing Karoo
settings.

**Why this priority**: The app cannot manage parts correctly unless its bike
catalog can be created and maintained locally.

**Independent Test**: Can be fully tested by starting with an empty local bike
catalog, adding two bikes from the bike list, renaming one, deleting one, and
activating the remaining bike.

**Acceptance Scenarios**:

1. **Given** the bike list is empty, **When** the user taps Add Bike and saves a
   valid bike name, **Then** kxgear creates a local bike record and shows it in
   the bike list.
2. **Given** a bike exists, **When** the user taps Edit and saves a new valid
   name, **Then** the bike is renamed without changing its parts or ride
   history.
3. **Given** a bike exists, **When** the user taps Delete, **Then** the bike is
   removed from the visible list and cannot receive future ride updates.
4. **Given** a bike is not active, **When** the user taps Activate, **Then** the
   bike becomes the active bike for ride processing.
5. **Given** a bike is active, **When** the list is shown, **Then** its third
   row shows "Active" instead of an Activate button.
6. **Given** at least one bike exists, **When** the bike list is shown, **Then**
   each bike panel shows name and bike mileage on the first row, View
   Bike/Edit/Delete actions on the second row, and active state on the third
   row.

---

### User Story 2 - Keep Local Parts Attached to Local Bikes (Priority: P2)

As a cyclist, I can continue managing parts for each local bike so that bike
catalog edits do not erase maintenance history attached to that bike.

**Why this priority**: Part history is the core user value, so bike catalog
changes must not erase or duplicate the existing local part records.

**Independent Test**: Can be fully tested by creating a local bike with parts,
then confirming its parts remain available after bike rename and active-bike
changes.

**Acceptance Scenarios**:

1. **Given** a local bike record already has installed and archived parts,
   **When** the user renames that bike, **Then** the existing parts remain
   attached to that bike.
2. **Given** a local bike exists, **When** the user opens View Bike, **Then**
   installed and archived parts are shown for that bike.
3. **Given** a local bike exists, **When** the user manages parts on that bike,
   **Then** installed and archived parts continue to support add, edit, archive,
   delete, and replace actions.
4. **Given** the user creates a part from Add Part or Replace Part, **When**
   the change is saved, **Then** the new part stores the current creation time.
5. **Given** the user opens Edit Part for an existing part, **When** the screen
   is rendered, **Then** it shows the part creation date at the top of the
   screen in `DD.MM.YY` format.
6. **Given** the user opens Edit Part, **When** alert settings are not
   configured for that part, **Then** the screen shows an alert button labeled
   `Alert 0.0km / 0.0km`.
7. **Given** the user opens Edit Part, **When** alert settings are configured
   for that part, **Then** the screen shows an alert button labeled
   `Alert curAlertMileage km / targetAlertMileage km`.
8. **Given** the user taps the alert button on Edit Part, **When** the alert
   dialog opens, **Then** it shows editable fields for alert text and alert
   target mileage plus a `Remove alert` action.

---

### User Story 3 - Track Ride Distance Efficiently for Local Bikes (Priority: P3)

As a cyclist, I can have ride distance applied to the selected local bike’s
installed parts while keeping writes efficient so the app stays responsive and
safe on-device.

**Why this priority**: Part mileage must continue to update correctly during
rides while bikes are managed locally.

**Independent Test**: Can be fully tested by selecting a local bike, replaying
recording-state changes and ride distance updates, and verifying part mileage
updates only during recording while persistence occurs every 100 meters or when
recording ends.

**Acceptance Scenarios**:

1. **Given** a local bike is active and has installed parts, **When** a ride
   distance update arrives with a higher cumulative value, **Then** installed
   part mileage updates for that bike on that event.
2. **Given** less than 100 additional meters have accumulated since the last
   persisted ride state, **When** another event arrives, **Then** the in-memory
   bike state reflects the new distance but no disk write occurs yet.
3. **Given** 100 or more additional meters have accumulated since the last
   persisted ride state, **When** the update is processed, **Then** the bike
   file is persisted with the latest ride and part mileage state.
4. **Given** a ride ends before 100 additional meters have been persisted,
   **When** the ride stream completes or stops, **Then** the pending ride state
   is persisted once.
5. **Given** one ride has accepted a cumulative distance greater than zero,
   **When** a later ride starts and Karoo reports distance from zero again,
   **Then** the new ride distance is accepted against a fresh baseline instead
   of being rejected as decreasing.
6. **Given** the ride is idle or paused, **When** distance updates arrive,
   **Then** those updates do not increase part mileage.
7. **Given** a part has `targetAlertMileage` configured, **When**
   `curAlertMileage + delta` reaches or exceeds that target, **Then** the
   system shows a maintenance alert and resets `curAlertMileage` to `0`.
8. **Given** a single mileage update exceeds the remaining alert distance by
   more than one target interval, **When** the update is processed, **Then**
   the system still shows one alert and resets `curAlertMileage` to `0`.

---

### User Story 4 - Prevent Edits While Riding (Priority: P2)

As a cyclist, I can still navigate the app while a ride is active, but cannot
change bikes or parts until the ride is idle, so maintenance data cannot be
edited during ride recording or pause states.

**Why this priority**: Ride processing and user edits both touch the same local
bike files, so the UI must prevent conflicting changes while a ride is active.

**Independent Test**: Can be fully tested by replaying idle, recording, and
paused ride-state changes and verifying navigation remains available while
mutating actions are disabled outside idle.

**Acceptance Scenarios**:

1. **Given** ride state is idle, **When** the user views the bike list or bike
   details, **Then** mutating actions are enabled and the top bar uses the
   normal title on a black background.
2. **Given** ride state is recording or paused, **When** the user views the bike
   list or bike details, **Then** Add/Edit/Delete/Activate/Add Part/Edit
   Part/Replace/Archive/Delete Part/Save actions are disabled.
3. **Given** ride state is recording or paused, **When** the user taps View Bike
   or Back, **Then** navigation still works.
4. **Given** ride state is recording or paused, **When** the app top bar is
   shown, **Then** it displays "Disabled while riding" on a red background.

### Edge Cases

- What happens when there are no bikes? kxgear shows an empty state and keeps
  the full-width Add Bike button available.
- What happens when the first bike is added and no active bike is selected?
  kxgear sets the new bike as active.
- What happens when a non-active bike is deleted? kxgear removes that bike and
  leaves the current active bike unchanged.
- What happens when the active bike is deleted? kxgear removes that bike and
  clears the active selection.
- What happens when a bike is renamed? kxgear updates the bike name while
  preserving parts and ride history.
- What happens when ride updates are duplicated, out of order, or decreasing?
  Within the same recording ride, the system leaves stored mileage unchanged
  and rejects invalid input safely.
- What happens when a new ride starts and Karoo distance restarts from zero?
  kxgear resets the local ride cursor for the active bike and computes deltas
  from the new ride baseline.
- What happens when distance updates arrive while the ride is not recording?
  kxgear ignores those updates and does not mutate part mileage.
- What happens when the distance stream remains subscribed while no ride is
  recording? This is expected behavior; the app may keep the stream subscribed,
  but it must ignore those events and must not write bike files.
- What happens when a ride ends before another 100 meters have accumulated? The
  pending ride state is persisted once at ride end.
- What happens when ride state is recording or paused and the user is already
  on an edit form? The form fields and Save action are disabled while Back
  remains available.
- What happens when ride state returns to idle? Mutating actions become
  available again and the normal black top bar title returns.
- What happens when an existing persisted part predates `createdDate` storage?
  The app continues to show that part with a creation date derived from its
  persisted creation timestamp.
- What happens when target alert mileage is empty in the alert dialog? Saving is
  rejected as a validation error.
- What happens when a part has no alert configured? The Edit Part button label
  shows `Alert 0.0km / 0.0km` and no maintenance alerts are generated.
- What happens when a mileage update is larger than the remaining alert
  distance? The system emits one alert and resets `curAlertMileage` to `0`.
- What happens when a single mileage update exceeds more than one target
  interval? The system still emits one alert and resets `curAlertMileage` to
  `0`.
- What happens when the user no longer wants alerts for a part? The user can
  remove the alert configuration from the Edit Part dialog.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST load the local bike catalog from persisted bike
  files when kxgear starts.
- **FR-002**: The system MUST NOT use the Karoo bike catalog to create, remove,
  rename, list, or activate bikes.
- **FR-003**: The system MUST allow users to add bikes from the bike list
  screen.
- **FR-004**: The system MUST allow users to rename bikes from the bike list
  screen.
- **FR-005**: The system MUST allow users to delete bikes from the bike list
  screen.
- **FR-006**: The system MUST allow users to open a bike from the bike list
  screen.
- **FR-007**: The system MUST preserve installed parts, archived parts, and
  ride history when renaming a local bike.
- **FR-008**: The system MUST keep bike data persisted as one JSON file per
  bike.
- **FR-009**: The system MUST continue to allow users to select one local bike
  as the active bike for ride processing.
- **FR-009a**: When no active bike is selected and the user adds a bike, the
  system MUST set the newly added bike as active.
- **FR-009b**: When the active bike is deleted, the system MUST clear the active
  bike selection.
- **FR-009c**: When a non-active bike is deleted, the system MUST keep the
  current active bike selection unchanged.
- **FR-010**: The system MUST continue to support part add, edit, archive,
  delete, and replacement flows on local bikes.
- **FR-010a**: The system MUST allow multiple parts on the same bike to have
  the same part name.
- **FR-010b**: Each part MUST persist a `createdDate` field.
- **FR-010c**: When a user creates a part from Add Part or Replace Part, the
  system MUST set that part's `createdDate` to the current time.
- **FR-010d**: Editing, archiving, deleting, ride updates, and bike-level
  changes MUST NOT replace an existing part's `createdDate`.
- **FR-010e**: The Edit Part screen MUST show the part creation date at the top
  of the screen.
- **FR-010f**: The part creation date shown in Edit Part MUST use `DD.MM.YY`
  format.
- **FR-010g**: Each part MUST persist a `curAlertMileage` value that starts at
  `0`.
- **FR-010h**: Each part MUST persist a `targetAlertMileage` value that starts
  at `0`.
- **FR-010i**: Each part MAY persist an `alertText` value used in maintenance
  alerts.
- **FR-010j**: The Edit Part screen MUST show an alert button for each
  editable part.
- **FR-010k**: The Edit Part alert button MUST show
  `Alert curAlertMileage km / targetAlertMileage km`.
- **FR-010l**: Tapping the alert button on Edit Part MUST open a dialog with
  editable `alertText` and `targetAlertMileage` fields.
- **FR-010m**: Saving alert configuration with empty target alert mileage MUST
  be rejected as a validation error.
- **FR-010n**: The alert dialog MUST include a `Remove alert` action that
  clears the alert configuration for that part and resets both
  `curAlertMileage` and `targetAlertMileage` to `0`.
- **FR-011**: The system MUST show bike mileage on each bike panel in the bike
  list.
- **FR-011a**: The system MUST display user-facing bike mileage in meters.
- **FR-011b**: The system MUST continue to store bike mileage, ridden part
  mileage, and ride cursor values internally in meters.
- **FR-011c**: Ridden part mileage entered by the user MUST be accepted in
  meters before persistence.
- **FR-011d**: The system MUST NOT expose or persist a separate user-entered
  part mileage offset for new writes; editable part mileage is the part's ridden
  mileage.
- **FR-012**: The system MUST update installed part mileage on every accepted
  ride distance event for the active local bike while a ride is recording.
- **FR-013**: The system MUST persist updated ride and part mileage every 100
  meters of additional distance or when ride recording ends, whichever happens
  first.
- **FR-014**: The system MUST ignore duplicate or decreasing cumulative ride
  values within the current recording ride without mutating stored state.
- **FR-014a**: When a new ride recording starts, the system MUST reset the
  active bike's ride-distance baseline so a new Karoo cumulative distance that
  starts at zero can produce valid deltas.
- **FR-014b**: The system MUST ignore distance updates received while the ride
  state is idle or paused.
- **FR-014c**: The system MAY keep the Karoo distance stream subscribed while
  ride state is idle or paused, but those events MUST NOT mutate mileage or
  trigger disk writes.
- **FR-014d**: When alerting is enabled for a part, the system MUST add each
  accepted ride delta to that part's `curAlertMileage`.
- **FR-014e**: When `curAlertMileage` reaches or exceeds `targetAlertMileage`,
  the system MUST show an Android alert/notification visible even if kxgear is
  in the background and another application is active.
- **FR-014f**: After an alert is emitted, the system MUST reset that part's
  `curAlertMileage` to `0`.
- **FR-014g**: If a single mileage update exceeds the remaining distance to the
  target by more than one interval, the system MUST still emit one alert and
  reset `curAlertMileage` to `0`.
- **FR-015**: The bike list MUST render each bike as a panel whose first row
  shows bike name on the left and bike mileage on the right.
- **FR-016**: The bike list MUST render View Bike on the left, Edit in the
  center, and Delete on the right in each bike panel's second row.
- **FR-017**: The bike list MUST render Activate for inactive bikes and Active
  text for the active bike in each bike panel's third row.
- **FR-018**: The bike list MUST render a full-width Add Bike button at the
  bottom of the list.
- **FR-019**: The system MUST derive a UI mutation gate from Karoo ride state.
- **FR-020**: The system MUST enable bike and part mutating actions only when
  ride state is idle.
- **FR-021**: The system MUST keep View Bike, Back, and system back navigation
  enabled regardless of ride state.
- **FR-022**: When ride state is recording or paused, the system MUST disable
  Add Bike, Edit Bike, Delete Bike, Activate, Add Part, Edit Part, Replace,
  Archive, Delete Part, and Save actions.
- **FR-023**: When ride state is recording or paused, the app top bar MUST show
  "Disabled while riding" on a red background.
- **FR-024**: When ride state is idle, the app top bar MUST show the normal
  title on a black background.

### Quality and Integrity Requirements

- Bike list ordering and bike profile updates MUST remain deterministic when
  the same local bike files are loaded multiple times.
- Local bike state MUST preserve part history and ride cursor data whenever a
  local bike is renamed or activated.
- Ride distance processing MUST remain testable in isolation from Karoo source
  types.
- Ride distance processing MUST distinguish ride-session boundaries from
  invalid decreasing values within the same ride.
- Bike lifecycle management MUST remain clearly separated from JSON persistence
  and part lifecycle logic.
- Ride-state UI gating MUST remain clearly separated from bike and part
  lifecycle business logic.
- Invalid, duplicate, or decreasing ride updates MUST NOT create partial or
  contradictory persisted state.

### Key Entities *(include if feature involves data)*

- **Local Bike Record**: The persisted bike file used by kxgear to store bike
  name, bike mileage, part history, and ride cursor data.
- **Part**: An installed or archived maintenance item whose current mileage is
  derived from ridden mileage and that also stores its creation date,
  `curAlertMileage`, `targetAlertMileage`, and optional alert text.
- **Ride Cursor**: The last accepted cumulative ride distance used to derive
  deltas safely within the current recording ride.
- **Ride Session**: A recording interval whose cumulative Karoo distance starts
  from its own baseline and is flushed when recording stops or pauses.
- **Ride-State UI Gate**: The current ride-state-derived permission that allows
  navigation at all times but allows bike and part mutations only while idle.
- **Part Alert Configuration**: The per-part current alert progress, target
  alert distance, and alert text used to decide when maintenance notifications
  should be shown.
- **Shared Metadata**: Lightweight cross-bike state containing the active bike
  selection and sorted local bike index.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of bikes added from the bike list become visible without an
  app restart.
- **SC-002**: 100% of bike renames preserve that bike's parts and ride history.
- **SC-003**: 100% of deleted bikes are removed from the visible list and, if
  active, no longer remain selected as active.
- **SC-004**: Ride processing persists no more frequently than every 100 meters
  of additional distance during a recording ride, while still persisting the
  final pending state when recording ends.
- **SC-005**: In 100% of cases where the first local bike is added and no bike
  is active, the newly added bike becomes active.
- **SC-006**: 100% of user-facing bike mileage displays use meters, matching
  saved mileage units.
- **SC-007**: After ending a ride and starting another ride, a new Karoo
  distance value beginning from zero is accepted for the active bike in 100% of
  cases where the bike remains available.
- **SC-008**: In 100% of recording or paused ride states, mutating bike and
  part UI actions are disabled while View Bike and Back remain usable.
- **SC-009**: In 100% of non-idle ride states shown in the app UI, the top bar
  displays "Disabled while riding" on a red background.
- **SC-010**: In 100% of newly added or replacement parts, the stored creation
  date matches the save time and is available in Edit Part in `DD.MM.YY`
  format.
- **SC-011**: In 100% of parts with alert settings saved from Edit Part,
  `curAlertMileage`, `targetAlertMileage`, and alert text persist across app
  restart.
- **SC-012**: In 100% of accepted ride updates for a part with alerts enabled,
  the system increments `curAlertMileage` by the accepted ride delta until an
  alert is emitted.
- **SC-013**: In 100% of cases where `curAlertMileage` reaches or exceeds
  `targetAlertMileage`, the system emits one maintenance alert and resets
  `curAlertMileage` to `0`.
- **SC-014**: In 100% of parts without alert configuration, no maintenance
  alerts are emitted.

## Assumptions

- Karoo remains the source of ride distance events, but not the source of the
  bike catalog.
- Local active-bike selection remains part of kxgear.
- Bike deletion is a local delete action for the selected bike record.
- Karoo ride distance is cumulative for the currently recording ride and may
  restart from zero on the next recording ride.
- Idle is the only ride state that permits bike or part mutations from the UI.
