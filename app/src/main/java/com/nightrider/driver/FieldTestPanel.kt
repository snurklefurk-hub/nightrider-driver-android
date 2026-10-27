package com.nightrider.driver

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.location.Location
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.maplibre.android.geometry.LatLng
import java.util.Locale

/**
 * Deliberately isolated field-testing controls. No NightRider account,
 * dispatch, ride or driver ping APIs are used here. Trace data stays in memory
 * until the tester explicitly taps Share CSV, and is lost when the screen closes.
 */
class FieldTestPanel(
    private val activity: Activity,
    private val changed: () -> Unit,
    private val routeRequested: () -> Unit
) {
    val trace = FieldTrace()
    var destination: LatLng? = null
        private set
    var follow = true
        private set
    private var latest: Location? = null
    private val recordButton = Button(activity)
    private val followButton = Button(activity)
    private val shareButton = Button(activity)
    private val saveButton = Button(activity)
    private val diagnostics = TextView(activity)
    val view: View

    init {
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(3))
        }
        panel.addView(TextView(activity).apply {
            text = "FIELD TEST · Real GPS · No ride/dispatch changes"
            textSize = 13f
            setTextColor(Color.rgb(213, 135, 32))
        })
        panel.addView(TextView(activity).apply {
            text = "Stop safely before interacting. Long-press the map to set a test destination."
            textSize = 12f
            setTextColor(Color.GRAY)
        })
        recordButton.setOnClickListener {
            if (trace.recording) trace.stop() else trace.start()
            render(); changed()
        }
        followButton.setOnClickListener { setFollow(!follow) }
        val routeButton = Button(activity).apply {
            text = "Route again"
            setOnClickListener { if (destination != null) routeRequested() }
        }
        val clearButton = Button(activity).apply {
            text = "Clear pin"
            setOnClickListener { destination = null; routeRequested() }
        }
        shareButton.text = "Share text"
        shareButton.setOnClickListener { share() }
        saveButton.text = "Save CSV"
        saveButton.setOnClickListener { saveToFile() }
        val clearTraceButton = Button(activity).apply {
            text = "Clear trace"
            setOnClickListener { trace.clear(); render(); changed() }
        }
        panel.addView(row(recordButton, followButton))
        panel.addView(row(routeButton, clearButton))
        panel.addView(row(saveButton, shareButton, clearTraceButton))
        diagnostics.textSize = 12f
        panel.addView(diagnostics)
        view = ScrollView(activity).apply {
            addView(panel)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(212))
        }
        render()
    }

    private fun dp(number: Int) = (number * activity.resources.displayMetrics.density + 0.5f).toInt()
    private fun row(vararg buttons: Button) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        buttons.forEach { button ->
            button.isAllCaps = false
            button.textSize = 12f
            button.setPadding(dp(3), 0, dp(3), 0)
            addView(button, LinearLayout.LayoutParams(0, dp(46), 1f))
        }
    }

    fun setDestination(point: LatLng) {
        destination = point
        routeRequested()
    }

    fun setFollow(enabled: Boolean) {
        follow = enabled
        render()
        changed()
    }

    fun onFix(value: Location) {
        latest = Location(value)
        trace.add(FieldFix(value.time, value.latitude, value.longitude,
            if (value.hasAccuracy()) value.accuracy else 999f,
            if (value.hasSpeed()) value.speed else 0f))
        render()
    }

    fun trail(): List<LatLng> = trace.samples.map { LatLng(it.lat, it.lon) }

    fun refreshDiagnostics() = render()

    fun pause() {
        // Never silently collect location in the background.
        trace.stop()
        render()
    }

    private fun render() {
        recordButton.text = if (trace.recording) "Stop recording" else "Record new trace"
        followButton.text = if (follow) "Follow: ON" else "Follow: OFF"
        shareButton.isEnabled = trace.samples.isNotEmpty()
        saveButton.isEnabled = trace.samples.isNotEmpty()
        val fix = latest
        val age = fix?.let { ((System.currentTimeMillis() - it.time) / 1000L).coerceAtLeast(0L) }
        val gps = when {
            fix == null -> "GPS: waiting for fix"
            age != null && age > 15 -> "GPS: stale (" + age + "s)"
            else -> "GPS: " + (if (fix.hasAccuracy()) "±" + fix.accuracy.toInt() + "m" else "accuracy unknown") +
                " · " + String.format(Locale.US, "%.0f", (if (fix.hasSpeed()) fix.speed else 0f) * 2.23694) + " mph"
        }
        diagnostics.text = gps + "\n" +
            (if (trace.recording) "Recording" else "Not recording") + " · " +
            trace.samples.size + " fixes · " +
            String.format(Locale.US, "%.2f", trace.distanceMeters() / 1609.344) +
            " mi traced · " + (if (destination == null) "No test pin" else "Test pin set")
    }

    private fun saveToFile() {
        if (trace.samples.isEmpty()) return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "nightrider-field-trace.csv")
        }
        // SAF asks the tester where to save. No storage permission required.
        activity.startActivityForResult(intent, EXPORT_CSV_REQUEST)
    }

    fun writeCsv(uri: Uri) {
        if (trace.samples.isEmpty()) return
        try {
            val output = activity.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Unable to open selected file")
            output.use { it.write(trace.csv().toByteArray(Charsets.UTF_8)) }
            Toast.makeText(activity, "GPS trace CSV saved.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(activity, "Could not save CSV. Try another location.", Toast.LENGTH_LONG).show()
        }
    }

    companion object { const val EXPORT_CSV_REQUEST = 741 }

    private fun share() {
        if (trace.samples.isEmpty()) return
        // Sharing requires a separate, explicit tap: user selects the destination app.
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "NightRider field GPS trace (CSV)")
            putExtra(Intent.EXTRA_TEXT, trace.csv())
        }
        activity.startActivity(Intent.createChooser(intent, "Share field trace (contains precise locations)"))
    }
}
