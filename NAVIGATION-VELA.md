# Vela-powered navigation — first implementation

## Source and scope
Upstream: https://github.com/PimpinPumpkin/Vela
Pin: 85eb69c946711df272b54d3f4305e72746ce1035
Focused module: vela-engine (provenance and modifications in vela-engine/NOTICE.md).
GPLv3 source and license are retained; corresponding source is published in this repository.

Vela NavEngine, its route/step model, projection, off-route corridors, arrival logic and
instruction text templates are used directly. Seven upstream NavEngine test suites cover
38 cases. The only imported implementation adjustment removes unused serialization
annotation/import from Geo.kt. There are no speech outputs in the module.

VelaNavigationActivity replaces the main driver board map entry. NightRider adapters own:
- authenticated read-only assignment lookup with NightRider's real ride-stage vocabulary;
- OSRM GeoJSON + full step/lane conversion to Vela routes;
- rejection of stale/coarse/out-of-order fixes before feeding Vela;
- silent consumption of reroute/arrival events with a 15-second network request cooldown;
- stale-position display and unknown-speed treatment;
- a small native MapLibre view and a simple speed-based follow camera;
- isolated test destinations and local GPS CSV export.

The broader Vela app UI, sensor-fusion/location-service pipeline, offline engine,
Google/traffic lookups, voice features and updater have not been imported.
The camera in this slice is a minimal NightRider integration, not Vela's full camera system.

## Operation
Driver board → map: choose the assigned ride already underway when there are several.
Pickup stages: accepted, to_pickup, at_pickup.
Drop-off stages: to_dropoff, at_dropoff.
Closed/unassigned/other-driver rides are excluded. Stage or coordinate changes invalidate
the previous route and stale network responses cannot overwrite the new destination.

Settings → Vela navigation test: does not call NightRider APIs. Long-press a destination.
GPS updates request a one-second interval and zero minimum displacement, so stationary
devices can supply fixes. Test trace collection only happens while the Activity is resumed.
OSRM receives route endpoints; OpenFreeMap receives tile requests. CSV stays local unless
the tester explicitly saves it through Android's document picker.

## Validation
Run:
- python -m unittest discover -s tests -p 'test_*.py' -v
- gradle :vela-engine:test :app:testDebugUnitTest :app:assembleDebug
- gradle :app:connectedDebugAndroidTest (isolated emulator real-screen smoke test)

Regression checks include actual NightRider stage selection, route invalidation, out-of-order
and coarse fixes, stationary heading, stale-position retention and no executed speech/haptics.
The native smoke test launches the Vela test Activity in LIGHT and DARK without an account.

## Remaining field checks
Stationary and moving real-phone GPS, turn timing, rerouting, map marker/camera smoothness,
roundabouts, lane display and poor-network recovery require drive testing. The first screen
shows text instructions and next-turn distance, not Vela's full maneuver/lane artwork.
The legacy Field Test remains solely as a comparison tool. No on-road validation is implied
by successful builds or emulator tests.
