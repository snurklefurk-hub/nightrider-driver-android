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

## What you need

- **Android Studio** (free): <https://developer.android.com/studio>
  Installing it also installs the Android SDK, Java and Gradle — nothing else to set up.
- Your **NightRider web address**, the same one you open in a browser, e.g.
  `https://your-app.onrender.com` (no trailing slash needed).
- A phone with **Android 8.0 or newer** (or the built-in emulator, if you just want to look first).

---

## Open the project

1. Unzip `nightrider-driver-android.zip`.
2. Open Android Studio → **File ▸ Open** → select the unzipped
   `nightrider-driver-android` folder (the folder that contains `settings.gradle`).
3. Wait for the first sync. The first one downloads Gradle and the libraries, so
   it can take a few minutes. The status bar at the bottom shows progress.
   If it offers to update anything, you can decline — the versions in the project are known-good.

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

## Put it on a phone

**Easiest — run it straight from Android Studio**

1. On the phone: **Settings ▸ About phone ▸ tap Build number 7 times**, then
   **Settings ▸ System ▸ Developer options ▸ USB debugging: on**.
2. Plug the phone into the computer with a USB cable and tap **Allow** on the phone.
3. In Android Studio, pick your phone in the device dropdown at the top and press
   the green **▶ Run** button.

**To get an installable file (.apk) to send to drivers**

1. **Build ▸ Build Bundle(s) / APK(s) ▸ Build APK(s)**.
2. When it finishes, click **locate** in the popup. The file is
   `app/build/outputs/apk/debug/app-debug.apk`.
3. Send that file to the phone (email, Drive, USB) and open it. Android will ask
   you to allow installing from that source the first time — allow it.
   For a Play Store upload you would use **Build ▸ Generate Signed Bundle / APK**
   instead and create a signing key; the debug APK above is fine for your own drivers.

**No phone handy** — in the device dropdown choose **Create Device** (a Pixel
works fine) and run; the app appears in the emulator window.

---

## Troubleshooting

| What you see | What to do |
| --- | --- |
| "Can't reach the driver page" | Your server may be asleep. Open the site in a browser first to wake it, then tap **Retry**. Also check the address has no typo and no trailing `/`. |
| Test says "Server answered with code 404" | The address is probably missing or has an extra path. It should be the site root, e.g. `https://your-app.onrender.com`. |
| The page is blank for a while | On a free Render instance the first load after idle can take 30–60 seconds. |
| Location never asks | The phone must have location switched on. The app asks the first time the page requests your position. |
| Gradle sync fails on the first open | Check the internet connection and try **File ▸ Sync Project with Gradle Files**. |
| It mentions the Gradle wrapper is missing | The wrapper program is the one small file Android Studio normally downloads for you. Accept its offer to use a Gradle distribution (pick **8.7**, or the newest 8.x), then sync again. |

---

## What's in the box

```
nightrider-driver-android/
├─ settings.gradle, build.gradle, gradle.properties   build setup
├─ gradle/wrapper/gradle-wrapper.properties           Gradle 8.7
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
  it has **not** been compiled here. The file structure, resources and references
  were verified; the first build happens in your Android Studio.
