package com.nightrider.driver

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import app.vela.core.model.LatLng as VelaPoint
import app.vela.core.nav.NavEvent
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

/** First Vela-powered driver navigation slice. All guidance is visual and silent. */
class VelaNavigationActivity : Activity(), LocationListener {
    private val session = VelaDriveSession()
    private val main = Handler(Looper.getMainLooper())
    private val network = Executors.newSingleThreadExecutor()
    private val rides = Executors.newSingleThreadExecutor()
    private lateinit var prefs: Prefs
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private lateinit var title: TextView
    private lateinit var instruction: TextView
    private lateinit var summary: TextView
    private lateinit var gps: TextView
    private lateinit var followButton: Button
    private var follow = true
    private var active = false
    private var test = false
    private var destination: VelaPoint? = null
    private var destinationName = "No assigned ride"
    private var destinationKey = ""
    private var marker: Marker? = null
    private var destinationMarker: Marker? = null
    private var line: Polyline? = null
    private var generation = 0
    private var routing = false
    private var rideBusy = false
    private var lastAttempt = Long.MIN_VALUE
    private var routingMessage = "Waiting for GPS"
    private val trace = FieldTrace()
    private var lastRaw: Location? = null
    private val pulse = object : Runnable {
        override fun run() {
            if (!active) return
            render()
            if (!test) refreshRide()
            if (destination != null && (session.route == null || session.state.offRoute)) requestRoute()
            main.postDelayed(this, 2000)
        }
    }

