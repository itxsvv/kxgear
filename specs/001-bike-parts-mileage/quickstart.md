# Quickstart: Bike Parts Mileage Management

## Goal

Implement local bike management with preserved part history, bike mileage stored
in meters, part creation dates visible in the parts list, and ride persistence
every 100 meters or at ride end.

## Recommended Build Order

1. Keep the local bike model and JSON storage shape as one file per bike.
2. Implement local bike add, edit, delete, view, and activate flows from the
   bike list screen.
3. Ensure the first locally added bike becomes active only when no active bike
   is selected.
4. Keep active-bike selection and part management attached to local bikes.
5. Show bike and part mileage in meters.
6. Verify every-event ride updates still persist only every 100 meters or at
   ride end.

## Minimum Verification Scenarios

1. Start the app with no bikes and confirm the empty state plus Add Bike button.
2. Add a bike with a name and starting mileage, then confirm it appears and is
   active when no previous active bike existed.
3. Add a second bike and confirm the existing active bike is not replaced.
4. Edit a bike name and mileage and confirm existing parts remain attached.
5. Delete an inactive bike and confirm the active bike stays selected.
6. Delete the active bike and confirm active selection is cleared.
7. Open a bike profile and confirm bike mileage is shown in meters.
8. Add, edit, archive, delete, and replace parts on a local bike.
9. Add a part and confirm its panel shows a Created row with the save date in
   `DD.MM.YY` format.
10. Replace a part and confirm the replacement part receives a new Created date
    while the archived part keeps its original creation date.
11. Add or edit two parts so they share the same name and confirm both remain
   independently manageable.
12. Enter ridden part mileage in meters and confirm stored part mileage remains
   meter-based.
13. Process ride distance updates and confirm installed part mileage updates on
   every accepted event.
14. Confirm no disk write occurs before 100 additional meters have accumulated.
15. Confirm the latest pending ride state persists when the ride ends before the
   next 100-meter threshold.
16. Restart the app and confirm local bikes, active-bike selection, bike
    mileage, parts, creation dates, and ride cursor state reload correctly.
