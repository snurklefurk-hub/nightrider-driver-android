package com.nightrider.driver

import app.vela.core.model.LatLng
import kotlin.random.Random

/** Small inland Savannah test area, entirely inside Chatham County, Georgia.
 * Sampling is intentionally limited to this city area, not a county bounding box.
 * OSRM must subsequently confirm a road within 100 metres and an endpoint in this area.
 */
object ChathamTestDestination {
    fun random(random: Random = Random.Default) =
        LatLng(random.nextDouble(32.055,32.070),random.nextDouble(-81.105,-81.095))
    fun contains(p: LatLng) = p.lat in 32.054..32.071 && p.lng in -81.106..-81.094
}
