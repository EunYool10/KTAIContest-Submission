package com.example

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.CallTarget
import com.example.data.FallLog
import com.example.data.LogKind
import com.example.data.Settings
import com.example.detect.Analysis
import com.example.detect.GRAVITY
import com.example.detect.ShockEvent
import com.example.detect.Verdict
import com.example.engine.AlertState
import com.example.engine.Live
import com.example.engine.MonitorStatus
import com.example.engine.Notice
import com.example.engine.Place
import com.example.engine.Recipient
import com.example.engine.SentReport
import com.example.engine.SmsStatus
import com.example.engine.UpdateInfo
import com.example.ui.AlertScreen
import com.example.ui.Calibration
import com.example.ui.CalStep
import com.example.ui.CalibrationScreen
import com.example.ui.Check
import com.example.ui.CheckId
import com.example.ui.HistoryScreen
import com.example.ui.HomeScreen
import com.example.ui.OnboardingScreen
import com.example.ui.ReadinessState
import com.example.ui.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

/** 화면 모양 확인용 스크린샷. `./gradlew recordRoborazziDebug` 로 src/test/screenshots 에 저장됩니다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7, sdk = [34])
class ScreenshotTest {
  @get:Rule val compose = createComposeRule()

  private val now = System.currentTimeMillis()
  private val settings = Settings(
    setupComplete = true, userName = "홍길동", guardianName = "김철수", guardianPhone = "01012345678",
    extraContact = "01098765432", callTarget = CallTarget.FIRE, monitoringEnabled = true,
  )
  private val history = List(100) { i ->
    val base = GRAVITY + (sin(i / 3.0) * 1.6).toFloat()
    when (i) { 78 -> 21f; 79 -> 28f; 80 -> 12f; else -> base }
  }
  private val live = Live(history, 9.9f, 0.4f, 9.7f, 1.2f, 7f, 12f)
  private val place = Place(37.5559, 126.9723, 14f, now - 120_000, "서울특별시 중구 한강대로 405")
  private val event = ShockEvent(now, 31.2f, 420f, 320, 82f, 0.05f)
  private val analysis = Analysis(
    Verdict.EMERGENCY, 0.92,
    "큰 충격(3.2G) 직전 0.3초 동안 떨어지는 움직임이 있었고, 그 뒤 거의 움직이지 않아 넘어져 다쳤을 가능성이 높아요.", "기기 안 규칙",
  )
  private val checks = listOf(
    Check(CheckId.SENSOR, "움직임 센서", "가속도 센서를 쓸 수 있어요.", "", true, true),
    Check(CheckId.CONTACT, "보호자 연락처", "김철수 · 010-1234-5678", "", true, true, "입력"),
    Check(CheckId.NOTIFY, "알림", "", "", true, true, "허용"),
    Check(CheckId.LOCATION, "위치", "도움 요청 문자에 현재 위치를 넣어요.", "", true, true, "허용"),
    Check(CheckId.SMS, "문자 자동 전송", "응답이 없으면 보호자에게 바로 문자를 보내요.", "", true, true, "허용"),
    Check(CheckId.BATTERY, "배터리 최적화 제외", "", "절전 모드에서 감지가 멈출 수 있어요.", false, false, "설정"),
  )
  private val logs = listOf(
    FallLog(1, now - 3_600_000, 37.5559, 126.9723, "응급상황 가능성 높음", "괜찮아요", false, 31f, 0.92, analysis.reason, "서울특별시 중구 한강대로 405", true, LogKind.EMERGENCY.key, "기기 안 규칙"),
    FallLog(2, now - 7_200_000, 37.5559, 126.9723, "낙상 의심", "안내만 표시", false, 18f, 0.68, "", "", true, LogKind.WARNING.key, "기기 안 규칙"),
    FallLog(3, now - 90_000_000, 37.5559, 126.9723, "응급상황 가능성 높음", "응답 없음 → 자동 문자", true, 33f, 0.95, "", "", false, LogKind.EMERGENCY.key, "기기 안 규칙"),
    FallLog(4, now - 95_000_000, 0.0, 0.0, "", "직접 도움 요청", false, 0f, 0.0, "", "", false, LogKind.SOS.key, ""),
  )

  private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
    compose.setContent {
      MyApplicationTheme(darkTheme = dark) {
        // 앱과 똑같이 Surface 가 배경색과 글자색을 정합니다.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) { content() }
      }
    }
    compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
  }

  @Composable
  private fun home(status: MonitorStatus = MonitorStatus.ON, notice: Notice? = null, update: UpdateInfo? = null) = HomeScreen(
    settings = settings, status = status, live = live, place = place, hasLocationPermission = true,
    notice = notice, testing = false, readiness = ReadinessState(checks) {}, recent = logs,
    onMonitoring = {}, onHelp = {}, onTest = {}, onDismissNotice = {}, onOpenHistory = {}, update = update,
  )

  @Test fun home_on() = shot("home_on") { home() }
  @Test fun home_update() = shot("home_update") {
    home(update = UpdateInfo(9, "2.4.0", "2026-11-20", UpdateInfo.RELEASES + "v2.4.0/ai-shock-alert.apk", UpdateInfo.SITE, listOf("새 버전이 나오면 앱에서 알려 줘요.", "문자 전송이 더 안정적이에요.")))
  }
  @Test fun home_dark() = shot("home_dark", dark = true) { home() }
  @Test fun home_off_notice() = shot("home_off_notice") {
    home(MonitorStatus.OFF, Notice("충격이 있었어요", "1.8G 충격이 감지됐어요. 넘어지셨다면 바로 일어나지 말고 잠시 숨을 고른 뒤 천천히 일어나세요.", now - 60_000))
  }

  @Test fun onboarding_welcome() = shot("onboarding_welcome") { OnboardingScreen(Settings(), { _, _, _, _, _ -> }, {}) }
  @Test fun onboarding_contacts() = shot("onboarding_contacts") { OnboardingScreen(Settings(userName = "홍길동"), { _, _, _, _, _ -> }, {}, startStep = 1) }
  @Test fun onboarding_permissions() = shot("onboarding_permissions") { OnboardingScreen(settings, { _, _, _, _, _ -> }, {}, startStep = 3) }

  @Test fun alert_countdown() = shot("alert_countdown") {
    AlertScreen(AlertState.Countdown(event, analysis, now + 21_400, 30, test = false), settings, true, {}, {}, {}, {}, {})
  }
  @Config(qualifiers = "+land")
  @Test fun alert_countdown_land() = shot("alert_countdown_land") {
    AlertScreen(AlertState.Countdown(event, analysis, now + 17_400, 30, test = false), settings, true, {}, {}, {}, {}, {})
  }
  @Test fun alert_countdown_test() = shot("alert_countdown_test") {
    AlertScreen(AlertState.Countdown(event, analysis, now + 28_000, 30, test = true), settings, false, {}, {}, {}, {}, {})
  }
  @Test fun alert_sent() = shot("alert_sent") {
    val msg = "[긴급] 홍길동님 휴대폰에 큰 충격이 감지됐고 30초 동안 응답이 없었어요.\n위치: 서울특별시 중구 한강대로 405 (오차 약 14m)\nhttps://maps.google.com/?q=37.555900,126.972300\n시각: 11월 8일 오후 3:21"
    val report = SentReport(
      false, false, msg,
      listOf(Recipient("김철수", "01012345678", SmsStatus.SENT), Recipient("추가 연락처", "01098765432", SmsStatus.SENDING)),
      true, "119", "119", place, 1L,
    )
    AlertScreen(AlertState.Sent(report), settings, false, {}, {}, {}, {}, {})
  }

  @Test fun history() = shot("history") { HistoryScreen(logs, {}, {}) }
  @Test fun settings() = shot("settings") {
    SettingsScreen(settings, false, {}, {}, {}, { _, _ -> }, {}, {})
  }
  @Test fun calibration_result() = shot("calibration_result") {
    CalibrationScreen(Calibration(CalStep.RESULT, 0, 15.2f, 44f), live, settings, {}, {}, {})
  }
}
