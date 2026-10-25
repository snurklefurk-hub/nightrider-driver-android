# NightRider Driver — Android app

This is the driver half of NightRider as a phone app. It shows the same driver
page your website serves, so everything a driver does on the site is in the app:
the ride workflow (head to pickup → arrive at pickup → head to destination →
arrive at destination → complete ride), the open ride requests board, active
rides, rider ratings and issue reports, the shared team text with the **Private**
toggle to message dispatch only, driver check-in, and the nightwave look
(dark indigo, cyan, plum, amber private messages).

Nothing about your ride data or chat changed — the app is a shell around your
own site, so the two always stay in step.

---

## Getting the installable app (.apk)

**You do not need Android Studio.** There are two routes; use the first one.

### Route 1 — GitHub builds it for you (recommended)

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
2. Let the first sync finish (it downloads Gradle and the libraries; a few minutes).
3. **Build ▸ Build Bundle(s) / APK(s) ▸ Build APK(s)**.
4. Click **locate** in the popup. The file is
   `app/build/outputs/apk/debug/app-debug.apk`.

Android Studio will offer to create the Gradle wrapper for you on first open —
accept that. This project ships without a wrapper on purpose, so that the GitHub
build cannot be derailed by a half-present one.

To run straight onto a plugged-in phone: enable **Developer options ▸ USB
debugging**, then press the green **▶ Run** button with your phone selected.

---

## Point it at your server

The app asks once, on first launch:

1. Type your NightRider address (e.g. `https://your-app.onrender.com`).
2. Tap **Test connection** — you want to see *Server reachable*.
3. Leave **Keep screen on while the app is open** ticked for driving.
4. Tap **Save** — the driver page opens.

To change it later: on the error screen tap **Change server**, or long-press the
NightRider icon on your home screen and pick **Server settings**.

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
| "Can't reach the driver page" | Your server may be asleep. Open the site in a browser first to wake it, then tap **Retry**. Also check the address has no typo and no trailing `/`. |
| Test says "Server answered with code 404" | The address is probably missing or has an extra path. It should be the site root, e.g. `https://your-app.onrender.com`. |
| The page is blank for a while | On a free Render instance the first load after idle can take 30–60 seconds. |
| Location never asks | The phone must have location switched on. The app asks the first time the page requests your position. |
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
└─ app/
   ├─ build.gradle                                    app module (Android 8.0+, target 14)
   └─ src/main/
      ├─ AndroidManifest.xml                          permissions, screens, app shortcut
      ├─ java/com/nightrider/driver/
      │  ├─ MainActivity.kt                           the driver shell (WebView + nightwave splash)
      │  ├─ SetupActivity.kt                          server address + connection test
      │  └─ Prefs.kt                                  saved address / keep-screen-on
      └─ res/
         ├─ layout/                                   splash, error panel, settings screen
         ├─ drawable/, values/                        nightwave colours, buttons, panels
         ├─ mipmap-*                                  launcher icons
         └─ xml/shortcuts.xml                         "Server settings" long-press shortcut
```

## Good to know

- The app uses your phone's internet, network state, location (only so the page
  can show the driver's position) and a keep-screen-on option. It does not ask
  for contacts, camera or files.
- Links that leave your site (for example a map link) open in the phone's browser,
  so your page keeps running in the app.
- This project was assembled and checked in a machine that has no Android SDK, so
  it has **not** been compiled here. The file structure, resources, every Kotlin
  resource reference and every XML reference were verified; the first real
  compile happens on GitHub (or in Android Studio). See `VERIFICATION.json`.
