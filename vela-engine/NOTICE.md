# Vela navigation source attribution
Source: https://github.com/PimpinPumpkin/Vela
Pinned revision: 85eb69c946711df272b54d3f4305e72746ce1035
License: GPL-3.0; see LICENSE in this directory.

The model, navigation engine, route projection, text phrasing and upstream NavEngine tests are copied from core/src/main and core/src/test at that revision. Copyright remains with the Vela contributors.

NightRider changes: Geo.kt omits its kotlinx.serialization import and @Serializable annotation because this focused JVM module does not serialize models. Other imported Kotlin implementation files and NavEngine tests are unchanged. No voice runtime, audio player, microphone code, or Vela remote configuration is included.

NightRider's adapter ignores speech and haptic requests. It executes only reroute/arrival state transitions; arrival never modifies a NightRider ride.

Build: gradle :vela-engine:test :app:testDebugUnitTest :app:assembleDebug

OsrmManeuvers.kt extracts osrmType and osrmPhrase from upstream core/data/RouteGeometry.kt. Method visibility is public and the enclosing object/package is adapted for this focused module; implementation bodies are unchanged.
