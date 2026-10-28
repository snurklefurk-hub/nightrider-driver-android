package app.vela.core.nav

import app.vela.core.model.ManeuverType

/** Extracted from upstream RouteGeometry.kt at the pinned Vela revision. */
object OsrmManeuvers {
    fun osrmType(type: String, mod: String?): ManeuverType {
        fun byMod() = when (mod) {
            "left" -> ManeuverType.TURN_LEFT
            "right" -> ManeuverType.TURN_RIGHT
            "slight left" -> ManeuverType.SLIGHT_LEFT
            "slight right" -> ManeuverType.SLIGHT_RIGHT
            "sharp left" -> ManeuverType.SHARP_LEFT
            "sharp right" -> ManeuverType.SHARP_RIGHT
            "uturn" -> ManeuverType.UTURN
            else -> ManeuverType.STRAIGHT
        }
        return when (type) {
            "depart" -> ManeuverType.DEPART
            "arrive" -> ManeuverType.ARRIVE
            "merge" -> ManeuverType.MERGE
            // "continue"/"new name" going STRAIGHT = the same physical road (possibly renamed —
            // surface-street name changes and highway ref changes alike), no driver action →
            // CONTINUE, which NavEngine keeps on the banner but does NOT speak (Google is silent
            // there too). Any real bend keeps byMod(): OSRM's "continue left" is a 90° bend that
            // keeps the name — that's a TURN_LEFT and must still be spoken. NB "turn"+"straight"
            // below stays STRAIGHT (spoken): OSRM emits type "turn" only where an instruction is
            // warranted (going straight is an active choice at that junction).
            // A "new name" step is JUST a road rename on the same physical road — OSRM often stamps a
            // few-degrees "slight left"/"slight right" bearing artifact on a dead-straight rename node, so
            // treat those as straight too → CONTINUE (silent, like Google). "Olive Dr becomes Richards
            // Way" going straight must NOT be announced. Keep "continue" NARROW: a "continue"+slight is more
            // plausibly a real gentle bend that keeps its name and should stay spoken.
            "new name" -> if (mod == null || mod == "straight" || mod == "slight left" || mod == "slight right")
                ManeuverType.CONTINUE else byMod()
            "continue" -> if (mod == null || mod == "straight") ManeuverType.CONTINUE else byMod()
            "on ramp", "off ramp", "ramp" -> if (mod?.contains("left") == true) ManeuverType.RAMP_LEFT else ManeuverType.RAMP_RIGHT
            "fork" -> if (mod?.contains("left") == true) ManeuverType.FORK_LEFT else ManeuverType.FORK_RIGHT
            "roundabout", "rotary", "roundabout turn" -> ManeuverType.ROUNDABOUT
            // OSRM's TWO-STEP roundabout: an enter step ("roundabout") + an EXIT step ("exit roundabout"/
            // "exit rotary") — and it's the exit step that carries maneuver.exit (the exit NUMBER) and the
            // exit road name. Without this the exit step fell through to byMod() → a bland "Continue onto X"
            // and the "take the Nth exit" was silently dropped (the reported "didn't say which exit").
            "exit roundabout", "exit rotary" -> ManeuverType.EXIT_ROUNDABOUT
            "end of road", "turn" -> byMod()
            else -> byMod()
        }
    }

    fun osrmPhrase(type: String, mod: String?, road: String?, dest: String?, exitNo: String?, rbExit: Int?): String {
        val strings = app.vela.core.i18n.NavStringsRegistry.current()
        // OSRM's arrive modifier says WHICH SIDE the destination is on — the banner + the
        // pre-arrival prompt become Google's "Your destination is on the right" instead of the
        // generic line (user 2026-07-11). A sideless arrive keeps the generic phrase.
        if (type == "arrive" && mod != null) {
            if (mod.contains("left")) return strings.destinationSide(left = true)
            if (mod.contains("right")) return strings.destinationSide(left = false)
        }
        // A U-turn is a U-turn whatever step type OSRM wraps it in: a "continue" or "turn" carrying
        // the uturn modifier read "Bear uturn onto ..." / "Turn uturn onto ..." (2026-09-25, seen on
        // a route that began facing away from the destination). Every language has a real phrase.
        if (mod?.trim() == "uturn" && type in setOf("continue", "new name", "turn", "end of road")) {
            return strings.phrase("uturn", null, road, dest, exitNo, rbExit)
        }
        // Straight on at a junction is not a turn: "Turn straight onto Mace Boulevard" (seen in a
        // step list, 2026-10-02). Every language has a continue phrase.
        if (mod?.trim() == "straight" && type in setOf("turn", "end of road")) {
            return strings.phrase("continue", "straight", road, dest, exitNo, rbExit)
        }
        return strings.phrase(type, mod, road, dest, exitNo, rbExit)
    }

}
