package com.nightrider.driver

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Live overview; navigation instructions remain a future feature. */
class DriverMapActivity : Activity(), LocationListener {
    private lateinit var mapView: MapView
    private lateinit var status: TextView
    private lateinit var prefs: Prefs
    private var map: MapLibreMap? = null
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val routing = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private var active = false
    private var location: Location? = null
    private var ride: JSONObject? = null
    private var lastRoute = 0L
    private var routeKey = ""
    private var routeGeneration = 0
    private var routePoints: List<LatLng> = emptyList()
    private var fittedRide = ""
    private var simulation: MapSimulationPanel? = null
    private val tick = object : Runnable {
        override fun run() { if (active) { refreshRide(); main.postDelayed(this, 6000) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        if (prefs.aeroEnabled) setTheme(R.style.Theme_NightRider_Aero)
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(if (prefs.aeroEnabled) Color.rgb(225,247,255) else Color.rgb(7,27,18)) }
        val controls = LinearLayout(this)
        controls.addView(Button(this).apply { text = "Back"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(Button(this).apply { text = "Recenter"; setOnClickListener { centerMap() } }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        status = TextView(this).apply { setTextColor(if (prefs.aeroEnabled) Color.rgb(18,61,87) else Color.WHITE); setPadding(20,10,20,10); text = "Loading map…" }
        mapView = MapView(this)
        root.addView(controls); root.addView(status)
        if (intent.getBooleanExtra("simulation", false)) {
            simulation = MapSimulationPanel(this, savedInstanceState) { updateMap() }
            root.addView(simulation!!.view)
        }
        root.addView(mapView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { ready ->
            map = ready
            ready.setMinPitchPreference(0.0); ready.setMaxPitchPreference(0.0)
            ready.cameraPosition = CameraPosition.Builder().target(LatLng(32.0811, -81.0911)).zoom(12.0).tilt(0.0).build()
            val styleUrl = if (prefs.aeroEnabled) "https://tiles.openfreemap.org/styles/fiord"
                else "https://tiles.openfreemap.org/styles/dark"
            ready.setStyle(styleUrl) { style ->
                if (prefs.aeroEnabled) applyMidnightPalette(style)
                updateMap()
            }
        }
    }
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() {
        super.onResume(); mapView.onResume()
        if (simulation == null && !prefs.loggedIn) { finish(); return }
        active = true
        if (simulation != null) simulation!!.resume()
        else { startLocation(); main.post(tick) }
    }
    override fun onPause() {
        active = false; main.removeCallbacks(tick)
        simulation?.pause()
        if (simulation == null) (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this)
        mapView.onPause(); super.onPause()
    }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { simulation?.destroy(); main.removeCallbacksAndMessages(null); io.shutdownNow(); routing.shutdownNow(); mapView.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(state: Bundle) { super.onSaveInstanceState(state); simulation?.save(state); mapView.onSaveInstanceState(state) }

    private fun startLocation() {
        if (simulation != null) return
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) { status.text = "Allow location on the driver board to show your position."; return }
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (manager.isProviderEnabled(provider)) {
                    manager.getLastKnownLocation(provider)?.let { if (location == null || it.time > location!!.time) location = it }
                    manager.requestLocationUpdates(provider, 5000L, 10f, this)
                }
            }
        } catch (_: SecurityException) { status.text = "Location permission is unavailable." }
        updateMap()
    }
    override fun onLocationChanged(value: Location) { location = Location(value); updateMap() }
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Legacy Android callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun refreshRide() {
        if (simulation != null) return
        if (!busy.compareAndSet(false, true)) return
        io.execute {
            try {
                val rides = Api.get("/api/rides").optJSONArray("rides") ?: JSONArray()
                var selected: JSONObject? = null
                for (i in 0 until rides.length()) {
                    val item = rides.optJSONObject(i) ?: continue
                    if (item.optString("driver") == prefs.driverName && item.optString("status") !in setOf("completed", "cancelled")) selected = item
                }
                runOnUiThread { if (active) { ride = selected; updateMap() } }
            } catch (_: Exception) { runOnUiThread { if (active) status.text = "Unable to refresh ride. Check your connection." } }
            finally { busy.set(false) }
        }
    }
    private fun point(value: JSONArray?): LatLng? {
        if (value == null || value.length() != 2) return null
        val lat = value.optDouble(0, Double.NaN); val lon = value.optDouble(1, Double.NaN)
        return if (lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) LatLng(lat, lon) else null
    }
    private fun driverPoint(): LatLng? = location?.takeIf { System.currentTimeMillis() - it.time < 120000 }?.let { LatLng(it.latitude, it.longitude) }
    private fun centerMap() {
        val target = driverPoint() ?: point(ride?.optJSONArray("pickup_coords")) ?: LatLng(32.0811, -81.0911)
        map?.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(target, 14.0))
    }
    private fun updateMap() {
        val m = map ?: return
        if (m.style == null) return
        val sim = simulation
        if (sim != null) {
            ride = sim.sampleRide()
            location = sim.sampleLocation()
        }
        val current = ride; val driver = driverPoint()
        val pickup = point(current?.optJSONArray("pickup_coords")); val dropoff = point(current?.optJSONArray("dropoff_coords"))
        val id = current?.optString("id") ?: ""; val stage = current?.optString("status") ?: ""
        val key = "$id|$stage"
        if (key != routeKey) { routeKey = key; routeGeneration++; routePoints = emptyList(); lastRoute = 0 }
        if (sim != null) {
            routePoints = if (sim.engine.stage == SimulationStage.COMPLETE) emptyList()
                else sim.engine.leg.points.map { LatLng(it.lat, it.lon) }
        }
        m.clear()
        driver?.let { m.addMarker(MarkerOptions().position(it).title(if (sim != null) "Simulated driver" else "You")) }
        pickup?.let { m.addMarker(MarkerOptions().position(it).title("Pickup")) }
        dropoff?.let { m.addMarker(MarkerOptions().position(it).title("Destination")) }
        if (routePoints.size >= 2) m.addPolyline(PolylineOptions().addAll(routePoints).color(Color.rgb(46,230,160)).width(5f))
        if (id != fittedRide && id.isNotEmpty()) { fittedRide = id; centerMap() }
        if (id.isEmpty()) fittedRide = ""
        if (sim != null) {
            status.text = "SIMULATION · ${sim.engine.stage.label}" +
                if (sim.engine.gps == SimulationGps.LOST) " · GPS lost" else ""
            if (sim.follow && driver != null) {
                m.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLng(driver), 400)
            }
            return // Simulation never calls the ride API, GPS service or live route endpoint.
        }
        status.text = when {
            current == null -> "No active ride · Tap Recenter to find yourself."
            driver == null -> "Active ride · Waiting for a fresh location."
            else -> "Active ride · ${stage.replace('_', ' ')} · Route is an estimate."
        }
        val target = if (stage in setOf("to_dropoff", "at_dropoff")) dropoff else pickup
        if (current != null && driver != null && target != null && System.currentTimeMillis() - lastRoute >= 30000) {
            lastRoute = System.currentTimeMillis(); val generation = ++routeGeneration
            routing.execute {
                try {
                    val url = "https://router.project-osrm.org/route/v1/driving/${driver.longitude},${driver.latitude};${target.longitude},${target.latitude}?overview=full&geometries=geojson"
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000; conn.readTimeout = 15000
                    val response = try { if (conn.responseCode != 200) throw Exception("Route unavailable"); conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
                    val coords = JSONObject(response).getJSONArray("routes").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
                    val points = (0 until coords.length()).map { coords.getJSONArray(it) }.map { LatLng(it.getDouble(1), it.getDouble(0)) }
                    runOnUiThread { if (active && generation == routeGeneration) { routePoints = points; updateMap() } }
                } catch (_: Exception) { runOnUiThread { if (active && generation == routeGeneration) status.text = "Route unavailable · Ride pins remain on the map." } }
            }
        }
    }
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

}
