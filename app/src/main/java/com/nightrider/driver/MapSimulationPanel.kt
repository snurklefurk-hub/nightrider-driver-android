package com.nightrider.driver

import android.app.Activity
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONObject

/** Saved test journeys only. This class cannot publish locations or modify rides. */
class MapSimulationPanel(private val activity: Activity, saved: Bundle?, private val changed: () -> Unit) {
    private val files = listOf("savannah", "bridge", "hilton-head")
    private val labels = listOf("Savannah streets", "Talmadge Bridge", "Hilton Head")
    private val speeds = listOf(1.0, 4.0, 10.0, 30.0)
    private val handler = Handler(Looper.getMainLooper())
    private var scenario = (saved?.getInt("simScenario") ?: 0).coerceIn(0, files.lastIndex)
    var engine = load(scenario)
        private set
    var follow = saved?.getBoolean("simFollow", true) ?: true
        private set
    private var lastTick = 0L
    private var updating = false
    private var resumed = false
    private val play = Button(activity)
    private val stage = Button(activity)
    private val gps = Button(activity)
    private val progress = SeekBar(activity)
    private val detail = TextView(activity)
    private val speed = Button(activity)
    val view: View

    private val tick = object : Runnable {
        override fun run() {
            if (!resumed) return
            val now = SystemClock.elapsedRealtime()
            engine.advance((now - lastTick).coerceAtMost(2000L) / 1000.0)
            lastTick = now
            publish()
            handler.postDelayed(this, 500)
        }
    }

    init {
        if (saved != null) {
            engine.setStage(SimulationStage.values()[(saved.getInt("simStage")).coerceIn(0, SimulationStage.values().lastIndex)])
            engine.seek(saved.getDouble("simProgress", 0.0))
            engine.speedMultiplier = saved.getDouble("simSpeed", 1.0)
            engine.gps = SimulationGps.values()[saved.getInt("simGps").coerceIn(0, SimulationGps.values().lastIndex)]
        }
        val panel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, dp(8), dp(4)) }
        panel.addView(TextView(activity).apply {
            text = "SIMULATION · Sample rides only"
            textSize = 13f; setTextColor(Color.rgb(218, 143, 33)); setPadding(0, dp(4), 0, dp(4))
        })
        val spinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, labels)
            setSelection(scenario)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, selected: View?, position: Int, id: Long) {
                    if (position == scenario) return
                    scenario = position; engine = load(position); publish()
                }
            }
        }
        val top = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        top.addView(spinner, LinearLayout.LayoutParams(0, dp(48), 1f))
        top.addView(CheckBox(activity).apply {
            text = "Follow"; isChecked = follow
            setOnCheckedChangeListener { _, checked -> follow = checked; changed() }
        })
        panel.addView(top)
        play.setOnClickListener {
            if (engine.playing) engine.pause() else engine.play()
            lastTick = SystemClock.elapsedRealtime(); publish()
        }
        stage.setOnClickListener {
            val stages = SimulationStage.values()
            engine.setStage(stages[(engine.stage.ordinal + 1) % stages.size]); publish()
        }
        speed.setOnClickListener {
            engine.speedMultiplier = speeds[(speeds.indexOf(engine.speedMultiplier) + 1) % speeds.size]; publish()
        }
        gps.setOnClickListener {
            val modes = SimulationGps.values()
            engine.gps = modes[(engine.gps.ordinal + 1) % modes.size]; publish()
        }
        val reset = Button(activity).apply { text = "Restart"; setOnClickListener { engine.reset(); publish() } }
        panel.addView(row(play, speed, reset))
        panel.addView(row(stage, gps))
        detail.textSize = 12f; panel.addView(detail)
        progress.max = 1000
        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                if (fromUser && !updating) { engine.seek(value / 1000.0); publish() }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) { engine.pause(); publish() }
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        panel.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)))
        // The testing controls scroll on short screens; the map keeps its own space.
        view = ScrollView(activity).apply { addView(panel); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250)) }
        render()
    }

    private fun row(vararg controls: Button): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        controls.forEach { button ->
            button.isAllCaps = false; button.textSize = 12f; button.setPadding(dp(3), 0, dp(3), 0)
            addView(button, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
    }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density + 0.5f).toInt()
    private fun load(index: Int): DriveSimulation {
        val json = activity.assets.open("simulation/${files[index]}.json").bufferedReader().use { JSONObject(it.readText()) }
        val legs = json.getJSONArray("legs")
        fun leg(index: Int): SimulationLeg {
            val points = legs.getJSONArray(index)
            return SimulationLeg((0 until points.length()).map {
                val p = points.getJSONArray(it); SimulationPoint(p.getDouble(0), p.getDouble(1))
            })
        }
        return DriveSimulation(leg(0), leg(1))
    }
    fun sampleRide(): JSONObject = JSONObject().apply {
        put("id", "simulation-${files[scenario]}"); put("status", engine.stage.rideStatus)
        val pickup = engine.approach.points.last(); val dropoff = engine.trip.points.last()
        put("pickup_coords", org.json.JSONArray().put(pickup.lat).put(pickup.lon))
        put("dropoff_coords", org.json.JSONArray().put(dropoff.lat).put(dropoff.lon))
    }
    fun sampleLocation(): Location? = engine.position?.let { point ->
        Location("nightrider-simulation").apply {
            latitude = point.lat; longitude = point.lon; time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(); accuracy = 5f
            speed = if (engine.playing) 13.4112f else 0f
        }
    }
    private fun render() {
        play.text = when {
            engine.playing -> "Pause"
            engine.stage == SimulationStage.WAITING -> "Start trip"
            else -> "Play"
        }
        play.isEnabled = engine.stage !in listOf(SimulationStage.ARRIVED, SimulationStage.COMPLETE)
        stage.text = "Next stage"
        gps.text = when (engine.gps) { SimulationGps.NORMAL -> "GPS: OK"; SimulationGps.LOST -> "GPS: lost"; SimulationGps.OFF_ROUTE -> "GPS: off route" }
        speed.text = "${engine.speedMultiplier.toInt()}× speed"
        detail.text = "${engine.stage.label} · ${(engine.progress * 100).toInt()}% · ${if (engine.playing) "Playing" else "Paused"}"
        progress.isEnabled = engine.stage !in listOf(SimulationStage.WAITING, SimulationStage.ARRIVED, SimulationStage.COMPLETE)
        updating = true; progress.progress = (engine.progress * 1000).toInt(); updating = false
    }
    private fun publish() { render(); changed() }
    fun resume() { if (resumed) return; resumed = true; lastTick = SystemClock.elapsedRealtime(); handler.post(tick) }
    fun pause() { resumed = false; handler.removeCallbacks(tick) }
    fun destroy() { pause(); handler.removeCallbacksAndMessages(null) }
    fun save(state: Bundle) {
        state.putInt("simScenario", scenario); state.putInt("simStage", engine.stage.ordinal)
        state.putDouble("simProgress", engine.progress); state.putDouble("simSpeed", engine.speedMultiplier)
        state.putInt("simGps", engine.gps.ordinal); state.putBoolean("simFollow", follow)
    }
}
