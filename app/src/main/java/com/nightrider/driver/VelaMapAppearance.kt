package com.nightrider.driver

import android.graphics.Color
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/** Presentation only. Never reads GPS, calculates routes, or changes navigation state. */
object VelaMapAppearance {
    fun apply(style: Style, light: Boolean) {
        NavigationRoadStyle.apply(style, light)
    }

    /** Keep the route geometry intact; restore the approved blue stroke and dark casing. */
    fun drawRoute(map: MapLibreMap, points: List<LatLng>, light: Boolean): List<Polyline> {
        if (points.size < 2) return emptyList()
        return listOf(
            map.addPolyline(PolylineOptions().addAll(points)
                .color(Color.rgb(20, 29, 46)).width(17f)),
            map.addPolyline(PolylineOptions().addAll(points)
                .color(if (light) Color.rgb(63, 132, 245) else Color.rgb(112, 181, 255)).width(10f))
        )
    }
}
