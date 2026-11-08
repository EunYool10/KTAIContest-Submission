package com.example

import com.example.data.Phone
import com.example.data.Sensitivity
import com.example.detect.FallDetector
import com.example.detect.FallSimulation
import com.example.detect.LocalRules
import com.example.detect.ShockEvent
import com.example.detect.SimSample
import com.example.detect.Verdict
import com.example.ui.Calibration
import com.example.ui.voiceIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class FallDetectorTest {
  private val medium = Sensitivity.MEDIUM

  private fun run(samples: List<SimSample>, threshold: Float = medium.warning): List<ShockEvent> {
    val d = FallDetector(threshold) { 0L }
    val out = mutableListOf<ShockEvent>()
    for (s in samples) {
      d.onGyro(s.gx, s.gy, s.gz)
      d.onAccel(s.tMs, s.ax, s.ay, s.az)?.let { out += it }
    }
    return out
  }

  @Test
  fun `넘어짐 시뮬레이션은 자유낙하, 자세 변화, 움직임 없음으로 잡히고 긴급으로 판단된다`() {
    val events = run(FallSimulation.fall(medium.emergency + 8f, seed = 7))
    assertEquals(1, events.size)
    val e = events.single()
    assertTrue("peak ${e.peakAccel}", e.peakAccel >= medium.emergency)
    assertTrue("freeFall ${e.freeFallMs}", e.freeFallMs >= 250)
    assertTrue("orientation ${e.orientationChange}", e.orientationChange >= 60f)
    assertTrue("stillness ${e.stillness}", e.stillness < 0.2f)
    assertEquals(Verdict.EMERGENCY, LocalRules.analyze(e, medium.emergency).verdict)
  }

  @Test
  fun `보통 민감도에서 걷기만 해서는 분석이 시작되지 않는다`() {
    assertTrue(run(FallSimulation.walking(20_000)).isEmpty())
  }

  @Test
  fun `큰 충격 뒤에도 계속 움직이면 정상으로 판단한다`() {
    val samples = buildList {
      var t = 0L
      while (t < 1000) { add(SimSample(t, 0f, 9.8f, 0.5f, 0f, 0f, 0f)); t += 20 }
      repeat(3) { add(SimSample(t, 20f, 20f, 10f, 3f, 0f, 0f)); t += 20 }
      // 휴대폰을 다시 집어 들고 흔들며 걸어감
      while (t < 4000) {
        val p = t / 1000.0 * 2 * Math.PI * 2
        add(SimSample(t, (sin(p) * 4).toFloat(), (9.8 + sin(p * 2) * 5).toFloat(), 1f, 1f, 1f, 1f)); t += 20
      }
    }
    val e = run(samples).single()
    assertTrue("stillness ${e.stillness}", e.stillness > 2f)
    assertEquals(Verdict.NORMAL, LocalRules.analyze(e, medium.emergency).verdict)
  }

  @Test
  fun `한 번 분석한 뒤에는 잠깐 쉬어서 같은 충격을 두 번 세지 않는다`() {
    val one = FallSimulation.fall(35f, seed = 1)
    val two = one.map { it.copy(tMs = it.tMs + one.last().tMs + 20) }
    assertEquals(2, run(one + two).size)
  }

  @Test
  fun `맞춤 보정 추천값은 일상 움직임과 충격 사이에 놓인다`() {
    val rec = Calibration(dailyPeak = 15f, impactPeak = 45f).recommendation
    assertNotNull(rec)
    val (warning, emergency) = rec!!
    assertTrue(emergency in 15f..40f)
    assertTrue(warning < emergency && warning > 15f)
    assertNull(Calibration(dailyPeak = 15f, impactPeak = 17f).recommendation)
  }

  @Test
  fun `음성 명령 해석`() {
    assertEquals(true, voiceIntent(listOf("괜찮아")))
    assertEquals(true, voiceIntent(listOf("취소 해 줘")))
    assertEquals(false, voiceIntent(listOf("안 괜찮아")))
    assertEquals(false, voiceIntent(listOf("도와주세요")))
    assertEquals(true, voiceIntent(listOf("안 아파요")))
    assertNull(voiceIntent(listOf("음")))
  }

  @Test
  fun `전화번호 검사와 표시`() {
    assertTrue(Phone.isValid("010-1234-5678"))
    assertTrue(Phone.isValid("119"))
    assertTrue(!Phone.isValid("1234"))
    assertEquals("010-1234-5678", Phone.pretty("01012345678"))
    assertEquals("02-123-4567", Phone.pretty("021234567"))
  }
}
