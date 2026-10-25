# NightRider Driver — Android app

This is the **driver-only** NightRider phone app. It is a native Android app that
talks straight to your server's driver endpoints — there is no web page inside it,
and the server address is fixed at `https://nightrider.onrender.com`, so a driver
never has to type or change it.

A driver gets: a sign-in card (driver name + a password for that phone), the open
ride requests board, their own rides with the ride actions and rider ratings, and
the shared team text with the **Private** toggle to message dispatch only — all in
the nightwave look (dark indigo, cyan, plum, amber private messages).

Riders and dispatch are deliberately **not** in the app — they keep using a
browser. Nothing about your ride data or chat changed, so the app and the site
read and write the same records.

---

## Getting the installable app (.apk)

**You do not need Android Studio.** The APK is already built for you; the build
routes are only needed if you change the app.

### Route 0 — it is already built (easiest)

`NightRider-Driver.apk` ships alongside this README. It is a signed, installable
debug build — copy it to a phone and follow
[Installing the APK on a phone](#installing-the-apk-on-a-phone). Nothing else on
this page is needed to use the app.

The app has **no external libraries**: it uses only the Android framework itself,
so no build ever has to download a library. That is what made the APK above
possible to build without Gradle or Android Studio.

### Route 1 — GitHub builds it for you (also recommended)

GitHub has free build computers. You upload this project, press one button, and
it hands you `NightRider-Driver.apk`.

**Full click-by-click instructions are in [`GET-THE-APK.md`](GET-THE-APK.md).**
The short version:

1. Put this project in a GitHub repository.
2. **Actions** tab → **Build driver app APK** → **Run workflow**.
3. After ~4 minutes, download `NightRider-Driver.apk` from the run's
   **Artifacts** box, or from the repository's **Releases** page.

The recipe lives in `.github/workflows/build-apk.yml`. It installs Java 17 and
Gradle 8.7 itself, so nothing needs to be installed on your computer. If the
build fails, the top of the run page shows the last 60 lines of the error.

### Route 2 — Android Studio, if you happen to have it

1. **File ▸ Open** → select the folder containing `settings.gradle`.
2. Let the first sync finish (it downloads Gradle only — the app needs no
   libraries — a couple of minutes).
3. **Build ▸ Build Bundle(s) / APK(s) ▸ Build APK(s)**.
4. Click **locate** in the popup. The file is
   `app/build/outputs/apk/debug/app-debug.apk`.

Android Studio will offer to create the Gradle wrapper for you on first open —
accept that. This project ships without a wrapper on purpose, so that the GitHub
build cannot be derailed by a half-present one.

To run straight onto a plugged-in phone: enable **Developer options ▸ USB
debugging**, then press the green **▶ Run** button with your phone selected.

---

## Signing in

The app opens on one card. On first use:

1. Type the **driver name** dispatch knows you by.
2. Type a **password** for this phone (at least 4 characters).
3. Tap **Sign in**. The card disappears and the board appears.

The password is stored on the phone only, as a salted SHA-256 digest, and is tied
to that driver name. It is a lock on the phone, **not** a backend account
credential — it is never sent to the server. That is what lets a driver sign in
without waiting on the network.

To sign out later, tap the small **Settings** button in the top bar, then **Log
out**. The same screen shows the fixed server address and a keep-screen-on
switch.

---

## Installing the APK on a phone

1. Get the file onto the phone (email it to yourself, Google Drive, or USB).
2. Tap it. Android will block it once — tap **Settings**, switch on **Allow from
   this source**, then **Install**.
3. The warning that the app is from an unknown developer is normal for anything
   not distributed through the Play Store.

The APK this builds is a **debug build**, which is fine for your own phones and
your drivers'. A Play Store listing would need a separate signed release build.

---

## Troubleshooting

| What you see | What to do |
| --- | --- |
| "Could not sign in: …" | Your server may be asleep. Open the site in a browser first to wake it, then try again. |
| "That password does not match …" | The name or the password typed on this phone differs from what was saved. Use **Settings ▸ Reset password** to start fresh. |
| The board stays empty for a while | On a free Render instance the first reply after idle can take 30–60 seconds. The app keeps polling while you are on the screen. |
| Location never asks | The phone must have location switched on. The app asks the first time a ride needs your position. |
| GitHub build shows a red ✗ | Open the run. The top of the page has a **The build did not finish** block with the last 60 lines of the error — copy that. Otherwise click the red **Build the APK** step and copy the last ~30 lines. The full log is also saved as the **build-log** artifact on the same page. |
| No "Build driver app APK" in the Actions list | The `.github/workflows/build-apk.yml` file did not make it into the repo. See `GET-THE-APK.md` step 4 for how to add it by hand. |
| Gradle sync fails in Android Studio | Check the internet connection and try **File ▸ Sync Project with Gradle Files**. Accept its offer to use Gradle **8.7**. |

---

## What's in the box

```
nightrider-driver-android/
├─ settings.gradle, build.gradle, gradle.properties   build setup
├─ .github/workflows/build-apk.yml                    cloud build recipe (no Android Studio needed)
├─ GET-THE-APK.md                                     click-by-click APK instructions
├─ NightRider-Driver.apk                               ready-to-install signed APK (debug)
└─ app/
   ├─ build.gradle                                    app module (Android 8.0+, target 14)
   └─ src/main/
      ├─ AndroidManifest.xml                          permissions and the two screens
      ├─ java/com/nightrider/driver/
      │  ├─ MainActivity.kt                           sign-in card + rides board + team text
      │  ├─ SettingsActivity.kt                       fixed server, keep-screen-on, logout, reset password
      │  ├─ Api.kt                                    small JSON client for the driver endpoints
      │  └─ Prefs.kt                                  driver name, password digest, keep-screen-on
      └─ res/
         ├─ layout/                                   sign-in card + driver board, settings screen
         ├─ drawable/, values/                        nightwave colours, buttons, panels, chat bubbles
         └─ mipmap-*                                  launcher icons
```

## Good to know

- The app uses your phone's internet, network state, location (only so a ride can
  show the driver's position) and a keep-screen-on option. It does not ask for
  contacts, camera or files.
- This exact revision was compiled and packaged here, without Gradle or Android
  Studio, into the signed `NightRider-Driver.apk` above. The APK's signature,
  package metadata and contents were checked. It has **not** been installed or run
  on a physical phone, so on-device behaviour is unverified. See
  `VERIFICATION.json` for the precise scope of what was and was not checked.
