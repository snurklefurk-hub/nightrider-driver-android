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

Under **Settings → Appearance**, choose **LIGHT** (Frutiger Aero glass,
sky blue/green controls and rounded humanist type) or **DARK** (the original
NightRider Verdant styling). DARK remains the default and the choice persists
on this device without changing the signed-in session or ride state.

The separate **Map Preview** test app provides the same two combined options.
LIGHT selects the NightRider Midnight vector map; DARK selects OpenFreeMap
Original Dark. There is no longer a separate map style switch. The production
Driver currently has no embedded map.

## Build from source

Both Android workflows install Java 17 and Gradle 8.7. Android Studio is
optional. The production driver module uses only the Android framework; the
separate `nav-prototype/` Map Lab uses MapLibre for visual experiments.

## Permissions

The app uses internet access, network state, optional location access, and a
keep-screen-on setting. It does not request contacts, camera, or files.

