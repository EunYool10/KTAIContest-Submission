@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.CallTarget
import com.example.data.Phone
import com.example.data.Sensitivity
import com.example.data.Settings
import com.example.detect.GRAVITY
import com.example.ui.theme.AppTheme
import java.util.Locale

@Composable
fun SettingsScreen(
  settings: Settings,
  geminiConfigured: Boolean,
  modelName: String,
  openContacts: Boolean,
  onContactsShown: () -> Unit,
  onUpdate: ((Settings) -> Settings) -> Unit,
  onSensitivity: (Sensitivity) -> Unit,
  onThresholds: (Float, Float) -> Unit,
  onCalibrate: () -> Unit,
  onReset: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var contactDialog by rememberSaveable { mutableStateOf(false) }
  var targetDialog by rememberSaveable { mutableStateOf(false) }
  var resetDialog by rememberSaveable { mutableStateOf(false) }
  var showDetail by rememberSaveable { mutableStateOf(settings.sensitivity == Sensitivity.CUSTOM) }

  LaunchedEffect(openContacts) {
    if (openContacts) {
      contactDialog = true
      onContactsShown()
    }
  }

  LazyColumn(
    modifier.fillMaxSize().testTag("settings"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Panel(padding = PaddingValues(vertical = 8.dp)) {
        GroupTitle("사람과 연락처")
        NavRow("내 이름", settings.userName.ifBlank { "입력 안 함" }) { contactDialog = true }
        NavRow("보호자", if (settings.guardianPhone.isBlank()) "입력 필요" else settings.guardianLine(), warn = !Phone.isValid(settings.guardianPhone)) { contactDialog = true }
        NavRow("추가 연락처", settings.extraContact.ifBlank { "없음" }.let { if (it == "없음") it else Phone.pretty(it) }) { contactDialog = true }
        NavRow("전화로 연결할 곳", "${settings.callTarget.title}${if (settings.callTarget == CallTarget.GUARDIAN || settings.callTarget == CallTarget.CUSTOM) " · " + Phone.pretty(settings.callNumber) else ""}") { targetDialog = true }
        Text(
          "응답이 없으면 보호자와 추가 연락처로 위치 문자를 보내요. '도움 요청'을 누르면 위 번호로 전화 화면도 열어요.",
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        )
      }
    }

    item {
      Panel {
        SectionHeader("감지 민감도")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
          Sensitivity.presets.forEachIndexed { i, level ->
            SegmentedButton(
              selected = settings.sensitivity == level,
              onClick = { onSensitivity(level) },
              shape = SegmentedButtonDefaults.itemShape(i, Sensitivity.presets.size),
              colors = segmentColors(),
            ) { Text(level.title) }
          }
        }
        Text(
          sensitivityText(settings),
          style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth().clickable { showDetail = !showDetail }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Text("세부 기준 직접 조정", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
          Text(if (showDetail) "접기" else "펼치기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (showDetail) ThresholdSliders(settings, onThresholds)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().clickable(onClick = onCalibrate).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text("내 휴대폰에 맞추기", style = MaterialTheme.typography.titleSmall)
            Text("평소 움직임과 가벼운 충격을 재서 기준을 추천해요. (약 1분)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
        }
      }
    }

    item {
      Panel {
        SectionHeader("경보")
        Text("괜찮은지 묻는 시간", style = MaterialTheme.typography.titleSmall)
        val options = listOf(15, 30, 60)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
          options.forEachIndexed { i, sec ->
            SegmentedButton(
              selected = settings.countdownSeconds == sec,
              onClick = { onUpdate { it.copy(countdownSeconds = sec) } },
              shape = SegmentedButtonDefaults.itemShape(i, options.size),
              colors = segmentColors(),
            ) { Text("${sec}초") }
          }
        }
        SwitchRow("경보음과 음성 안내", "알람 음량으로 울려요. 알람 음량이 0이면 들리지 않아요.", settings.soundEnabled) { v -> onUpdate { it.copy(soundEnabled = v) } }
        SwitchRow("진동", null, settings.vibrationEnabled) { v -> onUpdate { it.copy(vibrationEnabled = v) } }
        SwitchRow("말로 경보 끄기", "경보 중에 '괜찮아', '취소'라고 말하면 꺼져요. 마이크 권한이 필요해요.", settings.voiceCancelEnabled) { v -> onUpdate { it.copy(voiceCancelEnabled = v) } }
      }
    }

    item {
      Panel {
        SectionHeader("AI 판단")
        Text(
          if (geminiConfigured) "큰 충격이 오면 Gemini($modelName)가 센서 데이터를 보고 넘어진 건지 판단해요. 인터넷이 안 되거나 9초 안에 답이 없으면 기기 안 규칙으로 바로 판단해요."
          else "API 키가 설정되지 않아 기기 안 규칙으로만 판단해요. 프로젝트의 .env 파일에 GEMINI_API_KEY 를 넣고 다시 빌드하면 AI 판단을 써요.",
          style = MaterialTheme.typography.bodyMedium,
        )
        Text(
          "AI가 '정상'이라고 보면 경보 없이 안내만 해요. 애매하면 항상 사람에게 먼저 확인해요.",
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      Panel(padding = PaddingValues(vertical = 8.dp)) {
        GroupTitle("기타")
        NavRow("처음부터 다시 설정", "연락처와 설정을 지워요. 기록은 남아요.", danger = true) { resetDialog = true }
        NavRow("앱 버전", BuildConfig.VERSION_NAME, chevron = false) {}
      }
    }
    item { Spacer(Modifier.height(8.dp)) }
  }

  if (contactDialog) {
    ContactDialog(settings, onDismiss = { contactDialog = false }) { name, gName, gPhone, extra ->
      onUpdate { it.copy(userName = name, guardianName = gName, guardianPhone = gPhone, extraContact = extra) }
      contactDialog = false
    }
  }
  if (targetDialog) {
    TargetDialog(settings, onDismiss = { targetDialog = false }) { t ->
      onUpdate { it.copy(callTarget = t) }
      targetDialog = false
    }
  }
  if (resetDialog) {
    AlertDialog(
      onDismissRequest = { resetDialog = false },
      title = { Text("처음부터 다시 설정할까요?") },
      text = { Text("감지를 끄고 이름·연락처·민감도 설정을 지운 뒤 처음 화면으로 돌아가요. 기록은 지우지 않아요.") },
      confirmButton = { TextButton(onClick = { resetDialog = false; onReset() }) { Text("다시 설정", color = MaterialTheme.colorScheme.error) } },
      dismissButton = { TextButton(onClick = { resetDialog = false }) { Text("취소") } },
    )
  }
}

private fun g1(v: Float) = String.format(Locale.US, "%.1fG", v / GRAVITY)

private fun sensitivityText(s: Settings): String {
  val head = when (s.sensitivity) {
    Sensitivity.LOW -> "잘 넘어지지 않거나 활동이 많은 분께 맞아요."
    Sensitivity.MEDIUM -> "대부분의 분께 맞는 기본값이에요."
    Sensitivity.HIGH -> "작은 충격에도 확인해요. 거동이 불편한 분께 맞아요."
    Sensitivity.CUSTOM -> "직접 정한 기준을 쓰고 있어요."
  }
  return "$head ${g1(s.emergencyThreshold)} 이상이면 괜찮은지 묻고, ${g1(s.warningThreshold)} 이상이면서 넘어지는 모양이면 안내만 해요."
}

@Composable
private fun ThresholdSliders(settings: Settings, onThresholds: (Float, Float) -> Unit) {
  var warning by remember(settings.warningThreshold) { mutableFloatStateOf(settings.warningThreshold) }
  var emergency by remember(settings.emergencyThreshold) { mutableFloatStateOf(settings.emergencyThreshold) }
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row {
      Text("긴급 (괜찮은지 묻기)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
      Text("${g1(emergency)} · ${ms2(emergency)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
    }
    Slider(
      value = emergency, onValueChange = { emergency = it; if (warning > it - 2f) warning = it - 2f },
      valueRange = 15f..50f, onValueChangeFinished = { onThresholds(warning, emergency) },
      colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.error, activeTrackColor = MaterialTheme.colorScheme.error),
    )
    Row {
      Text("주의 (안내만)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
      Text("${g1(warning)} · ${ms2(warning)}", style = MaterialTheme.typography.labelLarge, color = AppTheme.extra.warning)
    }
    Slider(
      value = warning, onValueChange = { warning = it.coerceAtMost(emergency - 2f) },
      valueRange = 11f..45f, onValueChangeFinished = { onThresholds(warning, emergency) },
      colors = SliderDefaults.colors(thumbColor = AppTheme.extra.warning, activeTrackColor = AppTheme.extra.warning),
    )
    Text("참고: 가만히 있으면 1.0G, 걸을 때 1.2~1.8G, 넘어지면 보통 2.5G 이상이에요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun GroupTitle(text: String) {
  Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
}

@Composable
private fun NavRow(title: String, value: String, warn: Boolean = false, danger: Boolean = false, chevron: Boolean = true, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  Row(
    Modifier.fillMaxWidth().clickable(enabled = chevron, onClick = onClick).heightIn(min = 60.dp).padding(horizontal = 18.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = if (danger) scheme.error else scheme.onSurface)
      Text(value, style = MaterialTheme.typography.bodyMedium, color = if (warn) scheme.error else scheme.onSurfaceVariant)
    }
    if (chevron) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant)
  }
}

@Composable
private fun SwitchRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
    Modifier.fillMaxWidth().selectable(selected = checked, role = Role.Switch, onClick = { onChange(!checked) }).padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      if (desc != null) Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.width(12.dp))
    Switch(checked = checked, onCheckedChange = null)
  }
}

/** 이름·보호자·추가 연락처 입력 칸. 처음 설정과 설정 화면이 함께 씁니다. */
@Composable
fun ContactFields(
  name: String, onName: (String) -> Unit,
  guardianName: String, onGuardianName: (String) -> Unit,
  guardianPhone: String, onGuardianPhone: (String) -> Unit,
  extra: String, onExtra: (String) -> Unit,
  showErrors: Boolean,
) {
  val focus = LocalFocusManager.current
  val next = KeyboardOptions(imeAction = ImeAction.Next)
  val phoneNext = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
  val moveNext = KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) })
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    OutlinedTextField(
      name, onName, label = { Text("내 이름") }, placeholder = { Text("예: 홍길동") }, singleLine = true,
      isError = showErrors && name.isBlank(), supportingText = if (showErrors && name.isBlank()) ({ Text("문자에 들어갈 이름이에요.") }) else null,
      keyboardOptions = next, keyboardActions = moveNext, modifier = Modifier.fillMaxWidth().testTag("name_input"),
    )
    OutlinedTextField(
      guardianName, onGuardianName, label = { Text("보호자 이름") }, placeholder = { Text("예: 김철수 (아들)") }, singleLine = true,
      isError = showErrors && guardianName.isBlank(),
      keyboardOptions = next, keyboardActions = moveNext, modifier = Modifier.fillMaxWidth().testTag("guardian_name_input"),
    )
    OutlinedTextField(
      guardianPhone, onGuardianPhone, label = { Text("보호자 휴대폰 번호") }, placeholder = { Text("예: 010-1234-5678") }, singleLine = true,
      isError = showErrors && !Phone.isValid(guardianPhone),
      supportingText = { Text(if (showErrors && !Phone.isValid(guardianPhone)) "번호를 다시 확인해 주세요." else "응답이 없을 때 이 번호로 위치 문자를 보내요.") },
      keyboardOptions = phoneNext, keyboardActions = moveNext, modifier = Modifier.fillMaxWidth().testTag("guardian_phone_input"),
    )
    OutlinedTextField(
      extra, onExtra, label = { Text("추가 연락처 (선택)") }, placeholder = { Text("예: 다른 가족, 요양보호사") }, singleLine = true,
      isError = showErrors && extra.isNotBlank() && !Phone.isValid(extra),
      supportingText = { Text("넣으면 이 번호에도 같은 문자를 보내요.") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
      modifier = Modifier.fillMaxWidth().testTag("extra_input"),
    )
  }
}

