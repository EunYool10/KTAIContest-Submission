package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 도움 요청 때 전화로 연결할 곳. 저장 값은 이전 버전과 같은 문자열을 씁니다. */
enum class CallTarget(val key: String, val title: String) {
  FIRE("119", "119 (소방·구급)"),
  POLICE("police", "112 (경찰)"),
  GUARDIAN("guardian", "보호자"),
  CUSTOM("custom", "추가 연락처");

  companion object {
    fun of(key: String) = entries.firstOrNull { it.key == key } ?: FIRE
  }
}

/** 감지 민감도. 값은 가속도 크기(m/s²) 기준입니다. 1G ≈ 9.8 m/s². */
enum class Sensitivity(val key: String, val title: String, val warning: Float, val emergency: Float) {
  LOW("low", "둔감", 20f, 32f),
  MEDIUM("medium", "보통", 16f, 25f),
  HIGH("high", "민감", 13f, 20f),
  CUSTOM("custom", "직접 설정", 16f, 25f);

  companion object {
    val presets = listOf(LOW, MEDIUM, HIGH)
    fun of(key: String) = entries.firstOrNull { it.key == key } ?: MEDIUM
  }
}

data class Settings(
  val setupComplete: Boolean = false,
  val userName: String = "",
  val guardianName: String = "",
  val guardianPhone: String = "",
  val extraContact: String = "",
  val callTarget: CallTarget = CallTarget.FIRE,
  val sensitivity: Sensitivity = Sensitivity.MEDIUM,
  val warningThreshold: Float = Sensitivity.MEDIUM.warning,
  val emergencyThreshold: Float = Sensitivity.MEDIUM.emergency,
  val countdownSeconds: Int = 30,
  val soundEnabled: Boolean = true,
  val vibrationEnabled: Boolean = true,
  val voiceCancelEnabled: Boolean = true,
  /** 사용자가 감지를 켜 둔 상태인지. 재부팅 뒤 자동으로 다시 켤 때 씁니다. */
  val monitoringEnabled: Boolean = false,
  /** 앱에서 직접 입력한 Gemini API 키. 비어 있으면 설치 파일에 들어 있는 키(있다면)를 씁니다. */
  val geminiKey: String = "",
) {
  override fun toString(): String = "Settings(setup=$setupComplete, monitoring=$monitoringEnabled, key=${if (geminiKey.isBlank()) "없음" else "있음"})"

  /** 자동 문자를 받을 번호 목록 (보호자 + 추가 연락처). */
  val smsRecipients: List<Pair<String, String>>
    get() = buildList {
      if (guardianPhone.isNotBlank()) add(guardianName.ifBlank { "보호자" } to guardianPhone)
      if (extraContact.isNotBlank() && Phone.digits(extraContact) != Phone.digits(guardianPhone)) {
        add("추가 연락처" to extraContact)
      }
    }

  /** 문장에 넣을 받는 사람: "김철수님께" 또는 "김철수님과 추가 연락처로" */
  val smsTargetPhrase: String
    get() {
      val guardian = guardianName.ifBlank { "보호자" } + "님"
      return if (smsRecipients.size > 1) "${guardian}과 추가 연락처로" else "${guardian}께"
    }

  /** 도움 요청 때 전화할 번호와 이름. */
  val callNumber: String
    get() = when (callTarget) {
      CallTarget.FIRE -> "119"
      CallTarget.POLICE -> "112"
      CallTarget.GUARDIAN -> guardianPhone
      CallTarget.CUSTOM -> extraContact.ifBlank { guardianPhone }
    }

  val callLabel: String
    get() = when (callTarget) {
      CallTarget.FIRE -> "119"
      CallTarget.POLICE -> "112"
      CallTarget.GUARDIAN -> guardianName.ifBlank { "보호자" }
      CallTarget.CUSTOM -> if (extraContact.isBlank()) guardianName.ifBlank { "보호자" } else "추가 연락처"
    }
}

object Phone {
  fun digits(raw: String): String = raw.filter { it.isDigit() || it == '+' }

  /** 119/112 같은 짧은 번호 또는 일반 전화번호(9자리 이상)만 허용합니다. */
  fun isValid(raw: String): Boolean {
    val d = digits(raw).removePrefix("+")
    return d.length == 3 || d.length in 9..15
  }

  /** 01012345678 → 010-1234-5678 처럼 읽기 좋게 바꿉니다. */
  fun pretty(raw: String): String {
    val d = digits(raw)
    return when {
      d.length == 11 && d.startsWith("01") -> "${d.substring(0, 3)}-${d.substring(3, 7)}-${d.substring(7)}"
      d.length == 10 && d.startsWith("02") -> "${d.substring(0, 2)}-${d.substring(2, 6)}-${d.substring(6)}"
      d.length == 10 -> "${d.substring(0, 3)}-${d.substring(3, 6)}-${d.substring(6)}"
      d.length == 9 && d.startsWith("02") -> "${d.substring(0, 2)}-${d.substring(2, 5)}-${d.substring(5)}"
      else -> raw.trim()
    }
  }
}

