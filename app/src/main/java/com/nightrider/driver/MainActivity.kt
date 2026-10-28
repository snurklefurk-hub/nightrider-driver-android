package com.nightrider.driver

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The driver console: sign in once, then watch open requests, move your own
 * rides through the pickup/drop-off stages, and talk to dispatch.
 *
 * Rider and dispatch boards live in a browser; this app is driver only.
 */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private var aeroActive = false

    private fun themedColor(attr: Int): Int {
        val value = TypedValue()
        theme.resolveAttribute(attr, value, true)
        return if (value.resourceId != 0) getColor(value.resourceId) else value.data
    }
    private lateinit var loginScroll: ScrollView
    private lateinit var nameInput: EditText
    private lateinit var passInput: EditText
    private lateinit var loginButton: Button
    private lateinit var loginError: TextView
    private lateinit var boardPanel: View
    private lateinit var driverLine: TextView
    private lateinit var boardStatus: TextView
    private lateinit var openRides: LinearLayout
    private lateinit var myRides: LinearLayout
    private lateinit var chatTabTeam: Button
    private lateinit var chatTabDispatch: Button
    private lateinit var chatTabRider: Button
    private lateinit var chatRecipientSummary: TextView
    private lateinit var chatRideSelector: Spinner
    private lateinit var chatSendStatus: TextView
    private lateinit var chatLog: ScrollView
    private lateinit var chatContent: LinearLayout
    private lateinit var chatText: EditText
    private lateinit var chatSend: Button

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val ridesBusy = AtomicBoolean(false)
    private val chatBusy = AtomicBoolean(false)
    private val chatSending = AtomicBoolean(false)

    private var chatSeen = 0.0
    @Volatile private var chatNeedsHistory = false
    private var chatMode = "team"
    private val chatMessages = LinkedHashMap<String, JSONObject>()
    private val renderedChatKeys = HashSet<String>()
    private var assignedRideIds = listOf<String>()
    private var assignedRideNames = listOf<String>()
    private var selectedChatRide = ""
    private var chatPendingSignature = ""
    private var chatPendingId = ""
    private var chatInitialHistoryLoaded = false
    private val unreadChat = mutableMapOf("team" to 0, "dispatch" to 0, "rider" to 0)
    private var shownName = ""
    private var stage = "available"
    private var running = false
    private var ridesSignature = ""
    private var completedRidesExpanded = false
    private var latestLocation: Location? = null
    private var locationUpdatesRegistered = false
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            latestLocation = Location(location)
            updateLocationLine()
        }
        override fun onProviderEnabled(provider: String) { updateLocationLine() }
        override fun onProviderDisabled(provider: String) { updateLocationLine() }
        @Deprecated("Legacy callback for Android 8-10")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) { }
    }

    /** Mirrors the server's workflow so the app still works if config is slow. */
    private val statusLabels = mapOf(
        "requested" to "Requested",
        "accepted" to "Assigned",
        "to_pickup" to "Heading to pickup",
        "at_pickup" to "At pickup",
        "to_dropoff" to "Heading to destination",
        "at_dropoff" to "At destination",
        "completed" to "Completed",
        "cancelled" to "Cancelled"
    )
    private val stageAction = mapOf(
        "accepted" to "Head to pickup",
        "to_pickup" to "Arrive at pickup",
        "at_pickup" to "Head to destination",
        "to_dropoff" to "Arrive at destination",
        "at_dropoff" to "Complete ride"
    )

    private val rideTick = object : Runnable {
        override fun run() {
            if (!running) return
            refreshRides()
            main.postDelayed(this, 6_000)
        }
    }

    private val chatTick = object : Runnable {
        override fun run() {
            if (!running) return
            refreshChat()
            main.postDelayed(this, 4_000)
        }
    }

    private val pingTick = object : Runnable {
        override fun run() {
            if (!running) return
            pingDriver()
            main.postDelayed(this, 10_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        aeroActive = prefs.aeroEnabled
        if (aeroActive) setTheme(R.style.Theme_NightRider_Aero)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Api.sessionToken = prefs.sessionToken

        loginScroll = findViewById(R.id.loginScroll)
        if (aeroActive) {
            loginScroll.setBackgroundResource(R.drawable.bg_aero_sky)
        } else {
            val loginGrid = NightGridDrawable()
            loginScroll.background = loginGrid
            loginScroll.setOnScrollChangeListener { _, _, y, _, _ -> loginGrid.setParallaxOffset(y) }
        }
        nameInput = findViewById(R.id.nameInput)
        passInput = findViewById(R.id.passInput)
        loginButton = findViewById(R.id.loginButton)
        loginError = findViewById(R.id.loginError)
        boardPanel = findViewById(R.id.boardPanel)
        if (aeroActive) {
            boardPanel.setBackgroundResource(R.drawable.bg_aero_sky)
        } else {
            val boardGrid = NightGridDrawable()
            boardPanel.background = boardGrid
            findViewById<ScrollView>(R.id.boardScroll).setOnScrollChangeListener { _, _, y, _, _ -> boardGrid.setParallaxOffset(y) }
        }
        driverLine = findViewById(R.id.driverLine)
        boardStatus = findViewById(R.id.boardStatus)
        openRides = findViewById(R.id.openRides)
        myRides = findViewById(R.id.myRides)
        chatTabTeam = findViewById(R.id.chatTabTeam)
        chatTabDispatch = findViewById(R.id.chatTabDispatch)
        chatTabRider = findViewById(R.id.chatTabRider)
        chatRecipientSummary = findViewById(R.id.chatRecipientSummary)
        chatRideSelector = findViewById(R.id.chatRideSelector)
        chatSendStatus = findViewById(R.id.chatSendStatus)
        chatLog = findViewById(R.id.chatLog)
        chatContent = findViewById(R.id.chatContent)
        chatText = findViewById(R.id.chatText)
        chatSend = findViewById(R.id.chatSend)
        chatTabTeam.setOnClickListener { chooseChatMode("team") }
        chatTabDispatch.setOnClickListener { chooseChatMode("dispatch") }
        chatTabRider.setOnClickListener { chooseChatMode("rider") }
        chatRideSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (chatMode != "rider" || position !in assignedRideIds.indices) return
                val picked = assignedRideIds[position]
                if (picked != selectedChatRide) {
                    selectedChatRide = picked
                    renderChatHistory(true)
                }
            }
        }
        updateChatControls()

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.driverMapButton).setOnClickListener {
            startActivity(Intent(this, VelaNavigationActivity::class.java))
        }
        loginButton.setOnClickListener { attemptLogin() }
        passInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else {
                false
            }
        }
        chatSend.setOnClickListener { sendChat() }
        chatText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendChat()
                true
            } else {
                false
            }
        }

        if (prefs.lastName.isNotBlank()) nameInput.setText(prefs.lastName)
    }

    override fun onResume() {
        super.onResume()
        if (prefs.aeroEnabled != aeroActive) {
            recreate() // Returning from Settings immediately applies the selected appearance.
            return
        }
        applyKeepAwake()
        val name = prefs.driverName
        if (!prefs.loggedIn) {
            if (name.isNotBlank()) prefs.logout()
            stopPolling()
            showLogin()
        } else {
            if (shownName != name) {
                shownName = name
                chatSeen = 0.0
                chatMessages.clear()
                renderedChatKeys.clear()
                assignedRideIds = emptyList()
                assignedRideNames = emptyList()
                selectedChatRide = ""
                chatPendingSignature = ""
                chatPendingId = ""
                chatInitialHistoryLoaded = false
                unreadChat.keys.toList().forEach { unreadChat[it] = 0 }
                chatMode = "team"
                chatNeedsHistory = false
                updateChatControls()
                ridesSignature = ""
                chatContent.removeAllViews()
                openRides.removeAllViews()
                myRides.removeAllViews()
                completedRidesExpanded = false
            }
            showBoard(name)
            startPolling()
        }
    }

    override fun onPause() {
        super.onPause()
        stopPolling()
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacksAndMessages(null)
        io.shutdownNow()
    }

    // ---- session ---------------------------------------------------------

    private fun showLogin() {
        boardPanel.visibility = View.GONE
        loginScroll.visibility = View.VISIBLE
        loginError.text = ""
        loginButton.isEnabled = true
        passInput.setText("")
        if (nameInput.text.isNullOrBlank() && prefs.lastName.isNotBlank()) {
            nameInput.setText(prefs.lastName)
        }
    }

    private fun showBoard(name: String) {
        loginScroll.visibility = View.GONE
        boardPanel.visibility = View.VISIBLE
        updateLocationLine()
        driverLine.setOnClickListener {
            if (!hasLocationPermission()) {
                maybeAskLocation()
            } else {
                val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                    !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } else {
                    stopLocationUpdates()
                    startLocationUpdates()
                }
            }
        }
    }

    private fun attemptLogin() {
        val username = nameInput.text.toString().trim()
        val password = passInput.text.toString()
        loginError.text = ""

        if (username.isEmpty() || password.isEmpty()) {
            loginError.text = getString(R.string.err_credentials)
            return
        }

        loginButton.isEnabled = false
        io.execute {
            try {
                val response = Api.post(
                    "/api/auth/login",
                    JSONObject().put("username", username).put("password", password)
                )
                val user = response.optJSONObject("user") ?: throw ApiException(401, "Sign-in failed.")
                if (user.optString("role") != "driver") {
                    throw ApiException(403, "This account is not a driver account.")
                }
                val token = response.optString("token")
                if (token.isBlank()) throw ApiException(401, "The server did not start a session. Try again.")
                val displayName = user.optString("display_name", username)
                main.post {
                    prefs.login(username, displayName, token)
                    Api.sessionToken = token
                    passInput.setText("")
                    loginButton.isEnabled = true
                    maybeAskLocation()
                    shownName = ""
                    onResume()
                }
            } catch (e: Exception) {
                main.post {
                    loginButton.isEnabled = true
                    loginError.text = getString(R.string.err_unreachable, friendly(e))
                }
            }
        }
    }

    private fun maybeAskLocation() {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                91
            )
        }
    }

    private fun applyKeepAwake() {
        if (prefs.keepAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ---- polling ---------------------------------------------------------

    private fun startPolling() {
        if (running) return
        running = true
        startLocationUpdates()
        main.post(rideTick)
        main.post(chatTick)
        main.post(pingTick)
    }

    private fun stopPolling() {
        running = false
        stopLocationUpdates()
        main.removeCallbacks(rideTick)
        main.removeCallbacks(chatTick)
        main.removeCallbacks(pingTick)
    }

    private fun pingDriver() {
        val name = prefs.driverName
        if (name.isBlank()) return
        val fix = freshLocation()
        val body = JSONObject().put("name", name).put("status", stage)
            .put("location_sharing", hasLocationPermission())
        if (fix != null) {
            body.put("coords", JSONObject().put("lat", fix.latitude).put("lng", fix.longitude))
                .put("location_at", fix.time / 1000.0)
            if (fix.hasAccuracy()) body.put("accuracy", fix.accuracy.toDouble())
        }
        io.execute {
            try {
                Api.post(
                    "/api/drivers/ping",
                    body
                )
            } catch (ignored: Exception) {
                // Presence is best effort; the ride poll reports real failures.
            }
        }
    }

    // ---- rides -----------------------------------------------------------

    private fun refreshRides() {
        val name = prefs.driverName
        if (name.isBlank() || !ridesBusy.compareAndSet(false, true)) return
        io.execute {
            try {
                val all = Api.get("/api/rides").optJSONArray("rides") ?: JSONArray()
                val open = ArrayList<JSONObject>()
                val mine = ArrayList<JSONObject>()
                for (i in 0 until all.length()) {
                    val ride = all.optJSONObject(i) ?: continue
                    val status = ride.optString("status")
                    val driver = ride.optString("driver")
                    val unassigned = ride.isNull("driver") || driver.isBlank()
                    if (status == "requested" && unassigned) {
                        open.add(ride)
                    } else if (driver == name) {
                        mine.add(ride)
                    }
                }
                val live = mine.filter {
                    val s = it.optString("status")
                    s != "completed" && s != "cancelled"
                }
                stage = if (live.isEmpty()) "available" else live.last().optString("status", "available")
                runOnUiThread { renderRides(open, mine) }
            } catch (e: Exception) {
                runOnUiThread {
                    if (e is ApiException && e.status == 401) {
                        prefs.logout()
                        Api.sessionToken = ""
                        stopPolling()
                        showLogin()
                    } else {
                        boardStatus.text = getString(R.string.board_offline, friendly(e))
                    }
                }
            } finally {
                ridesBusy.set(false)
            }
        }
    }

    private fun renderRides(open: List<JSONObject>, mine: List<JSONObject>) {
        boardStatus.text = ""
        updateChatRides(mine)
        val signature = signatureOf(open, mine)
        if (signature == ridesSignature) return
        ridesSignature = signature

        openRides.removeAllViews()
        if (open.isEmpty()) {
            openRides.addView(emptyText(R.string.empty_open))
        } else {
            open.forEach { openRides.addView(rideCard(it, false)) }
        }

        myRides.removeAllViews()
        // Finished rides are terminal regardless of whether the trip was completed or cancelled.
        val finishedStatuses = setOf("completed", "cancelled")
        val active = mine.filter { it.optString("status") !in finishedStatuses }
        val completed = mine.withIndex()
            .filter { it.value.optString("status") in finishedStatuses }
            .sortedWith(compareByDescending<IndexedValue<JSONObject>> { rideHistoryTimestamp(it.value) }
                .thenByDescending { it.index })
            .take(5)
            .map { it.value }

        active.forEach { myRides.addView(rideCard(it, true)) }
        if (completed.isNotEmpty()) {
            val completedContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (completedRidesExpanded) View.VISIBLE else View.GONE
            }
            val toggle = Button(this).apply {
                setBackgroundResource(if (aeroActive) R.drawable.btn_aero_green else R.drawable.btn_outline_plum)
                setTextColor(themedColor(R.attr.nrSecondaryButtonText))
                minHeight = dp(48)
                textSize = 13f
                text = getString(R.string.completed_rides_toggle, completed.size,
                    getString(if (completedRidesExpanded) R.string.completed_collapse else R.string.completed_expand))
                isAllCaps = false
                setOnClickListener {
                    completedRidesExpanded = !completedRidesExpanded
                    completedContainer.visibility = if (completedRidesExpanded) View.VISIBLE else View.GONE
                    text = getString(R.string.completed_rides_toggle, completed.size,
                        getString(if (completedRidesExpanded) R.string.completed_collapse else R.string.completed_expand))
                }
            }
            myRides.addView(toggle)
            completed.forEach { completedContainer.addView(rideCard(it, true)) }
            myRides.addView(completedContainer)
        }
        if (active.isEmpty() && completed.isEmpty()) {
            myRides.addView(emptyText(R.string.empty_mine))
        }
    }

    /** Prefer server ride timestamps; API list position breaks missing-date ties. */
    private fun rideHistoryTimestamp(ride: JSONObject): Double {
        for (key in listOf("completed_at", "updated_at", "created_at", "requested_at", "at")) {
            val value = ride.opt(key) ?: continue
            value.toString().toDoubleOrNull()?.let { if (it.isFinite()) return it }
            try {
                return java.time.Instant.parse(value.toString()).toEpochMilli().toDouble()
            } catch (_: Exception) { }
        }
        return 0.0
    }

    private fun signatureOf(open: List<JSONObject>, mine: List<JSONObject>): String {
        val sb = StringBuilder()
        open.forEach { sb.append(it.optString("id")).append(it.optString("status")).append('|') }
        sb.append("//")
        mine.forEach {
            sb.append(it.optString("id"))
                .append(it.optString("status"))
                .append(it.optString("driver"))
                .append('|')
        }
        return sb.toString()
    }

    private fun rideCard(ride: JSONObject, isMine: Boolean): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(if (aeroActive) R.drawable.panel_aero else R.drawable.panel)
        val pad = dp(14)
        card.setPadding(pad, pad, pad, pad)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = dp(10)
        card.layoutParams = lp

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        val idView = TextView(this)
        idView.text = ride.optString("id")
        idView.setTextColor(themedColor(R.attr.nrMuted))
        idView.textSize = 11f
        head.addView(idView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val statusView = TextView(this)
        statusView.text = statusLabels[ride.optString("status")] ?: ride.optString("status")
        statusView.setTextColor(themedColor(R.attr.nrAccent))
        statusView.textSize = 11f
        head.addView(statusView)
        card.addView(head)

        card.addView(field(R.string.field_pickup, ride.optString("pickup")))
        card.addView(field(R.string.field_dropoff, ride.optString("dropoff")))

        val bits = ArrayList<String>()
        num(ride, "distance_mi")?.let { bits.add(String.format(Locale.US, "%.1f mi", it)) }
        num(ride, "driver_to_pickup_mi")?.let {
            bits.add(String.format(Locale.US, "%.1f mi to pickup", it))
        }
        num(ride, "fare")?.let { bits.add(money(it)) }
        ride.optString("payment_method").takeIf { it.isNotBlank() }?.let { bits.add(it) }
        ride.optString("rider_name").takeIf { it.isNotBlank() }?.let { bits.add(it) }
        if (bits.isNotEmpty()) {
            val meta = TextView(this)
            meta.text = bits.joinToString("  \u00b7  ")
            meta.setTextColor(themedColor(R.attr.nrMuted))
            meta.textSize = 12f
            meta.setPadding(0, dp(6), 0, 0)
            card.addView(meta)
        }

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(10), 0, 0)
        val status = ride.optString("status")
        val rideId = ride.optString("id")
        if (!isMine) {
            row.addView(actionButton(getString(R.string.action_accept)) { acceptRide(rideId) })
        } else {
            stageAction[status]?.let { label ->
                if (status != "completed" && status != "cancelled") {
                    row.addView(actionButton(label) { advanceRide(rideId) })
                }
            }
            if (status == "completed") {
                row.addView(actionButton(getString(R.string.action_rate)) { rateDialog(rideId) })
            }
            if (status != "completed" && status != "cancelled") {
                row.addView(actionButton(getString(R.string.action_cancel_ride)) { confirmCancelRide(rideId) })
            }
        }
        if (row.childCount > 0) card.addView(row)

        return card
    }

    private fun field(labelRes: Int, value: String): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(8), 0, 0)
        val label = TextView(this)
        label.text = getString(labelRes)
        label.setTextColor(themedColor(R.attr.nrMuted))
        label.textSize = 10f
        box.addView(label)
        val text = TextView(this)
        text.text = value
        text.setTextColor(themedColor(R.attr.nrText))
        text.textSize = 15f
        box.addView(text)
        return box
    }

    private fun actionButton(label: String, onClick: () -> Unit): View {
        val button = Button(this)
        button.text = label
        button.isAllCaps = false
        button.setTextColor(themedColor(R.attr.nrPrimaryButtonText))
        button.textSize = 12f
        button.setBackgroundResource(if (aeroActive) R.drawable.btn_aero_blue else R.drawable.btn_outline)
        button.minimumHeight = 0
        button.setPadding(dp(14), dp(8), dp(14), dp(8))
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.marginEnd = dp(8)
        button.layoutParams = lp
        button.setOnClickListener { onClick() }
        return button
    }

    private fun emptyText(resId: Int): View {
        val text = TextView(this)
        text.text = getString(resId)
        text.setTextColor(themedColor(R.attr.nrMuted))
        text.textSize = 13f
        text.setPadding(0, dp(4), 0, dp(12))
        return text
    }

    private fun acceptRide(rideId: String) {
        val name = prefs.driverName
        if (name.isBlank()) return
        val coords = currentCoords()
        io.execute {
            try {
                val body = JSONObject().put("driver", name)
                if (coords != null) {
                    body.put(
                        "driver_coords",
                        JSONObject().put("lat", coords.first).put("lng", coords.second)
                    )
                }
                Api.post("/api/rides/" + Api.enc(rideId) + "/accept", body)
                main.post {
                    ridesSignature = ""
                    boardStatus.text = ""
                    refreshRides()
                }
            } catch (e: Exception) {
                main.post { boardStatus.text = getString(R.string.action_failed, friendly(e)) }
            }
        }
    }

    private fun advanceRide(rideId: String) {
        io.execute {
            try {
                Api.post("/api/rides/" + Api.enc(rideId) + "/advance", JSONObject())
                main.post {
                    ridesSignature = ""
                    boardStatus.text = ""
                    refreshRides()
                }
            } catch (e: Exception) {
                main.post { boardStatus.text = getString(R.string.action_failed, friendly(e)) }
            }
        }
    }

    private fun confirmCancelRide(rideId: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.cancel_ride_title)
            .setMessage(R.string.cancel_ride_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_cancel_ride) { _, _ -> cancelRide(rideId) }
            .show()
    }

    private fun cancelRide(rideId: String) {
        io.execute {
            try {
                Api.post("/api/rides/" + Api.enc(rideId) + "/cancel", JSONObject())
                main.post {
                    boardStatus.text = getString(R.string.cancel_ride_done)
                    ridesSignature = ""
                    refreshRides()
                }
            } catch (e: Exception) {
                main.post { boardStatus.text = getString(R.string.action_failed, friendly(e)) }
            }
        }
    }

    private fun rateDialog(rideId: String) {
        val stars = arrayOf("1 \u2605", "2 \u2605", "3 \u2605", "4 \u2605", "5 \u2605")
        AlertDialog.Builder(this)
            .setTitle(R.string.rate_title)
            .setItems(stars) { _, which -> sendRating(rideId, which + 1) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun sendRating(rideId: String, stars: Int) {
        val name = prefs.driverName
        io.execute {
            try {
                Api.post(
                    "/api/rides/" + Api.enc(rideId) + "/rating",
                    JSONObject()
                        .put("by", "driver")
                        .put("by_name", name)
                        .put("stars", stars)
                        .put("issues", JSONArray())
                )
                main.post {
                    boardStatus.text = getString(R.string.rate_saved, stars)
                    ridesSignature = ""
                    refreshRides()
                }
            } catch (e: Exception) {
                main.post { boardStatus.text = getString(R.string.action_failed, friendly(e)) }
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun isRecent(location: Location): Boolean {
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L
        return ageMs in 0..110_000L
    }

    private fun freshLocation(): Location? {
        if (!hasLocationPermission()) return null
        latestLocation?.takeIf { isRecent(it) }?.let { return it }
        return try {
            val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            manager.allProviders.mapNotNull { provider ->
                try { manager.getLastKnownLocation(provider) } catch (_: Exception) { null }
            }.filter { isRecent(it) }.maxByOrNull { it.elapsedRealtimeNanos }
        } catch (_: Exception) { null }
    }

    private fun updateLocationLine() {
        driverLine.text = prefs.driverName
    }

    private fun startLocationUpdates() {
        if (!running || locationUpdatesRegistered || !hasLocationPermission()) {
            updateLocationLine()
            return
        }
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(provider, 10_000L, 0f, locationListener, Looper.getMainLooper())
                    locationUpdatesRegistered = true
                }
            } catch (_: Exception) { /* Approximate-only access may exclude GPS. */ }
        }
        updateLocationLine()
    }

    private fun stopLocationUpdates() {
        try {
            (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(locationListener)
        } catch (_: Exception) { }
        locationUpdatesRegistered = false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 91 && prefs.loggedIn && running) {
            startLocationUpdates()
            pingDriver()
        }
    }

    private fun currentCoords(): Pair<Double, Double>? {
        val location = freshLocation() ?: return null
        return Pair(location.latitude, location.longitude)
    }

    // ---- team text -------------------------------------------------------

    // Chat updates only add content inside a fixed scroll region. They never
    // navigate, open a modal, raise the keyboard, or scroll the driver's view.
    private fun chooseChatMode(mode: String) {
        if (mode !in listOf("team", "dispatch", "rider")) return
        chatMode = mode
        unreadChat[mode] = 0
        updateChatControls()
        renderChatHistory(true) // Explicit tap: permitted to show latest messages.
    }

    private fun updateChatRides(mine: List<JSONObject>) {
        val next = mine.filter { it.optString("id").isNotBlank() }
        val ids = next.map { it.optString("id") }
        val labels = next.map { (it.optString("rider_name").ifBlank { "Ride" }) + " · " + it.optString("id") }
        if (ids == assignedRideIds && labels == assignedRideNames) return
        val previous = selectedChatRide
        assignedRideIds = ids
        assignedRideNames = labels
        selectedChatRide = if (previous in ids) previous else ids.firstOrNull() ?: ""
        updateChatControls()
        if (previous != selectedChatRide && chatMode == "rider") renderChatHistory(false)
        // A driver assigned to an existing ride needs messages older than their
        // latest team-message timestamp. Always merge the full server history.
        refreshChat(true)
    }

    private fun updateChatBadges() {
        listOf(chatTabTeam to "team", chatTabDispatch to "dispatch", chatTabRider to "rider")
            .forEach { (tab, mode) ->
                val count = unreadChat[mode] ?: 0
                val label = mode.replaceFirstChar { it.uppercase() }
                tab.text = if (count > 0) "$label ${minOf(count, 99)}" else label
                tab.isSelected = chatMode == mode
                tab.alpha = if (chatMode == mode) 1.0f else 0.65f
            }
    }

    private fun updateChatControls() {
        updateChatBadges()
        chatRecipientSummary.text = when (chatMode) {
            "dispatch" -> "Private to Dispatch"
            "rider" -> "Rider + Dispatch"
            else -> "All drivers (Team)"
        }
        val labels = if (chatMode == "rider") assignedRideNames else
            listOf(if (chatMode == "dispatch") "Dispatch only" else "All drivers")
        val options = if (labels.isEmpty()) listOf("No assigned ride") else labels
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        chatRideSelector.adapter = adapter
        chatRideSelector.isEnabled = chatMode == "rider" && assignedRideIds.isNotEmpty()
        if (chatMode == "rider") {
            val index = assignedRideIds.indexOf(selectedChatRide)
            if (index >= 0) chatRideSelector.setSelection(index)
        }
        chatSend.isEnabled = !chatSending.get() && (chatMode != "rider" || selectedChatRide.isNotBlank())
    }

    private fun chatKey(message: JSONObject): String =
        message.optString("message_kind", if (message.has("ride_id")) "ride" else "team") +
            ":" + message.optString("id")

    private fun matchesChat(message: JSONObject): Boolean = when (chatMode) {
        "rider" -> selectedChatRide.isNotBlank() && message.optString("ride_id") == selectedChatRide
        "dispatch" -> !message.has("ride_id") && message.optString("channel") == "private"
        else -> !message.has("ride_id") && message.optString("channel") != "private"
    }

    private fun renderChatHistory(commanded: Boolean) {
        val scroll = chatLog.scrollY
        chatContent.removeAllViews()
        renderedChatKeys.clear()
        chatMessages.values.filter(::matchesChat).sortedBy { it.optDouble("at", 0.0) }
            .forEach { message ->
                renderedChatKeys.add(chatKey(message))
                addChatBubble(message)
            }
        if (commanded) chatLog.post { chatLog.fullScroll(View.FOCUS_DOWN) }
        else chatLog.post { chatLog.scrollTo(0, scroll) }
    }

    private fun refreshChat(full: Boolean = false) {
        val name = prefs.driverName
        if (name.isBlank()) return
        if (!chatBusy.compareAndSet(false, true)) {
            if (full) chatNeedsHistory = true
            return
        }
        val since = if (full) 0.0 else (chatSeen - 0.05).coerceAtLeast(0.0)
        io.execute {
            try {
                val res = Api.get("/api/chat/messages?since=" + String.format(Locale.US, "%.6f", since))
                val items = res.optJSONArray("messages") ?: JSONArray()
                val received = ArrayList<JSONObject>()
                for (i in 0 until items.length()) {
                    val message = items.optJSONObject(i) ?: continue
                    received.add(message)
                }
                main.post {
                    received.forEach { message ->
                        val key = chatKey(message)
                        chatSeen = maxOf(chatSeen, message.optDouble("at", 0.0))
                        if (chatMessages.putIfAbsent(key, message) == null) {
                            if (chatInitialHistoryLoaded && !message.optBoolean("mine")) {
                                val tab = if (message.has("ride_id")) "rider" else
                                    if (message.optString("channel") == "private") "dispatch" else "team"
                                unreadChat[tab] = (unreadChat[tab] ?: 0) + 1
                            }
                            if (matchesChat(message) && renderedChatKeys.add(key)) addChatBubble(message)
                        }
                    }
                    chatInitialHistoryLoaded = true
                    updateChatBadges()
                    // No fullScroll here: arriving texts must never move the view.
                }
            } catch (ignored: Exception) {
                // The message remains in its compose field on send failure.
            } finally {
                chatBusy.set(false)
                if (chatNeedsHistory) {
                    chatNeedsHistory = false
                    main.post { refreshChat(true) }
                }
            }
        }
    }

    private fun addChatBubble(message: JSONObject) {
        val from = message.optString("from")
        val mine = message.optBoolean("mine")
        val priv = message.optString("channel") == "private" ||
            message.optString("audience") == "rider"
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (mine) Gravity.END else Gravity.START
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(6); layoutParams = lp
        }
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(
                if (aeroActive) {
                    if (priv) R.drawable.bubble_aero_private else if (mine) R.drawable.bubble_aero_me else R.drawable.bubble_aero_them
                } else {
                    if (priv) R.drawable.bubble_private else if (mine) R.drawable.bubble_me else R.drawable.bubble_them
                }
            )
            val pad = dp(10); setPadding(pad, dp(7), pad, dp(7))
        }
        val color = try { android.graphics.Color.parseColor(message.optString("chat_color", "#ffffff")) }
            catch (_: Exception) { themedColor(R.attr.nrText) }
        val who = TextView(this).apply {
            text = (if (mine) "You" else from) +
                (if (message.has("ride_id")) " · Ride " + message.optString("ride_id") else "") +
                (if (priv) " · private" else "")
            setTextColor(if (aeroActive && color == android.graphics.Color.WHITE) themedColor(R.attr.nrAccent) else color); textSize = 10f
        }
        bubble.addView(who)
        val body = TextView(this).apply {
            text = message.optString("text")
            setTextColor(themedColor(R.attr.nrText)); textSize = 13f
            maxWidth = (resources.displayMetrics.widthPixels * 0.74).toInt() - dp(20)
        }
        bubble.addView(body)
        val at = message.optDouble("at", 0.0)
        val time = TextView(this).apply {
            text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date((at * 1000).toLong()))
            setTextColor(themedColor(R.attr.nrMuted)); textSize = 9f
        }
        bubble.addView(time); row.addView(bubble)
        chatContent.addView(row)
    }

    private fun sendChat() {
        val name = prefs.driverName
        if (name.isBlank()) return
        val text = chatText.text.toString().trim()
        if (text.isEmpty() || !chatSending.compareAndSet(false, true)) return
        if (chatMode == "rider" && selectedChatRide.isBlank()) {
            chatSending.set(false)
            return
        }
        val body = JSONObject().put("text", text)
        if (chatMode == "rider") {
            body.put("ride_id", selectedChatRide)
            body.put("audience", "shared")
        } else if (chatMode == "dispatch") {
            body.put("private", true)
        }
        val signature = chatMode + ":" + selectedChatRide + ":" + text
        if (chatPendingSignature != signature) {
            chatPendingSignature = signature
            chatPendingId = UUID.randomUUID().toString()
        }
        body.put("client_id", chatPendingId)
        chatSend.isEnabled = false
        io.execute {
            try {
                Api.post("/api/chat/messages", body)
                main.post {
                    if (chatText.text.toString().trim() == text) chatText.setText("")
                    chatPendingSignature = ""
                    chatPendingId = ""
                    chatSendStatus.text = "Sent"
                    chatSending.set(false)
                    updateChatControls()
                    refreshChat()
                }
            } catch (e: Exception) {
                main.post {
                    chatSendStatus.text = "Send failed — draft kept. Try again."
                    chatSending.set(false)
                    updateChatControls()
                }
            }
        }
    }

    // ---- helpers ---------------------------------------------------------

    private fun num(obj: JSONObject, key: String): Double? {
        val value = obj.optDouble(key, Double.NaN)
        return if (value.isNaN()) null else value
    }

    private fun money(value: Double): String =
        "\$" + String.format(Locale.US, "%.2f", value)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun friendly(e: Throwable): String = when (e) {
        is ApiException -> e.message ?: "Server error"
        is java.net.UnknownHostException -> "No network connection."
        is java.net.SocketTimeoutException -> "Server timed out."
        is java.net.ConnectException -> "Server unreachable."
        else -> e.message ?: e.javaClass.simpleName
    }
}


