# NightRider Driver — in-app updates (prototype channel)

**For the sole developer/tester.** Settings now checks the public GitHub Releases
feed for newer **signed** NightRider updates. Tap **Download and install update**
to download, verify and stage the APK from within the app. Android still controls
the final install approval and may require enabling "Allow from this source" once.

No background or forced installs are performed. The existing debug APK workflow
is retained for prototyping, but **debug APKs are not part of the update channel**.

## One-time signing setup (required before updates work)

Android allows an APK to update an installed copy only when both use the same
application ID, a compatible signing certificate, and a suitable version code.
GitHub's temporary debug keys are **not** stable across separate workflow runs.

1. On a trusted computer with the Java JDK, create a long-lived release keystore.
   For example, in PowerShell or a terminal:

   ```sh
   keytool -genkeypair -v -keystore nightrider-release.jks -storetype PKCS12 -alias nightrider -keyalg RSA -keysize 3072 -validity 10000
   ```

   Save the keystore and both passwords **offline in secure backups**. Losing
   the key means future releases cannot update existing installations.

2. Encode the keystore as Base64 (encoding is **not** encryption). On Windows
   PowerShell:

   ```powershell
   [Convert]::ToBase64String([System.IO.File]::ReadAllBytes("nightrider-release.jks"))
   ```

3. In GitHub open this repository, then **Settings → Secrets and variables →
   Actions → New repository secret**. Create these four secrets:

   | Secret | Value |
   |---|---|
   | `NR_SIGNING_KEYSTORE_BASE64` | Base64 text from step 2 |
   | `NR_SIGNING_STORE_PASSWORD` | Keystore password |
   | `NR_SIGNING_KEY_ALIAS` | `nightrider` (or the alias chosen above) |
   | `NR_SIGNING_KEY_PASSWORD` | Key password (may equal keystore password for PKCS12) |

   Do not commit the keystore, Base64 text, or passwords, and don't paste
   them into a chat. If no signing secrets exist, the workflow safely skips
   publishing; incomplete signing configuration fails the job.

4. Open **Actions → Publish signed NightRider in-app update → Run workflow**.
   A successful run publishes a GitHub Release tagged `nr-driver-v<versionCode>`
   with a signed `NightRider-Driver.apk` and its SHA-256 checksum.

5. **One-time migration:** Install that signed APK on the Android phone.
   If an older debug-signed NightRider copy is installed, Android will reject
   a signature-mismatched update. In that case, uninstall the old **app**
   before installing the signed APK. Uninstall removes locally stored login
   and settings; your backend account/ride data remains on the server.
   Afterward, future signed releases can update in place without visiting GitHub.

## Normal update workflow

1. Commit a working Android change to `main`.
2. `signed-update.yml` builds, signs, verifies and publishes a higher-version APK
   *when signing secrets are configured*. The existing debug workflow still builds
   developer test artifacts.
3. On the phone: NightRider → **Settings → Check for updates**.
4. If a release is available, tap **Download and install update**. The app
   downloads to private cache, checks the GitHub release SHA-256 digest, and
   validates the package ID and version. Android checks the APK's signature.
5. The first attempt may take you to the Android **Install unknown apps** setting.
   Allow NightRider as an install source, return, and tap the update button again.
   Confirm installation in Android's system prompt.

The app checks when Settings is opened, and also offers a manual recheck.
It does not silently replace its own binary.

## Verification before relying on it

- Confirm one signed Release exists; note its version code and certificate SHA-256.
- Start with that signed release installed on a real phone.
- Commit another small test change, confirm GitHub Actions succeeds and a newer
  `nr-driver-v...` release appears.
- Verify Settings detects the newer release, downloads it, and Android asks for
  confirmation. Verify the installed version changes and login remains intact.
- Test no network, declined install permission, canceled installation, and
  a corrupted download. Confirm no unsupported silent installations.
- Verify different-signature APKs are rejected by Android.

## Prototype limits and future B2B considerations

The update feed uses unauthenticated GitHub APIs and **public** Release assets.
Private repositories, managed fleets, internal controls, staged releases,
and customer-specific versions will require a different authenticated update
service or managed app distribution when the project moves beyond prototyping.

To roll back a problematic version, fix/revert its source and publish a new
APK signed with the **same key and a higher version code**. Android does not
normally allow in-place installation of an older version code.
