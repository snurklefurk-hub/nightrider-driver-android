# NightRider Driver for Android

**NightRider is work-in-progress software only. No transportation services are
currently offered; all rides and payments are simulated.** Friends, family,
drivers, riders, and anyone interested can explore the test features.

The NightRider Driver Android app connects to the software demo at
`https://nightrider.onrender.com` and shares simulated trips and team messages
with the website. To explore the Driver role, sign in using the public test
username `DEMO` and password `PASSWORD`. The same test credentials work for
Rider and restricted Dispatch roles when selected on the web sign-in page.
**Use fictional trip and message data only; demo accounts are shared.**

Private driver accounts, when configured by an administrator, can still sign in
with their own credentials. The Android app stores an encrypted session token
using Android Keystore; it does not retain the password. Rider and Dispatch
workflows are available in the browser. Public Rider account registration is
paused while NightRider remains a software demo.

## The NightRider three-test commit rule

**Before EVERY commit, ask all three questions:**

1. **Does it make delivering a professional ride easier or safer?**
2. **Does it make the rider's experience more dependable, transparent, or trustworthy?**
3. **Does it make the industry better?**

Improving the industry can include practical advances for drivers, local transportation companies, accessibility, fair competition, and service standards; vague claims are not enough.

If a change does not pass at least one of these, **do not commit it**, unless the product owner explicitly approves that specific change with **MAKE IT SO**. This applies to every agent and every subsystem, including infrastructure and maintenance. See [AGENTS.md](AGENTS.md) for the full decision rule.

**Owner-only override:** The product owner can say **MAKE IT SO** for one specific change, even if it fails all three value tests. Record the authorization and exact scope in the PR; use a commit footer like `NR-Override: MAKE IT SO - Implement the specifically requested experiment.` The exception does **not** waive security, privacy, driver safety, testing, or merge/release controls. CI checks the footer's syntax, while the integration owner verifies that authorization came from the owner. See [AGENTS.md](AGENTS.md).

Use `safety`, `customer-trust`, or `industry` as the commit benefit category, aligned to the three questions. Every standard commit needs a real `NR-Value:` trailer, such as `NR-Value: safety - Prevents unreliable GPS fixes from moving the navigation marker.` The `NightRider commit value gate` workflow checks every commit message in new PRs; it cannot judge whether the claimed benefit is true, so the integration owner must still review it. To enable the automatic *local, before-commit* question prompt in a Git clone, run `python3 scripts/setup_commit_gate.py` once. GitHub API commits bypass local hooks and must apply the questions before writing.

**Important:** The CI check reports violations, but it only becomes a mandatory GitHub merge blocker when branch protection/rulesets require `Require concrete benefit on every commit`. The repository's GitHub branch protection is not confirmed by this documentation.

## Working with multiple AI agents

NightRider's website/backend and Android app may be developed by independent AI agents at the same time. **Read [AGENTS.md](AGENTS.md) before changing code.** GitHub is the shared coordination hub; separate chat instances do not automatically share their plans or decisions.

- **Current work (live):** [Web/backend pull requests](https://github.com/snurklefurk-hub/NightRider/pulls) · [Android pull requests](https://github.com/snurklefurk-hub/nightrider-driver-android/pulls).
- **Announce work early:** Open a **draft pull request** on a dedicated branch so other agents can see scope, ownership and expected files.
- **Automatic coordination:** `.github/workflows/agent-coordination.yml` checks new PRs immediately using read-only access, flags overlapping source files in the same repo, and posts/refreshes cross-repo status comments through a trusted scheduled run every four hours (or a manual workflow run). It warns about other work; human review is still needed for cross-repo API/design conflicts.
- **Who can implement and merge:** **Both CHAT NR and WORK NR** may push and integrate owner-authorized changes, including major features, after appropriate review and tests. **WORK NR provides heavier execution/compute**, not exclusive authority. Use GitHub branches/PRs to coordinate concurrent work and check deployment effects; see [AGENTS.md](AGENTS.md). A merge into web `main` may redeploy Render; a merge into Android `main` may publish a signed pilot APK. The bot **never** automatically merges or deploys anything.
- **Important:** Check current PRs instead of trusting a static status summary. A green overlap report is not a guarantee of compatible behavior. All relevant tests must pass before integration.

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

The main board's map button opens the first **Vela-powered navigation** iteration.
Vela's pinned turn progression, arrival and off-route engine drives silent on-screen guidance.
Assigned rides route to pickup; the existing to_dropoff stage switches to the destination.
Navigation never changes ride stages, sends chat, or modifies assignments.

**Settings → Vela navigation test (live GPS)** opens the new screen without an account.
Long-press a destination, then use Save GPS CSV to export raw samples and map acceptance decisions.
Precise GPS is required; coarse/old fixes do not move the navigation state. A stale accepted
position stays visible with its age and speed becomes unavailable. Heading remains fixed
when stopped. Guidance only runs while this screen is open; leaving it stops GPS updates.

Routes use the free OSRM demonstration service and OpenFreeMap tiles. Directions have no
live traffic. No Google lookups, audio, microphone, offline downloads or background navigation
service are included in this initial integration. This is Vela's navigation **engine**, with
a native NightRider map screen; Vela's full Compose interface is not embedded.

The old simulator and screen-recording Field Test are explicitly labeled **Legacy** in Settings
and remain available for comparing earlier GPS evidence. They are not the new navigation screen.
See [NAVIGATION-VELA.md](NAVIGATION-VELA.md) for provenance, boundaries and validation.

## Next implementation priorities

The shared [NightRider roadmap](https://github.com/snurklefurk-hub/NightRider/blob/main/ROADMAP.md)
records the proposed navigation lifecycle, safe trip-action and field-validation work.
These are planned changes, not capabilities of the current signed release.

## Build from source

The Android workflows install Java 17 and Gradle 8.7. Android Studio is
optional. Both the production driver module and the separate `nav-prototype/`
appearance preview use the pinned MapLibre Android SDK.

Run `python -m unittest discover -s tests -p 'test_*.py' -v` for source/package
checks and `gradle :vela-engine:test testDebugUnitTest assembleDebug` for the Kotlin unit tests
and development APK. On Windows without a POSIX `sh`, the shell-only commit-hook
test is explicitly skipped; Linux CI still runs it. The native Vela screen check
also runs for shared app source/resources and root build configuration changes.
The signed-release workflow also runs the source and
Kotlin checks before building an update.

## Permissions

The app uses internet access, network state, optional location access, a
keep-screen-on setting and Android's package installer for approved updates.
Field Test screen recording requests Android's screen-capture consent for
each session and runs a visible foreground service. CSV/MP4 exports use
Android's file picker. No microphone, camera, contacts or broad storage
permissions are requested.

