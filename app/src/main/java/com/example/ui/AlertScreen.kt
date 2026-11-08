package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.ui.theme.AlarmRedDeep
import kotlinx.coroutines.delay
import kotlin.math.ceil

private val OnAlarm = Color.White
private val OnAlarmSoft = Color.White.copy(alpha = 0.82f)

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
    is AlertState.Checking -> {
      BackHandler { }
      AlarmBackdrop(modifier, calm = true) { CheckingContent(state, onOkay, onHelp) }
    }
    is AlertState.Countdown -> {
      BackHandler { }
      AlarmBackdrop(modifier, calm = false) { CountdownContent(state, settings, listening, onOkay, onHelp) }
    }
    is AlertState.Sending -> {
      BackHandler { }
      Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
          CircularProgressIndicator(Modifier.size(56.dp))
          Text(if (state.test) "테스트 결과를 만드는 중이에요" else "위치를 확인하고 문자를 보내고 있어요", style = MaterialTheme.typography.titleLarge)
          Text("잠시만 기다려 주세요.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    is AlertState.Sent -> {
      BackHandler(onBack = onClose)
      SentContent(state.report, modifier, onClose, onCall, onSmsApp)
    }
  }
}

@Composable
private fun AlarmBackdrop(modifier: Modifier, calm: Boolean, content: @Composable () -> Unit) {
  val pulse = rememberInfiniteTransition(label = "pulse")
  val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "t")
  val top = if (calm) AlarmRedDeep else lerpColor(AlarmRed, AlarmRedDeep, t * 0.5f)
  Box(
    modifier
      .fillMaxSize()
      .background(Brush.verticalGradient(listOf(top, AlarmRedDeep)))
      .systemBarsPadding()
      .testTag("alert"),
  ) { content() }
}

private fun lerpColor(a: Color, b: Color, f: Float) = Color(
  red = a.red + (b.red - a.red) * f,
  green = a.green + (b.green - a.green) * f,
  blue = a.blue + (b.blue - a.blue) * f,
)

@Composable
private fun TestBanner() {
  Surface(color = Color.White.copy(alpha = 0.16f), contentColor = OnAlarm, shape = MaterialTheme.shapes.small) {
    Text("테스트 중 · 실제 문자는 보내지 않아요", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
  }
}

@Composable
private fun AlarmButtons(okText: String, onOkay: () -> Unit, onHelp: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Button(
      onClick = onOkay,
      modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).testTag("okay_button"),
      colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AlarmRedDeep),
      shape = MaterialTheme.shapes.large,
    ) {
      Text(okText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
    OutlinedButton(
      onClick = onHelp,
      modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
      colors = ButtonDefaults.outlinedButtonColors(contentColor = OnAlarm),
      border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.7f)),
      shape = MaterialTheme.shapes.large,
    ) {
      Text("지금 도움 요청", style = MaterialTheme.typography.titleMedium)
    }
  }
}

@Composable
private fun CheckingContent(state: AlertState.Checking, onOkay: () -> Unit, onHelp: () -> Unit) {
  // 내용은 위, 버튼은 엄지가 닿는 아래쪽에 둡니다. 화면이 작으면 스크롤됩니다.
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val minHeight = maxHeight
    if (maxWidth > maxHeight) {
      Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
          if (state.test) TestBanner()
          Text("큰 충격이 감지됐어요", style = MaterialTheme.typography.headlineSmall, color = OnAlarm)
          Text("AI가 넘어진 건지 확인하고 있어요. 괜찮으시면 버튼을 눌러 주세요.", style = MaterialTheme.typography.bodyLarge, color = OnAlarmSoft)
          EventFacts(state.event)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
          AlarmButtons("괜찮아요", onOkay, onHelp)
        }
      }
      return@BoxWithConstraints
    }
    Column(
      Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = minHeight).padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      if (state.test) TestBanner()
      Spacer(Modifier.height(12.dp))
      CircularProgressIndicator(color = OnAlarm, trackColor = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(52.dp))
      Text("큰 충격이 감지됐어요", style = MaterialTheme.typography.headlineMedium, color = OnAlarm)
      Text("AI가 넘어진 건지 확인하고 있어요. 괜찮으시면 바로 아래 버튼을 눌러 주세요.", style = MaterialTheme.typography.bodyLarge, color = OnAlarmSoft)
      EventFacts(state.event)
      Spacer(Modifier.weight(1f))
      AlarmButtons("괜찮아요", onOkay, onHelp)
    }
  }
}

