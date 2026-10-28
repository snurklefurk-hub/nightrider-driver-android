package com.nightrider.driver

import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory

/**
 * Flat, high-contrast road treatment for the driver navigation canvas.
 *
 * Changes only existing OpenFreeMap vector paint layers. Unknown/missing
 * layers are skipped, so a tile-style update cannot break the map screen.
 * No new tile provider, route requests, permissions or settings are added.
 */
object NavigationRoadStyle {
    fun apply(style: Style, light: Boolean) {
        val background = if (light) "#E5EEF4" else "#373C47"
        (style.getLayer("background") as? BackgroundLayer)?.setProperties(
            PropertyFactory.backgroundColor(background)
        )

        val fills = if (light) mapOf(
            "water" to "#C9E1EE",
            "park" to "#DBE9E4",
            "landcover_wood" to "#D7E4DF",
            "landuse_residential" to "#E8F0F6",
            "building" to "#D6E0E9"
        ) else mapOf(
            "water" to "#252F42",
            "park" to "#35464A",
            "landcover_wood" to "#34454A",
            "landuse_residential" to "#3A414B",
            "building" to "#424752"
        )
        fills.forEach { (id, color) ->
            (style.getLayer(id) as? FillLayer)?.setProperties(
                PropertyFactory.fillColor(color)
            )
        }

        // Road casings make intersections and ramps legible at a glance.
        // Keep local streets subdued so the blue route remains the focal point.
        val roads = if (light) mapOf(
            "highway_path" to "#ACBDC9",
            "highway_minor" to "#B2C0CC",
            "highway_major_casing" to "#8798A7",
            "highway_major_inner" to "#FCFEFF",
            "highway_major_subtle" to "#D6E0E9",
            "highway_motorway_casing" to "#708CA3",
            "highway_motorway_inner" to "#FDFEFF",
            "highway_motorway_subtle" to "#D6E4ED",
            "tunnel_motorway_casing" to "#8A9FAF",
            "tunnel_motorway_inner" to "#DBE8F0"
        ) else mapOf(
            "highway_path" to "#505A68",
            "highway_minor" to "#69727F",
            "highway_major_casing" to "#1A2130",
            "highway_major_inner" to "#BBC1CA",
            "highway_major_subtle" to "#868F9D",
            "highway_motorway_casing" to "#141B29",
            "highway_motorway_inner" to "#E5E7EC",
            "highway_motorway_subtle" to "#A7B0BE",
            "tunnel_motorway_casing" to "#222A38",
            "tunnel_motorway_inner" to "#8995A5"
        )
        roads.forEach { (id, color) ->
            (style.getLayer(id) as? LineLayer)?.setProperties(
                PropertyFactory.lineColor(color)
            )
        }

        val streetText = if (light) "#29445A" else "#F1F3F7"
        val secondaryText = if (light) "#547085" else "#AEBBCB"
        listOf("highway_name_other", "highway_ref", "highway_name_motorway")
            .forEach { id ->
                (style.getLayer(id) as? SymbolLayer)?.setProperties(
                    PropertyFactory.textColor(streetText),
                    PropertyFactory.textHaloColor(background),
                    PropertyFactory.textHaloWidth(1.5f)
                )
            }
        listOf("place_city", "place_city_large", "place_town", "place_suburb")
            .forEach { id ->
                (style.getLayer(id) as? SymbolLayer)?.setProperties(
                    PropertyFactory.textColor(secondaryText),
                    PropertyFactory.textHaloColor(background)
                )
            }
        // Suppress nonessential small place labels, never street names.
        (style.getLayer("place_other") as? SymbolLayer)?.setProperties(
            PropertyFactory.textOpacity(0.12f)
        )
    }
}
