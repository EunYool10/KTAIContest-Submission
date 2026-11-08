package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.FallLog
import com.example.data.Phone
import com.example.data.Settings
import com.example.engine.Live
import com.example.engine.MonitorStatus
import com.example.engine.Notice
import com.example.engine.Place
import com.example.ui.theme.AppTheme

@Composable
fun HomeScreen(
  settings: Settings,
  status: MonitorStatus,
  live: Live,
  place: Place?,
  hasLocationPermission: Boolean,
  notice: Notice?,
  testing: Boolean,
  readiness: ReadinessState,
  recent: List<FallLog>,
  onMonitoring: (Boolean) -> Unit,
  onHelp: () -> Unit,
  onTest: () -> Unit,
  onDismissNotice: () -> Unit,
  onOpenHistory: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var confirmHelp by rememberSaveable { mutableStateOf(false) }
  var confirmOff by rememberSaveable { mutableStateOf(false) }

  LazyColumn(
    modifier.fillMaxSize().testTag("home"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Text("안녕하세요, ${settings.userName.ifBlank { "사용자" }}님", style = MaterialTheme.typography.headlineSmall)
        Text(dateText(System.currentTimeMillis()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    item {
      StatusPanel(settings, status, readiness.missingEssential, onStart = { onMonitoring(true) }, onStop = { confirmOff = true })
    }
    if (notice != null) {
      item { NoticePanel(notice, onDismissNotice) }
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
          onClick = { confirmHelp = true },
          modifier = Modifier.weight(1f).heightIn(min = 64.dp).testTag("help_button"),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
          shape = MaterialTheme.shapes.medium,
        ) {
          Icon(Icons.Filled.Sos, null, Modifier.size(26.dp))
          Spacer(Modifier.width(8.dp))
          Text("도움 요청", style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(
          onClick = onTest,
          enabled = !testing,
          modifier = Modifier.weight(1f).heightIn(min = 64.dp).testTag("test_button"),
          shape = MaterialTheme.shapes.medium,
        ) {
          Icon(Icons.Outlined.PlayCircle, null, Modifier.size(24.dp))
          Spacer(Modifier.width(8.dp))
          Text(if (testing) "테스트 중…" else "경보 테스트", style = MaterialTheme.typography.titleMedium)
        }
      }
    }
    item { MotionPanel(live, settings, status, testing) }
    item { LocationPanel(place, hasLocationPermission, onAllow = { readiness.fix(CheckId.LOCATION) }) }
    item { ReadinessPanel(readiness) }
    if (recent.isNotEmpty()) {
      item {
        Panel {
          SectionHeader("최근 기록") { TextButton(onClick = onOpenHistory) { Text("전체 보기") } }
          recent.take(3).forEachIndexed { i, log ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LogSummary(log)
          }
        }
      }
    }
    item { Spacer(Modifier.height(8.dp)) }
  }

  if (confirmHelp) {
    AlertDialog(
      onDismissRequest = { confirmHelp = false },
      icon = { Icon(Icons.Filled.Sos, null, tint = MaterialTheme.colorScheme.error) },
      title = { Text("도움을 요청할까요?") },
      text = { Text("${settings.smsTargetPhrase} 현재 위치를 문자로 보내고, ${settings.callLabel}에 바로 걸 수 있게 전화 화면을 열어요.") },
      confirmButton = {
        Button(
          onClick = { confirmHelp = false; onHelp() },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text("요청 보내기") }
      },
      dismissButton = { TextButton(onClick = { confirmHelp = false }) { Text("취소") } },
    )
  }
  if (confirmOff) {
    AlertDialog(
      onDismissRequest = { confirmOff = false },
      title = { Text("감지를 끌까요?") },
      text = { Text("끄면 넘어지거나 큰 충격이 있어도 보호자에게 알리지 않아요.") },
      confirmButton = { TextButton(onClick = { confirmOff = false; onMonitoring(false) }) { Text("끄기") } },
      dismissButton = { TextButton(onClick = { confirmOff = false }) { Text("계속 감지") } },
    )
  }
}

@Composable
private fun StatusPanel(settings: Settings, status: MonitorStatus, missing: Int, onStart: () -> Unit, onStop: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  when (status) {
    MonitorStatus.ON -> Panel(color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer, modifier = Modifier.testTag("status_on")) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Icons.Filled.GppGood, scheme.onPrimary, scheme.primary, size = 52)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text("충격 감지 중", style = MaterialTheme.typography.headlineSmall)
          Text("화면이 꺼져 있어도 지켜보고 있어요.", style = MaterialTheme.typography.bodyMedium)
        }
      }
      FlowSteps(settings)
      if (missing > 0) {
        Text("필요한 설정 ${missing}개가 빠져 있어요. 아래 '준비 상태'를 확인해 주세요.", style = MaterialTheme.typography.bodyMedium, color = scheme.error, fontWeight = FontWeight.SemiBold)
      }
      TextButton(onClick = onStop, contentPadding = PaddingValues(0.dp)) { Text("감지 끄기", color = scheme.onPrimaryContainer) }
    }
    MonitorStatus.OFF -> Panel(color = scheme.surfaceContainerHighest, modifier = Modifier.testTag("status_off")) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Icons.Outlined.Shield, scheme.onSurfaceVariant, scheme.surface, size = 52)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text("감지가 꺼져 있어요", style = MaterialTheme.typography.headlineSmall)
          Text("켜면 넘어지거나 큰 충격이 있을 때 보호자에게 알려요.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
      }
      Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("start_button"), shape = MaterialTheme.shapes.medium) {
        Text("감지 시작", style = MaterialTheme.typography.titleMedium)
      }
    }
    MonitorStatus.NO_SENSOR -> Panel(color = scheme.errorContainer, contentColor = scheme.onErrorContainer) {
      Text("이 기기에서는 감지할 수 없어요", style = MaterialTheme.typography.headlineSmall)
      Text("가속도 센서가 없어요. '경보 테스트'와 '도움 요청'은 사용할 수 있어요.", style = MaterialTheme.typography.bodyMedium)
      TextButton(onClick = onStop, contentPadding = PaddingValues(0.dp)) { Text("감지 끄기", color = scheme.onErrorContainer) }
    }
  }
}

