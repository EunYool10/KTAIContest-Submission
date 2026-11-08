package com.example.detect

import java.util.Locale

enum class Verdict(val label: String) {
  NORMAL("정상"),
  SUSPECTED("낙상 의심"),
  EMERGENCY("응급상황 가능성 높음"),
}

data class Analysis(
  val verdict: Verdict,
  /** 기록용 대략적인 확신 정도 (화면에는 보여 주지 않습니다) */
  val confidence: Double,
  val reason: String,
  val source: String,
)

private fun f1(v: Float) = String.format(Locale.US, "%.1f", v)
private fun g(v: Float) = String.format(Locale.US, "%.1fG", v / GRAVITY)

/** 인터넷 없이 휴대폰 안에서 센서 수치만으로 판단합니다. */
object LocalRules {
  const val SOURCE = "기기 안 규칙"

  fun analyze(e: ShockEvent, emergencyThreshold: Float): Analysis {
    val severe = e.peakAccel >= emergencyThreshold
    val still = e.stillness < 0.8f
    val littleMotion = e.stillness < 2.0f
    val fallLike = e.freeFallMs >= 80 || e.orientationChange >= 40f
    val clues = buildList {
      add("최대 충격 ${g(e.peakAccel)}")
      if (e.freeFallMs >= 80) add("충격 직전 ${e.freeFallMs}ms 동안 떨어지는 움직임")
      if (e.orientationChange >= 40f) add("자세가 ${e.orientationChange.toInt()}° 바뀜")
      add(if (still) "충격 뒤 거의 움직이지 않음" else "충격 뒤 움직임 ${f1(e.stillness)}")
    }.joinToString(", ")

    return when {
      severe && still -> Analysis(
        Verdict.EMERGENCY, if (fallLike) 0.92 else 0.82,
        "$clues. 큰 충격 뒤 움직임이 없어 넘어져 다쳤을 가능성이 높아요.", SOURCE,
      )
      severe && (littleMotion || fallLike) -> Analysis(
        Verdict.SUSPECTED, 0.68,
        "$clues. 넘어졌을 수 있어 상태를 확인할게요.", SOURCE,
      )
      else -> Analysis(
        Verdict.NORMAL, 0.6,
        "$clues. 충격 뒤에도 계속 움직이고 있어 일상적인 충격으로 보여요.", SOURCE,
      )
    }
  }
}
