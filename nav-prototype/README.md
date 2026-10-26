# NightRider Map Lab — isolated Stage B prototype

This is a **separate Android application** (`com.nightrider.navpreview`), not
a new screen in the installed NightRider Driver app (`com.nightrider.driver`).

- No ride/API code, driver session, background service, location permissions,
  authentication, or dispatch communication.
- No changes to the production Android `app/` module or NightRider website.
- Does not trigger the main Android signed-update workflow or reuse its signing key.
- Its debug APK is for testing only and may require reinstalling if CI's debug
  certificate changes. It cannot update the separately installed driver app.

## Run it

On GitHub choose **Actions → Build isolated MapLibre preview**, then download
the `NightRider-Map-Preview` artifact, or open the `nav-prototype` folder as
its own project in Android Studio and run `:prototype:assembleDebug`.
Install its APK on Android 8+ as a second, separate app.

## What to compare

The demo centers on publicly chosen **Dallas, Texas** test locations (city
streets and an interchange), not your actual location. Switch among:
1. **NightRider Midnight** — DEFAULT experimental midtone. Fiord vector
   layers with darker navy land, subdued water, brighter class-differentiated
   roads, and readable labels. It preserves the Fiord tile sources, underlying
   geometry and attribution. Runtime paint overrides are not a final hosted
   production style.
2. **OpenFreeMap Fiord** — original blue-toned lighter candidate, no key.
3. **OpenFreeMap Dark** — original darker candidate, no key.
4. **MapTiler Streets Dark v4** — optional hosted comparison; enter your own
   MapTiler key at runtime. Nothing is written to local preferences or Git.
   The older Streets Night v2 style is deprecated; v4 Dark is used instead.

Camera pitch and tilt gestures are locked to zero; pan and zoom remain usable.
Change styles without moving the map to compare identical roads/zoom levels.
The preset buttons restore fixed test views. Default MapLibre attribution
controls must remain visible and map providers' licensing must be followed.

## Stage B limitations and device checklist

**Not turn-by-turn navigation.** There is intentionally no live position,
route, guidance, GPS permission, or SmartZoom yet (those are Stage C/D tasks).
Verify on a real device before integrating:
- Compare NightRider Midnight against both unmodified OpenFreeMap styles at
  identical camera positions. Midnight should keep dark land while revealing
  local roads, major roads, highway ramps and readable street labels.
- Pitch stays flat after gestures and style switches.
- Pan, pinch zoom, and both preset buttons work.
- Rendering is smooth without native crashes (check Vulkan/OpenGL compatibility).
- Map attribution is visible, styles load on mobile data, and offline failures
  fail gracefully. Test MapTiler with a test-only API key if desired.

## Development dependencies and cost

This prototype pins `org.maplibre.gl:android-sdk:13.6.1` from Maven Central.
MapLibre Android is BSD-licensed. OpenFreeMap's hosted public styles currently
advertise free use without API keys; they require attribution and availability
is not guaranteed. MapTiler requires its own API key and its pricing/terms apply.
No paid service is provisioned by this repository.

Stage C can add static road-aligned route overlays and simulated SmartZoom.
Stage D requires explicit approval to connect ride actions and navigation
lifecycle; the original driver app is intentionally untouched.
