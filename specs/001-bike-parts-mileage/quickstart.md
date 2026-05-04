# Quickstart: Bike Parts Mileage Management

## Goal

Implement local bike management with preserved part history, bike mileage stored
in meters, startup Karoo bike discovery and automatic imports, part
creation dates visible in the parts list, and ride persistence every 100 meters
or at ride end.

## Recommended Build Order

1. Keep the local bike model and JSON storage shape as one file per bike.
2. Implement startup Karoo bike fetch, name-based comparison, and missing-bike
   auto-import before normal bike-list interaction.
3. Implement local bike add, edit, delete, view, and activate flows from the
   bike list screen.
4. Ensure the first locally added or imported bike becomes active only when no
   active bike is selected.
5. Keep active-bike selection and part management attached to local bikes.
6. Show bike and part mileage in meters.
7. Verify every-event ride updates still persist only every 100 meters or at
   ride end.

## Minimum Verification Scenarios

1. Start the app with no bikes and confirm the empty state plus Add Bike button.
2. Start the app with Karoo bikes available but missing locally and confirm the
   imported bikes appear locally automatically with persisted `karooBikeId`.
3. Start the app with a local bike whose name already matches a Karoo bike and
   confirm no duplicate prompt appears while the local bike gains `karooBikeId`.
4. Verify any locally created bike still persists `karooBikeId = null`.
5. Add a second bike through service or test fixture and confirm the existing
   active bike is not replaced.
6. Edit a bike name and mileage and confirm existing parts remain attached.
7. Delete an inactive bike and confirm the active bike stays selected.
8. Delete the active bike and confirm active selection is cleared.
9. Open a bike profile and confirm bike mileage is shown in meters.
10. Add, edit, archive, delete, and replace parts on a local bike.
11. Add a part and confirm its panel shows a Created row with the save date in
   `DD.MM.YY` format.
12. Replace a part and confirm the replacement part receives a new Created date
    while the archived part keeps its original creation date.
13. Add or edit two parts so they share the same name and confirm both remain
   independently manageable.
14. Enter ridden part mileage in meters and confirm stored part mileage remains
   meter-based.
15. Process ride distance updates and confirm installed part mileage updates on
   every accepted event.
16. Confirm no disk write occurs before 100 additional meters have accumulated.
17. Confirm the latest pending ride state persists when the ride ends before the
   next 100-meter threshold.
18. Restart the app and confirm local bikes, active-bike selection, bike
    mileage, parts, creation dates, and ride cursor state reload correctly.
