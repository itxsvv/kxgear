# Contract: JSON Storage

## Purpose

Define the persistence behavior for local bike records, parts, and ride
cursor data using JSON files only with one bike stored per file.

## Storage Shape

- One JSON file per local bike record, e.g. `bikes/<bike-id>.json`
- Each bike file contains:
  - local bike record identity
  - bike name and mileage in meters
  - installed and archived parts
  - ride cursor state
- One shared metadata file stores only the active-bike selection and visible
  local bike index
- Optional temporary file is used during atomic replacement beside the file
  being written

## Read Rules

- Load bike files independently and validate schema version before use.
- Load shared metadata separately if present.
- Treat malformed JSON as a recoverable repository error, never as partial valid
  state.
- Rebuild the visible bike index from local bike files.

## Write Rules

1. Serialize the target bike file or metadata file to JSON.
2. Write to a temporary file in the same directory.
3. Flush and close the temporary file.
4. Replace the target canonical JSON file atomically.
5. Only acknowledge success after replacement completes.

## Guarantees

- A bike update, ride update, part archival, or part replacement within
  one bike is either fully persisted in that bike file or not persisted at all.
- Local bike rename and mileage edits preserve that bike file's existing parts
  and ride cursor data.
- Shared metadata preserves an existing visible active bike and clears active
  selection when the active bike file no longer exists.
- Mileage values are stored in meters.
- No SQL database or Room layer is used.
- File I/O concerns stay inside the repository/storage layer.

## Failure Semantics

- If serialization fails, keep the previous file untouched.
- If atomic replace fails, keep the previous canonical file untouched.
- If startup reads invalid JSON, surface an explicit repository error path for
  recovery handling.
