package com.nightrider.navpreview

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory

/**
 * Standalone Stage B map comparison. No NightRider session, backend, ride,
 * GPS location or routing services are used. MapTiler key exists in RAM only.
 */
class PreviewActivity : Activity() {
    private lateinit var mapView: MapView
    private lateinit var status: TextView
    private var map: MapLibreMap? = null
    private val styleButtons = mutableListOf<Button>()
    private var selectedStyle = 0
    private var mapTilerKey: String? = null

    private val canvasColor = Color.rgb(3, 11, 8)
    private val surface = Color.rgb(11, 29, 19)
    private val green = Color.rgb(46, 230, 160)
    private val cyan = Color.rgb(47, 233, 255)
    private val silver = Color.rgb(143, 174, 179)
    private val white = Color.rgb(243, 253, 251)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must initialize before constructing MapView.
        MapLibre.getInstance(this)
        val mapOptions = MapLibreMapOptions.createFromAttributes(this, null)
            .camera(CameraPosition.Builder()
                .target(LatLng(32.7767, -96.7970))
                .zoom(14.2).bearing(0.0).tilt(0.0).build())
            .minPitchPreference(0.0)
            .maxPitchPreference(0.0)
            .tiltGesturesEnabled(false)
            .rotateGesturesEnabled(false)
            .minZoomPreference(3.0)
            .maxZoomPreference(20.0)
        mapView = MapView(this, mapOptions)
        val root = FrameLayout(this).apply { setBackgroundColor(canvasColor) }
        root.addView(mapView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundedSurface()
        }
        val title = TextView(this).apply {
            text = "NIGHTRIDER / MAP LAB"
            textSize = 15f
            setTextColor(cyan)
            letterSpacing = 0.12f
        }
        controls.addView(title)
        controls.addView(TextView(this).apply {
            text = "Stage B · isolated 2D basemap comparison"
            textSize = 12f
            setTextColor(silver)
        })
        val styles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        listOf("Midnight", "Fiord", "Dark").forEachIndexed { index, label ->
            val button = makeButton(label).apply { setOnClickListener { selectStyle(index) } }
            styles.addView(button, LinearLayout.LayoutParams(
                0, dp(42), 1f).apply { marginEnd = if (index == 2) 0 else dp(6) })
            styleButtons.add(button)
        }
        controls.addView(styles)

