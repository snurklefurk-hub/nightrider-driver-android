package com.nightrider.driver

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
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
    private lateinit var chatReplyHint: TextView
    private lateinit var chatLog: ScrollView
    private lateinit var chatContent: LinearLayout
    private lateinit var chatText: EditText
    private lateinit var chatPrivate: CheckBox
    private lateinit var chatSend: Button

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val ridesBusy = AtomicBoolean(false)
    private val chatBusy = AtomicBoolean(false)
    private val chatSending = AtomicBoolean(false)

    private var chatSeen = 0.0
    private val shownChatIds = HashSet<String>()
    private var chatReplyTarget: JSONObject? = null
    private var shownName = ""
    private var stage = "available"
    private var running = false
    private var ridesSignature = ""

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
            main.postDelayed(this, 20_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        Api.sessionToken = prefs.sessionToken

        loginScroll = findViewById(R.id.loginScroll)
        val loginGrid = NightGridDrawable()
        loginScroll.background = loginGrid
        loginScroll.setOnScrollChangeListener { _, _, y, _, _ -> loginGrid.setParallaxOffset(y) }
        nameInput = findViewById(R.id.nameInput)
        passInput = findViewById(R.id.passInput)
        loginButton = findViewById(R.id.loginButton)
        loginError = findViewById(R.id.loginError)
        boardPanel = findViewById(R.id.boardPanel)
        val boardGrid = NightGridDrawable()
        boardPanel.background = boardGrid
        findViewById<ScrollView>(R.id.boardScroll).setOnScrollChangeListener { _, _, y, _, _ -> boardGrid.setParallaxOffset(y) }
        driverLine = findViewById(R.id.driverLine)
        boardStatus = findViewById(R.id.boardStatus)
        openRides = findViewById(R.id.openRides)
        myRides = findViewById(R.id.myRides)
        chatReplyHint = findViewById(R.id.chatReplyHint)
        chatLog = findViewById(R.id.chatLog)
        chatContent = findViewById(R.id.chatContent)
        chatText = findViewById(R.id.chatText)
        chatPrivate = findViewById(R.id.chatPrivate)
        chatSend = findViewById(R.id.chatSend)

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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
                ridesSignature = ""
                chatContent.removeAllViews()
                openRides.removeAllViews()
                myRides.removeAllViews()
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
        driverLine.text = getString(R.string.signed_in_as, name)
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
        main.post(rideTick)
        main.post(chatTick)
        main.post(pingTick)
    }

    private fun stopPolling() {
        running = false
        main.removeCallbacks(rideTick)
        main.removeCallbacks(chatTick)
        main.removeCallbacks(pingTick)
    }

    private fun pingDriver() {
        val name = prefs.driverName
        if (name.isBlank()) return
        io.execute {
            try {
                Api.post(
                    "/api/drivers/ping",
                    JSONObject().put("name", name).put("status", stage)
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
        if (mine.isEmpty()) {
            myRides.addView(emptyText(R.string.empty_mine))
        } else {
            mine.forEach { myRides.addView(rideCard(it, true)) }
        }
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
        card.setBackgroundResource(R.drawable.panel)
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
        idView.setTextColor(getColor(R.color.mist))
        idView.textSize = 11f
        head.addView(idView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val statusView = TextView(this)
        statusView.text = statusLabels[ride.optString("status")] ?: ride.optString("status")
        statusView.setTextColor(getColor(R.color.cyan))
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
            meta.setTextColor(getColor(R.color.mist))
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
        label.setTextColor(getColor(R.color.mist))
        label.textSize = 10f
        box.addView(label)
        val text = TextView(this)
        text.text = value
        text.setTextColor(getColor(R.color.ice))
        text.textSize = 15f
        box.addView(text)
        return box
    }

    private fun actionButton(label: String, onClick: () -> Unit): View {
        val button = Button(this)
        button.text = label
        button.isAllCaps = false
        button.setTextColor(getColor(R.color.cyan))
        button.textSize = 12f
        button.setBackgroundResource(R.drawable.btn_outline)
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
        text.setTextColor(getColor(R.color.mist))
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

    private fun currentCoords(): Pair<Double, Double>? {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc == null) null else Pair(loc.latitude, loc.longitude)
        } catch (e: Exception) {
            null
        }
    }

    // ---- team text -------------------------------------------------------

    private fun refreshChat() {
        val name = prefs.driverName
        if (name.isBlank() || !chatBusy.compareAndSet(false, true)) return
        val since = chatSeen
        io.execute {
            try {
                val res = Api.get("/api/chat/messages?since=" + String.format(Locale.US, "%.3f", since))
                val messages = res.optJSONArray("messages") ?: JSONArray()
                val fresh = ArrayList<JSONObject>()
                for (i in 0 until messages.length()) {
                    val message = messages.optJSONObject(i) ?: continue
                    val unique = message.optString("message_kind") + ":" + message.optString("id")
                    if (!shownChatIds.add(unique)) continue
                    val at = message.optDouble("at", 0.0)
                    if (at > chatSeen) chatSeen = at
                    fresh.add(message)
                }
                if (fresh.isNotEmpty()) runOnUiThread {
                    fresh.forEach { addChatBubble(it) }
                    chatLog.post { chatLog.fullScroll(View.FOCUS_DOWN) }
                }
            } catch (ignored: Exception) {
                // Chat is a side channel; the ride board already shows outages.
            } finally {
                chatBusy.set(false)
            }
        }
    }

    private fun addChatBubble(message: JSONObject) {
        val from = message.optString("from")
        val mine = message.optBoolean("mine")
        val priv = message.optString("channel") == "private" ||
            message.optString("audience") == "dispatch"
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
            setBackgroundResource(if (priv) R.drawable.bubble_private else if (mine) R.drawable.bubble_me else R.drawable.bubble_them)
            val pad = dp(10); setPadding(pad, dp(7), pad, dp(7))
        }
        val color = try { android.graphics.Color.parseColor(message.optString("chat_color", "#ffffff")) }
            catch (_: Exception) { getColor(R.color.ice) }
        val who = TextView(this).apply {
            text = (if (mine) "You" else from) +
                (if (message.has("ride_id")) " · Ride " + message.optString("ride_id") else "") +
                (if (priv) " · private" else "")
            setTextColor(color); textSize = 10f
        }
        bubble.addView(who)
        val body = TextView(this).apply {
            text = message.optString("text")
            setTextColor(getColor(R.color.ice)); textSize = 13f
            maxWidth = (resources.displayMetrics.widthPixels * 0.74).toInt() - dp(20)
        }
        bubble.addView(body)
        val at = message.optDouble("at", 0.0)
        val time = TextView(this).apply {
            text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date((at * 1000).toLong()))
            setTextColor(getColor(R.color.mist)); textSize = 9f
        }
        bubble.addView(time); row.addView(bubble)
        if (priv) {
            android.animation.ObjectAnimator.ofFloat(bubble, "elevation", dp(2).toFloat(), dp(12).toFloat()).apply {
                duration = 1250; repeatMode = android.animation.ValueAnimator.REVERSE
                repeatCount = android.animation.ValueAnimator.INFINITE; start()
            }
        }
        if (!mine) {
            val reply = Button(this).apply {
                text = "Reply"; isAllCaps = false
                setOnClickListener { beginChatReply(message) }
            }
            row.addView(reply)
        }
        chatContent.addView(row)
    }

    private fun beginChatReply(message: JSONObject) {
        val target = JSONObject()
        val rideId = message.optString("ride_id")
        val role = message.optString("role")
        val label: String
        if (rideId.isNotBlank()) {
            target.put("ride_id", rideId)
            target.put("audience", if (message.optString("audience") == "dispatch") "dispatch" else "shared")
            label = "Replying in ride " + rideId + " — tap here to cancel"
        } else if (role == "dispatch") {
            target.put("private", true)
            label = "Replying privately to Dispatch — tap here to cancel"
        } else {
            chatReplyTarget = null
            chatReplyHint.visibility = View.GONE
            chatPrivate.visibility = View.VISIBLE
            chatText.hint = getString(R.string.chat_hint)
            return
        }
        chatReplyTarget = target
        chatReplyHint.text = label
        chatReplyHint.visibility = View.VISIBLE
        chatReplyHint.setOnClickListener {
            chatReplyTarget = null; chatReplyHint.visibility = View.GONE
            chatPrivate.visibility = View.VISIBLE; chatPrivate.isChecked = false
            chatText.hint = getString(R.string.chat_hint)
        }
        chatPrivate.visibility = View.GONE
        chatText.hint = if (rideId.isNotBlank()) getString(R.string.ride_chat_hint) else "Private note to Dispatch"
        chatText.requestFocus()
    }

    private fun sendChat() {
        val name = prefs.driverName
        if (name.isBlank()) return
        val text = chatText.text.toString().trim()
        if (text.isEmpty() || !chatSending.compareAndSet(false, true)) return
        val body = JSONObject().put("text", text)
        chatReplyTarget?.let { target ->
            val keys = target.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                body.put(key, target.get(key))
            }
        }
        if (chatReplyTarget == null && chatPrivate.isChecked) body.put("private", true)
        chatSend.isEnabled = false
        io.execute {
            try {
                Api.post("/api/chat/messages", body)
                main.post {
                    chatText.setText("")
                    chatSend.isEnabled = true
                    chatSending.set(false)
                    chatReplyTarget = null
                    chatReplyHint.visibility = View.GONE
                    chatPrivate.visibility = View.VISIBLE
                    chatPrivate.isChecked = false
                    chatText.hint = getString(R.string.chat_hint)
                    refreshChat()
                }
            } catch (e: Exception) {
                main.post {
                    chatSend.isEnabled = true
                    chatSending.set(false)
                    boardStatus.text = getString(R.string.action_failed, friendly(e))
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