@Composable
private fun EventFacts(e: ShockEvent) {
  Surface(color = Color.White.copy(alpha = 0.12f), contentColor = OnAlarm, shape = MaterialTheme.shapes.medium) {
    Row(Modifier.fillMaxWidth().padding(14.dp)) {
      Fact("최대 충격", gForce(e.peakAccel), Modifier.weight(1f))
      Fact("떨어짐", if (e.freeFallMs >= 80) "${e.freeFallMs}ms" else "없음", Modifier.weight(1f))
      Fact("자세 변화", "${e.orientationChange.toInt()}°", Modifier.weight(1f))
    }
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

  val ring: @Composable (Int) -> Unit = { size ->
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
      Canvas(Modifier.fillMaxSize()) {
        val stroke = 12.dp.toPx()
        drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, style = Stroke(stroke))
        drawArc(Color.White, -90f, 360f * fraction, false, style = Stroke(stroke, cap = StrokeCap.Round))
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          "$seconds", fontSize = (size * 0.39f).sp, fontWeight = FontWeight.Bold, color = OnAlarm,
          modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text("초", style = MaterialTheme.typography.titleMedium, color = OnAlarmSoft)
      }
    }
  }
  val message: @Composable () -> Unit = {
    Text(
      "응답이 없으면 ${settings.smsTargetPhrase} 현재 위치를 문자로 보내요.",
      style = MaterialTheme.typography.titleMedium, color = OnAlarm, textAlign = TextAlign.Center,
    )
  }
  val voice: @Composable () -> Unit = {
    if (settings.voiceCancelEnabled) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (listening) 1f else 0.6f)) {
        Icon(Icons.Outlined.Mic, null, tint = OnAlarm, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(
          if (listening) "듣고 있어요 · '괜찮아' 또는 '취소'라고 말해도 돼요" else "'괜찮아'라고 말해도 돼요",
          style = MaterialTheme.typography.bodyMedium, color = OnAlarm,
        )
      }
    }
  }

  BoxWithConstraints(Modifier.fillMaxSize()) {
    if (maxWidth > maxHeight) {
      // 가로 화면 (넘어지면서 휴대폰이 옆으로 누운 경우): 버튼이 화면 밖으로 밀리지 않게 두 칸으로.
      Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(
          Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
          if (state.test) TestBanner()
          Text("괜찮으세요?", style = MaterialTheme.typography.headlineMedium, color = OnAlarm)
          ring(140)
          message()
        }
        Column(
          Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
          AlarmButtons("괜찮아요", onOkay, onHelp)
          voice()
          AnalysisBox(state)
        }
      }
    } else {
      Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        if (state.test) TestBanner()
        Text("괜찮으세요?", style = MaterialTheme.typography.displaySmall, color = OnAlarm, textAlign = TextAlign.Center)
        ring(196)
        message()
        AlarmButtons("괜찮아요", onOkay, onHelp)
        voice()
        AnalysisBox(state)
      }
    }
  }
}

