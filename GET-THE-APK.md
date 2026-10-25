# Get the APK without touching Android Studio

**Already done for you:** `NightRider-Driver.apk` is in this folder. It is a
signed, installable build — skip straight to
[Step 6 — Install it on a phone](#step-6--install-it-on-a-phone). The rest of
this page is for rebuilding the app yourself after a change to the code.

You do **not** need Android Studio on your computer. GitHub has free build
computers: you drop the project in, press one button, and it hands you a finished
`NightRider-Driver.apk` you can send to a phone. That is exactly what the file
`.github/workflows/build-apk.yml` in this project is for.

Time needed: about 10 minutes, plus ~4 minutes of waiting for the build.

---

## Step 1 — Unzip the project

Unzip `nightrider-driver-android.zip`. You get one folder called
`nightrider-driver-android`. Open it — inside you should see:

```
settings.gradle
build.gradle
gradle.properties
app/             (folder - the app itself)
.github/         (folder - the build recipe)
README.md
GET-THE-APK.md
VERIFICATION.json
```

There is deliberately **no** `gradle/wrapper` folder. The build computer installs
Gradle itself, and a half-finished wrapper file would stop the build before it
starts. Everything else is already in place — you do not need to create or edit
any file.

## Step 2 — Put it in a GitHub repository

**If you already have a GitHub account** (you do, for the website), you can either:

- **New repo (recommended, cleanest):** github.com → **+** (top right) →
  **New repository** → name it `nightrider-driver-app` → **Create repository**.
- **Or reuse the website repo:** drop this project in as a sub-folder named
  `android`. The build recipe finds the project wherever it is, so both work.

## Step 3 — Upload the files

On the repository page:

1. Click **Add file ▸ Upload files**.
2. Open the unzipped `nightrider-driver-android` folder, then drag **the things
   inside it** (not the folder itself) into the upload page — `settings.gradle`,
   `build.gradle`, `gradle.properties`, and the `app` and `.github` folders.
3. Click **Commit changes**.

**One catch about the `.github` folder:** folders whose name starts with a dot are
hidden by Finder/Explorer, and drag-and-drop sometimes skips them. If it did not
upload, add it by hand — it is only one small file:

1. On the repo page: **Add file ▸ Create new file**.
2. In the filename box type exactly: `.github/workflows/build-apk.yml`
   (typing the slashes creates the folders).
3. Paste in the whole contents of that file from the unzipped project, then
   **Commit changes**.

## Step 4 — Press the button

1. Open the **Actions** tab of the repository.
2. On the left, click **Build driver app APK**.
3. Click **Run workflow ▸ Run workflow** (a button at the top right of that page).
4. Wait ~4 minutes. A green tick means the APK is ready. (It also runs by itself
   every time you change a project file.)

**If there is no "Build driver app APK" in that left-hand list**, the
`.github/workflows/build-apk.yml` file is not in the repo yet — this is the one
thing that usually gets missed when uploading, because folders whose name starts
with a dot are hidden. To add it, on the repository page:

1. **Add file ▸ Create new file**.
2. In the filename box type exactly: `.github/workflows/build-apk.yml`
   (typing the slashes creates the folders — no need to make them first).
3. Paste the whole file contents into the big text box.
4. Click **Commit changes**. The build starts on its own; open **Actions** and it
   will be at the top. If it does not, refresh the page and use
   **Run workflow ▸ Run workflow**.

Still nothing in the list? Open **Settings ▸ Actions ▸ General** and make sure
**Allow all actions and reusable workflows** is selected.

## Step 5 — Download the APK

Two ways, either is fine:

- **Easiest to share:** repo home page → right-hand sidebar → **Releases** →
  the newest one → tap **NightRider-Driver.apk**. That link can be sent straight
  to a driver.
- **From the build run:** Actions → the green run → scroll to **Artifacts** →
  download **NightRider-Driver-APK**. That gives you `NightRider-Driver.apk`
  straight away — nothing to unzip. (If your GitHub still hands it over as a
  zip, unzip it and the APK is inside.)

## Step 6 — Install it on a phone

1. Get the APK onto the phone: email it to yourself, put it in Google Drive, or
   copy it over USB.
2. Tap the file on the phone. Android will say the source isn't allowed — tap
   **Settings** and switch on **Allow from this source**, then tap **Install**.
   (It may also warn that the app is from an unknown developer; that is normal for
   an app that isn't in the Play Store.)
3. Open **NightRider Driver**, type your driver name and a password for the phone,
   then tap **Sign in**. The board opens. (The server address is already built in.)

That APK is a **debug build** — perfectly fine for installing on your own phones
and your drivers' phones. If you ever want it in the Play Store, that needs a
separate signed build and a Google Play developer account.

## If the build fails

A red run is not something you did wrong, and it does not mean starting over. It
means the project hit an error and I need to see it.

1. Open the **Actions** tab and click the red run.
2. At the top of that page there is a section titled **The build did not finish**.
   It already contains the last 60 lines of the build's own error output.
3. Copy that block and send it to me — I will fix the project and you can re-run
   it with **Run workflow**.

If the summary block is not there, click the red step (usually **Build the APK**),
copy the last 30 lines, and send those. The run page also keeps a **build-log**
file in its Artifacts box at the bottom, which is the complete log — send that
instead if 30 lines are not enough.

## Free minutes

Builds run on GitHub's computers. Public repositories get unlimited free build
minutes; private repositories get 2,000 free minutes a month, and each build here
uses about 4–5 of them — so this is effectively free either way.

## If you would rather not use GitHub

Any computer with Android Studio installed can build it instead: open the project,
then **Build ▸ Build Bundle(s) / APK(s) ▸ Build APK(s)**. The APK lands at
`app/build/outputs/apk/debug/app-debug.apk`. The README has the longer version of
that route.
