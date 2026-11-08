package com.example.engine

import android.content.Context
import android.os.SystemClock
import com.example.data.FallLog
import com.example.data.FallLogDao
import com.example.data.LogKind
import com.example.data.SettingsStore
import com.example.detect.Analysis
import com.example.detect.FallDetector
import com.example.detect.FallSimulation
import com.example.detect.GRAVITY
import com.example.detect.LocalRules
import com.example.detect.ShockEvent
import com.example.detect.Verdict
import com.example.service.Notifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class MonitorStatus { OFF, ON, NO_SENSOR }

/** 화면에 보여 줄 실시간 움직임. [history] 는 0.1초마다 최대 가속도(m/s²), 최근 10초. */
data class Live(
  val history: List<Float> = emptyList(),
  val magnitude: Float = GRAVITY,
  val x: Float = 0f,
  val y: Float = 0f,
  val z: Float = 0f,
  val tilt: Float = 0f,
  val rotation: Float = 0f,
)

/** 충격이 약하거나 AI 가 괜찮다고 본 경우의 안내. */
data class Notice(val title: String, val message: String, val time: Long = System.currentTimeMillis())

data class SentReport(
  val byUser: Boolean,
  val test: Boolean,
  val message: String,
  val recipients: List<Recipient>,
  val smsAvailable: Boolean,
  val callNumber: String,
  val callLabel: String,
  val place: Place?,
  val sendId: Long,
  val time: Long = System.currentTimeMillis(),
)

sealed interface AlertState {
  val test: Boolean

  /** "괜찮으세요?" 확인 카운트다운 */
  data class Countdown(
    val event: ShockEvent?,
    val analysis: Analysis,
    val endsAt: Long,
    val totalSeconds: Int,
    override val test: Boolean,
  ) : AlertState

  /** 위치를 확인하고 문자를 보내는 중 */
  data class Sending(val byUser: Boolean, override val test: Boolean) : AlertState

  data class Sent(val report: SentReport) : AlertState {
    override val test: Boolean get() = report.test
  }
}

/**
 * 감지 → 판단 → 확인 → 도움 요청까지 전체 흐름을 맡습니다.
 * 앱 전체에서 하나만 만들고, 화면은 이 상태를 구독하기만 합니다.
 */
