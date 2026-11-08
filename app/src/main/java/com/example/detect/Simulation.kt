package com.example.detect

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** 센서 한 번 측정값. 가속도는 m/s², 자이로는 rad/s. */
data class SimSample(val tMs: Long, val ax: Float, val ay: Float, val az: Float, val gx: Float, val gy: Float, val gz: Float)

/**
 * 경보 테스트용 가짜 센서 데이터: 걷기 → 미끄러져 떨어짐 → 바닥 충격 → 움직임 없음.
 * 실제 감지 로직에 그대로 넣어서, 진짜 넘어졌을 때와 같은 과정을 거치게 합니다.
 */
object FallSimulation {
  const val INTERVAL_MS = 20L

  fun fall(impactPeak: Float, seed: Long = System.currentTimeMillis()): List<SimSample> {
    val r = Random(seed)
    fun n(a: Double) = r.nextDouble(-a, a).toFloat()
    val out = ArrayList<SimSample>()
    var t = 0L

    // 1. 걷기 1.2초: 휴대폰을 세워 들고 걷는 정도의 흔들림 (약 0.8~1.3G)
    while (t < 1200) {
      val p = t / 1000.0 * 2 * PI * 1.8
      out += SimSample(t, (sin(p) * 1.2).toFloat() + n(0.3), (9.6 + sin(p * 2) * 2.0).toFloat() + n(0.3), (1.2 + cos(p) * 0.8).toFloat() + n(0.3),
        n(0.3), n(0.3), n(0.3))
      t += INTERVAL_MS
    }
    // 2. 떨어지는 0.32초: 가속도가 거의 0 에 가까워지고 몸이 돌아갑니다.
    val fallEnd = t + 320
    while (t < fallEnd) {
      out += SimSample(t, n(0.8), 1.5f + n(0.8), n(0.8), 3.5f + n(0.5), -2.0f + n(0.5), 1.0f + n(0.5))
      t += INTERVAL_MS
    }
    // 3. 바닥 충격 0.1초
    val impactEnd = t + 100
    var i = 0
    while (t < impactEnd) {
      val k = if (i == 1) 1f else 0.7f
      val c = impactPeak * k / sqrt(3f)
      out += SimSample(t, c + n(1.0), c + n(1.0), c + n(1.0), 6f + n(1.0), -5f + n(1.0), 4f + n(1.0))
      t += INTERVAL_MS; i++
    }
    // 4. 옆으로 누운 채 움직임 없음 2.8초
    val end = t + 2800
    while (t < end) {
      out += SimSample(t, 9.6f + n(0.03), 1.4f + n(0.03), 0.6f + n(0.03), n(0.01), n(0.01), n(0.01))
      t += INTERVAL_MS
    }
    return out
  }

  /** 맞춤 보정 단위 테스트용: 그냥 걷기만 하는 데이터. */
  fun walking(durationMs: Long, seed: Long = 1): List<SimSample> {
    val r = Random(seed)
    val out = ArrayList<SimSample>()
    var t = 0L
    while (t < durationMs) {
      val p = t / 1000.0 * 2 * PI * 1.8
      out += SimSample(t, (sin(p) * 1.5).toFloat() + r.nextFloat() * 0.4f, (9.6 + sin(p * 2) * 3.5).toFloat(), (1.2 + cos(p)).toFloat(), 0.2f, 0.2f, 0.2f)
      t += INTERVAL_MS
    }
    return out
  }
}
