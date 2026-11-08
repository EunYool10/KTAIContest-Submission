package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.App
import com.example.data.FallLog
import com.example.data.Settings
import com.example.data.Sensitivity
import com.example.engine.UpdateCheckResult
import com.example.engine.UpdateInfo
import com.example.service.MonitorService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

enum class CalStep { IDLE, INTRO, DAILY, IMPACT_READY, IMPACT, RESULT }

data class Calibration(
  val step: CalStep = CalStep.IDLE,
  val secondsLeft: Int = 0,
  val dailyPeak: Float = 0f,
  val impactPeak: Float = 0f,
) {
  /** 추천 기준 (주의, 긴급). 충격이 충분하지 않으면 null. */
  val recommendation: Pair<Float, Float>?
    get() {
      if (dailyPeak <= 0f || impactPeak < dailyPeak + 4f) return null
      val emergency = (dailyPeak + (impactPeak - dailyPeak) * 0.45f).coerceIn(15f, 40f).roundTo(0.5f)
      val warning = max(dailyPeak + 2.5f, emergency * 0.65f).coerceAtMost(emergency - 3f).coerceAtLeast(11f).roundTo(0.5f)
      return warning to emergency
    }

  private fun Float.roundTo(step: Float) = (this / step).roundToInt() * step
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
  private val app = application as App
  val engine = app.engine
  private val store = app.settings

  val settings: StateFlow<Settings> = store.state
  val status = engine.status
  val live = engine.live
  val alert = engine.alert
  val notice = engine.notice
  val testing = engine.testing
  val place = engine.location.place
  val logs: StateFlow<List<FallLog>> = engine.logs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val hasAccelerometer: Boolean get() = engine.hasAccelerometer

  // ───────── 업데이트 ─────────
  val update: StateFlow<UpdateInfo?> = app.updates.available
  val updateDismissed: StateFlow<Int> = app.updates.dismissedCode
  val updateChecking: StateFlow<Boolean> = app.updates.checking
  private val _updateResult = MutableStateFlow<UpdateCheckResult?>(null)
  /** 설정에서 직접 확인했을 때의 결과 */
  val updateResult: StateFlow<UpdateCheckResult?> = _updateResult.asStateFlow()

  fun checkUpdate(force: Boolean) {
    viewModelScope.launch {
      val r = app.updates.check(force)
      if (force) _updateResult.value = r
    }
  }

  fun dismissUpdate(code: Int) = app.updates.dismiss(code)

  // ───────── 처음 설정 직후 문자 권한 안내 (안드로이드 15+) ─────────
  private val _smsGuide = MutableStateFlow(false)
  val smsGuide: StateFlow<Boolean> = _smsGuide.asStateFlow()
  fun showSmsGuide() { _smsGuide.value = true }
  fun closeSmsGuide() { _smsGuide.value = false }

  fun completeSetup(name: String, guardianName: String, guardianPhone: String, extra: String, target: com.example.data.CallTarget) {
    store.update {
      it.copy(
        userName = name.trim(), guardianName = guardianName.trim(), guardianPhone = guardianPhone.trim(),
        extraContact = extra.trim(), callTarget = target,
      )
    }
  }

  fun finishOnboarding(startMonitoring: Boolean) {
    store.update { it.copy(setupComplete = true) }
    setMonitoring(startMonitoring)
  }

  fun setMonitoring(on: Boolean) {
    store.update { it.copy(monitoringEnabled = on) }
    if (on) MonitorService.start(app) else MonitorService.stop(app)
  }

  /** 앱을 열었을 때, 켜 둔 감지가 꺼져 있으면 다시 켭니다 (강제 종료 뒤 등). */
  fun ensureMonitoring() {
    val s = settings.value
    if (s.setupComplete && s.monitoringEnabled) MonitorService.start(app)
    engine.location.refresh()
  }

  fun update(block: (Settings) -> Settings) = store.update(block)

  fun setSensitivity(level: Sensitivity) = store.update {
    it.copy(sensitivity = level, warningThreshold = level.warning, emergencyThreshold = level.emergency)
  }

  fun setThresholds(warning: Float, emergency: Float) = store.update {
    val e = emergency.coerceIn(12f, 60f)
    val w = warning.coerceIn(10f, e - 2f)
    it.copy(sensitivity = Sensitivity.CUSTOM, warningThreshold = w, emergencyThreshold = e)
  }

  fun resetAll() {
    MonitorService.stop(app)
    store.reset()
  }

  fun startTest() = engine.startTest()
  fun imOkay() = engine.imOkay()
  fun requestHelp() = engine.requestHelp()
  fun closeSent() = engine.closeSent()
  fun dismissNotice() = engine.dismissNotice()
  fun deleteLog(id: Int) = engine.deleteLog(id)
  fun clearLogs() = engine.clearLogs()

  // ───────── 맞춤 보정 ─────────

  private val _calibration = MutableStateFlow(Calibration())
  val calibration: StateFlow<Calibration> = _calibration.asStateFlow()
  private var calJob: Job? = null

  fun startCalibration() {
    calJob?.cancel()
    engine.setSuppressed(true)
    engine.acquireSensors(CAL)
    _calibration.value = Calibration(step = CalStep.INTRO)
  }

  fun calibrationNext() {
    when (_calibration.value.step) {
      CalStep.INTRO -> measure(CalStep.DAILY, 10) { c, peak -> c.copy(dailyPeak = max(c.dailyPeak, peak)) }
      CalStep.IMPACT_READY -> measure(CalStep.IMPACT, 6) { c, peak -> c.copy(impactPeak = max(c.impactPeak, peak)) }
      CalStep.RESULT -> {
        _calibration.value.recommendation?.let { (w, e) -> setThresholds(w, e) }
        endCalibration()
      }
      else -> Unit
    }
  }

  fun retryImpact() {
    _calibration.update { it.copy(step = CalStep.IMPACT_READY, impactPeak = 0f) }
  }

  fun endCalibration() {
    calJob?.cancel()
    calJob = null
    engine.releaseSensors(CAL)
    engine.setSuppressed(false)
    _calibration.value = Calibration()
  }

  private fun measure(step: CalStep, seconds: Int, record: (Calibration, Float) -> Calibration) {
    calJob?.cancel()
    _calibration.update { it.copy(step = step, secondsLeft = seconds) }
    calJob = viewModelScope.launch {
      val collector = launch {
        engine.live.collect { l -> l.history.lastOrNull()?.let { p -> _calibration.update { record(it, p) } } }
      }
      for (left in seconds downTo 1) {
        _calibration.update { it.copy(secondsLeft = left) }
        delay(1000)
      }
      collector.cancel()
      _calibration.update {
        it.copy(step = if (step == CalStep.DAILY) CalStep.IMPACT_READY else CalStep.RESULT, secondsLeft = 0)
      }
    }
  }

  override fun onCleared() {
    if (_calibration.value.step != CalStep.IDLE) endCalibration()
    super.onCleared()
  }

  private companion object {
    const val CAL = "calibration"
  }
}