class GuardEngine(
  private val context: Context,
  val settings: SettingsStore,
  private val dao: FallLogDao,
  private val scope: CoroutineScope,
) {
  val location = LocationTracker(context, scope)
  private val alarm = Alarm(context)
  private val sms = SmsSender(context)

  private val lock = Any()
  private val detector = FallDetector(settings.value.let { min(it.warningThreshold, it.emergencyThreshold) })
  private val feed = SensorFeed(context, ::onSensorAccel, ::onSensorGyro)
  private val sensorUsers = mutableSetOf<String>()

  private val _status = MutableStateFlow(MonitorStatus.OFF)
  val status: StateFlow<MonitorStatus> = _status.asStateFlow()

  private val _live = MutableStateFlow(Live())
  val live: StateFlow<Live> = _live.asStateFlow()

  private val _alert = MutableStateFlow<AlertState?>(null)
  val alert: StateFlow<AlertState?> = _alert.asStateFlow()

  private val _notice = MutableStateFlow<Notice?>(null)
  val notice: StateFlow<Notice?> = _notice.asStateFlow()

  private val _testing = MutableStateFlow(false)
  val testing: StateFlow<Boolean> = _testing.asStateFlow()

  val logs: Flow<List<FallLog>> = dao.getAllLogs()

  val hasAccelerometer: Boolean get() = feed.hasAccelerometer

  @Volatile private var simulating = false
  @Volatile private var suppressAlerts = false
  private var countdownJob: Job? = null
  private var soundJob: Job? = null
  private var helpJob: Job? = null
  private var activeLogId: Int? = null

  // 실시간 그래프용 0.1초 묶음
  private val history = ArrayDeque<Float>()
  private var bucketStart = Long.MIN_VALUE
  private var bucketMax = 0f
  private var lastRotation = 0f

  init {
    // 앱이 막 켜진 지금은 진행 중인 경보가 없으니, '확인 중'으로 남은 기록은 이전 실행의 흔적입니다.
    scope.launch(Dispatchers.IO) { runCatching { dao.closeUnfinished() } }
    scope.launch {
      settings.state.collect { s ->
        detector.triggerThreshold = min(s.warningThreshold, s.emergencyThreshold)
      }
    }
  }

  // ───────── 센서 ─────────

  /** 감지 서비스, 맞춤 보정 등 센서가 필요한 쪽이 이름을 걸고 빌립니다. */
  fun acquireSensors(user: String): Boolean = synchronized(sensorUsers) {
    sensorUsers += user
    feed.start()
  }

  fun releaseSensors(user: String) = synchronized(sensorUsers) {
    sensorUsers -= user
    if (sensorUsers.isEmpty()) {
      feed.stop()
      synchronized(lock) { detector.reset(); resetLive() }
    }
  }

  fun onMonitoringStarted() {
    val ok = acquireSensors(MONITOR)
    _status.value = if (ok) MonitorStatus.ON else MonitorStatus.NO_SENSOR
    location.start()
  }

  fun onMonitoringStopped() {
    releaseSensors(MONITOR)
    location.stop()
    _status.value = MonitorStatus.OFF
  }

  /** 맞춤 보정처럼 일부러 충격을 주는 동안 경보를 막습니다. */
  fun setSuppressed(value: Boolean) {
    suppressAlerts = value
  }

  private fun onSensorAccel(tMs: Long, x: Float, y: Float, z: Float) {
    if (simulating) return
    process(tMs, x, y, z)
  }

  private fun onSensorGyro(x: Float, y: Float, z: Float) {
    if (simulating) return
    synchronized(lock) { lastRotation = detector.onGyro(x, y, z) }
  }

  private fun process(tMs: Long, x: Float, y: Float, z: Float) {
    val event = synchronized(lock) {
      val e = detector.onAccel(tMs, x, y, z)
      updateLive(tMs, x, y, z)
      e
    }
    if (event != null) {
      val test = simulating
      scope.launch { handleShock(event, test) }
    }
  }

  private fun updateLive(tMs: Long, x: Float, y: Float, z: Float) {
    val mag = sqrt(x * x + y * y + z * z)
    if (bucketStart == Long.MIN_VALUE || tMs < bucketStart) bucketStart = tMs
    bucketMax = max(bucketMax, mag)
    if (tMs - bucketStart < 100) return
    history.addLast(bucketMax)
    while (history.size > HISTORY_POINTS) history.removeFirst()
    val tilt = if (mag > 0.5f) (acos((z / mag).coerceIn(-1f, 1f)) * 57.29578f) else 0f
    _live.value = Live(history.toList(), mag, x, y, z, tilt, lastRotation)
    bucketStart = tMs
    bucketMax = 0f
  }

  private fun resetLive() {
    history.clear()
    bucketStart = Long.MIN_VALUE
    bucketMax = 0f
    _live.value = Live()
  }

  // ───────── 테스트 ─────────

  /** 가짜 낙상 데이터를 실제 감지 로직에 넣어 전체 과정을 시험합니다. 문자·전화는 실제로 보내지 않습니다. */
  fun startTest() {
    if (_testing.value || _alert.value != null) return
    _testing.value = true
    scope.launch(Dispatchers.Default) {
      try {
        synchronized(lock) { simulating = true; detector.reset(); resetLive() }
        val samples = FallSimulation.fall(settings.value.emergencyThreshold + 8f)
        val start = SystemClock.elapsedRealtime()
        for (s in samples) {
          val wait = start + s.tMs - SystemClock.elapsedRealtime()
          if (wait > 0) delay(wait)
          synchronized(lock) { lastRotation = detector.onGyro(s.gx, s.gy, s.gz) }
          process(start + s.tMs, s.ax, s.ay, s.az)
        }
      } finally {
        synchronized(lock) { detector.reset(); simulating = false }
        _testing.value = false
      }
    }
  }

  // ───────── 충격 처리 ─────────

  private suspend fun handleShock(e: ShockEvent, test: Boolean) {
    if (suppressAlerts && !test) return
    if (_alert.value != null) return
    val s = settings.value

    if (e.peakAccel < s.emergencyThreshold) {
      // 약한 충격: 넘어지는 모양일 때만 안내합니다 (걷기·뛰기의 흔들림은 무시).
      val fallLike = e.freeFallMs >= 80 || e.orientationChange >= 45f
      if (!fallLike) return
      val analysis = LocalRules.analyze(e, s.emergencyThreshold)
      _notice.value = Notice(
        "충격이 있었어요",
        "${gText(e.peakAccel)} 충격이 감지됐어요. 넘어지셨다면 바로 일어나지 말고 잠시 숨을 고른 뒤 천천히 일어나세요. 아프면 '도움 요청'을 눌러 주세요.",
      )
      insertLog(LogKind.WARNING, e, analysis, "안내만 표시", test)
      withContext(Dispatchers.Main) {
        if (s.vibrationEnabled) alarm.pulse()
        if (s.soundEnabled) alarm.speak("충격이 감지됐어요. 천천히 숨을 고르고 조심해서 일어나세요.")
      }
      Notifications.showNotice(context, "충격이 감지됐어요", "넘어지셨다면 천천히 일어나세요. 아프면 앱에서 도움을 요청할 수 있어요.")
      return
    }

    // 휴대폰 안에서 바로 판단합니다 (인터넷 필요 없음).
    val analysis = LocalRules.analyze(e, s.emergencyThreshold)
    if (analysis.verdict == Verdict.NORMAL) {
      _notice.value = Notice("큰 충격이 있었지만 괜찮아 보여요", analysis.reason)
      insertLog(LogKind.EMERGENCY, e, analysis, "경보 없음 (정상으로 판단)", test)
      return
    }
    // 문자에 넣을 위치를 미리 새로 받아 둡니다.
    if (!test) scope.launch { location.fresh() }
    startCountdown(e, analysis, test)
  }

  private suspend fun startCountdown(e: ShockEvent?, analysis: Analysis, test: Boolean) {
    val s = settings.value
    val total = s.countdownSeconds
    val endsAt = System.currentTimeMillis() + total * 1000L
    _alert.value = AlertState.Countdown(e, analysis, endsAt, total, test)
    activeLogId = insertLog(LogKind.EMERGENCY, e, analysis, "확인 중", test)
    Notifications.showCountdown(context, endsAt, s, test)

    withContext(Dispatchers.Main) {
      if (s.vibrationEnabled) alarm.startVibration()
      soundJob?.cancel()
      if (s.soundEnabled) {
        alarm.speak("괜찮으세요? ${total}초 안에 응답이 없으면 보호자에게 위치를 보내요. 괜찮으면 화면의 괜찮아요 버튼을 누르세요.")
        soundJob = scope.launch {
          delay(7000)
          if (_alert.value is AlertState.Countdown) alarm.startSound()
        }
      }
    }
    countdownJob?.cancel()
    countdownJob = scope.launch {
      delay(total * 1000L)
      // 문자 보내기는 별도 작업으로 넘깁니다. (카운트다운 작업 안에서 보내면
      // stopAlarm() 이 카운트다운 작업을 취소하면서 보내기까지 같이 멈춰 버립니다.)
      if (_alert.value is AlertState.Countdown) launchHelp(byUser = false)
    }
  }

  // ───────── 사용자 응답 ─────────

  /** "괜찮아요" */
  fun imOkay() {
    val a = _alert.value ?: return
    if (a is AlertState.Sending) return
    if (a is AlertState.Sent) return closeSent()
    stopAlarm()
    _alert.value = null
    Notifications.cancelAlert(context)
    val logId = activeLogId
    activeLogId = null
    if (a is AlertState.Countdown && logId != null) {
      scope.launch { dao.updateResponse(logId, "괜찮아요") }
    }
  }

  /** 경보 화면의 "지금 도움 요청" 또는 홈 화면의 "도움 요청" */
  fun requestHelp() {
    val a = _alert.value
    if (a is AlertState.Sending || a is AlertState.Sent) return
    stopAlarm()
    launchHelp(byUser = true)
  }

  private fun launchHelp(byUser: Boolean) {
    if (helpJob?.isActive == true) return
    helpJob = scope.launch { sendHelp(byUser) }
  }

  fun closeSent() {
    if (_alert.value is AlertState.Sent) {
      _alert.value = null
      Notifications.cancelAlert(context)
    }
  }

  fun dismissNotice() {
    _notice.value = null
  }

  private suspend fun sendHelp(byUser: Boolean) {
    val from = _alert.value
    val test = from?.test ?: false
    stopAlarm()
    _alert.value = AlertState.Sending(byUser, test)
    Notifications.cancelAlert(context)

    val s = settings.value
    val place = try {
      if (location.hasPermission()) location.fresh(if (test) 3000L else 7000L) else null
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      location.place.value // 위치를 못 구해도 문자는 보냅니다.
    }
    // 여기부터는 무슨 일이 있어도 끝까지 보냅니다.
    withContext(NonCancellable) { deliver(from, byUser, test, s, place) }
  }

  private suspend fun deliver(from: AlertState?, byUser: Boolean, test: Boolean, s: com.example.data.Settings, place: Place?) {
    val message = EmergencyMessage.build(s, place, byUser)
    val sendId = System.currentTimeMillis()
    val smsAvailable = sms.canSend()
    val recipients = s.smsRecipients.map { (label, number) ->
      val status = when {
        test -> SmsStatus.SKIPPED
        !smsAvailable -> SmsStatus.SKIPPED
        sms.send(sendId, number, message) -> SmsStatus.SENDING
        else -> SmsStatus.FAILED
      }
      Recipient(label, number, status)
    }
    val report = SentReport(byUser, test, message, recipients, smsAvailable, s.callNumber, s.callLabel, place, sendId)
    _alert.value = AlertState.Sent(report)

    val response = when {
      from is AlertState.Countdown && !byUser -> if (test) "응답 없음 (테스트라 문자 안 보냄)" else "응답 없음 → 자동 문자"
      from != null -> if (test) "도움 요청 (테스트라 문자 안 보냄)" else "도움 요청"
      else -> "직접 도움 요청"
    }
    val logId = activeLogId
    activeLogId = null
    if (logId != null) {
      dao.updateResponse(logId, response)
    } else {
      val kind = if (from == null) LogKind.SOS else LogKind.EMERGENCY
      insertLog(kind, (from as? AlertState.Countdown)?.event, null, response, test, place)
    }

    if (!test) {
      Notifications.showSent(context, report)
      if (s.soundEnabled) withContext(Dispatchers.Main) {
        alarm.speak(if (recipients.any { it.status == SmsStatus.SENDING }) "보호자에게 위치를 문자로 보냈어요." else "문자를 보내지 못했어요. 화면에서 전화를 걸어 주세요.")
      }
    }
  }

  fun onSmsResult(sendId: Long, number: String, ok: Boolean) {
    _alert.update { a ->
      if (a is AlertState.Sent && a.report.sendId == sendId) {
        a.copy(report = a.report.copy(recipients = a.report.recipients.map {
          if (it.number == number) it.copy(status = if (ok) SmsStatus.SENT else SmsStatus.FAILED) else it
        }))
      } else a
    }
  }

  private fun stopAlarm() {
    countdownJob?.cancel()
    countdownJob = null
    soundJob?.cancel()
    soundJob = null
    scope.launch(Dispatchers.Main) { alarm.stop() }
  }

  // ───────── 기록 ─────────

  private suspend fun insertLog(
    kind: LogKind,
    e: ShockEvent?,
    analysis: Analysis?,
    response: String,
    test: Boolean,
    place: Place? = location.place.value,
  ): Int {
    val log = FallLog(
      timestamp = e?.timestamp ?: System.currentTimeMillis(),
      latitude = place?.latitude ?: 0.0,
      longitude = place?.longitude ?: 0.0,
      hasLocation = place != null,
      address = place?.address.orEmpty(),
      aiResult = analysis?.verdict?.label.orEmpty(),
      confidence = analysis?.confidence ?: 0.0,
      reason = analysis?.reason.orEmpty(),
      analyzer = analysis?.source.orEmpty(),
      userResponse = response,
      isSimulated = test,
      maxAccel = e?.peakAccel ?: 0f,
      kind = kind.key,
    )
    return withContext(Dispatchers.IO) { dao.insertLog(log).toInt() }
  }

  fun deleteLog(id: Int) = scope.launch(Dispatchers.IO) { dao.deleteLogById(id) }
  fun clearLogs() = scope.launch(Dispatchers.IO) { dao.clearAllLogs() }

  companion object {
    const val MONITOR = "monitor"
    const val HISTORY_POINTS = 100
    fun gText(accel: Float) = String.format(java.util.Locale.US, "%.1fG", accel / GRAVITY)
  }
}
