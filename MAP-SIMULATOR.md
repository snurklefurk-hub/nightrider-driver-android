# Driver map simulator

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
