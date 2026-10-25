# NightRider Driver for Android

The NightRider Driver app is for drivers. It connects to the NightRider service
at `https://nightrider.onrender.com` and shares rides and team messages with the
website.

Drivers sign in with their NightRider username and password. Dispatch creates
driver accounts and gives each driver their sign-in details privately. The app
stores only an encrypted session token in Android Keystore; it does not save the
password. Riders and dispatch use the website.

## Get a current APK

Build the APK from GitHub Actions:

1. After the account-enabled code is merged, open this repository's **Actions**
   tab.
2. Select **Build driver app APK** and choose **Run workflow**, or open the
   automatic build from the latest code change.
3. Download `NightRider-Driver.apk` from the run's **Artifacts** box. Builds on
   the default branch are also published on the **Releases** page.

The APK included in older source archives predates NightRider server accounts.
Do not install it. Install the APK from a build of the account-enabled source.
If Android reports a signature conflict when replacing an older copy, uninstall
the old app, then install the new APK.

To install, move the APK to the Android phone, tap it, allow installs from that
source when prompted, and finish installation. This is a debug build for the
NightRider pilot, not a Play Store release.

## Sign in

1. Get your NightRider username and initial password from dispatch.
2. Open the app and enter both credentials.
3. Allow location if you want the app to share your position when accepting a
   ride. The app still works if you decline location sharing.

Use **Settings → Log out** to end the session on the phone. If you forget your
password, contact dispatch; they can reset it after verifying your identity.

## Build from source

The `.github/workflows/build-apk.yml` workflow installs Java 17 and Gradle 8.7,
then builds the installable APK. Android Studio is optional. This project uses
the Android framework and has no app libraries to download.

## Permissions

The app uses internet access, network state, optional location access, and a
keep-screen-on setting. It does not request contacts, camera, or files.