/** 큰 충격 → 확인 → 문자 순서를 한 줄로 */
@Composable
private fun FlowSteps(settings: Settings) {
  val steps = listOf("큰 충격 감지", "${settings.countdownSeconds}초 동안 괜찮은지 확인", "응답이 없으면 ${settings.smsTargetPhrase} 위치 문자")
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    steps.forEachIndexed { i, s ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${i + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(20.dp))
        Text(s, style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

@Composable
private fun NoticePanel(notice: Notice, onDismiss: () -> Unit) {
  val extra = AppTheme.extra
  Panel(color = extra.warningContainer, contentColor = extra.onWarningContainer, modifier = Modifier.testTag("notice")) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Outlined.Info, null, tint = extra.warning)
      Spacer(Modifier.width(8.dp))
      Text(notice.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
      Text(agoText(notice.time), style = MaterialTheme.typography.labelMedium)
    }
    Text(notice.message, style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) { Text("확인했어요", color = extra.onWarningContainer) }
  }
}

@Composable
private fun MotionPanel(live: Live, settings: Settings, status: MonitorStatus, testing: Boolean) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  val scheme = MaterialTheme.colorScheme
  val active = status == MonitorStatus.ON || testing
  val recentPeak = live.history.takeLast(10).maxOrNull() ?: 0f
  val (label, color) = when {
    !active -> "꺼짐" to scheme.onSurfaceVariant
    recentPeak >= settings.emergencyThreshold -> "강한 충격" to scheme.error
    recentPeak >= settings.warningThreshold -> "흔들림" to AppTheme.extra.warning
    else -> "안정" to scheme.primary
  }
  Panel {
    SectionHeader(if (testing) "움직임 (테스트 데이터)" else "움직임") {
      Text(label, style = MaterialTheme.typography.labelLarge, color = color)
      if (active) {
        Spacer(Modifier.width(10.dp))
        Text(gForce(live.magnitude), style = MaterialTheme.typography.titleLarge)
      }
    }
    MotionChart(if (active) live.history else emptyList(), settings.warningThreshold, settings.emergencyThreshold)
    Text(
      "점선을 넘는 충격이 오면 분석을 시작해요. 걷거나 휴대폰을 흔드는 정도로는 경보가 울리지 않아요.",
      style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
    )
    if (active) {
      Row(
        Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("센서 값 자세히", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
      }
      AnimatedVisibility(expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row {
            LabelValue("X (좌우)", ms2(live.x), Modifier.weight(1f))
            LabelValue("Y (위아래)", ms2(live.y), Modifier.weight(1f))
            LabelValue("Z (앞뒤)", ms2(live.z), Modifier.weight(1f))
          }
          Row {
            LabelValue("기울기", "${live.tilt.toInt()}°", Modifier.weight(1f))
            LabelValue("회전 속도", "${live.rotation.toInt()}°/s", Modifier.weight(1f))
            LabelValue("최근 최대", gForce2(live.history.maxOrNull() ?: 0f), Modifier.weight(1f))
          }
        }
      }
    }
  }
}

