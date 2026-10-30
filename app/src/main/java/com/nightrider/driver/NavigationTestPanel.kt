package com.nightrider.driver

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.widget.*
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One control for the capture session; saving uses an explicit Android document picker. */
class NavigationTestPanel(private val activity: Activity, private val begin: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var id = ""
    private var waiting = false
    private var starting = false
    private var running = false
    private var stopping = false
    private var exporting = false
    private var startAt = 0L
    private val toggle = Button(activity)
    private val save = Button(activity)
    private val status = TextView(activity)
    val view = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(activity).apply { text = "Park before using controls. Local screen + precise location + sensors. No audio. 10 minute limit." })
        addView(LinearLayout(activity).apply {
            addView(toggle, LinearLayout.LayoutParams(0,48 * activity.resources.displayMetrics.density.toInt(),1f))
            addView(save, LinearLayout.LayoutParams(0,48 * activity.resources.displayMetrics.density.toInt(),1f))
        })
        addView(status)
    }
    private val poll = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this,250) }
    }
    init {
        id = activity.getPreferences(0).getString("last_test_id","").orEmpty()
        toggle.setOnClickListener { if (running || starting) stop("user_stop") else start() }
        save.text = "Save test ZIP"
        save.setOnClickListener {
            if (id.isBlank()) return@setOnClickListener
            activity.startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"
                putExtra(Intent.EXTRA_TITLE,"$id.zip")
            }, 745)
        }
        handler.post(poll)
    }
    private fun start() {
        if (waiting || starting || running || exporting) return
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Grant precise location, then press Start test."
            activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),701)
            return
        }
        waiting = true
        try {
            val manager = activity.getSystemService(Activity.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            activity.startActivityForResult(manager.createScreenCaptureIntent(),744)
        } catch (e: Exception) { waiting = false; status.text = "Capture unavailable: ${e.message}" }
        refresh()
    }
    fun result(code: Int, result: Int, data: Intent?): Boolean {
        if (code == 744) {
            waiting = false
            if (result != Activity.RESULT_OK || data == null) { status.text = "Capture cancelled. Test not started."; return true }
            id = "nightrider-field-${System.currentTimeMillis()}"
            activity.getPreferences(0).edit().putString("last_test_id",id).apply()
            starting = true; startAt = android.os.SystemClock.elapsedRealtime()
            try {
                activity.startForegroundService(Intent(activity,FieldScreenRecorderService::class.java)
                    .setAction(FieldScreenRecorderService.ACTION_START)
                    .putExtra(FieldScreenRecorderService.EXTRA_SESSION,id)
                    .putExtra(FieldScreenRecorderService.EXTRA_PERMISSION,data)
                    .putExtra(FieldScreenRecorderService.EXTRA_RESULT_CODE,result))
            } catch(e: Exception) { starting = false; status.text = "Start failed: ${e.message}" }
            return true
        }
        if (code != 745) return false
        val uri = data?.data
        if (result != Activity.RESULT_OK || uri == null) return true
        val exportId = id
        exporting = true; refresh()
        Thread {
            val outcome = runCatching {
                val journal = File(activity.noBackupFilesDir,"$exportId.jsonl")
                check(journal.isFile) { "Diagnostics unavailable" }
                val video = FieldScreenRecorderService.videoFile(activity,exportId)
                val files = listOfNotNull(journal, video?.takeIf { it.isFile })
                val stream = activity.contentResolver.openOutputStream(uri) ?: error("Cannot open destination")
                ZipOutputStream(stream).use { zip ->
                    for (file in files) {
                        zip.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }
                    zip.putNextEntry(ZipEntry("README.txt"))
                    zip.write("Local navigation test. JSONL wall_ms and elapsed_ns align sensor/GPS/video events. No session_end means interruption. Video may be missing after a capture failure. Precise location and screen contents are sensitive.\n".toByteArray())
                    zip.closeEntry()
                }
            }
            handler.post { exporting = false; status.text = if(outcome.isSuccess) "Test ZIP saved." else "Export failed: ${outcome.exceptionOrNull()?.message}"; refresh() }
        }.start()
        return true
    }
    fun stop(reason: String) {
        if (!running && !starting) return
        FieldScreenRecorderService.telemetry?.event(reason)
        FieldScreenRecorderService.requestStop(activity)
        starting = false; running = false; stopping = true
    }
    fun pause() { if (!waiting) stop("screen_left") }
    fun destroy() { stop("activity_destroyed"); handler.removeCallbacks(poll) }
    private fun refresh() {
        val state = FieldScreenRecorderService.state
        if (state.sessionId == id) {
            if (starting && state.phase == FieldScreenRecorderService.Phase.RECORDING) {
                starting = false; running = true; begin()
            }
            if (state.phase == FieldScreenRecorderService.Phase.READY || state.phase == FieldScreenRecorderService.Phase.ERROR) {
                if (running || starting || stopping) status.text = state.message
                running = false; starting = false; stopping = false
            }
        }
        if (starting && android.os.SystemClock.elapsedRealtime()-startAt > 15000) {
            stop("start_timeout"); status.text = "Recorder start timed out."
        }
        FieldScreenRecorderService.telemetry?.failure?.let { stop("diagnostic_failure"); status.text = it }
        toggle.text = when { waiting -> "Awaiting consent"; stopping -> "Finishing"; running || starting -> "Stop test"; else -> "Start test" }
        toggle.isEnabled = !waiting && !stopping && !exporting
        save.isEnabled = !waiting && !running && !starting && !stopping && !exporting && File(activity.noBackupFilesDir,"$id.jsonl").isFile
        if (running) status.text = "REC  SYNC ${System.currentTimeMillis()}  $id"
    }
}
