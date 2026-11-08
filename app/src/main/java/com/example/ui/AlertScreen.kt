package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Phone
import com.example.data.Settings
import com.example.detect.ShockEvent
import com.example.detect.Verdict
import com.example.engine.AlertState
import com.example.engine.SentReport
import com.example.engine.SmsStatus
import com.example.ui.theme.AlarmRed
import kotlinx.coroutines.delay
import kotlin.math.ceil

private val OnAlarm = Color.White
private val OnAlarmSoft = Color.White.copy(alpha = 0.85f)
private val AlarmLine = Color.White.copy(alpha = 0.35f)

@Composable
fun AlertScreen(
  state: AlertState,
  settings: Settings,
  listening: Boolean,
  onOkay: () -> Unit,
  onHelp: () -> Unit,
  onClose: () -> Unit,
  onCall: (String) -> Unit,
  onSmsApp: (SentReport) -> Unit,
  modifier: Modifier = Modifier,
) {
  when (state) {
    is AlertState.Countdown -> {
      BackHandler { }
      AlarmPage(modifier) { CountdownContent(state, settings, listening, onOkay, onHelp) }
    }
    is AlertState.Sending -> {
      BackHandler { }
      Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
          CircularProgressIndicator(Modifier.size(40.dp), strokeWidth = 3.dp)
          Text(if (state.test) "테스트 결과를 만드는 중이에요" else "위치를 확인하고 문자를 보내고 있어요", style = MaterialTheme.typography.titleMedium)
          Caption("잠시만 기다려 주세요.")
        }
      }
    }
    is AlertState.Sent -> {
      BackHandler(onBack = onClose)
      SentContent(state.report, modifier, onClose, onCall, onSmsApp)
    }
  }
}

/** 경보 화면 바탕: 단색 빨강 (그라데이션·깜빡임 없음) */
@Composable
private fun AlarmPage(modifier: Modifier, content: @Composable () -> Unit) {
  Box(modifier.fillMaxSize().background(AlarmRed).systemBarsPadding().testTag("alert")) { content() }
}

@Composable
private fun TestLabel() {
  Text(
    "테스트 · 실제 문자는 보내지 않아요", style = MaterialTheme.typography.labelLarge, color = OnAlarm,
    modifier = Modifier.border(1.dp, AlarmLine, ButtonShape).padding(horizontal = 10.dp, vertical = 5.dp),
  )
}

@Composable
private fun AlarmButtons(onOkay: () -> Unit, onHelp: () -> Unit, modifier: Modifier = Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Button(
      onClick = onOkay, shape = ButtonShape, elevation = null,
      colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AlarmRed),
      modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("okay_button"),
    ) { Text("괜찮아요", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
    OutlinedButton(
      onClick = onHelp, shape = ButtonShape, border = BorderStroke(1.5.dp, Color.White),
      colors = ButtonDefaults.outlinedButtonColors(contentColor = OnAlarm),
      modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text("지금 도움 요청", style = MaterialTheme.typography.titleMedium) }
  }
}

/** 흰 선 상자 안의 짧은 표 */
@Composable
private fun AlarmBox(content: @Composable () -> Unit) {
  Box(Modifier.fillMaxWidth().border(1.dp, AlarmLine, ButtonShape).padding(14.dp)) { content() }
}

@Composable
private fun EventFacts(e: ShockEvent) {
  Row(Modifier.fillMaxWidth()) {
    Fact("최대 충격", gForce(e.peakAccel), Modifier.weight(1f))
    Fact("떨어짐", if (e.freeFallMs >= 80) "${e.freeFallMs}ms" else "없음", Modifier.weight(1f))
    Fact("자세 변화", "${e.orientationChange.toInt()}°", Modifier.weight(1f))
  }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier) {
  Column(modifier) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = OnAlarmSoft)
    Text(value, style = MaterialTheme.typography.titleLarge, color = OnAlarm)
  }
}

@Composable
private fun CountdownContent(
  state: AlertState.Countdown,
  settings: Settings,
  listening: Boolean,
  onOkay: () -> Unit,
  onHelp: () -> Unit,
) {
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(state.endsAt) {
    while (true) {
      now = System.currentTimeMillis()
      delay(200)
    }
  }
  val leftMs = (state.endsAt - now).coerceAtLeast(0)
  val seconds = ceil(leftMs / 1000.0).toInt()
  val fraction = (leftMs / (state.totalSeconds * 1000f)).coerceIn(0f, 1f)

  val head: @Composable () -> Unit = {
    if (state.test) TestLabel()
    Text("괜찮으세요?", style = MaterialTheme.typography.displaySmall, color = OnAlarm)
    Row(verticalAlignment = Alignment.Bottom) {
      Text(
        "$seconds", fontSize = 88.sp, lineHeight = 88.sp, fontWeight = FontWeight.Bold, color = OnAlarm,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
      )
      Spacer(Modifier.width(8.dp))
      Text("초 남았어요", style = MaterialTheme.typography.titleMedium, color = OnAlarmSoft, modifier = Modifier.padding(bottom = 14.dp))
    }
    LinearProgressIndicator(
      progress = { fraction }, color = OnAlarm, trackColor = AlarmLine, strokeCap = StrokeCap.Butt, gapSize = 0.dp,
      drawStopIndicator = {}, modifier = Modifier.fillMaxWidth().height(6.dp),
    )
    Text("응답이 없으면 ${settings.smsTargetPhrase} 현재 위치를 문자로 보내요.", style = MaterialTheme.typography.titleMedium, color = OnAlarm)
  }
  val tail: @Composable () -> Unit = {
    if (settings.voiceCancelEnabled) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Mic, null, tint = if (listening) OnAlarm else OnAlarmSoft, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(
          if (listening) "듣고 있어요 · '괜찮아' 또는 '취소'라고 말해도 돼요" else "'괜찮아'라고 말해도 돼요",
          style = MaterialTheme.typography.bodyMedium, color = OnAlarmSoft,
        )
      }
    }
    AnalysisBox(state)
  }

  BoxWithConstraints(Modifier.fillMaxSize()) {
    if (maxWidth > maxHeight) {
      // 가로 화면 (넘어지면서 휴대폰이 옆으로 누운 경우): 버튼이 화면 밖으로 밀리지 않게 두 칸으로.
      Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(
          Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) { head() }
        Column(
          Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
          AlarmButtons(onOkay, onHelp)
          tail()
        }
      }
    } else {
      Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        head()
        Spacer(Modifier.height(6.dp))
        AlarmButtons(onOkay, onHelp)
        tail()
      }
    }
  }
}

