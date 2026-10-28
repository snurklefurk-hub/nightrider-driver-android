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
import android.os.SystemClock
import android.view.ViewGroup
import org.maplibre.android.gestures.MoveGestureDetector
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
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
    private lateinit var hud: NavigationHud
    private val driverMarker by lazy { NavigationMarker.create(this, prefs.aeroEnabled) }
    private var navigationFollow = true
    private var rememberedHeading: Double? = null
    private var navigationZoom: Double? = null
    private val motion = NavigationMotionSmoother()
    private var displayMarker: Marker? = null
    private var lastRenderedFixMs = Long.MIN_VALUE
    private var lastRenderedFraction = -1.0
    private val motionFrames = object : Runnable {
        override fun run() {
            if (!active || simulation != null) return
            renderMotionFrame()
            main.postDelayed(this, 125L)
        }
    }
    private var routeDistanceM: Double? = null
    private var routeDurationS: Double? = null
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
    private var fieldTest: FieldTestPanel? = null
    private var fieldRouteStatus = "Long-press map to set a test destination."
    private var fieldCameraReady = false
    private val fieldRefresh = object : Runnable {
        override fun run() {
            if (active && fieldTest != null) {
                fieldTest?.refreshDiagnostics()
                updateMap()
                main.postDelayed(this, 4000)
            }
        }
    }
    private val tick = object : Runnable {
        override fun run() { if (active) { refreshRide(); main.postDelayed(this, 6000) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        if (prefs.aeroEnabled) setTheme(R.style.Theme_NightRider_Aero)
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (prefs.aeroEnabled) Color.rgb(225,247,255) else Color.rgb(7,27,18))
        }
        // Map-first layout: HUD floats over the same MapLibre canvas in every mode.
        mapView = MapView(this)
        hud = NavigationHud(this, prefs.aeroEnabled, { finish() }, { centerMap() })
        status = hud.status
        val mapStage = FrameLayout(this).apply {
            addView(mapView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(hud.view, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        root.addView(mapStage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (intent.getBooleanExtra("simulation", false)) {
            simulation = MapSimulationPanel(this, savedInstanceState) { updateMap() }
            root.addView(simulation!!.view)
        } else if (intent.getBooleanExtra("field_test", false)) {
            fieldTest = FieldTestPanel(this, { updateMap() }, { fieldDestinationChanged() })
            root.addView(fieldTest!!.view)
        }
        setContentView(root)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { ready ->
            map = ready
            ready.setMinPitchPreference(0.0); ready.setMaxPitchPreference(0.0)
            ready.uiSettings.isRotateGesturesEnabled = false
            ready.uiSettings.isTiltGesturesEnabled = false
            // Keep MapLibre and OSM attribution visible above the lower route card.
            val footerInset = (138 * resources.displayMetrics.density).toInt()
            ready.uiSettings.setAttributionMargins(0, 0, 12, footerInset)
            ready.uiSettings.setLogoMargins(12, 0, 0, footerInset)
            ready.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                override fun onMoveBegin(detector: MoveGestureDetector) {
                    navigationFollow = false
                    fieldTest?.setFollow(false)
                    simulation?.setFollow(false)
                }
                override fun onMove(detector: MoveGestureDetector) {}
                override fun onMoveEnd(detector: MoveGestureDetector) {}
            })
            if (fieldTest != null) {
                ready.addOnMapLongClickListener { point ->
                    fieldTest?.setDestination(point)
                    true
                }
            }
            ready.cameraPosition = CameraPosition.Builder().target(LatLng(32.0811, -81.0911)).zoom(12.0).tilt(0.0).build()
            val styleUrl = if (prefs.aeroEnabled) "https://tiles.openfreemap.org/styles/fiord"
                else "https://tiles.openfreemap.org/styles/dark"
            ready.setStyle(styleUrl) { style ->
                // One LIGHT/DARK preference; both styles use the same road hierarchy.
                NavigationRoadStyle.apply(style, prefs.aeroEnabled)
                updateMap()
            }
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FieldTestPanel.EXPORT_CSV_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { fieldTest?.writeCsv(it) }
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() {
        super.onResume(); mapView.onResume()
        if (simulation == null && fieldTest == null && !prefs.loggedIn) { finish(); return }
        active = true
        when {
            simulation != null -> simulation!!.resume()
            fieldTest != null -> {
                startLocation()
                main.post(fieldRefresh)
                main.post(motionFrames)
            }
            else -> {
                startLocation()
                main.post(tick)
                main.post(motionFrames)
            }
        }
    }
    override fun onPause() {
        active = false; main.removeCallbacks(tick); main.removeCallbacks(fieldRefresh)
        main.removeCallbacks(motionFrames)
        motion.clear()
        lastRenderedFixMs = Long.MIN_VALUE
        lastRenderedFraction = -1.0
        simulation?.pause()
        fieldTest?.pause()
        if (simulation == null) (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this)
        mapView.onPause(); super.onPause()
    }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { simulation?.destroy(); main.removeCallbacksAndMessages(null); io.shutdownNow(); routing.shutdownNow(); mapView.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(state: Bundle) { super.onSaveInstanceState(state); simulation?.save(state); mapView.onSaveInstanceState(state) }

    private fun navigationFix(value: Location): NavigationFix = NavigationFix(
        value.latitude, value.longitude, value.time,
        if (value.hasAccuracy()) value.accuracy.toDouble() else null,
        if (value.hasSpeed()) value.speed.toDouble() else null,
        value.provider ?: ""
    )

    private fun acceptsMapFix(value: Location): Boolean =
        NavigationFixFilter.accepts(
            navigationFix(value), location?.let { navigationFix(it) },
            System.currentTimeMillis()
        )

    private fun startLocation() {
        if (simulation != null) return
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            if (fieldTest != null) {
                status.text = "Allow location permission to test live GPS (no dispatch sharing)."
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION), 92)
            } else status.text = "Allow location on the driver board to show your position."
            return
        }
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (manager.isProviderEnabled(provider)) {
                    manager.getLastKnownLocation(provider)?.let { fix ->
                        if (acceptsMapFix(fix)) {
                            location = Location(fix)
                            motion.acceptFix(
                                SimulationPoint(fix.latitude, fix.longitude),
                                if (fix.hasBearing() && fix.hasSpeed() && fix.speed >= 2f)
                                    fix.bearing.toDouble() else null,
                                SystemClock.elapsedRealtime())
                        }
                    }
                    manager.requestLocationUpdates(provider, 5000L, 10f, this)
                }
            }
        } catch (_: SecurityException) { status.text = "Location permission is unavailable." }
        updateMap()
    }
    override fun onLocationChanged(value: Location) {
        // Always retain the raw reading for Field Test CSV diagnosis, even if
        // the navigation arrow and camera must ignore this unreliable fix.
        fieldTest?.onFix(value)
        if (!acceptsMapFix(value)) {
            fieldTest?.onMapDecision(value.time, false)
            fieldTest?.onFilteredMapFix()
            return
        }
        fieldTest?.onMapDecision(value.time, true)
        val old = location
        val destination = SimulationPoint(value.latitude, value.longitude)
        val derivedBearing = if (value.hasBearing() && value.hasSpeed() && value.speed >= 2f) {
            value.bearing.toDouble()
        } else if (old != null && value.time > old.time &&
            SimulationLeg.meters(
                SimulationPoint(old.latitude, old.longitude), destination) >= 10.0) {
            NavigationCameraMath.bearingDegrees(
                SimulationPoint(old.latitude, old.longitude), destination)
        } else null
        motion.acceptFix(destination, derivedBearing, SystemClock.elapsedRealtime())
        location = Location(value)
        updateMap()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 92 && fieldTest != null && active) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) startLocation()
            else status.text = "GPS denied. Enable location for NightRider in Android Settings."
        }
    }
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
    private fun driverPoint(): LatLng? = location?.takeIf {
        System.currentTimeMillis() - it.time < if (fieldTest != null) 20_000L else 120_000L
    }?.let { LatLng(it.latitude, it.longitude) }

    /** Visual position only. Routing and diagnostics still use accepted GPS. */
    private fun displayDriverPoint(): LatLng? {
        val raw = driverPoint() ?: return null
        if (simulation != null) return raw
        val frame = motion.frame(SystemClock.elapsedRealtime()) ?: return raw
        return LatLng(frame.position.lat, frame.position.lon)
    }

    private fun renderMotionFrame() {
        val m = map ?: return
        if (m.style == null || driverPoint() == null) return
        val marker = displayMarker ?: return
        val sample = motion.frame(SystemClock.elapsedRealtime()) ?: return
        val fixTime = location?.time ?: return
        if (sample.progress >= 1.0 && lastRenderedFraction >= 1.0 &&
            lastRenderedFixMs == fixTime) return
        val newFix = lastRenderedFixMs != fixTime
        lastRenderedFixMs = fixTime
        lastRenderedFraction = sample.progress
        marker.position = LatLng(sample.position.lat, sample.position.lon)
        m.updateMarker(marker)

        val follows = if (fieldTest != null) fieldTest?.follow == true else navigationFollow
        if (!follows) return

        // Calculate route-based zoom once per fix; frames only interpolate
        // position and heading. Avoid restarting 450ms camera animations.
        if (newFix || navigationZoom == null) {
            val speed = gpsSpeed() ?: 0.0
            val bend = if (routePoints.size >= 3) NavigationCameraMath.nextBendMeters(
                SimulationPoint(location!!.latitude, location!!.longitude),
                routePoints.map { SimulationPoint(it.latitude, it.longitude) }
            ) else null
            val base = NavigationCameraMath.zoomForSpeed(speed)
            navigationZoom = NavigationCameraMath.easeZoom(
                navigationZoom, NavigationCameraMath.zoomForUpcomingBend(base, bend))
        }
        val heading = sample.bearingDegrees ?: rememberedHeading
        val ahead = NavigationCameraMath.lookAhead(
            sample.position, heading, gpsSpeed() ?: 0.0)
        val camera = CameraPosition.Builder()
            .target(LatLng(ahead.lat, ahead.lon))
            .bearing(heading ?: 0.0)
            .zoom(navigationZoom ?: NavigationCameraMath.zoomForSpeed(gpsSpeed() ?: 0.0))
            .tilt(0.0).build()
        m.moveCamera(CameraUpdateFactory.newCameraPosition(camera))
    }

    private fun centerMap() {
        navigationFollow = true
        fieldTest?.setFollow(true)
        simulation?.setFollow(true)
        val target = displayDriverPoint() ?: point(ride?.optJSONArray("pickup_coords")) ?: LatLng(32.0811, -81.0911)
        val m = map ?: return
        if (driverPoint() != null && simulation != null) moveNavigationCamera(m, target)
        else m.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 15.5))
    }

    private fun gpsSpeed(): Double? = location?.takeIf {
        System.currentTimeMillis() - it.time < 120000 && it.hasSpeed()
    }?.speed?.toDouble()?.coerceAtLeast(0.0)

    private fun navigationBearing(driver: LatLng): Double? {
        val sim = simulation
        if (sim != null) {
            val leg = sim.engine.leg
            val next = leg.pointAt((sim.engine.distanceMeters + 40.0).coerceAtMost(leg.lengthMeters))
            val current = leg.pointAt(sim.engine.distanceMeters)
            val heading = NavigationCameraMath.bearingDegrees(current, next)
            if (heading != null) rememberedHeading = heading
        } else {
            val fix = location
            if (fix != null && fix.hasBearing() && fix.hasSpeed() && fix.speed >= 2f &&
                System.currentTimeMillis() - fix.time < 15000) {
                rememberedHeading = fix.bearing.toDouble()
            }
        }
        return rememberedHeading
    }

    private fun moveNavigationCamera(m: MapLibreMap, driver: LatLng) {
        if ((simulation == null && fieldTest == null && !navigationFollow) ||
            simulation?.follow == false || fieldTest?.follow == false) return
        val speed = gpsSpeed() ?: 0.0
        val bearing = navigationBearing(driver)
        val ahead = NavigationCameraMath.lookAhead(
            SimulationPoint(driver.latitude, driver.longitude), bearing, speed)
        val bend = if (routePoints.size >= 3) NavigationCameraMath.nextBendMeters(
            SimulationPoint(driver.latitude, driver.longitude),
            routePoints.map { SimulationPoint(it.latitude, it.longitude) }
        ) else null
        val baseZoom = NavigationCameraMath.zoomForSpeed(speed)
        val desiredZoom = NavigationCameraMath.zoomForUpcomingBend(baseZoom, bend)
        val zoom = NavigationCameraMath.easeZoom(navigationZoom, desiredZoom)
        navigationZoom = zoom
        val camera = CameraPosition.Builder()
            .target(LatLng(ahead.lat, ahead.lon))
            .zoom(zoom)
            .bearing(bearing ?: 0.0)
            .tilt(0.0).build()
        m.animateCamera(CameraUpdateFactory.newCameraPosition(camera), 450)
    }

    private fun paintRoute(m: MapLibreMap) {
        if (routePoints.size < 2) return
        // Road-outline contrast is essential against both dark vector styles.
        m.addPolyline(PolylineOptions().addAll(routePoints).color(Color.rgb(20, 29, 46)).width(17f))
        m.addPolyline(PolylineOptions().addAll(routePoints).color(
            if (prefs.aeroEnabled) Color.rgb(63, 132, 245) else Color.rgb(112, 181, 255)
        ).width(10f))
    }

    private fun displayStage(stage: String): String = when (stage) {
        "accepted", "to_pickup" -> "Head to pickup"
        "at_pickup" -> "At pickup"
        "to_dropoff" -> "Head to destination"
        "at_dropoff" -> "Destination reached"
        "completed" -> "Trip complete"
        else -> "Driver map"
    }

    private fun renderNavigation(stage: String) {
        val sim = simulation
        val field = fieldTest
        val metres = if (sim != null) {
            if (sim.engine.stage == SimulationStage.COMPLETE) null
            else (sim.engine.leg.lengthMeters - sim.engine.distanceMeters).coerceAtLeast(0.0)
        } else routeDistanceM
        val seconds = if (sim != null) metres?.div(13.4112) else routeDurationS
        hud.render(
            when {
                field != null && field.destination != null -> "Test destination"
                field != null -> "Choose test destination"
                sim != null -> displayStage(sim.engine.stage.rideStatus)
                else -> displayStage(stage)
            },
            when {
                field != null -> "FIELD TEST · No turn guidance"
                sim != null -> "SIMULATED DRIVE · No live rides"
                else -> "ROUTE PREVIEW · No turn guidance"
            },
            metres, seconds, gpsSpeed()
        )
    }
    private fun updateMap() {
        val m = map ?: return
        if (m.style == null) return
        val sim = simulation
        if (fieldTest != null) { drawFieldMap(m); return }
        if (sim != null) {
            ride = sim.sampleRide()
            location = sim.sampleLocation()
        }
        val current = ride; val driver = driverPoint(); val displayDriver = displayDriverPoint()
        val pickup = point(current?.optJSONArray("pickup_coords")); val dropoff = point(current?.optJSONArray("dropoff_coords"))
        val id = current?.optString("id") ?: ""; val stage = current?.optString("status") ?: ""
        val key = "$id|$stage"
        if (key != routeKey) {
            routeKey = key; routeGeneration++; routePoints = emptyList(); lastRoute = 0
            routeDistanceM = null; routeDurationS = null
            navigationZoom = null
        }
        if (sim != null) {
            routePoints = if (sim.engine.stage == SimulationStage.COMPLETE) emptyList()
                else sim.engine.leg.points.map { LatLng(it.lat, it.lon) }
        }
        m.clear()
        displayMarker = null
        displayDriver?.let {
            displayMarker = m.addMarker(MarkerOptions().position(it).icon(driverMarker)
                .title(if (sim != null) "Simulated driver" else "You"))
        }
        pickup?.let { m.addMarker(MarkerOptions().position(it).title("Pickup")) }
        dropoff?.let { m.addMarker(MarkerOptions().position(it).title("Destination")) }
        paintRoute(m)
        renderNavigation(stage)
        if (driver != null && sim != null) moveNavigationCamera(m, driver)
        if (id != fittedRide && id.isNotEmpty()) { fittedRide = id; centerMap() }
        if (id.isEmpty()) fittedRide = ""
        if (sim != null) {
            status.text = "SIMULATION · ${sim.engine.stage.label}" +
                if (sim.engine.gps == SimulationGps.LOST) " · GPS lost" else ""
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
                    val route = JSONObject(response).getJSONArray("routes").getJSONObject(0)
                    val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
                    if (coords.length() !in 2..30000) throw Exception("Invalid route")
                    val points = (0 until coords.length()).map { coords.getJSONArray(it) }
                        .map { LatLng(it.getDouble(1), it.getDouble(0)) }
                    runOnUiThread {
                        if (active && generation == routeGeneration) {
                            routePoints = points
                            routeDistanceM = route.optDouble("distance").takeIf { it.isFinite() && it >= 0 }
                            routeDurationS = route.optDouble("duration").takeIf { it.isFinite() && it >= 0 }
                            updateMap()
                        }
                    }
                } catch (_: Exception) { runOnUiThread { if (active && generation == routeGeneration) status.text = "Route unavailable · Ride pins remain on the map." } }
            }
        }
    }

    private fun drawFieldMap(m: MapLibreMap) {
        val panel = fieldTest ?: return
        val driver = driverPoint()
        val displayDriver = displayDriverPoint()
        m.clear()
        displayMarker = null
        displayDriver?.let {
            displayMarker = m.addMarker(MarkerOptions().position(it)
                .icon(driverMarker).title("Live GPS · field test"))
        }
        panel.destination?.let { m.addMarker(MarkerOptions().position(it).title("Test destination")) }
        paintRoute(m)
        val trail = panel.trail()
        if (trail.size >= 2) {
            m.addPolyline(PolylineOptions().addAll(trail).color(Color.rgb(255, 171, 58)).width(4f))
        }
        if (panel.follow && driver != null) {
            fieldCameraReady = true
        }
        renderNavigation("")
        val fix = location
        val separation = if (driver != null && routePoints.size >= 2) {
            FieldRouteMath.distanceMeters(SimulationPoint(driver.latitude, driver.longitude),
                routePoints.map { SimulationPoint(it.latitude, it.longitude) })
        } else null
        val deviationLabel = if (separation != null && fix != null && fix.hasAccuracy() &&
            fix.accuracy <= 50f) {
            if (separation > 80) " · Off route: " + separation.toInt() + "m"
            else " · Route offset: " + separation.toInt() + "m"
        } else ""
        status.text = "FIELD TEST · " + fieldRouteStatus + deviationLabel
    }

    private fun fieldDestinationChanged() {
        routeGeneration++
        routePoints = emptyList()
        routeDistanceM = null
        routeDurationS = null
        val destination = fieldTest?.destination
        if (destination == null) {
            fieldRouteStatus = "Long-press map to set a test destination."
            updateMap()
            return
        }
        val driver = driverPoint()
        if (driver == null) {
            fieldRouteStatus = "Waiting for fresh GPS. Tap Route again after a fix."
            updateMap()
            return
        }
        fieldRouteStatus = "Requesting test route from OSRM…"
        updateMap()
        val generation = routeGeneration
        routing.execute {
            try {
                val url = "https://router.project-osrm.org/route/v1/driving/" +
                    driver.longitude + "," + driver.latitude + ";" +
                    destination.longitude + "," + destination.latitude +
                    "?overview=full&geometries=geojson"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                val response = try {
                    if (conn.responseCode != 200) throw Exception("Route unavailable")
                    conn.inputStream.bufferedReader().use { it.readText() }
                } finally { conn.disconnect() }
                val route = JSONObject(response).getJSONArray("routes").getJSONObject(0)
                val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
                if (coords.length() !in 2..30000) throw Exception("Invalid route")
                val points = (0 until coords.length()).map { coords.getJSONArray(it) }
                    .map { LatLng(it.getDouble(1), it.getDouble(0)) }
                runOnUiThread {
                    if (active && generation == routeGeneration && fieldTest != null) {
                        routePoints = points
                        routeDistanceM = route.optDouble("distance").takeIf { it.isFinite() && it >= 0 }
                        routeDurationS = route.optDouble("duration").takeIf { it.isFinite() && it >= 0 }
                        fieldRouteStatus = "Planned route (no traffic) · Orange = recorded trail."
                        updateMap()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (active && generation == routeGeneration && fieldTest != null) {
                        fieldRouteStatus = "Route unavailable. Test pin and GPS still work."
                        updateMap()
                    }
                }
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
