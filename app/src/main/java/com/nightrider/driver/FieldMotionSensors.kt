package com.nightrider.driver

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock

/**
 * Field-Test-only foreground sensor adapter. The physical phone must be
 * securely mounted. Never starts automatically in live ride navigation.
 */
class FieldMotionSensors(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val gyroscopeAvailable = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
    val accelerometerAvailable = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    val gravityAvailable = manager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null
    val rotationVectorAvailable = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
    val fusion = FieldMotionFusion()
    var running: Boolean = false
        private set

    val availability: String get() = when {
        !gyroscopeAvailable -> "Gyroscope unavailable"
        !gravityAvailable && !accelerometerAvailable -> "Gravity/accelerometer unavailable"
        else -> "Gyro + " + (if (gravityAvailable) "gravity" else "accelerometer gravity") +
            (if (rotationVectorAvailable) " + compass reference" else "")
    }

    fun start(): Boolean {
        stop()
        val gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: return false
        val gravity = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (gravity == null && accel == null) return false

        val gyroOk = manager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
        val gravityOk = if (gravity != null)
            manager.registerListener(this, gravity, SensorManager.SENSOR_DELAY_GAME) else false
        val accelOk = if (accel != null)
            manager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME) else false
        if (!gyroOk || (!gravityOk && !accelOk)) {
            stop()
            return false
        }
        manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            // Reference-only: never treated as uncalibrated vehicle course.
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        running = true
        return true
    }

    fun stop() {
        manager.unregisterListener(this)
        running = false
        fusion.reset()
    }

    fun gpsCourse(bearingDeg: Double?, speedMps: Double?) {
        if (running) fusion.gpsCourse(bearingDeg, speedMps, SystemClock.elapsedRealtimeNanos())
    }

    fun heading(): Double? = if (running)
        fusion.headingAt(SystemClock.elapsedRealtimeNanos()) else null

    fun snapshot(): FieldMotionSnapshot? = if (running)
        fusion.snapshot(SystemClock.elapsedRealtimeNanos()) else null

    override fun onSensorChanged(event: SensorEvent) {
        if (!running) return
        val v = event.values
        if (v.size < 3) return
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> fusion.gravity(
                v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
            Sensor.TYPE_ACCELEROMETER -> fusion.accelerometer(
                v[0].toDouble(), v[1].toDouble(), v[2].toDouble(), event.timestamp)
            Sensor.TYPE_GYROSCOPE -> fusion.gyroscope(
                v[0].toDouble(), v[1].toDouble(), v[2].toDouble(), event.timestamp)
            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotation = FloatArray(9)
                val angles = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(rotation, v)
                SensorManager.getOrientation(rotation, angles)
                fusion.phoneAzimuth(Math.toDegrees(angles[0].toDouble()))
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
