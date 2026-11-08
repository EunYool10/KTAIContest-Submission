package com.example.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread

/** 가속도·자이로 센서를 별도 스레드에서 50Hz 로 읽습니다. */
class SensorFeed(
  context: Context,
  private val onAccel: (tMs: Long, x: Float, y: Float, z: Float) -> Unit,
  private val onGyro: (x: Float, y: Float, z: Float) -> Unit,
) : SensorEventListener {
  private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
  private val accel: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
  private val gyro: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
  private var thread: HandlerThread? = null

  val hasAccelerometer: Boolean get() = accel != null
  val hasGyroscope: Boolean get() = gyro != null
  val running: Boolean get() = thread != null

  @Synchronized
  fun start(): Boolean {
    val m = manager ?: return false
    val a = accel ?: return false
    if (thread != null) return true
    val t = HandlerThread("guard-sensors").also { it.start() }
    val handler = Handler(t.looper)
    m.registerListener(this, a, SensorManager.SENSOR_DELAY_GAME, handler)
    gyro?.let { m.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, handler) }
    thread = t
    return true
  }

  @Synchronized
  fun stop() {
    manager?.unregisterListener(this)
    thread?.quitSafely()
    thread = null
  }

  override fun onSensorChanged(event: SensorEvent) {
    val v = event.values
    when (event.sensor.type) {
      Sensor.TYPE_ACCELEROMETER -> onAccel(event.timestamp / 1_000_000L, v[0], v[1], v[2])
      Sensor.TYPE_GYROSCOPE -> onGyro(v[0], v[1], v[2])
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