@Composable
private fun LocationPanel(place: Place?, hasPermission: Boolean, onAllow: () -> Unit) {
  val uri = LocalUriHandler.current
  val clipboard = LocalClipboardManager.current
  val scheme = MaterialTheme.colorScheme
  Panel {
    SectionHeader("현재 위치")
    when {
      !hasPermission -> {
        Text("위치 권한이 없어 도움 요청 문자에 위치를 넣을 수 없어요.", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onAllow, shape = MaterialTheme.shapes.medium) { Text("위치 허용") }
      }
      place == null -> Text("위치를 찾고 있어요…", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
      else -> {
        Row(verticalAlignment = Alignment.Top) {
          Icon(Icons.Outlined.LocationOn, null, tint = scheme.primary, modifier = Modifier.padding(top = 2.dp))
          Spacer(Modifier.width(8.dp))
          Column(Modifier.weight(1f)) {
            Text(place.address.ifBlank { place.coords }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text("오차 약 ${place.accuracy.toInt()}m · ${agoText(place.time)} 확인", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = { uri.openUri(place.mapUrl) }, shape = MaterialTheme.shapes.medium) {
            Icon(Icons.Outlined.Map, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("지도에서 보기")
          }
          TextButton(onClick = { clipboard.setText(AnnotatedString(place.address.ifBlank { place.coords } + "\n" + place.mapUrl)) }) {
            Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("복사")
          }
        }
      }
    }
  }
}

@Composable
private fun ReadinessPanel(state: ReadinessState) {
  var showOk by rememberSaveable { mutableStateOf(false) }
  val scheme = MaterialTheme.colorScheme
  val pending = state.checks.filter { !it.ok }
  val done = state.checks.filter { it.ok }
  Panel(modifier = Modifier.testTag("readiness")) {
    SectionHeader("준비 상태") {
      val needs = state.missingEssential
      Text(
        when {
          needs > 0 -> "필수 ${needs}개 확인 필요"
          pending.isNotEmpty() -> "권장 ${pending.size}개"
          else -> "모두 준비됐어요"
        },
        style = MaterialTheme.typography.labelLarge,
        color = if (needs > 0) scheme.error else scheme.primary,
      )
    }
    pending.forEach { CheckRow(it, onFix = { state.fix(it.id) }) }
    if (done.isNotEmpty()) {
      Row(
        Modifier.fillMaxWidth().clickable { showOk = !showOk }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("준비된 항목 ${done.size}개", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Icon(if (showOk) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = scheme.onSurfaceVariant)
      }
      AnimatedVisibility(showOk) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { done.forEach { CheckRow(it, onFix = {}) } }
      }
    }
  }
}

@Composable
private fun CheckRow(check: Check, onFix: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val (icon, tint) = when {
    check.ok -> Icons.Outlined.CheckCircle to scheme.primary
    check.essential -> Icons.Outlined.ErrorOutline to scheme.error
    else -> Icons.Outlined.Info to AppTheme.extra.warning
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(check.title, style = MaterialTheme.typography.titleSmall)
      Text(check.text, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
    if (!check.ok && check.actionLabel != null) {
      Spacer(Modifier.width(8.dp))
      OutlinedButton(onClick = onFix, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) { Text(check.actionLabel) }
    }
  }
}

@Composable
fun LogSummary(log: FallLog, modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  val kind = com.example.data.LogKind.of(log)
  Row(modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(kind.title, style = MaterialTheme.typography.titleSmall)
        if (log.isSimulated) {
          Spacer(Modifier.width(6.dp))
          Tag("테스트", scheme.onSurfaceVariant, scheme.surfaceVariant)
        }
      }
      Text(
        listOfNotNull(dateTimeText(log.timestamp), log.maxAccel.takeIf { it > 0f }?.let { "최대 ${gForce(it)}" }).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
      )
    }
    Text(log.userResponse, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
  }
}

/** 미리보기·테스트용: 전화번호를 보기 좋게 */
fun Settings.guardianLine(): String = "${guardianName.ifBlank { "보호자" }} · ${Phone.pretty(guardianPhone)}"
