package com.nightrider.driver

import android.content.Context
import android.hardware.*
import android.os.Build
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Bounded, flushed, local evidence journal. A missing end record means interruption. */
class NavigationTelemetry(private val context: Context, val id: String) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val output = FileOutputStream(File(context.noBackupFilesDir, "$id.jsonl"))
    private var bytes = 0L
    private var closed = false
    var failure: String? = null
        private set
    private val last = mutableMapOf<Int, Long>()
    init {
        event("session_start", JSONObject().put("device", Build.MODEL).put("manufacturer", Build.MANUFACTURER)
            .put("sdk", Build.VERSION.SDK_INT).put("version", context.packageManager.getPackageInfo(context.packageName,0).versionName))
        val types = listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GRAVITY,
            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR,
            Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_PRESSURE, Sensor.TYPE_LIGHT, Sensor.TYPE_PROXIMITY)
        for (type in types) {
            val sensor = manager.getDefaultSensor(type)
            val registered = sensor != null && runCatching { manager.registerListener(this, sensor, 100_000) }.getOrDefault(false)
            event("sensor_inventory", JSONObject().put("type",type).put("available",sensor != null)
                .put("registered",registered).put("name",sensor?.name).put("vendor",sensor?.vendor))
        }
    }
    @Synchronized fun event(type: String, data: JSONObject = JSONObject()) {
        if (closed || failure != null) return
        try {
            val line = JSONObject().put("event",type).put("wall_ms",System.currentTimeMillis())
                .put("elapsed_ns",SystemClock.elapsedRealtimeNanos()).put("data",data).toString()+"\n"
            val raw = line.toByteArray(Charsets.UTF_8)
            check(bytes + raw.size <= 32L * 1024 * 1024) { "Diagnostic size limit reached" }
            output.write(raw); output.flush(); bytes += raw.size
        } catch (e: Exception) { failure = e.message ?: "Diagnostic storage failed" }
    }
    override fun onSensorChanged(e: SensorEvent) {
        if (e.timestamp - (last[e.sensor.type] ?: 0L) < 100_000_000L) return
        last[e.sensor.type] = e.timestamp
        event("sensor",JSONObject().put("type",e.sensor.type).put("sensor_ns",e.timestamp)
            .put("accuracy",e.accuracy).put("values",JSONArray(e.values.map { if (it.isFinite()) it.toDouble() else JSONObject.NULL })))
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        event("sensor_accuracy",JSONObject().put("type",sensor?.type).put("accuracy",accuracy))
    }
    @Synchronized fun close(reason: String) {
        if (closed) return
        manager.unregisterListener(this)
        event("session_end",JSONObject().put("reason",reason).put("failure",failure))
        closed = true
        runCatching { output.close() }
    }
}
