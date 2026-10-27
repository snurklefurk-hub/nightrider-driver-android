package com.nightrider.driver

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Map displays only the driver's own active ride and device location. */
class DriverMapActivity : Activity(), LocationListener {
    private lateinit var map: WebView
    private lateinit var status: TextView
    private lateinit var prefs: Prefs
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private var active = false
    private var pageReady = false
    private var ride: JSONObject? = null
    private var location: Location? = null
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            refreshRide()
            main.postDelayed(this, 6_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xff071b12.toInt())
        }
        val back = Button(this).apply { text = "Back to driver board"; setOnClickListener { finish() } }
        status = TextView(this).apply {
            setTextColor(0xffe8f7ea.toInt()); textSize = 14f
            setPadding(24, 12, 24, 12); text = "Loading your map…"
        }
        map = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setGeolocationEnabled(false)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) { pageReady = true; updateMap() }
                override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean = true
            }
        }
        root.addView(back)
        root.addView(status)
        root.addView(map, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        val html = assets.open("driver-map.html").bufferedReader().use { it.readText() }
        map.loadDataWithBaseURL("https://nightrider.onrender.com/", html, "text/html", "UTF-8", null)
    }

    override fun onResume() {
        super.onResume()
        if (!prefs.loggedIn) { finish(); return }
        active = true
        startLocation()
        main.post(tick)
    }

    override fun onPause() {
        active = false
        main.removeCallbacks(tick)
        (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this)
        super.onPause()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        io.shutdownNow()
        map.destroy()
        super.onDestroy()
    }

    private fun startLocation() {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) { status.text = "Allow location on the driver board to show your position."; return }
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (provider in providers) {
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
    @Deprecated("Legacy Android callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun refreshRide() {
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
            } catch (_: Exception) {
                runOnUiThread { if (active) status.text = "Unable to refresh ride. Check your connection." }
            } finally { busy.set(false) }
        }
    }

    private fun updateMap() {
        if (!pageReady) return
        val current = ride
        val data = JSONObject()
        data.put("id", current?.optString("id") ?: "")
        data.put("status", current?.optString("status") ?: "")
        data.put("pickup", current?.optJSONArray("pickup_coords") ?: JSONObject.NULL)
        data.put("dropoff", current?.optJSONArray("dropoff_coords") ?: JSONObject.NULL)
        val loc = location
        data.put("driver", if (loc != null && System.currentTimeMillis() - loc.time < 120000) JSONArray().put(loc.latitude).put(loc.longitude) else JSONObject.NULL)
        status.text = when {
            current == null -> "No active ride. Your location appears when available."
            loc == null -> "Active ride · Waiting for your location."
            else -> "Active ride · ${current.optString("status").replace('_', ' ')} · Route is an estimate."
        }
        map.evaluateJavascript("window.updateDriverMap(${data});", null)
    }
}

