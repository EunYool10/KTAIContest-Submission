package com.example.detect

import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sqrt

const val GRAVITY = 9.80665f

/** 충격 하나를 분석한 결과. 값은 모두 센서에서 직접 계산한 것입니다. */
data class ShockEvent(
  /** 벽시계 기준 시각 (ms) */
  val timestamp: Long,
  /** 충격 직후 0.3초 동안의 최대 가속도 (m/s²) */
  val peakAccel: Float,
  /** 충격 전후 최대 회전 속도 (deg/s) */
  val peakGyro: Float,
  /** 충격 직전 1초 안에서 자유낙하(0.6G 미만)가 이어진 가장 긴 시간 (ms) */
  val freeFallMs: Int,
  /** 충격 전 자세와 충격 2초 뒤 자세 사이의 각도 (도) */
  val orientationChange: Float,
  /** 충격 뒤 2초 동안 가속도 크기의 표준편차 (m/s²). 작을수록 움직임이 없습니다. */
  val stillness: Float,
)

/**
 * 가속도/자이로 값을 받아 충격을 찾고, 충격 앞뒤 구간을 모아 [ShockEvent] 로 정리합니다.
 *
 * 1. 가속도 크기가 [triggerThreshold] 이상이면 분석을 시작합니다.
 * 2. 충격 직전 1초 동안 자유낙하가 있었는지 봅니다.
 * 3. 충격 뒤 2초 동안 움직임(표준편차)과 자세 변화를 봅니다.
 *
 * 시간은 센서 이벤트의 타임스탬프(ms)를 씁니다. 그래서 같은 데이터를 넣으면 항상 같은 결과가 나오고,
 * 테스트용 가짜 데이터도 똑같이 처리됩니다. 한 스레드에서만 호출해야 합니다.
 */
class FallDetector(
  @Volatile var triggerThreshold: Float,
  private val wallClock: () -> Long = System::currentTimeMillis,
) {
  private class Sample(val t: Long, val x: Float, val y: Float, val z: Float, val mag: Float)

  private class Capture(val impactAt: Long, val wallTime: Long, val before: List<Sample>, gyro: Float) {
    val after = ArrayList<Sample>(160)
    var peakGyro = gyro
  }

  private val history = ArrayDeque<Sample>()
  private var capture: Capture? = null
  private var cooldownUntil = Long.MIN_VALUE
  private var lastGyro = 0f

  val isCapturing: Boolean get() = capture != null

  fun reset() {
    history.clear()
    capture = null
    cooldownUntil = Long.MIN_VALUE
    lastGyro = 0f
  }

  /** 자이로 값 (rad/s). 회전 속도(deg/s)를 돌려줍니다. */
  fun onGyro(x: Float, y: Float, z: Float): Float {
    val dps = sqrt(x * x + y * y + z * z) * RAD_TO_DEG
    lastGyro = dps
    capture?.let { if (dps > it.peakGyro) it.peakGyro = dps }
    return dps
  }

  /** 가속도 값 (m/s²). 분석이 끝난 충격이 있으면 돌려줍니다. */
  fun onAccel(tMs: Long, x: Float, y: Float, z: Float): ShockEvent? {
    val s = Sample(tMs, x, y, z, sqrt(x * x + y * y + z * z))
    val c = capture
    if (c != null) {
      c.after += s
      if (tMs - c.impactAt >= PEAK_WINDOW_MS + SETTLE_MS + POST_WINDOW_MS) {
        capture = null
        cooldownUntil = tMs + COOLDOWN_MS
        history.clear()
        return finish(c)
      }
      return null
    }

    history.addLast(s)
    while (history.isNotEmpty() && tMs - history.first().t > HISTORY_MS) history.removeFirst()

    if (s.mag >= triggerThreshold && tMs >= cooldownUntil) {
      capture = Capture(tMs, wallClock(), history.toList(), lastGyro).also { it.after += s }
    }
    return null
  }

  private fun finish(c: Capture): ShockEvent {
    val peakEnd = c.impactAt + PEAK_WINDOW_MS
    val postStart = peakEnd + SETTLE_MS
    var peak = 0f
    for (s in c.after) if (s.t <= peakEnd) peak = max(peak, s.mag)
    for (s in c.before) if (s.t >= c.impactAt - 100) peak = max(peak, s.mag)

    val post = c.after.filter { it.t >= postStart }
    val stillness = stdDev(post.map { it.mag })

    // 자유낙하: 충격 직전 1초 안에서 0.6G 미만이 이어진 가장 긴 구간
    var longest = 0L
    var runStart = -1L
    var prevT = -1L
    for (s in c.before) {
      if (s.t < c.impactAt - FREE_FALL_LOOKBACK_MS) continue
      if (s.mag < FREE_FALL_LIMIT) {
        if (runStart < 0) runStart = prevT.takeIf { it >= 0 } ?: s.t
        longest = max(longest, s.t - runStart)
      } else {
        runStart = -1L
      }
      prevT = s.t
    }

    // 자세 변화: 충격 0.5초 이전의 평균 중력 방향 vs 마지막 0.5초 평균 방향
    val calmBefore = c.before.filter { it.t <= c.impactAt - 500 }.ifEmpty { c.before.take(5) }
    val lastPart = post.filter { it.t >= c.after.last().t - 500 }.ifEmpty { post }
    val change = angleBetween(meanVector(calmBefore), meanVector(lastPart))

    return ShockEvent(
      timestamp = c.wallTime,
      peakAccel = peak,
      peakGyro = c.peakGyro,
      freeFallMs = longest.toInt(),
      orientationChange = change,
      stillness = stillness,
    )
  }

  private fun meanVector(samples: List<Sample>): FloatArray {
    if (samples.isEmpty()) return floatArrayOf(0f, 0f, 0f)
    var x = 0f; var y = 0f; var z = 0f
    for (s in samples) { x += s.x; y += s.y; z += s.z }
    val n = samples.size
    return floatArrayOf(x / n, y / n, z / n)
  }

  private fun angleBetween(a: FloatArray, b: FloatArray): Float {
    val la = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
    val lb = sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2])
    if (la < 1f || lb < 1f) return 0f
    val cos = ((a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (la * lb)).coerceIn(-1f, 1f)
    return acos(cos) * RAD_TO_DEG
  }

  private fun stdDev(values: List<Float>): Float {
    if (values.size < 2) return 0f
    val mean = values.average()
    val v = values.sumOf { (it - mean) * (it - mean) } / values.size
    return sqrt(v).toFloat()
  }

  companion object {
    const val PEAK_WINDOW_MS = 300L
    const val SETTLE_MS = 200L
    const val POST_WINDOW_MS = 2000L
    const val HISTORY_MS = 1600L
    const val FREE_FALL_LOOKBACK_MS = 1000L
    const val COOLDOWN_MS = 1500L
    val FREE_FALL_LIMIT = 0.6f * GRAVITY
    private const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()

    /** 충격을 찾고 나서 결과가 나오기까지 걸리는 시간 */
    const val ANALYSIS_DELAY_MS = PEAK_WINDOW_MS + SETTLE_MS + POST_WINDOW_MS
  }
}
