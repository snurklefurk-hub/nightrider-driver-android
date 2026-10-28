# Driver map simulator

## Road testing on an actual phone (Field Test)

From **Settings → Field test (live GPS)**, you can separately test the production
MapLibre map with the phone's real location **without creating a ride**. Unlike
the simulator, this screen requires Android location permission and map internet
access. You do **not** need to be signed into a driver account.

1. **While parked**, open Field Test and allow precise location if comfortable.
   The map centers on your first usable GPS fix. GPS accuracy, speed, and fix age
   are shown so poor fixes can be distinguished from map problems.
2. Long-press anywhere on the map to place a **test destination**. Its planned
   route is requested once from public OSRM. Tap **Route again** to refresh
   manually; routes have no traffic or spoken guidance.
3. Tap **Record new trace** before moving. Orange shows your recorded GPS trail;
   green shows the OSRM planned route. The top line shows distance from the
   planned route when the fix is accurate enough. A >80m discrepancy is flagged
   for testing, **not** used for actual navigation.
4. Pan the map to disable Follow. Tap **Recenter** to resume following your GPS.
   **Stop recording** ends collection; **Clear trace** discards it.
5. Use **Save CSV** to choose a destination with Android's file picker, then
   open that file in the Web Map Lab for a local overlay. **Share text** instead
   passes the precise coordinates to an app you explicitly select.

Privacy and scope: location fixes and trails stay in memory, are never
persisted across screen closure, and are never sent to NightRider, a dispatch
board, a ride, or another driver. Saving or sharing via CSV is opt-in. A route request
does send the **current GPS and chosen destination coordinates** to public OSRM;
the map itself loads OpenFreeMap. The screen requests location only while it is
visible, and automatically stops recording on leaving. There is no background
tracking. Up to 1,500 timestamped GPS fixes are retained in memory; older
samples drop from the visual trail and CSV. This is a test tool, **not**
turn-by-turn navigation. Have a passenger operate it or park before interacting.
GPS traces are approximate and do not establish a verified traveled route.


Install the latest signed driver update, open **Settings → Map simulator**.
It uses the actual driver map with synthetic journeys. No account or location
permission is required for the simulator. It does not send a driver's real
position, create rides, change ride status, or send messages to dispatch.
Map tiles still load from OpenFreeMap and need internet access.

- Pick Savannah streets, Talmadge Bridge, or Hilton Head.
- Press **Play** to travel toward pickup. Playback stops at pickup; press
  **Start trip** to continue to the destination. It stops there too.
- **Next stage** previews Assigned, Heading to pickup, At pickup, Heading to
  destination, At destination, and Completed, then returns to Assigned.
- Tap the speed button to cycle through 1×, 4×, 10× and 30×. At 1×, the simulated
  vehicle travels at a constant 30 mph; this is not a realistic traffic model.
- Drag the progress slider to jump along the current leg. Scrubbing pauses playback.
- The GPS button cycles between OK, lost signal, and a repeatable off-route offset.
  The planned route stays visible, which makes discrepancies easy to inspect.
- Turn **Follow** off to explore the map. **Recenter** returns to the simulated vehicle.
- **Restart** returns to the beginning. Leaving the screen suspends playback.

Suggested check: play to pickup, start the trip, pause and scrub, try lost GPS,
restore GPS, try off-route, and repeat in LIGHT and DARK (selected in Settings).
When the simulator opens again, the current Settings appearance is applied.

This is a map playback environment. The driver app does not yet implement
turn-by-turn guidance or rerouting; the new speed-sensitive, flat SmartZoom
camera is a visual preview, not a turn-aware navigation system. The deterministic playback engine has local
unit tests; Android CI runs them before assembling the APK.

The bundled synthetic journeys were generated from public sample coordinates
using OSRM on OpenStreetMap data. They are repeatable test fixtures, not a record
of a real driver. Route playback does not make OSRM requests. Source route data:
https://router.project-osrm.org/ and https://www.openstreetmap.org/copyright

