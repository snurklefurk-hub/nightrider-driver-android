# Build the NightRider Driver APK

Use GitHub Actions to build the current driver app. The APK included in older
source archives does not support server-backed NightRider accounts and should
not be installed.

## Build and download

1. Open the `nightrider-driver-android` repository on GitHub.
2. Select **Actions** → **Build driver app APK**.
3. For a pull request build, open the latest run. For a default-branch build,
   open the latest run or the repository's **Releases** page.
4. Download `NightRider-Driver.apk` from **Artifacts** (or Releases for a
   default-branch build).

The workflow builds automatically on pull requests and when Android source
changes land on the default branch. It installs Java 17 and Gradle 8.7 on its
build computer; Android Studio is optional.

## Install on Android

1. Move the APK to the phone and tap it.
2. If Android asks, allow installs from that source, then finish installing.
3. If upgrading an older NightRider app reports a signature conflict, uninstall
   the old app first, then install the new APK.
4. Open the app and sign in with the NightRider driver username and password
   provided by dispatch.

This debug APK is for the NightRider pilot. It is not a Play Store release.

