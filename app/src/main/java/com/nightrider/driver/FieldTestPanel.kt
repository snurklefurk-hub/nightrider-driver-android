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
    private val routeRequested: () -> Unit,
    private val motionChanged: (Boolean) -> Boolean,
    private val screenAction: (Boolean) -> Unit,
    private val saveScreenVideo: () -> Unit
) {
    val trace = FieldTrace()
    var destination: LatLng? = null
        private set
    var follow = true
        private set
    var motionEnabled = false
        private set
    private var motionAvailability = "Checking motion sensors…"
    private var motionSnapshot: FieldMotionSnapshot? = null
    private val motionButton = Button(activity)
    private val screenButton = Button(activity)
    private val saveVideoButton = Button(activity)
    private var videoPending = false
    private var videoRecording = false
    private var videoStopping = false
    private var videoReady = false
    private var videoSessionId = ""
    private var videoStartedMs = 0L
    private var videoMessage = ""
    private var latest: Location? = null
    private var filteredMapFixes = 0
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
            if (videoPending || videoRecording || videoStopping) return@setOnClickListener
            if (trace.recording) trace.stop() else {
                trace.start()
                videoSessionId = ""
                videoReady = false
                filteredMapFixes = 0
            }
            render(); changed()
        }
        followButton.setOnClickListener { setFollow(!follow) }
        motionButton.setOnClickListener {
            motionEnabled = motionChanged(!motionEnabled)
            render()
        }
        screenButton.setOnClickListener {
            if (videoRecording) {
                videoStopping = true
                trace.stop()
                videoMessage = "Finishing MP4…"
                screenAction(false)
                render()
            } else if (!videoPending && !videoStopping) {
                videoPending = true
                videoReady = false
                videoMessage = "Waiting for Android screen-capture permission…"
                render()
                screenAction(true)
            }
        }
        saveVideoButton.setOnClickListener { if (videoReady) saveScreenVideo() }
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
            setOnClickListener {
                if (videoRecording || videoPending || videoStopping) return@setOnClickListener
                trace.clear()
                videoSessionId = ""
                videoReady = false
                filteredMapFixes = 0
                render()
                changed()
            }
        }
        panel.addView(row(recordButton, followButton))
        panel.addView(row(motionButton))
        panel.addView(row(screenButton, saveVideoButton))
        panel.addView(row(routeButton, clearButton))
        panel.addView(row(saveButton, shareButton, clearTraceButton))
        diagnostics.textSize = 12f
        panel.addView(diagnostics)
        view = ScrollView(activity).apply {
            addView(panel)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(310))
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
            if (value.hasSpeed()) value.speed else 0f,
            (value.provider ?: "unknown").lowercase(Locale.US)))
        render()
    }

    fun onMapDecision(epochMs: Long, accepted: Boolean) {
        trace.markDecision(epochMs, accepted)
    }

    fun onMotionAtFix(epochMs: Long, snapshot: FieldMotionSnapshot?) {
        trace.markMotion(epochMs, snapshot)
    }

    fun onFilteredMapFix() {
        filteredMapFixes++
        render()
    }

    fun trail(): List<LatLng> = trace.samples.map { LatLng(it.lat, it.lon) }

    fun reportMotion(availability: String, snapshot: FieldMotionSnapshot?) {
        motionAvailability = availability
        motionSnapshot = snapshot
        render()
    }

    /** Keep the GPS session tied to the exact MediaRecorder session identity. */
    fun updateVideoState(state: FieldScreenRecorderService.RecordingState) {
        when (state.phase) {
            FieldScreenRecorderService.Phase.RECORDING -> {
                if (!videoRecording && (videoPending || videoSessionId == state.sessionId)) {
                    videoPending = false
                    videoStopping = false
                    videoRecording = true
                    videoReady = false
                    videoSessionId = state.sessionId
                    videoStartedMs = state.startedEpochMs
                    videoMessage = "Screen + GPS running (no audio)"
                    trace.start()
                    filteredMapFixes = 0
                }
            }
            FieldScreenRecorderService.Phase.READY -> {
                if ((videoRecording || videoStopping || videoPending) &&
                    videoSessionId == state.sessionId) {
                    videoRecording = false
                    videoStopping = false
                    videoPending = false
                    videoReady = true
                    trace.stop()
                    videoMessage = "MP4 ready. Save video and CSV separately."
                }
            }
            FieldScreenRecorderService.Phase.ERROR -> {
                if (videoPending || videoRecording || videoStopping) {
                    videoRecording = false
                    videoStopping = false
                    videoPending = false
                    trace.stop()
                    videoMessage = state.message
                }
            }
            else -> Unit
        }
        render()
    }

    fun screenCaptureCancelled() {
        videoPending = false
        videoMessage = "Android screen capture was cancelled."
        render()
    }

    fun isVideoRunning(): Boolean = videoRecording

    fun isVideoReady(): Boolean = videoReady

    fun filenameBase(): String = videoSessionId.takeIf { it.isNotBlank() }
        ?: "nightrider-field-trace"

    fun refreshDiagnostics() = render()

    fun pause() {
        // Never silently collect location in the background.
        trace.stop()
        render()
    }

    private fun render() {
        recordButton.text = if (trace.recording) "Stop recording" else "Record new trace"
        recordButton.isEnabled = !videoRecording && !videoPending && !videoStopping
        screenButton.text = when {
            videoRecording -> "Stop screen + GPS"
            videoPending -> "Awaiting consent…"
            videoStopping -> "Finishing video…"
            else -> "Record screen + GPS"
        }
        screenButton.isEnabled = !videoPending && !videoStopping
        saveVideoButton.text = "Save MP4"
        saveVideoButton.isEnabled = videoReady
        followButton.text = if (follow) "Follow: ON" else "Follow: OFF"
        motionButton.text = if (motionEnabled) "Motion fusion: ON · experimental"
            else "Motion fusion: OFF · tap when parked"
        shareButton.isEnabled = trace.samples.isNotEmpty() && !videoRecording
        saveButton.isEnabled = trace.samples.isNotEmpty() && !videoRecording
        val fix = latest
        val age = fix?.let { ((System.currentTimeMillis() - it.time) / 1000L).coerceAtLeast(0L) }
        val gps = when {
            fix == null -> "GPS: waiting for fix"
            age != null && age > 15 -> "GPS: stale (" + age + "s)"
            else -> "GPS: " + (if (fix.hasAccuracy()) "±" + fix.accuracy.toInt() + "m" else "accuracy unknown") +
                " · " + String.format(Locale.US, "%.0f", (if (fix.hasSpeed()) fix.speed else 0f) * 2.23694) + " mph" +
                " · " + (fix.provider ?: "unknown")
        }
        diagnostics.text = gps + "\n" +
            (if (trace.recording) "Recording" else "Not recording") + " · " +
            trace.samples.size + " fixes · " +
            String.format(Locale.US, "%.2f", trace.distanceMeters() / 1609.344) +
            " mi traced (estimate) · " + (if (destination == null) "No test pin" else "Test pin set") +
            "\nMap ignored " + filteredMapFixes +
            " unreliable fixes · CSV keeps raw readings" +
            "\nSensors: " + motionAvailability + " · " +
            (if (!motionEnabled) "OFF" else {
                val snapshot = motionSnapshot
                if (snapshot?.headingDeg == null) "Waiting for moving GPS course"
                else "Heading " + String.format(Locale.US, "%.0f°", snapshot.headingDeg)
            }) +
            (motionSnapshot?.yawRateDps?.let {
                " · Turn " + String.format(Locale.US, "%+.0f°/s", it)
            } ?: "") +
            (motionSnapshot?.linearAccelerationMps2?.let {
                " · Motion " + String.format(Locale.US, "%.1f m/s²", it)
            } ?: "") +
            "\nScreen video: " + (if (videoRecording) "● REC" else
                if (videoPending || videoStopping) "Preparing" else
                if (videoReady) "Ready" else "Off") +
            (if (videoMessage.isBlank()) "" else " · " + videoMessage) +
            (if (videoRecording) "\nSYNC epoch_ms: " + System.currentTimeMillis() +
                " · video started: " + videoStartedMs else "")
    }

    private fun saveToFile() {
        if (trace.samples.isEmpty()) return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, filenameBase() + ".csv")
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
