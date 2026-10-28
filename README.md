# NightRider Driver for Android

The NightRider Driver app is for drivers. It connects to the NightRider service
at `https://nightrider.onrender.com` and shares rides and team messages with the
website.

Drivers sign in with their NightRider username and password. Dispatch creates
driver accounts and gives each driver their sign-in details privately. The app
stores only an encrypted session token in Android Keystore; it does not save the
password. Riders and dispatch use the website.

## Get the driver app

The **signed NightRider release** is the update channel for installed pilot
devices. Open this repository's **Releases** page, choose the newest release
whose tag begins `nr-driver-v`, and download its `NightRider-Driver.apk` for a
first installation. Older `driver-app-` releases are debug builds. Once installed, use
**Settings → Check for updates** inside the app for later versions. Android
will ask you to approve each installation. See [IN-APP-UPDATES.md](IN-APP-UPDATES.md)
for the signing and update details.

The separate **Build driver app APK** workflow produces a debug artifact for
development checks. A debug APK uses a different signing key and cannot update
an installed signed release. See [GET-THE-APK.md](GET-THE-APK.md) for both paths.

## Sign in

1. Get your NightRider username and initial password from dispatch.
2. Open the app and enter both credentials.
3. Allow location if you want the app to share your position when accepting a
   ride. The app still works if you decline location sharing.

Use **Settings → Log out** to end the session on the phone. If you forget your
password, contact dispatch; they can reset it after verifying your identity.

## Appearance: LIGHT / DARK

Use the single button at the top right of **Settings** to switch to **LIGHT** (Frutiger Aero glass,
sky blue/green controls and rounded humanist type) or **DARK** (the original
NightRider Verdant styling). DARK remains the default and the choice persists
on this device without changing the signed-in session or ride state.

The embedded driver map, simulator and GPS Field Test follow this setting,
with a road-contrast palette tuned for the driver view. There is no separate
map style switch. The standalone **Map Preview** test app has its own local
setting: LIGHT selects NightRider Midnight and DARK selects OpenFreeMap
Original Dark.

## Driver map and testing

Open the driver map from the main board to see your position, ride endpoints
and an estimated route. The map includes a flat heading-up camera, SmartZoom,
GPS quality filtering and motion smoothing. It does not provide turn-by-turn
guidance or automatic rerouting.

**Settings → Map simulator** replays bundled Savannah, Talmadge Bridge and
Hilton Head sample journeys without using real GPS or changing rides.
**Settings → Field test (live GPS)** tests the same map with actual location,
local CSV export, optional motion fusion and explicitly consented screen
video recording without audio. See [MAP-SIMULATOR.md](MAP-SIMULATOR.md) for
controls, recording and trace review in the website's Map Lab.

## Build from source

The Android workflows install Java 17 and Gradle 8.7. Android Studio is
optional. Both the production driver module and the separate `nav-prototype/`
appearance preview use the pinned MapLibre Android SDK.

Run `python -m unittest discover -s tests -p 'test_*.py' -v` for source/package
checks and `gradle testDebugUnitTest assembleDebug` for the Kotlin unit tests
and development APK. The signed-release workflow also runs the source and
Kotlin checks before building an update.

## Permissions

The app uses internet access, network state, optional location access, a
keep-screen-on setting and Android's package installer for approved updates.
Field Test screen recording requests Android's screen-capture consent for
each session and runs a visible foreground service. CSV/MP4 exports use
Android's file picker. No microphone, camera, contacts or broad storage
permissions are requested.

