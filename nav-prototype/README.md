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

## Two ecosystem map modes

The standalone preview uses the same two **vector basemap modes** as the
NightRider Rider website, Dispatch map and desktop web map lab:

1. **Light · NightRider Midnight** — Fiord vector geometry recolored with the
   NightRider navy-charcoal land, brighter road hierarchy and readable labels.
2. **Dark · OpenFreeMap Original Dark** — upstream vector style without paint
   overrides.

The selected map mode persists locally in Android `SharedPreferences`; on the
first launch it follows the Android system light/dark appearance. Website modes
use browser localStorage; **preferences are per-device, not synced via an
account API**. This changes only the *map basemap*, not the full application
interface. Neither mode requires MapTiler or any API key.

The demo centers on Dallas, Texas. Streets and Junction presets let you
compare both modes at fixed camera positions and zooms. Camera pitch and tilt
gestures remain locked to zero. Map provider attribution stays visible.

## Stage B limitations and device checklist

**Not turn-by-turn navigation.** There is intentionally no live position,
route, guidance, GPS permission, or SmartZoom yet (those are Stage C/D tasks).
Verify on a real device before integrating:
- Compare Light Midnight against Dark Original at
  identical camera positions. Midnight should keep dark land while revealing
  local roads, major roads, highway ramps and readable street labels.
- Pitch stays flat after gestures and style switches.
- Pan, pinch zoom, and both preset buttons work.
- Rendering is smooth without native crashes (check Vulkan/OpenGL compatibility).
- Map attribution is visible, styles load on mobile data, and offline failures
  fail gracefully. Verify both map modes with Wi-Fi and mobile data.

## Development dependencies and cost

This prototype pins `org.maplibre.gl:android-sdk:13.6.1` from Maven Central.
MapLibre Android is BSD-licensed. OpenFreeMap's hosted public styles currently
advertise free use without API keys; they require attribution and availability
is not guaranteed. No MapTiler integration is loaded or billed by the preview.
No paid service is provisioned by this repository.

Stage C can add static road-aligned route overlays and simulated SmartZoom.
Stage D requires explicit approval to connect ride actions and navigation
lifecycle; the original driver app is intentionally untouched.