        val presets = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        val mapTiler = makeButton("MapTiler*").apply {
            setOnClickListener {
                if (mapTilerKey.isNullOrBlank()) askMapTilerKey() else selectStyle(3)
            }
        }
        val streets = makeButton("Streets").apply {
            setOnClickListener { centerAt(32.7767, -96.7970, 14.2) }
        }
        val junction = makeButton("Junction").apply {
            setOnClickListener { centerAt(32.9244, -96.7612, 15.2) }
        }
        listOf(mapTiler, streets, junction).forEachIndexed { i, button ->
            presets.addView(button, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginEnd = if (i == 2) 0 else dp(6)
            })
        }
        styleButtons.add(mapTiler)
        controls.addView(presets)

        status = TextView(this).apply {
            text = "Loading map…"
            textSize = 12f
            setTextColor(white)
            setPadding(0, dp(8), 0, 0)
        }
        controls.addView(status)
        controls.addView(TextView(this).apply {
            text = "Pan / pinch to inspect roads. Pitch locked to 0°. No GPS, live rides, or guidance. *MapTiler needs your own key."
            textSize = 10f
            setTextColor(silver)
            setPadding(0, dp(5), 0, 0)
        })
        root.addView(controls, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            setMargins(dp(10), dp(10), dp(10), 0)
        })
        setContentView(root)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.setMinPitchPreference(0.0)
            readyMap.setMaxPitchPreference(0.0)
            selectStyle(0)
        }
    }

    private fun centerAt(lat: Double, lon: Double, zoom: Double) {
        map?.cameraPosition = CameraPosition.Builder()
            .target(LatLng(lat, lon)).zoom(zoom).bearing(0.0).tilt(0.0).build()
    }

    private fun selectStyle(index: Int) {
        val readyMap = map ?: return
        // Midnight recolors Fiord's vector layers, preserving original map data.
        val url = when (index) {
            0, 1 -> "https://tiles.openfreemap.org/styles/fiord"
            2 -> "https://tiles.openfreemap.org/styles/dark"
            else -> "https://api.maptiler.com/maps/streets-v4-dark/style.json?key=" +
                Uri.encode(mapTilerKey ?: return)
        }
        selectedStyle = index
        styleButtons.forEachIndexed { i, b ->
            b.background = pill(i == index)
            b.setTextColor(if (i == index) canvasColor else green)
        }
        val label = listOf("NightRider Midnight", "OpenFreeMap Fiord", "OpenFreeMap Dark",
            "MapTiler Streets Dark")[index]
        status.text = "Loading $label…"
        // Runtime overrides are applied only to the freshly loaded Midnight style.
        readyMap.setStyle(url) { loadedStyle ->
            if (selectedStyle == index) {
                if (index == 0) applyMidnightPalette(loadedStyle)
                status.text = "$label · zoom/pan enabled · 2D only"
            }
        }
    }

    /**
     * Early Midnight palette: navy-charcoal land, subdued water, brighter
     * class-differentiated roads and soft labels. Geometry/tile data unchanged.
     */
    private fun applyMidnightPalette(style: Style) {
        (style.getLayer("background") as? BackgroundLayer)?.setProperties(
            PropertyFactory.backgroundColor("#172635")
        )
        val fills = mapOf(
            "water" to "#0D1D33",
            "landuse_residential" to "#1D2D3C",
            "landcover_ice_shelf" to "#243748",
            "landcover_wood" to "#1C3440",
            "park" to "#213A42",
            "building" to "#324455",
            "road_area_pier" to "#263747"
        )
        fills.forEach { (id, color) ->
            (style.getLayer(id) as? FillLayer)?.setProperties(PropertyFactory.fillColor(color))
        }
        val lines = mapOf(
            "waterway" to "#19394B",
            "park_outline" to "#34576A",
            "road_pier" to "#263747",
            "highway_path" to "#3E5A72",
            "highway_minor" to "#536F87",
            "highway_major_casing" to "#3A5368",
            "highway_major_inner" to "#7891A7",
            "highway_major_subtle" to "#607D94",
            "highway_motorway_casing" to "#465F75",
            "highway_motorway_inner" to "#9BAEC2",
            "highway_motorway_subtle" to "#728FA7",
            "tunnel_motorway_casing" to "#304459",
            "tunnel_motorway_inner" to "#53677E"
        )
        lines.forEach { (id, color) ->
            (style.getLayer(id) as? LineLayer)?.setProperties(PropertyFactory.lineColor(color))
        }
        val labels = mapOf(
            "highway_name_other" to "#CEDBE6",
            "highway_ref" to "#E5EFF7",
            "water_name" to "#829EB7",
            "place_city" to "#B5C7D4",
            "place_city_large" to "#C4D5DF",
            "place_town" to "#A7C0CE",
            "place_suburb" to "#7E96A8",
            "place_village" to "#7F96A8"
        )
        labels.forEach { (id, color) ->
            (style.getLayer(id) as? SymbolLayer)?.setProperties(
                PropertyFactory.textColor(color),
                PropertyFactory.textHaloColor("#172635")
            )
        }
        (style.getLayer("place_other") as? SymbolLayer)?.setProperties(
            PropertyFactory.textOpacity(0.2f)
        )
    }

    private fun askMapTilerKey() {
        val input = EditText(this).apply {
            hint = "MapTiler API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Optional MapTiler comparison")
            .setMessage("A MapTiler API key is required. It is used for map requests only and is not saved to disk, sent to NightRider, or included in the APK.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Load map") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotEmpty()) {
                    mapTilerKey = key
                    selectStyle(3)
                } else {
                    status.text = "MapTiler key not provided; Fiord and Dark are available without one."
                }
            }.show()
    }

    private fun makeButton(label: String): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 11f
        setTextColor(green)
        background = pill(false)
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(4), 0, dp(4), 0)
    }

    private fun pill(selected: Boolean): GradientDrawable = GradientDrawable().apply {
        setColor(if (selected) green else surface)
        cornerRadius = dp(24).toFloat()
        setStroke(dp(1), green)
    }

    private fun roundedSurface(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.argb(242, 7, 20, 13))
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), Color.argb(130, 46, 230, 160))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
