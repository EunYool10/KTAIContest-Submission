package com.example.detect

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class Verdict(val label: String) {
  NORMAL("정상"),
  SUSPECTED("낙상 의심"),
  EMERGENCY("응급상황 가능성 높음");

  companion object {
    fun parse(text: String): Verdict? = when {
      text.contains("응급") -> EMERGENCY
      text.contains("의심") -> SUSPECTED
      text.contains("정상") -> NORMAL
      else -> null
    }
  }
}

data class Analysis(
  val verdict: Verdict,
  val confidence: Double,
  val reason: String,
  /** 판단한 주체: "Gemini" 또는 "기기 안 규칙" */
  val source: String,
)

private fun f1(v: Float) = String.format(Locale.US, "%.1f", v)
private fun g(v: Float) = String.format(Locale.US, "%.1fG", v / GRAVITY)

/** 인터넷이나 API 키 없이도 동작하는 기본 판단 규칙. */
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

/** Gemini 로 충격 데이터를 판단합니다. 실패하거나 시간이 넘으면 null 을 돌려줍니다. */
class GeminiAnalyzer(private val apiKey: String, private val model: String) {
  val isConfigured: Boolean get() = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"

  private val client = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .build()

  suspend fun analyze(e: ShockEvent, warningThreshold: Float, emergencyThreshold: Float): Analysis? {
    if (!isConfigured) return null
    return withTimeoutOrNull(TIMEOUT_MS) {
      try {
        withContext(Dispatchers.IO) { request(e, warningThreshold, emergencyThreshold) }
      } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        Log.w(TAG, "Gemini 분석 실패: ${t.message}")
        null
      }
    }
  }

  private suspend fun request(e: ShockEvent, warning: Float, emergency: Float): Analysis? {
    val body = JSONObject()
      .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
      .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt(e, warning, emergency))))))
      .put(
        "generationConfig", JSONObject()
          .put("responseMimeType", "application/json")
          .put("responseSchema", SCHEMA)
          .put("temperature", 0.1)
      )
    val req = Request.Builder()
      .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
      .header("x-goog-api-key", apiKey)
      .post(body.toString().toRequestBody("application/json".toMediaType()))
      .build()

    val text = client.newCall(req).await().use { res ->
      val raw = res.body?.string().orEmpty()
      if (!res.isSuccessful) {
        Log.w(TAG, "Gemini HTTP ${res.code}: ${raw.take(300)}")
        return null
      }
      JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
        ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
    } ?: return null

    val json = JSONObject(text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
    val verdict = Verdict.parse(json.optString("status")) ?: return null
    val confidence = json.optDouble("confidence", 0.7).let { if (it > 1.0) it / 100.0 else it }.coerceIn(0.0, 1.0)
    val reason = json.optString("reason").trim().ifBlank { "센서 데이터를 바탕으로 판단했어요." }
    return Analysis(verdict, confidence, reason, SOURCE)
  }

  private fun prompt(e: ShockEvent, warning: Float, emergency: Float) = """
    휴대폰 센서로 측정한 충격 데이터입니다. 사람이 넘어져 도움이 필요한 상황인지 판단해 주세요.

    - 최대 가속도: ${f1(e.peakAccel)} m/s² (${g(e.peakAccel)}, 평소 정지 상태는 1.0G)
    - 최대 회전 속도: ${e.peakGyro.toInt()} °/s
    - 충격 직전 자유낙하 시간: ${e.freeFallMs} ms (0.6G 미만이 이어진 시간, 80ms 이상이면 떨어지는 움직임)
    - 충격 전후 자세 변화: ${e.orientationChange.toInt()}°
    - 충격 뒤 2초 동안 움직임(가속도 표준편차): ${String.format(Locale.US, "%.2f", e.stillness)} m/s² (0.8 미만이면 거의 움직이지 않음, 2.0 이상이면 활발히 움직임)
    - 사용자가 정한 기준: 주의 ${f1(warning)} m/s², 긴급 ${f1(emergency)} m/s²
  """.trimIndent()

  companion object {
    const val SOURCE = "Gemini"
    private const val TAG = "GeminiAnalyzer"
    private const val TIMEOUT_MS = 9000L

    private const val SYSTEM = """
너는 휴대폰 낙상 감지 앱의 판단 모듈이다. 센서 수치만 보고 아래 셋 중 하나로 판정한다.
- "응급상황 가능성 높음": 큰 충격 뒤 거의 움직임이 없다. 의식을 잃었거나 크게 다쳤을 수 있다.
- "낙상 의심": 큰 충격과 자세 변화가 있었지만 움직임이 조금 있다.
- "정상": 충격 뒤에도 활발히 움직인다. 휴대폰을 떨어뜨렸다 바로 집었거나 일상적인 충격이다.
휴대폰만 바닥에 떨어진 경우도 움직임이 없을 수 있으니, 애매하면 사람에게 확인하도록 "낙상 의심" 이상으로 판정한다.
reason 은 사용자에게 그대로 보여 주므로 해요체 한국어 한두 문장으로, 수치를 근거로 쉽게 쓴다.
"""

    private val SCHEMA = JSONObject()
      .put("type", "OBJECT")
      .put(
        "properties", JSONObject()
          .put("status", JSONObject().put("type", "STRING").put("enum", JSONArray(listOf("정상", "낙상 의심", "응급상황 가능성 높음"))))
          .put("confidence", JSONObject().put("type", "NUMBER"))
          .put("reason", JSONObject().put("type", "STRING"))
      )
      .put("required", JSONArray(listOf("status", "confidence", "reason")))
  }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
  enqueue(object : Callback {
    override fun onResponse(call: Call, response: Response) = cont.resume(response)
    override fun onFailure(call: Call, e: IOException) {
      if (!cont.isCancelled) cont.resumeWithException(e)
    }
  })
  cont.invokeOnCancellation { runCatching { cancel() } }
}