@Composable
private fun AnalysisBox(state: AlertState.Countdown) {
  val a = state.analysis
  Surface(color = Color.White.copy(alpha = 0.12f), contentColor = OnAlarm, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${a.source} 판단", style = MaterialTheme.typography.labelLarge, color = OnAlarmSoft, modifier = Modifier.weight(1f))
        Text("${(a.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = OnAlarmSoft)
      }
      Text(if (a.verdict == Verdict.EMERGENCY) "도움이 필요할 수 있어요" else "넘어졌을 수 있어요", style = MaterialTheme.typography.titleMedium)
      Text(a.reason, style = MaterialTheme.typography.bodyMedium, color = OnAlarmSoft)
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
  Box(modifier.fillMaxSize().background(scheme.background).systemBarsPadding().testTag("sent")) {
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      if (report.test) Tag("테스트", scheme.onSurfaceVariant, scheme.surfaceVariant)
      Text(
        when {
          report.test -> "실제 상황이었다면 이렇게 보냈어요"
          sentAny -> if (report.byUser) "도움 요청을 보냈어요" else "응답이 없어 도움 요청을 보냈어요"
          else -> "문자를 보내지 못했어요"
        },
        style = MaterialTheme.typography.headlineSmall,
      )
      Text(
        when {
          report.test -> "테스트라서 문자는 보내지 않았어요. 실제로는 아래 내용이 보호자에게 가요."
          sentAny -> "전화로 직접 상황을 알리면 더 빨리 도움을 받을 수 있어요."
          !report.smsAvailable -> "문자 권한이 없거나 이 기기에서 문자를 보낼 수 없어요. 아래 버튼으로 직접 보내거나 전화해 주세요."
          else -> "아래 버튼으로 직접 보내거나 전화해 주세요."
        },
        style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant,
      )

      if (report.callNumber.isNotBlank()) {
        Button(
          onClick = { onCall(report.callNumber) },
          modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
          colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
          shape = MaterialTheme.shapes.medium,
        ) {
          Icon(Icons.Outlined.Call, null)
          Spacer(Modifier.width(8.dp))
          val number = Phone.pretty(report.callNumber)
          Text(
            if (report.callLabel == number || report.callLabel == report.callNumber) "${report.callLabel}에 전화"
            else "${report.callLabel}에게 전화 ($number)",
            style = MaterialTheme.typography.titleMedium,
          )
        }
      }
      if (!report.test && !sentAny && report.recipients.isNotEmpty()) {
        OutlinedButton(onClick = { onSmsApp(report) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium) {
          Icon(Icons.Outlined.Sms, null)
          Spacer(Modifier.width(8.dp))
          Text("문자 앱으로 보내기", style = MaterialTheme.typography.titleMedium)
        }
      }

      Panel {
        SectionHeader("받는 사람")
        if (report.recipients.isEmpty()) {
          Text("등록된 연락처가 없어요. 설정에서 보호자 번호를 넣어 주세요.", style = MaterialTheme.typography.bodyMedium, color = scheme.error)
        }
        report.recipients.forEachIndexed { i, r ->
          if (i > 0) HorizontalDivider(color = scheme.outlineVariant)
          Row(verticalAlignment = Alignment.CenterVertically) {
            val (icon, tint) = when (r.status) {
              SmsStatus.SENT -> Icons.Outlined.CheckCircle to scheme.primary
              SmsStatus.SENDING -> Icons.Outlined.Schedule to scheme.primary
              SmsStatus.FAILED -> Icons.Outlined.ErrorOutline to scheme.error
              SmsStatus.SKIPPED -> Icons.Outlined.Sms to scheme.onSurfaceVariant
            }
            Icon(icon, null, tint = tint)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
              Text(r.label, style = MaterialTheme.typography.titleSmall)
              Text(Phone.pretty(r.number), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Text(r.status.label, style = MaterialTheme.typography.labelLarge, color = tint)
          }
        }
      }

      Panel(color = scheme.surfaceContainerHigh) {
        SectionHeader("문자 내용")
        Text(report.message, style = MaterialTheme.typography.bodyMedium)
      }

      TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("close_sent")) {
        Text("닫기", style = MaterialTheme.typography.titleMedium)
      }
    }
  }
}
