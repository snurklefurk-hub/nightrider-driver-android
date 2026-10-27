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

## Unified LIGHT / DARK appearance

The preview uses a **single LIGHT / DARK selector** for both its UI and vector
basemap. LIGHT = Frutiger Aero controls with rounded humanist typography plus
NightRider Midnight (recolored OpenFreeMap Fiord vectors). DARK = Classic
NightRider controls plus unmodified OpenFreeMap Original Dark vectors.

The choice is stored locally as `aero_ui`, independently from web browsers
and the production Driver app; the old independent `map_mode` preference is
ignored. No API key or MapTiler usage is required.

The demo centers on Dallas. Streets and Junction presets remain available.
Camera pitch and tilt gestures remain locked to zero.

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