## Classic navigation presentation preview

The live driver map, simulator, and isolated Field Test now share a map-first
presentation inspired by classic rideshare navigation. The map receives a large
top destination banner, a compact lower trip estimate/speed panel, a blue
vehicle-position arrow, and high-contrast blue route geometry. Appearance still
follows the single Settings LIGHT/DARK choice; no additional map toggle.

The **2D heading-up camera** follows movement while using speed-based zoom:
closer at low speeds, wider on faster roads, always zero pitch. It leaves extra
visible road ahead of the driver's position. Dragging the map disables follow;
tap the on-map **◎ Recenter** button to resume. The simulator's Follow option
continues to work independently. In Field Test, the same camera uses real GPS
while leaving recorded traces untouched.

Important boundaries: **No turn guidance.** This is a **visual navigation preview**, not a finished
navigation system. It does not claim a next turn, issue spoken guidance, or
reroute automatically. When OSRM returns a route, displayed distance and
duration are estimates without traffic, based on the time of the last request,
not a continuously updated arrival prediction. The simulator's countdown uses
its fixed synthetic playback speed. Long-press Field Test destination selection,
CSV export, and live rider/dispatch isolation remain unchanged. The view
requires a real device test for camera smoothness and readability before release.

Suggested parked/passenger-assisted test: open the simulator, cycle between
Savannah, bridge, and Hilton Head, play at 1×/4×, inspect both themes, drag and
recenter; test GPS lost/off-route to ensure controls remain usable. In Field Test,
choose a safe destination while parked, drive with no interactions, confirm
the highlighted route and camera scale, stop safely, and save a trace.

## Road-first navigation polish (2026-10-27)

The native MapLibre driver canvas now applies a flat road-focused palette in
both LIGHT and DARK. DARK uses charcoal land and a subdued street network with
brighter primary roads and casings to separate intersections; LIGHT uses
soft blue-gray land with bright roads and restrained labels. The blue
navigation route remains the strongest visual element. This changes paint
only on known OpenFreeMap layers and skips missing layers safely; OpenFreeMap,
OSRM, ride APIs and permissions are unchanged.

The heading-up 2D camera now has bounded zoom transitions and can look ahead
on the drawn route for a *sharp geometry bend* while near the route. This is
a visual-only zoom cue, **not** evidence of a turn instruction or a street name.
It does not reroute or give spoken guidance. GPS farther than 70m from the
planned path disables the bend cue. The driver can still pan to stop follow
and use Recenter to resume. Simulator and Field Test remain isolated from
live dispatch actions.

Road-test checklist (park to interact, or let a passenger operate the device):
1. Simulate downtown streets, bridge and Hilton Head in both LIGHT/DARK;
   check local roads, main-road casings, vehicle visibility and route contrast.
2. Compare approach to sharp bends at slow/fast speeds; check that zoom
   transitions are calm and never tilt into 3D.
3. Pan the map, use Recenter, test LOST/OFF-ROUTE simulator GPS.
4. In Field Test, record GPS while safely driving; inspect the map without
   interacting, export CSV once parked, then review it in Web Map Lab.
5. Confirm no false turn banners, audio, or live ride/dispatch changes.

## Raw GPS vs navigation GPS (field trace feedback)

A real phone Field Test trace exposed intermittent, highly inaccurate fixes
(roughly 64–400m accuracy, including recurring positions) among usable GPS
readings. These can make the vehicle marker appear to teleport despite otherwise
smooth driving.

NightRider now records **all** raw Field Test readings as before, including
ones that are too unreliable to drive the camera. The navigation marker,
heading-up camera, Field Test route-offset diagnostic, and new last-known fix
selection use a separate local quality gate:

- Require valid coordinates, timestamp no older than 20 seconds, and reported
  horizontal accuracy within 35 meters.
