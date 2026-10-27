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
turn-by-turn guidance, rerouting or SmartZoom, so these controls do not claim
to test those future features. The deterministic playback engine has local
unit tests; Android CI runs them before assembling the APK.

The bundled synthetic journeys were generated from public sample coordinates
using OSRM on OpenStreetMap data. They are repeatable test fixtures, not a record
of a real driver. Route playback does not make OSRM requests. Source route data:
https://router.project-osrm.org/ and https://www.openstreetmap.org/copyright