@Composable
private fun AnalysisBox(state: AlertState.Countdown) {
  val a = state.analysis
  AlarmBox {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        if (a.verdict == Verdict.EMERGENCY) "큰 충격 뒤 움직임이 없어요" else "넘어졌을 수 있어요",
        style = MaterialTheme.typography.titleSmall, color = OnAlarm,
      )
      // 판단 근거는 잰 숫자 그대로 보여 줍니다.
      if (state.event != null) EventFacts(state.event) else Text(a.reason, style = MaterialTheme.typography.bodyMedium, color = OnAlarmSoft)
    }
  }
}

@Composable
private fun SentContent(
  report: SentReport,
  modifier: Modifier,
  onClose: () -> Unit,
  onCall: (String) -> Unit,
  onSmsApp: (SentReport) -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val sentAny = report.recipients.any { it.status == SmsStatus.SENDING || it.status == SmsStatus.SENT }
  Column(modifier.fillMaxSize().background(scheme.background).systemBarsPadding().verticalScroll(rememberScrollState()).testTag("sent")) {
    Section {
      Column(Modifier.sidePadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (report.test) Tag("테스트", scheme.onSurfaceVariant, scheme.surfaceVariant)
        Text(
          when {
            report.test -> "실제 상황이었다면 이렇게 보냈어요"
            sentAny -> if (report.byUser) "도움 요청을 보냈어요" else "응답이 없어 도움 요청을 보냈어요"
            else -> "문자를 보내지 못했어요"
          },
          style = MaterialTheme.typography.headlineSmall,
          color = if (!report.test && !sentAny) scheme.error else scheme.onSurface,
        )
        Text(
          when {
            report.test -> "테스트라서 문자는 보내지 않았어요. 실제로는 아래 내용이 보호자에게 가요."
            sentAny -> "전화로 직접 상황을 알리면 더 빨리 도움을 받을 수 있어요."
            !report.smsAvailable ->
              "문자 권한이 꺼져 있어 자동으로 보내지 못했어요. 아래 '문자 앱으로 보내기'를 누른 뒤 문자 앱에서 '보내기'를 눌러 주세요. 다음부터 자동으로 보내려면 홈의 '준비 상태'에서 문자 권한을 켜 주세요."
            else -> "통신사 사정으로 보내지 못했어요. 아래 '문자 앱으로 보내기'를 누르거나 전화해 주세요."
          },
          style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
        )
      }
      Spacer(Modifier.height(16.dp))
      Column(Modifier.sidePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (report.test) {
          // 연습 중에 실수로 119 에 전화하지 않도록 테스트에서는 전화 버튼을 보여 주지 않습니다.
          Caption("실제 상황이라면 여기에 '${report.callLabel}에 전화' 버튼이 나와요. 테스트에서는 누를 수 없게 숨겼어요.")
        } else if (report.callNumber.isNotBlank()) {
          val number = Phone.pretty(report.callNumber)
          AppButton(
            if (report.callLabel == number || report.callLabel == report.callNumber) "${report.callLabel}에 전화" else "${report.callLabel}에게 전화 ($number)",
            { onCall(report.callNumber) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Call,
            container = AlarmRed, content = Color.White, height = 56.dp,
          )
        }
        if (!report.test && !sentAny && report.recipients.isNotEmpty()) {
          AppOutlinedButton("문자 앱으로 보내기", { onSmsApp(report) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Sms)
        }
      }
    }

    SectionGap()
    Section("받는 사람") {
      if (report.recipients.isEmpty()) {
        Text("등록된 연락처가 없어요. 설정에서 보호자 번호를 넣어 주세요.", style = MaterialTheme.typography.bodyMedium, color = scheme.error, modifier = Modifier.sidePadding())
      }
      report.recipients.forEachIndexed { i, r ->
        if (i > 0) InsetDivider()
        RowItem(
          r.label, subtitle = Phone.pretty(r.number), value = r.status.label,
          valueColor = when (r.status) {
            SmsStatus.SENT, SmsStatus.SENDING -> scheme.primary
            SmsStatus.FAILED -> scheme.error
            SmsStatus.SKIPPED -> scheme.onSurfaceVariant
          },
        )
      }
    }

    SectionGap()
    Section("문자 내용") {
      Text(report.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.sidePadding())
    }

    Spacer(Modifier.height(16.dp))
    AppOutlinedButton("닫기", onClose, Modifier.fillMaxWidth().sidePadding().testTag("close_sent"))
    Spacer(Modifier.height(24.dp))
  }
}
