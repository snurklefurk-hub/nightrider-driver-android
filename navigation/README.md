# NightRider navigation: Vela foundation

Decision: replace NightRider's custom navigation prototype with Vela-based navigation. Stop extending the legacy camera/filter/HUD stack. Existing production code remains runnable until the replacement passes its acceptance checks.

## Source baseline
- Upstream: https://github.com/PimpinPumpkin/Vela
- Revision: 85eb69c946711df272b54d3f4305e72746ce1035
- Source: vela/ (Git submodule pinned to the revision above; clone with --recurse-submodules)
- License: upstream GPLv3; preserve LICENSE, attribution and corresponding source.
- The upstream tree is initially unmodified. This does not yet integrate Vela into the production APK.

## Implementation sequence
1. Establish a reproducible Vela baseline build and run its navigation tests. Use upstream docs/BUILDING.md and CI for exact dependency/runtime versions.
2. Produce a separately installable NightRider navigation development build. Disable voice guidance, audio alerts, voice search and microphone entry points before distribution. Do not change NightRider's signing secrets or overwrite its update channel with a standalone Vela APK.
3. Connect assigned ride pickup/drop-off coordinates through a narrow adapter to Vela's route/session API. NightRider owns accounts, chat and ride stage changes; navigation must never advance a ride by itself.
4. Embed Vela navigation in the driver workflow, preserving Settings LIGHT/DARK, required attribution and free service choices.
5. Remove the retired custom navigation stack after replacement acceptance. Keep the old implementation recoverable in Git history.

## First acceptance checks
- Install/update preserves NightRider identity, accounts, chat and assignments.
- No audio or microphone behavior.
- Assigned pickup and destination start the correct navigation leg.
- Turn guidance, off-route recovery and arrival presentation use Vela's engine.
- Stationary phone does not spin, drift the camera, or present stale speed as current.
- Loss/recovery of GPS has an explicit last-known/stale state.
- Stationary and moving tests retain timestamped diagnostic evidence.
- Builds, upstream navigation unit tests and NightRider regressions pass.
- A signed release is published only after the replacement is wired and verified.

## Current status
Pinned upstream source foundation staged as a submodule. Production navigation replacement, silent build, integration and on-device validation remain outstanding. No replacement release has been published from this branch.