    override fun onCreate(saved: Bundle?) {
        prefs = Prefs(this)
        if (prefs.aeroEnabled) setTheme(R.style.Theme_NightRider_Aero)
        super.onCreate(saved)
        test = intent.getBooleanExtra("test", false)
        if (!test && !prefs.loggedIn) { finish(); return }
        if (prefs.keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        MapLibre.getInstance(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(appearanceColor(R.attr.nrTopbar))
        }
        val tools = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        tools.addView(Button(this).apply { text = "Back"; setOnClickListener { finish() } })
        tools.addView(TextView(this).apply {
            text = if (test) "VELA · TEST" else "NIGHTRIDER · VELA"
            setTextColor(appearanceColor(R.attr.nrAccent)); textSize = 13f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        followButton = Button(this).apply {
            text = "Following"
            setOnClickListener { follow = true; center(); render() }
        }
        tools.addView(followButton)
        root.addView(tools)
        title = label(14f); instruction = label(22f)
        root.addView(title); root.addView(instruction)
        mapView = MapView(this)
        root.addView(mapView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        summary = label(18f); gps = label(13f)
        root.addView(summary); root.addView(gps)
        if (test) {
            root.addView(label(12f).apply { text = "Long-press map to set a destination. No ride changes." })
            val row = LinearLayout(this)
            row.addView(Button(this).apply { text = "Save GPS CSV"; setOnClickListener { exportCsv() } })
            row.addView(Button(this).apply { text = "Route again"; setOnClickListener { requestRoute() } })
            root.addView(row)
            trace.start()
        }
        root.setPadding(0, 0, 0, dp(14))
        setContentView(root)
        mapView.onCreate(saved)
        mapView.getMapAsync { m ->
            if (isFinishing || isDestroyed) return@getMapAsync
            map = m
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isRotateGesturesEnabled = false
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    follow = false; render()
                }
            }
            if (test) m.addOnMapLongClickListener { p ->
                setDestination(VelaPoint(p.latitude, p.longitude), "Test destination", "${p.latitude},${p.longitude}")
                true
            }
            m.cameraPosition = CameraPosition.Builder().target(LatLng(32.0811, -81.0911)).zoom(13.0).build()
            m.setStyle(if (prefs.aeroEnabled) "https://tiles.openfreemap.org/styles/fiord"
                else "https://tiles.openfreemap.org/styles/dark") {
                if (!isFinishing && !isDestroyed) { renderRoute(); renderMarker(); center() }
            }
        }
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    private fun label(size: Float) = TextView(this).apply {
        textSize = size; setTextColor(appearanceColor(R.attr.nrText))
        setPadding(dp(12), dp(5), dp(12), dp(5))
        maxLines = 3
    }
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() {
        super.onResume(); mapView.onResume(); active = true
        if (!test && !prefs.loggedIn) { finish(); return }
        startGps(); main.post(pulse)
    }
    override fun onPause() {
        active = false; main.removeCallbacks(pulse)
        try { (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(this) } catch (_: Exception) {}
        mapView.onPause(); super.onPause()
    }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onDestroy() {
        generation++; network.shutdownNow(); rides.shutdownNow(); main.removeCallbacksAndMessages(null)
        mapView.onDestroy(); super.onDestroy()
    }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); mapView.onSaveInstanceState(out) }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }

    private fun startGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 701)
            return
        }
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        var enabled = false
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try { if (lm.isProviderEnabled(provider)) {
                lm.requestLocationUpdates(provider, 1000L, 0f, this, Looper.getMainLooper()); enabled = true
            } } catch (_: Exception) {}
        }
        if (!enabled) { routingMessage = "Enable location in Android Settings"; render() }
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 701 && active) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startGps()
            else { routingMessage = "Precise location is required for navigation"; render() }
        }
    }
    override fun onLocationChanged(value: Location) {
        if (!active) return
        lastRaw = Location(value)
        if (test) trace.add(FieldFix(value.time, value.latitude, value.longitude,
            if (value.hasAccuracy()) value.accuracy else 999f,
            if (value.hasSpeed()) value.speed else 0f, value.provider ?: "unknown"))
        val events = session.accept(VelaDriveSession.Fix(VelaPoint(value.latitude, value.longitude),
            value.elapsedRealtimeNanos / 1_000_000,
            if (value.hasAccuracy()) value.accuracy.toDouble() else 999.0,
            if (value.hasSpeed()) value.speed.toDouble() else null,
            if (value.hasBearing()) value.bearing.toDouble() else null), SystemClock.elapsedRealtime())
        if (test) trace.markDecision(value.time, events != null)
        if (events != null) {
            renderMarker(); center()
            if (events.any { it is NavEvent.RerouteNeeded } || (session.route == null && destination != null)) requestRoute()
        }
        render()
    }
    override fun onProviderEnabled(provider: String) { render() }
    override fun onProviderDisabled(provider: String) { render() }
    @Deprecated("Legacy location callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun setDestination(p: VelaPoint?, name: String, key: String) {
        if (destinationKey == key) return
        generation++; routing = false; destinationKey = key; destination = p; destinationName = name
        session.clearRoute(); lastAttempt = Long.MIN_VALUE
        routingMessage = if (p == null) "No assigned ride" else "Waiting for fresh GPS"
        renderRoute(); requestRoute(); render()
    }
    private fun refreshRide() {
        if (rideBusy || !prefs.loggedIn) return
        rideBusy = true
        rides.execute {
            try {
                val array = Api.get("/api/rides").optJSONArray("rides") ?: JSONArray()
                val target = VelaRideTarget.select(array, prefs.driverName)
                main.post { if (active && prefs.loggedIn) setDestination(target?.point,
                    target?.label ?: "No assigned ride", target?.key ?: "none") }
            } catch (_: Exception) {
                main.post { if (active) { routingMessage = "Could not refresh assignment; return to board to verify"; render() } }
            } finally { main.post { rideBusy = false } }
        }
    }

    private fun requestRoute() {
        val target = destination ?: return
        val fix = session.lastFix ?: return
        val now = SystemClock.elapsedRealtime()
        if (!active || routing || !session.fresh(now) ||
            (lastAttempt != Long.MIN_VALUE && now - lastAttempt < 15_000)) return
        routing = true; lastAttempt = now; val request = generation
        routingMessage = if (session.route == null) "Finding route…" else "Recalculating route…"
        render()
        network.execute {
            val result = runCatching {
                val path = "${fix.point.lng},${fix.point.lat};${target.lng},${target.lat}"
                val conn = URL("https://router.project-osrm.org/route/v1/driving/$path?steps=true&overview=full&geometries=geojson").openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 10000; conn.readTimeout = 15000
                    conn.setRequestProperty("User-Agent", "NightRiderDriver/Vela-navigation-prototype")
                    check(conn.responseCode in 200..299)
                    VelaRouteAdapter.parse(JSONObject(conn.inputStream.bufferedReader().use { it.readText() }))
                } finally { conn.disconnect() }
            }
            main.post {
                if (isFinishing || isDestroyed || request != generation) return@post
                routing = false
                result.onSuccess {
                    session.adopt(it); routingMessage = "OSRM · no live traffic"; renderRoute()
                    // The next fresh location advances Vela's engine; never replay a stale fix as new.
                }.onFailure { routingMessage = "Route unavailable · retrying when GPS is fresh" }
                render()
            }
        }
    }
    private fun renderRoute() {
        val m = map ?: return
        if (m.style == null) return
        line?.let { m.removePolyline(it) }; line = null
        destinationMarker?.let { m.removeMarker(it) }; destinationMarker = null
        session.route?.let { r ->
            line = m.addPolyline(PolylineOptions().addAll(r.polyline.map { LatLng(it.lat,it.lng) })
                .color(appearanceColor(R.attr.nrAccent)).width(6f))
        }
        destination?.let { destinationMarker = m.addMarker(MarkerOptions().position(LatLng(it.lat,it.lng)).title(destinationName)) }
    }
    private fun renderMarker() {
        val m = map ?: return
        if (m.style == null) return
        val fix = session.lastFix ?: return
        val p = LatLng(fix.point.lat, fix.point.lng)
        if (marker == null) marker = m.addMarker(MarkerOptions().position(p).icon(NavigationMarker.create(this, prefs.aeroEnabled)).title("Your position"))
        else { marker!!.position = p; m.updateMarker(marker!!) }
    }
    private fun center() {
        if (!follow || !session.fresh(SystemClock.elapsedRealtime())) return
        val fix = session.lastFix ?: return
        val speed = fix.speedMps ?: 0.0
        val zoom = when { speed >= 25 -> 15.5; speed >= 15 -> 16.0; else -> 17.0 }
        map?.easeCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder()
            .target(LatLng(fix.point.lat,fix.point.lng)).zoom(zoom).bearing(session.heading).tilt(0.0).build()), 700)
    }
    private fun render() {
        val fresh = session.fresh(SystemClock.elapsedRealtime())
        val r = session.route; val s = session.state
        title.text = destinationName + if (fresh && session.route != null && s.distanceToNextManeuver > 10)
            String.format(Locale.US, " · Next turn %.0f ft", s.distanceToNextManeuver * 3.28084) else ""
        instruction.text = when {
            !fresh -> if (session.lastFix == null) "Waiting for precise GPS" else "GPS stale · last known position"
            s.arrived -> "Destination reached"
            r == null -> routingMessage
            routing -> "Recalculating route…"
            s.offRoute -> "Off route · finding a new route"
            else -> r.maneuvers.getOrNull(s.stepIndex)?.instruction ?: "Follow the route"
        }
        val speed = session.lastFix?.speedMps
        val speedText = if (fresh && speed != null && speed.isFinite() && speed >= 0)
            String.format(Locale.US,"%.0f mph",speed * 2.236936) else "Speed unavailable"
        val distance = if (s.remainingDistance > 0 || s.arrived) s.remainingDistance else r?.distanceMeters
        val duration = if (s.remainingDuration > 0 || s.arrived) s.remainingDuration else r?.durationSeconds
        summary.text = if (r != null && distance != null && duration != null)
            String.format(Locale.US,"%.1f mi · %.0f min · %s",distance/1609.344,kotlin.math.ceil(duration/60),speedText)
            else speedText
        val fix = session.lastFix
        val age = fix?.let { ((SystemClock.elapsedRealtime()-it.elapsedMs)/1000).coerceAtLeast(0) }
        val raw = lastRaw
        gps.text = (if (fix == null) "No precise fix yet" else if (fresh) "GPS ±${fix.accuracyM.toInt()} m" else "Last accepted GPS: ${age}s ago") +
            (if (raw != null && raw.hasAccuracy() && raw.accuracy > 50) " · coarse ${raw.provider} fix ignored" else "") +
            "\n$routingMessage" + (if (test) " · ${trace.samples.size} samples recorded" else "")
        marker?.let { it.title = if (fresh) "Your position" else "Last known position · ${age}s old"; map?.updateMarker(it) }
        followButton.text = if (follow) "Following" else "Recenter"
    }
    private fun exportCsv() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "text/csv"
            putExtra(Intent.EXTRA_TITLE,"nightrider-vela-${System.currentTimeMillis()}.csv")
        },702)
    }
    @Deprecated("Legacy document callback") override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        super.onActivityResult(code,result,data)
        if (code == 702 && result == RESULT_OK) data?.data?.let { uri ->
            try {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(trace.csv()) }
                Toast.makeText(this,"GPS CSV saved",Toast.LENGTH_SHORT).show()
            } catch (_: Exception) { Toast.makeText(this,"Could not save CSV",Toast.LENGTH_LONG).show() }
        }
    }
}
