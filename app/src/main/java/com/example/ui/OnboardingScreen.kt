package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.CallTarget
import com.example.data.Settings

@Composable
fun OnboardingScreen(
  initial: Settings,
  onSaveContacts: (name: String, guardianName: String, guardianPhone: String, extra: String, target: CallTarget) -> Unit,
  onFinish: (startMonitoring: Boolean) -> Unit,
  startStep: Int = 0,
) {
  var step by rememberSaveable { mutableIntStateOf(startStep) }
  var name by rememberSaveable { mutableStateOf(initial.userName) }
  var gName by rememberSaveable { mutableStateOf(initial.guardianName) }
  var gPhone by rememberSaveable { mutableStateOf(initial.guardianPhone) }
  var extra by rememberSaveable { mutableStateOf(initial.extraContact) }
  var target by rememberSaveable { mutableStateOf(initial.callTarget) }
  var showErrors by rememberSaveable { mutableStateOf(false) }
  val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onFinish(true) }

  if (step > 0) BackHandler { step-- }

  Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().testTag("onboarding")) {
    if (step > 0) {
      Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { step-- }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로") }
        LinearProgressIndicator(progress = { step / 3f }, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        Text("$step / 3", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 16.dp))
      }
    }
    Column(
      Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      when (step) {
        0 -> {
          Spacer(Modifier.height(24.dp))
          IconBadge(Icons.Filled.GppGood, MaterialTheme.colorScheme.onPrimary, MaterialTheme.colorScheme.primary, size = 72)
          Text("넘어지면\n보호자에게 알려 드려요", style = MaterialTheme.typography.headlineMedium)
          Text(
            "휴대폰 센서로 큰 충격을 감지하고, AI가 넘어진 것인지 판단해요. 괜찮다고 응답하지 않으면 보호자에게 위치를 보내요.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(8.dp))
          HowItWorks(1, "큰 충격 감지", "떨어지는 움직임, 충격, 그 뒤 움직임을 함께 봐요.")
          HowItWorks(2, "괜찮은지 확인", "경보가 울리면 '괜찮아요'를 누르거나 말로 끌 수 있어요.")
          HowItWorks(3, "보호자에게 알림", "응답이 없으면 현재 위치를 문자로 보내요.")
        }
        1 -> {
          Text("누구에게 알릴까요?", style = MaterialTheme.typography.headlineSmall)
          Text("문자에 들어갈 이름과 보호자 번호를 넣어 주세요.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
          ContactFields(name, { name = it }, gName, { gName = it }, gPhone, { gPhone = it }, extra, { extra = it }, showErrors)
        }
        2 -> {
          Text("'도움 요청'을 누르면 어디로 전화할까요?", style = MaterialTheme.typography.headlineSmall)
          Text("문자는 항상 보호자에게 가요. 여기서 고른 곳은 전화 화면에 미리 입력돼요.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
          TargetOptions(target, extra.isNotBlank()) { target = it }
        }
        else -> {
          Text("몇 가지 권한이 필요해요", style = MaterialTheme.typography.headlineSmall)
          Text("허용하지 않은 항목은 나중에 홈 화면의 '준비 상태'에서 다시 켤 수 있어요.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
          PermissionRow(Icons.Outlined.LocationOn, "위치", "도움 요청 문자에 지금 있는 곳을 넣어요.")
          PermissionRow(Icons.Outlined.Sms, "문자", "응답이 없을 때 보호자에게 자동으로 문자를 보내요.")
          PermissionRow(Icons.Outlined.Notifications, "알림", "화면이 꺼져 있어도 경보를 보여 줘요.")
          PermissionRow(Icons.Outlined.Mic, "마이크", "경보 중 '괜찮아'라고 말해서 끌 수 있어요. 녹음하거나 저장하지 않아요.")
        }
      }
    }
    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      when (step) {
        0 -> PrimaryButton("시작하기", "start_setup") { step = 1 }
        1 -> PrimaryButton("다음", "next_contacts") {
          if (contactsValid(name, gName, gPhone, extra)) { showErrors = false; step = 2 } else showErrors = true
        }
        2 -> PrimaryButton("다음", "next_target") {
          val t = if (target == CallTarget.CUSTOM && extra.isBlank()) CallTarget.FIRE else target
          onSaveContacts(name.trim(), gName.trim(), gPhone.trim(), extra.trim(), t)
          step = 3
        }
        else -> {
          PrimaryButton("허용하고 감지 시작", "grant_and_start") { permissions.launch(onboardingPermissions()) }
          TextButton(onClick = { onFinish(true) }, modifier = Modifier.fillMaxWidth()) { Text("나중에 허용하기") }
        }
      }
    }
  }
}

@Composable
private fun PrimaryButton(text: String, tag: String, onClick: () -> Unit) {
  Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).testTag(tag), shape = MaterialTheme.shapes.medium) {
    Text(text, style = MaterialTheme.typography.titleMedium)
  }
}

@Composable
private fun HowItWorks(no: Int, title: String, desc: String) {
  Row(verticalAlignment = Alignment.Top) {
    Text("$no", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(32.dp))
    Column {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, desc: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    IconBadge(icon, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, size = 44)
    Spacer(Modifier.size(14.dp))
    Column {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
