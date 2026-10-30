package com.nightrider.driver

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import java.io.File
import kotlin.math.min

/**
 * On-device, consent-based Field Test screen recording, with NO microphone/audio.
 *
 * The Activity owns the opt-in UI and GPS trace. This short-lived foreground
 * service owns Android's one-use MediaProjection token and video encoder.
 * Never used from the production driver map or simulator.
 */
class FieldScreenRecorderService : Service() {
    enum class Phase { IDLE, STARTING, RECORDING, READY, ERROR }
    data class RecordingState(
        val phase: Phase = Phase.IDLE,
        val sessionId: String = "",
        val startedEpochMs: Long = 0,
        val message: String = ""
    )

    companion object {
        const val ACTION_START = "com.nightrider.driver.FIELD_VIDEO_START"
        const val ACTION_STOP = "com.nightrider.driver.FIELD_VIDEO_STOP"
        const val EXTRA_PERMISSION = "permission_result"
        const val EXTRA_SESSION = "field_session"
        const val EXTRA_RESULT_CODE = "permission_code"
        const val REQUEST_CAPTURE = 742
        const val REQUEST_SAVE_MP4 = 743
        private const val CHANNEL_ID = "nightrider-field-screen-video"
        private const val NOTIFICATION_ID = 523
        private const val MAX_DURATION_MS = 10 * 60 * 1000
        private const val MAX_FILE_BYTES = 250L * 1024L * 1024L

        @Volatile var state = RecordingState()
            private set

        /** No file-provider or Internet permission: SAF explicitly copies to chosen Uri. */
        fun videoFile(context: Context, sessionId: String): File? {
            if (!sessionId.matches(Regex("nightrider-field-[0-9]{13}"))) return null
            return File(File(context.noBackupFilesDir, "field-videos"), "$sessionId.mp4")
        }

        fun requestStop(context: Context) {
            context.startService(Intent(context, FieldScreenRecorderService::class.java)
                .setAction(ACTION_STOP))
        }
    }

    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var display: VirtualDisplay? = null
    private var callback: MediaProjection.Callback? = null
    private var output: File? = null
    private var sessionId: String = ""
    private var started = false
    private var finishing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture(intent)
            ACTION_STOP -> finishCapture()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID, "NightRider Field Test screen recording",
            NotificationManager.IMPORTANCE_LOW)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
        val stopIntent = Intent(this, FieldScreenRecorderService::class.java)
            .setAction(ACTION_STOP)
        val stopAction = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("NightRider Field Test recording")
            .setContentText("Screen + GPS test. No audio. Stop from notification.")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_media_pause, "Stop recording", stopAction).build())
            .build()
    }

    private fun startCapture(intent: Intent) {
        // Duplicate start requests must not destroy an active capture service.
        if (started || state.phase == Phase.RECORDING || state.phase == Phase.STARTING) {
            return
        }
        sessionId = intent.getStringExtra(EXTRA_SESSION).orEmpty()
        val candidate = videoFile(this, sessionId)
        if (candidate == null) {
            state = RecordingState(Phase.ERROR, message = "Invalid session name.")
            stopSelf()
            return
        }
        state = RecordingState(Phase.STARTING, sessionId)
        try {
            // Android 14+: consent must precede starting this FGS type;
            // the foreground state must precede getMediaProjection().
            startForeground(NOTIFICATION_ID, notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            @Suppress("DEPRECATION")
            val consent = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(EXTRA_PERMISSION, Intent::class.java)
            else intent.getParcelableExtra(EXTRA_PERMISSION) as? Intent
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            if (resultCode != Activity.RESULT_OK || consent == null)
                throw SecurityException("Screen capture permission was not granted.")

            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager
            val captured = manager.getMediaProjection(resultCode, consent)
                ?: throw IllegalStateException("Screen capture permission expired.")
            projection = captured

            // Screen capture may be full display or a user-selected single app.
            // Target a light, common 720–1280px MP4, no microphone or app audio.
            val window = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val bounds = if (Build.VERSION.SDK_INT >= 30)
                window.maximumWindowMetrics.bounds else null
            @Suppress("DEPRECATION")
            val metrics = resources.displayMetrics
            val sourceWidth = bounds?.width() ?: metrics.widthPixels
            val sourceHeight = bounds?.height() ?: metrics.heightPixels
            val factor = min(1.0, 1280.0 / maxOf(sourceWidth, sourceHeight).toDouble())
            val width = ((sourceWidth * factor).toInt() / 16 * 16).coerceAtLeast(16)
            val height = ((sourceHeight * factor).toInt() / 16 * 16).coerceAtLeast(16)

            candidate.parentFile?.mkdirs()
            // The previous session is preserved until a new session starts.
            output = candidate
            if (candidate.exists()) candidate.delete()
            @Suppress("DEPRECATION")
            val encoder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this)
                else MediaRecorder()
            recorder = encoder
            encoder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            encoder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            encoder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            encoder.setVideoSize(width, height)
            encoder.setVideoFrameRate(15)
            encoder.setVideoEncodingBitRate(2_000_000)
            encoder.setOutputFile(candidate.absolutePath)
            encoder.setMaxDuration(MAX_DURATION_MS)
            encoder.setMaxFileSize(MAX_FILE_BYTES)
            encoder.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                    what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) finishCapture()
            }
            encoder.prepare()
            val registered = object : MediaProjection.Callback() {
                override fun onStop() {
                    // Android can revoke capture on screen lock, user stop, or another session.
                    Handler(Looper.getMainLooper()).post { finishCapture() }
                }
            }
            callback = registered
            captured.registerCallback(registered, Handler(Looper.getMainLooper()))
            display = captured.createVirtualDisplay(
                "NightRider Field Test", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                encoder.surface, null, Handler(Looper.getMainLooper()))
            encoder.start()
            started = true
            state = RecordingState(Phase.RECORDING, sessionId,
                System.currentTimeMillis(), "Screen recording active; no audio")
        } catch (error: Exception) {
            state = RecordingState(Phase.ERROR, sessionId, message =
                "Screen video unavailable: " + (error.message ?: "capture failed"))
            finishCapture(keepError = true)
        }
    }

    private fun finishCapture(keepError: Boolean = false) {
        if (finishing) return
        finishing = true
        var validVideo = started
        try {
            if (started) recorder?.stop()
        } catch (_: RuntimeException) {
            validVideo = false
        }
        started = false
        try { display?.release() } catch (_: Exception) {}
        display = null
        try { recorder?.reset() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        try { callback?.let { projection?.unregisterCallback(it) } } catch (_: Exception) {}
        callback = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        val old = state
        val file = output
        if (keepError || !validVideo || file == null || !file.isFile ||
            file.length() < 4_096L) {
            file?.delete()
            if (!keepError) state = RecordingState(Phase.ERROR,
                old.sessionId, old.startedEpochMs, "Video could not be finalized.")
        } else {
            state = RecordingState(Phase.READY, old.sessionId, old.startedEpochMs,
                "Screen video ready to save")
        }
        @Suppress("DEPRECATION")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (!finishing) finishCapture()
        super.onDestroy()
    }
}