- Ignore non-monotonic timestamps and physically implausible jumps (>55m/s
  between two fixes).
- Prefer a fresh GPS fix over a newly arriving NETWORK-provider fix for 12
  seconds. An accurate network fix can still be used when GPS goes stale.
- Field Test diagnostics show how many incoming fixes the map ignored.
  Recordings keep the full raw stream to investigate GPS/network problems.

This is **not** map matching or a guarantee of lane-level accuracy. In a long
GPS outage, the location marker can become stale until a credible fix arrives.
The test route and live dispatch behavior are otherwise unchanged. To validate
on a second road test, compare the new ignored-fix count with the raw CSV and
watch whether the arrow avoids sudden jumps; perform any interactions only while
parked or with a passenger operating the test.


## Follow-camera motion smoothing (second real-world trace)

The second Field Test trace contained mostly valid highway-speed GPS fixes
roughly five seconds apart. The earlier quality gate rejected obviously bad
readings, but the driver arrow still jumped up to around 120 meters on each
*valid* fix. Redrawing a marker only on arrival of each GPS fix makes motion
look discontinuous even when position quality is good.

The driver map and isolated Field Test now use a **display-only** position
smoother. When a valid GPS fix arrives, the blue arrow advances gradually
from its current rendered location to that accepted fix; the flat follow
camera tracks the same interpolated position and rotates through the shortest
heading change. Smooth display frames run only while the screen is active;
the simulator retains its own deterministic map movement.

The animation scales to the recent fix interval, capped at 3.6 seconds.
There is **no speculative motion beyond the most recent GPS fix**. This can
make the displayed arrow lag actual motion by several seconds at highway
speeds. After a long GPS outage or a large discontinuity it snaps to the
new *accepted* fix rather than concealing an uncertain recovery. These are
visual-preview behavior choices, not a production navigation guarantee.

Crucially, routing, Field Test route-offset checks, and CSV recording
continue to use their existing original positions. The native marker is
updated in place during animation instead of being recreated on each
animation frame. Pan-to-disable-follow, Recenter, 2D-only rendering,
LIGHT/DARK appearance, no-audio, and simulation isolation remain unchanged.

**Road test**: Install the next signed update. In a parked/passenger-observed
Field Test, verify that the arrow flows rather than hopping at approximately
five-second intervals and that heading changes stay controlled. Use
Recenter and manual pan while parked. Export a new raw CSV, and report
whether the movement feels smoother without noticeably lagging the car.
Never depend on this preview for safety-critical turn instructions.


## Field Test CSV: reported map decisions (next iteration)

New CSV exports retain the original first five columns and append two:
`provider` (gps/network/fused/passive/unknown) and `map_decision`
(accepted/rejected/unrated). The decision matches the **actual GPS fix
quality gate in the running app**, not the Web Map Lab's estimate.

All raw fixes are still stored locally, subject to the same 1s deduplication
and 1,500-fix bound. An `unrated` sample means no corresponding map decision
was recorded; it should not be counted as accepted. The Web Map Lab accepts
both earlier five-column exports and the seven-column enhanced format.
The phone's accepted/rejected count represents only sampled fixes written
to the CSV. It does not reconstruct motion-smoothing animation frames and
cannot independently prove visual smoothness.

Keep any exported precise-location traces private; they are never transmitted
to NightRider servers automatically. Use the Field Test only as a passenger
or with no interaction while driving.


## Opt-in motion-sensor fusion in Field Test

This experimental mode is **OFF by default**. Before driving, secure the
phone to a rigid mount, open **Settings → Driver Map Field Test**, and tap
**Motion fusion: OFF** to turn it on. Keep the phone fixed relative to the
car until the run ends. Do not operate the screen while driving. The feature
is intentionally unavailable in live rides and the deterministic simulator.