fun contactsValid(name: String, guardianName: String, guardianPhone: String, extra: String) =
  name.isNotBlank() && guardianName.isNotBlank() && Phone.isValid(guardianPhone) && (extra.isBlank() || Phone.isValid(extra))

@Composable
private fun ContactDialog(settings: Settings, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
  var name by rememberSaveable { mutableStateOf(settings.userName) }
  var gName by rememberSaveable { mutableStateOf(settings.guardianName) }
  var gPhone by rememberSaveable { mutableStateOf(settings.guardianPhone) }
  var extra by rememberSaveable { mutableStateOf(settings.extraContact) }
  var showErrors by rememberSaveable { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("사람과 연락처") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        ContactFields(name, { name = it }, gName, { gName = it }, gPhone, { gPhone = it }, extra, { extra = it }, showErrors)
      }
    },
    confirmButton = {
      TextButton(onClick = {
        if (contactsValid(name, gName, gPhone, extra)) onSave(name.trim(), gName.trim(), gPhone.trim(), extra.trim()) else showErrors = true
      }) { Text("저장") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
  )
}

/** 전화 연결 대상 선택 목록 */
@Composable
fun TargetOptions(selected: CallTarget, hasExtra: Boolean, onSelect: (CallTarget) -> Unit) {
  Column {
    CallTarget.entries.forEach { t ->
      val desc = when (t) {
        CallTarget.FIRE -> "다쳤을 때 구급차를 부를 수 있어요."
        CallTarget.POLICE -> "위험한 상황일 때"
        CallTarget.GUARDIAN -> "보호자에게 바로 전화해요."
        CallTarget.CUSTOM -> if (hasExtra) "추가 연락처로 전화해요." else "추가 연락처를 먼저 넣어 주세요."
      }
      val enabled = t != CallTarget.CUSTOM || hasExtra
      Row(
        Modifier.fillMaxWidth().selectable(selected = selected == t, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(t) })
          .heightIn(min = 56.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RadioButton(selected = selected == t, onClick = null, enabled = enabled)
        Spacer(Modifier.width(10.dp))
        Column {
          Text(t.title, style = MaterialTheme.typography.titleSmall, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
          Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

@Composable
private fun TargetDialog(settings: Settings, onDismiss: () -> Unit, onSelect: (CallTarget) -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("전화로 연결할 곳") },
    text = { TargetOptions(settings.callTarget, settings.extraContact.isNotBlank(), onSelect) },
    confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
  )
}

@Composable
private fun segmentColors() = SegmentedButtonDefaults.colors(
  activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
  activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)
