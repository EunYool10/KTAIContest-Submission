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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.data.FallLog
import com.example.data.LogKind
import com.example.data.Phone
import com.example.data.Settings
import com.example.engine.Live
import com.example.engine.MonitorStatus
import com.example.engine.Notice
import com.example.engine.Place
import com.example.engine.UpdateInfo
import com.example.ui.theme.AlarmRed
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.launch

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
  update: UpdateInfo? = null,
  onUpdate: (UpdateInfo) -> Unit = {},
) {
  var confirmHelp by rememberSaveable { mutableStateOf(false) }
  var confirmOff by rememberSaveable { mutableStateOf(false) }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  // 항목 순서: (업데이트), 상태, (안내), 움직임, 위치, 준비 상태, 최근 기록
  val readinessIndex = (if (update != null) 1 else 0) + 1 + (if (notice != null) 1 else 0) + 2

  LazyColumn(modifier.fillMaxSize().testTag("home"), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
    if (update != null) {
      item(key = "update") { Column { UpdateSection(update) { onUpdate(update) }; SectionGap() } }
    }
    item(key = "status") {
      StatusSection(
        settings, status, readiness.missingEssential, testing,
        onToggle = { on -> if (on) onMonitoring(true) else confirmOff = true },
        onShowMissing = { scope.launch { listState.animateScrollToItem(readinessIndex) } },
        onHelp = { confirmHelp = true },
        onTest = onTest,
      )
    }
    if (notice != null) {
      item(key = "notice") { Column { SectionGap(); NoticeSection(notice, onDismissNotice) } }
    }
    item(key = "motion") { Column { SectionGap(); MotionSection(live, settings, status, testing) } }
    item(key = "location") { Column { SectionGap(); LocationSection(place, hasLocationPermission, onAllow = { readiness.fix(CheckId.LOCATION) }) } }
    item(key = "readiness") { Column { SectionGap(); ReadinessSection(readiness) } }
    if (recent.isNotEmpty()) {
      item(key = "recent") {
        Column {
          SectionGap()
          Section("최근 기록", trailing = { AppTextButton("전체 보기", onOpenHistory) }) {
            recent.take(3).forEachIndexed { i, log ->
              if (i > 0) InsetDivider()
              LogLine(log)
            }
          }
        }
      }
    }
  }

  if (confirmHelp) {
    AlertDialog(
      onDismissRequest = { confirmHelp = false },
      title = { Text("도움을 요청할까요?") },
      text = { Text("${settings.smsTargetPhrase} 현재 위치를 문자로 보내고, ${settings.callLabel}에 바로 걸 수 있게 전화 화면을 열어요.") },
      confirmButton = {
        AppButton(
          "요청 보내기", { confirmHelp = false; onHelp() },
          container = MaterialTheme.colorScheme.error, content = MaterialTheme.colorScheme.onError, height = 44.dp,
        )
      },
      dismissButton = { AppTextButton("취소", { confirmHelp = false }, color = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
  }
  if (confirmOff) {
    AlertDialog(
      onDismissRequest = { confirmOff = false },
      title = { Text("감지를 끌까요?") },
      text = { Text("끄면 넘어지거나 큰 충격이 있어도 보호자에게 알리지 않아요.") },
      confirmButton = { AppTextButton("끄기", { confirmOff = false; onMonitoring(false) }, color = MaterialTheme.colorScheme.error) },
      dismissButton = { AppTextButton("계속 감지", { confirmOff = false }) },
    )
  }
}

@Composable
private fun StatusSection(
  settings: Settings,
  status: MonitorStatus,
  missing: Int,
  testing: Boolean,
  onToggle: (Boolean) -> Unit,
  onShowMissing: () -> Unit,
  onHelp: () -> Unit,
  onTest: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val (title, color, desc) = when (status) {
    MonitorStatus.ON -> Triple(
      "감지 중", scheme.primary,
      "화면이 꺼져 있어도 감지해요. 큰 충격 뒤 ${settings.countdownSeconds}초 안에 응답이 없으면 ${settings.smsTargetPhrase} 위치를 문자로 보내요.",
    )
    MonitorStatus.OFF -> Triple("감지 꺼짐", scheme.onSurfaceVariant, "켜면 넘어지거나 큰 충격이 있을 때 보호자에게 알려요.")
    MonitorStatus.NO_SENSOR -> Triple("감지할 수 없음", scheme.error, "이 기기에는 가속도 센서가 없어요. 경보 테스트와 도움 요청은 쓸 수 있어요.")
  }
  Section(modifier = Modifier.testTag(if (status == MonitorStatus.ON) "status_on" else "status_off")) {
    Row(Modifier.fillMaxWidth().sidePadding(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Dot(color, 10.dp)
          Spacer(Modifier.width(8.dp))
          Text(title, style = MaterialTheme.typography.headlineSmall, color = color)
        }
        Spacer(Modifier.height(4.dp))
        Text(desc, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
      }
      Spacer(Modifier.width(16.dp))
      Switch(
        checked = status != MonitorStatus.OFF,
        onCheckedChange = onToggle,
        modifier = Modifier.testTag("monitor_switch").semantics { contentDescription = "충격 감지" },
      )
    }
    if (missing > 0) {
      Spacer(Modifier.height(12.dp))
      Row(
        Modifier.fillMaxWidth().clickable(onClick = onShowMissing).sidePadding().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Outlined.WarningAmber, null, tint = scheme.error, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("필수 설정 ${missing}개가 빠져 있어요", style = MaterialTheme.typography.labelLarge, color = scheme.error, modifier = Modifier.weight(1f))
        Text("확인", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
      }
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth().sidePadding(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      AppButton(
        "도움 요청", onHelp, Modifier.weight(1f).testTag("help_button"), icon = Icons.Outlined.Call,
        container = AlarmRed, content = Color.White, height = 56.dp,
      )
      AppOutlinedButton(
        if (testing) "테스트 중…" else "경보 테스트", onTest, Modifier.weight(1f).testTag("test_button"),
        enabled = !testing, height = 56.dp,
      )
    }
  }
}

@Composable
private fun NoticeSection(notice: Notice, onDismiss: () -> Unit) {
  val extra = AppTheme.extra
  Section(modifier = Modifier.testTag("notice")) {
    Row(Modifier.fillMaxWidth().sidePadding(), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Outlined.WarningAmber, null, tint = extra.warning, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(8.dp))
      Text(notice.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
      Caption(agoText(notice.time))
    }
    Spacer(Modifier.height(6.dp))
    Text(notice.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.sidePadding())
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
      AppTextButton("확인했어요", onDismiss)
    }
  }
}

@Composable
private fun MotionSection(live: Live, settings: Settings, status: MonitorStatus, testing: Boolean) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  val scheme = MaterialTheme.colorScheme
  val active = status == MonitorStatus.ON || testing
  val recentPeak = live.history.takeLast(10).maxOrNull() ?: 0f
  val (label, color) = when {
    !active -> "꺼짐" to scheme.onSurfaceVariant
    recentPeak >= settings.emergencyThreshold -> "강한 충격" to scheme.error
    recentPeak >= settings.warningThreshold -> "흔들림" to AppTheme.extra.warning
    else -> "안정" to scheme.onSurfaceVariant
  }
  Section(
    if (testing) "움직임 (테스트 데이터)" else "움직임",
    trailing = { Text(if (active) "${gForce(live.magnitude)} · $label" else label, style = MaterialTheme.typography.labelLarge, color = color) },
  ) {
    MotionChart(if (active) live.history else emptyList(), settings.warningThreshold, settings.emergencyThreshold, Modifier.sidePadding())
    Spacer(Modifier.height(8.dp))
    Caption("점선(주의·긴급)을 넘는 충격이 오면 분석을 시작해요. 걷거나 휴대폰을 흔드는 정도로는 경보가 울리지 않아요.", Modifier.sidePadding())
    if (active) {
      Spacer(Modifier.height(8.dp))
      InsetDivider()
      Row(
        Modifier.fillMaxWidth().clickable { expanded = !expanded }.sidePadding().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("센서 값 자세히", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = scheme.outline)
      }
      AnimatedVisibility(expanded) {
        Column(Modifier.sidePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
private fun LocationSection(place: Place?, hasPermission: Boolean, onAllow: () -> Unit) {
  val uri = LocalUriHandler.current
  val clipboard = LocalClipboardManager.current
  val scheme = MaterialTheme.colorScheme
  Section("현재 위치") {
    when {
      !hasPermission -> {
        Text("위치 권한이 없어 도움 요청 문자에 위치를 넣을 수 없어요.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.sidePadding())
        Spacer(Modifier.height(12.dp))
        AppButton("위치 허용", onAllow, Modifier.sidePadding(), height = 44.dp)
      }
      place == null -> Text("위치를 찾고 있어요…", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.sidePadding())
      else -> {
        Text(place.address.ifBlank { place.coords }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.sidePadding())
        Caption("오차 약 ${place.accuracy.toInt()}m · ${agoText(place.time)} 확인", Modifier.sidePadding())
        Spacer(Modifier.height(6.dp))
        Row(Modifier.padding(horizontal = 12.dp)) {
          AppTextButton("지도에서 보기", { uri.openUri(place.mapUrl) }, icon = Icons.Outlined.Map)
          AppTextButton(
            "복사", { clipboard.setText(AnnotatedString(place.address.ifBlank { place.coords } + "\n" + place.mapUrl)) },
            icon = Icons.Outlined.ContentCopy,
          )
        }
      }
    }
  }
}

@Composable
private fun ReadinessSection(state: ReadinessState) {
  var showOk by rememberSaveable { mutableStateOf(false) }
  val scheme = MaterialTheme.colorScheme
  val pending = state.checks.filter { !it.ok }
  val done = state.checks.filter { it.ok }
  Section(
    "준비 상태", Modifier.testTag("readiness"),
    trailing = {
      Text(
        when {
          state.missingEssential > 0 -> "필수 ${state.missingEssential}개 필요"
          pending.isNotEmpty() -> "권장 ${pending.size}개"
          else -> "모두 준비됨"
        },
        style = MaterialTheme.typography.labelLarge,
        color = if (state.missingEssential > 0) scheme.error else scheme.onSurfaceVariant,
      )
    },
  ) {
    pending.forEachIndexed { i, c ->
      if (i > 0) InsetDivider()
      CheckLine(c) { state.fix(c.id) }
    }
    if (done.isNotEmpty()) {
      if (pending.isNotEmpty()) InsetDivider()
      RowItem(
        "준비된 항목 ${done.size}개", value = if (showOk) "접기" else "보기",
        onClick = { showOk = !showOk }, chevron = false,
        trailing = { Icon(if (showOk) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = scheme.outline) },
      )
      AnimatedVisibility(showOk) {
        Column {
          done.forEach { c ->
            InsetDivider()
            CheckLine(c) {}
          }
        }
      }
    }
  }
}

@Composable
private fun CheckLine(check: Check, onFix: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val actionable = !check.ok && check.actionLabel != null
  val (value, color) = when {
    check.ok -> "준비됨" to scheme.onSurfaceVariant
    check.essential -> "필요" to scheme.error
    else -> "권장" to AppTheme.extra.warning
  }
  RowItem(
    check.title, subtitle = check.text, value = value, valueColor = color,
    onClick = if (actionable) onFix else null,
  )
}

/** 기록 한 줄 (홈의 '최근 기록'과 같은 모양) */
@Composable
fun LogLine(log: FallLog, modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  val kind = LogKind.of(log)
  Row(modifier.fillMaxWidth().sidePadding().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
    Dot(logColor(log))
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(kind.title, style = MaterialTheme.typography.bodyLarge)
        if (log.isSimulated) {
          Spacer(Modifier.width(6.dp))
          Tag("테스트", scheme.onSurfaceVariant, scheme.surfaceVariant)
        }
      }
      Caption(listOfNotNull(dateTimeText(log.timestamp), log.maxAccel.takeIf { it > 0f }?.let { "최대 ${gForce(it)}" }).joinToString(" · "))
    }
    Spacer(Modifier.width(8.dp))
    Caption(log.userResponse.takeIf { it != kind.title }.orEmpty())
  }
}

/** 기록 종류별 점 색: 문자를 보냈으면 빨강, 주의는 주황, 테스트는 회색, 나머지는 파랑 */
@Composable
fun logColor(log: FallLog) = when {
  log.isSimulated -> MaterialTheme.colorScheme.outline
  LogKind.of(log) == LogKind.WARNING -> AppTheme.extra.warning
  LogKind.of(log) == LogKind.SOS || log.userResponse.contains("자동 문자") || log.userResponse.startsWith("도움 요청") -> MaterialTheme.colorScheme.error
  else -> MaterialTheme.colorScheme.primary
}

/** 미리보기·테스트용: 전화번호를 보기 좋게 */
fun Settings.guardianLine(): String = "${guardianName.ifBlank { "보호자" }} · ${Phone.pretty(guardianPhone)}"
