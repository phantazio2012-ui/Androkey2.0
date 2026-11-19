package com.cgsi.clavier

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface

/**
 * "Souris aérienne" : les rotations du téléphone (gyroscope) déplacent le curseur.
 * Tourner l'appareil à droite/gauche = curseur horizontal ; l'incliner en avant/arrière = curseur vertical.
 */
class GyroMouse(
    private val activity: Activity,
    private val send: (dx: Int, dy: Int) -> Unit
) : SensorEventListener {

    private val sm = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyro: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    @Volatile var sensitivity = 1.6
    @Volatile var invertX = false
    @Volatile var invertY = false

    private var lastTs = 0L
    private var remX = 0.0
    private var remY = 0.0

    fun start(): Boolean {
        val g = gyro ?: return false
        lastTs = 0L; remX = 0.0; remY = 0.0
        return sm.registerListener(this, g, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() = sm.unregisterListener(this)

    @Suppress("DEPRECATION")
    override fun onSensorChanged(e: SensorEvent) {
        if (lastTs == 0L) { lastTs = e.timestamp; return }
        val dt = (e.timestamp - lastTs) / 1e9
        lastTs = e.timestamp
        val gx = e.values[0].toDouble()
        val gy = e.values[1].toDouble()

        // Vitesses angulaires ramenées aux axes de l'écran selon l'orientation
        val (sx, sy) = when (activity.windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> Pair(gy, -gx)
            Surface.ROTATION_180 -> Pair(-gx, -gy)
            Surface.ROTATION_270 -> Pair(-gy, gx)
            else -> Pair(gx, gy)
        }
        // Zone morte pour éviter la dérive quand l'appareil est immobile
        val wx = if (Math.abs(sx) < 0.04) 0.0 else sx
        val wy = if (Math.abs(sy) < 0.04) 0.0 else sy

        val ppr = 700.0 * sensitivity            // pixels par radian
        remX += (if (invertX) wy else -wy) * dt * ppr
        remY += (if (invertY) -wx else wx) * dt * ppr
        val ix = remX.toInt(); val iy = remY.toInt()
        remX -= ix; remY -= iy
        if (ix != 0 || iy != 0) send(ix, iy)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