The phone samples gyroscope, accelerometer and gravity data while the Field
Test screen is foregrounded. When available, Android's rotation vector is
recorded only as a **phone-orientation diagnostic**, never as uncalibrated car
heading. Gyro yaw is projected onto the measured gravity axis so upright and
flat mounts work without special orientation settings. Once a *valid moving*
GPS course (>=3m/s) anchors the heading, sensor yaw updates the heading-up
camera between location fixes. Estimates expire after 8s without fresh
qualified GPS course, or 1s without a gyroscope update. At very low speed
we fall back to ordinary GPS behavior. No inertial dead-reckoning of position,
voice prompts, premium services, network uploads, or new permissions.

The GPS quality gate, route calculations, recorded raw locations and
display-position smoother remain unchanged. This is an experimental camera
heading assist, **not** turn-by-turn navigation. Moving the phone or
magnetic interference can distort the sensor estimates.

Field Test's info panel shows motion-sensor availability, fused heading,
turn rate and acceleration. New CSV exports append four optional columns
after the original seven: `fusion_heading_deg`, `gyro_yaw_dps`,
`linear_accel_mps2` and `phone_azimuth_deg`. Blank fields mean
disabled/unavailable/not yet GPS-calibrated. The browser Map Lab supports
legacy five/seven-column CSVs as well as this eleven-column format. Traces
remain local and are only exported by explicit tap.

**Suggested test:** while parked, toggle the feature on and record. Have
a passenger observe the compass/heading during a short controlled turn or
U-turn, then stop, save the CSV and report whether the map turned earlier
without snapping or lagging. Compare a run with the toggle OFF if practical.
Do not make route decisions from the experimental navigation view.


## Paired screen video + GPS Field Test (opt-in)

Field Test now has a **Record screen + GPS** button that begins one explicitly
consented Android MediaProjection capture and one synchronized raw GPS trace.
No other screen uses this feature. To run it safely, park first, secure the phone,
open Settings → Driver Map Field Test, choose the map destination if useful,
enable experimental Motion Fusion if desired, and tap Record screen + GPS.

Android asks for screen recording permission **every time**. Select
**A single app → NightRider** when offered to avoid recording other apps.
Only after consent and successful video encoder start does NightRider clear and
begin the paired GPS trace. The Field Test panel shows a visible recording
indicator, a local `SYNC epoch_ms` clock updated on screen, and the encoder
start epoch timestamp. Use the clock on the video to match samples in the CSV
with onscreen camera/arrow jumps. The video has **no microphone or app audio**.

To finish, safely park, tap **Stop screen + GPS** (or the notification Stop
action). Wait until the panel says **MP4 ready**. Tap **Save CSV** and
**Save MP4**; choose a folder for each in Android's document picker. The two
files share a basename `nightrider-field-<epoch_ms>`, with `.csv` and
`.mp4` suffixes. The raw CSV schema remains the established eleven columns
and can still be imported in Web Map Lab; no server side changes are needed.

The MP4 is initially held in NightRider's app-private, non-backed-up files
directory. The app **never uploads it**. After successful MP4 export,
NightRider deletes its internal temporary video file; save the paired CSV
before clearing or starting another trace. Video is capped at ten minutes and
250 MiB, encoded as H.264 in an MP4 at approximately 15 fps without audio.
System screen-capture controls, locking the screen, or foreground loss can end
a recording. The foreground service displays an ongoing recording notification
with an explicit Stop action; the Android 14 media-projection FGS permission and
one-use capture token are required. Never assume capture started until the
Field Test panel confirms that it is recording. Keep the phone securely mounted
and do not interact with the app while driving.

The recording can show private locations, notifications, or account information
if you share the whole screen: review it before sharing. The tool is for
observational debugging; recorded footage is not a driving/navigation aid.

## Vela transition
The simulator and screen-recording Field Test described here are now labeled Legacy in Settings. The main map button uses Vela-powered navigation; its separate Vela navigation test is documented in NAVIGATION-VELA.md.
