# Data Model: Bike Parts Mileage Management

## Entities

### Local Bike Record

- `bikeId`: local file identity used by kxgear routes and JSON storage
- `karooBikeId`: legacy nullable field, not populated by current local bike
  management
- `name`: user-managed bike name
- `karooMileageMeters`: bike mileage stored in meters
- `createdAt`: local record creation time
- `updatedAt`: last local update time

**Rules**

- One JSON file per local bike record.
- Created, renamed, deleted, opened, and activated from the kxgear bike list.
- Mileage is stored in meters.

### Bike File

- `version`
- `bike: Local Bike Record`
- `parts: List<Part>`
- `rideCursor: RideCursor`
- `lastUpdatedAt`

**Rules**

- Canonical persisted state for one bike and all of its part history.
- Remains the boundary for atomic writes.

### Part

- `partId`
- `name`
- `riddenMileage`
- `status`
- `createdAt`
- `createdDate`
- `updatedAt`
- `archivedAt`

**Rules**

- Parts remain attached to their local bike record.
- Part names are user-facing labels and do not have to be unique within a bike.
- Installed parts gain ridden mileage from accepted ride deltas.
- Archived parts keep their final mileage and stop accumulating.
- Ridden and current mileage are stored in meters.
- `createdDate` stores the part creation timestamp for UI display and is set
  when the user creates a part from Add Part or Replace Part.
- For legacy persisted parts that do not yet store `createdDate`, the app
  derives it from `createdAt` during load.

### Ride Cursor

- `lastAcceptedMetricValue`
- `lastAcceptedAt`

**Rules**

- Stores the last accepted cumulative ride distance.
- Guards duplicate and decreasing ride inputs.

### Shared Metadata

- `activeBikeId`
- `bikeIndex: List<BikeSummary>`

**Rules**

- Contains local bike list entries.
- Keeps `activeBikeId` when the chosen bike remains in the local bike index.
- Sets `activeBikeId` to the first newly added bike only when no active bike is
  selected.
- Clears `activeBikeId` when the active bike is deleted.

## Relationships

- One `Local Bike Record` owns one `Bike File`.
- One `Bike File` owns many `Part` entries.
- One `Bike File` owns one `Ride Cursor`.
- `Shared Metadata` references visible `Local Bike Record` entries by `bikeId`.

## State Transitions

### Bike Lifecycle

1. Add Bike creates one local bike file and updates the local bike index.
2. Edit Bike updates the bike name and current mileage without changing parts.
3. Delete Bike removes the local bike file and clears active selection when the
   deleted bike was active.
4. Activate updates local metadata to point at the selected local bike.
5. Loading the bike list rebuilds metadata from local bike files and clears
   stale active-bike references.

### Ride Processing

1. Accept a new cumulative distance event for the active local bike.
2. Apply ride delta to installed parts immediately.
3. Persist when 100 additional meters have accumulated since the last persisted
   bike file, or when the ride ends.

## Validation Rules

- Bike names must be non-blank.
- Bike mileage must be a non-negative whole number in meters.
- Duplicate or decreasing ride distance values must not mutate stored state.
- Duplicate part names are valid; part lifecycle operations target the stable
  part identifier, not the part name.
- User-entered ridden mileage accepts meters before validation and persistence.
- Part creation date display uses `DD.MM.YY` format in the part panel.
