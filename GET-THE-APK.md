# Build the NightRider Driver APK

Use the signed release for a phone that will receive future updates. The
debug build is only for development testing.

## Signed app for a pilot phone

1. Open this repository's **Releases** page. Choose the newest release whose
   tag begins `nr-driver-v`, then download `NightRider-Driver.apk`. Older
   `driver-app-` releases are debug builds.
2. Move it to the phone and tap it. Allow installs from that source if Android
   asks, then approve the installation.
3. Sign in with the NightRider driver username and password from dispatch.
4. For later versions, open **Settings → Check for updates** in the app.

If the phone still has an older debug-signed copy, Android may reject the
signed update. Uninstall that old copy once, then install the signed release.
Uninstalling clears local sign-in and preferences; backend account and ride
records remain on the server.

## Debug build for development

1. Open the `nightrider-driver-android` repository on GitHub.
2. Select **Actions** → **Build driver app APK**.
3. Open the latest successful run for the branch or pull request you are testing.
4. Download the build artifact from **Artifacts** on that run.

The workflow builds automatically on pull requests and when Android source
changes land on the default branch. Debug builds do not use the permanent
release signing key and cannot update an installed signed release.

The debug artifact can be installed for a separate test, but it cannot replace
the signed release in place. Neither APK is a Play Store release. The signed
release is the pilot update channel described in
[IN-APP-UPDATES.md](IN-APP-UPDATES.md).