/** SharedPreferences 에 저장하고 StateFlow 로 바로 알려 주는 설정 저장소. 키는 이전 버전과 호환됩니다. */
class SettingsStore(context: Context) {
  private val prefs = context.getSharedPreferences("app_settings_pref", Context.MODE_PRIVATE)
  /** API 키 전용 파일. 백업에서 빠지도록 backup_rules / data_extraction_rules 에 적어 두었습니다. */
  private val secrets = context.getSharedPreferences("secret_pref", Context.MODE_PRIVATE)
  private val _state = MutableStateFlow(load())
  val state: StateFlow<Settings> = _state.asStateFlow()
  val value: Settings get() = _state.value

  fun update(block: (Settings) -> Settings) {
    synchronized(this) {
      val next = block(_state.value)
      save(next)
      _state.value = next
    }
  }

  /** 처음부터 다시 설정: 연락처·설정을 지웁니다. 직접 넣은 API 키는 남겨 둡니다. */
  fun reset() {
    synchronized(this) {
      prefs.edit().clear().apply()
      _state.value = Settings(geminiKey = _state.value.geminiKey)
    }
  }

  private fun load(): Settings {
    // 이전 버전이 쓰던 쓸모없는 키 정리
    if (prefs.contains("impact_events_raw") || prefs.contains("phone_preset")) {
      prefs.edit().remove("impact_events_raw").remove("phone_preset").remove("sensitivity_threshold").apply()
    }
    val d = Settings()
    val sensitivity = Sensitivity.of(prefs.getString(K_SENSITIVITY, d.sensitivity.key) ?: d.sensitivity.key)
    var warning = prefs.getFloat(K_WARNING, d.warningThreshold)
    var emergency = prefs.getFloat(K_EMERGENCY, d.emergencyThreshold)
    if (warning < 10f || warning > 60f) warning = d.warningThreshold
    if (emergency < 12f || emergency > 70f) emergency = d.emergencyThreshold
    if (warning >= emergency) warning = (emergency - 4f).coerceAtLeast(10f)
    return Settings(
      setupComplete = prefs.getBoolean(K_SETUP, false),
      userName = prefs.getString(K_USER, "") ?: "",
      guardianName = prefs.getString(K_GUARDIAN, "") ?: "",
      guardianPhone = prefs.getString(K_GUARDIAN_PHONE, "") ?: "",
      extraContact = prefs.getString(K_EXTRA, "") ?: "",
      callTarget = CallTarget.of(prefs.getString(K_TARGET, "119") ?: "119"),
      sensitivity = sensitivity,
      warningThreshold = warning,
      emergencyThreshold = emergency,
      countdownSeconds = prefs.getInt(K_COUNTDOWN, d.countdownSeconds).coerceIn(10, 120),
      soundEnabled = prefs.getBoolean(K_SOUND, true),
      vibrationEnabled = prefs.getBoolean(K_VIBRATION, true),
      voiceCancelEnabled = prefs.getBoolean(K_VOICE, true),
      monitoringEnabled = prefs.getBoolean(K_MONITORING, prefs.getBoolean(K_SETUP, false)),
      geminiKey = secrets.getString(K_GEMINI_KEY, "") ?: "",
    )
  }

  private fun save(s: Settings) {
    prefs.edit()
      .putBoolean(K_SETUP, s.setupComplete)
      .putString(K_USER, s.userName)
      .putString(K_GUARDIAN, s.guardianName)
      .putString(K_GUARDIAN_PHONE, s.guardianPhone)
      .putString(K_EXTRA, s.extraContact)
      .putString(K_TARGET, s.callTarget.key)
      .putString(K_SENSITIVITY, s.sensitivity.key)
      .putFloat(K_WARNING, s.warningThreshold)
      .putFloat(K_EMERGENCY, s.emergencyThreshold)
      .putInt(K_COUNTDOWN, s.countdownSeconds)
      .putBoolean(K_SOUND, s.soundEnabled)
      .putBoolean(K_VIBRATION, s.vibrationEnabled)
      .putBoolean(K_VOICE, s.voiceCancelEnabled)
      .putBoolean(K_MONITORING, s.monitoringEnabled)
      .apply()
    if (secrets.getString(K_GEMINI_KEY, "") != s.geminiKey) {
      secrets.edit().putString(K_GEMINI_KEY, s.geminiKey).apply()
    }
  }

  private companion object {
    const val K_SETUP = "setup_complete"
    const val K_USER = "user_name"
    const val K_GUARDIAN = "guardian_name"
    const val K_GUARDIAN_PHONE = "guardian_phone"
    const val K_EXTRA = "emergency_contact"
    const val K_TARGET = "emergency_target"
    const val K_SENSITIVITY = "sensitivity"
    const val K_WARNING = "warning_threshold"
    const val K_EMERGENCY = "emergency_threshold"
    const val K_COUNTDOWN = "countdown_seconds"
    const val K_SOUND = "sound_enabled"
    const val K_VIBRATION = "vibration_enabled"
    const val K_VOICE = "voice_cancel_enabled"
    const val K_MONITORING = "monitoring_enabled"
    const val K_GEMINI_KEY = "gemini_api_key"
  }
}
