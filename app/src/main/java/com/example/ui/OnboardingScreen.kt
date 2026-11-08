package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
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
  val scheme = MaterialTheme.colorScheme

  if (step > 0) BackHandler { step-- }

  Column(Modifier.fillMaxSize().background(scheme.surface).systemBarsPadding().imePadding().testTag("onboarding")) {
    if (step > 0) {
      Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { step-- }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로") }
        Text(
          listOf("", "연락처", "전화 연결", "권한")[step], style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
        )
        Text("$step / 3", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp))
      }
      LinearProgressIndicator(
        progress = { step / 3f }, strokeCap = StrokeCap.Butt, gapSize = 0.dp, drawStopIndicator = {},
        trackColor = scheme.outlineVariant, modifier = Modifier.fillMaxWidth().height(2.dp),
      )
    }
    Column(
      Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      when (step) {
        0 -> {
          Spacer(Modifier.height(20.dp))
          AppIcon(64)
          Text("AI 충격 감지 경보", style = MaterialTheme.typography.headlineMedium)
          Text(
            "휴대폰을 지니고 있다가 넘어지거나 큰 충격을 받으면, 먼저 괜찮은지 묻고 응답이 없을 때 보호자에게 지금 있는 곳을 문자로 보내요.",
            style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(4.dp))
          HorizontalDivider(color = scheme.outlineVariant)
          HowItWorks(1, "큰 충격 감지", "떨어지는 움직임, 충격, 그 뒤 움직임을 함께 봐요. 화면이 꺼져 있어도 감지해요.")
          HowItWorks(2, "괜찮은지 확인", "경보가 울리면 '괜찮아요'를 누르거나 \"괜찮아\"라고 말해서 끌 수 있어요.")
          HowItWorks(3, "보호자에게 알림", "응답이 없으면 현재 위치를 문자로 보내요.")
        }
        1 -> {
          Text("누구에게 알릴까요?", style = MaterialTheme.typography.headlineSmall)
          Text("문자에 들어갈 이름과 보호자 번호를 넣어 주세요.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
          ContactFields(name, { name = it }, gName, { gName = it }, gPhone, { gPhone = it }, extra, { extra = it }, showErrors)
        }
        2 -> {
          Text("'도움 요청'을 누르면 어디로 전화할까요?", style = MaterialTheme.typography.headlineSmall)
          Text("문자는 항상 보호자에게 가요. 여기서 고른 곳은 전화 화면에 미리 입력돼요.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
          TargetOptions(target, extra.isNotBlank()) { target = it }
        }
        else -> {
          Text("이 권한이 필요해요", style = MaterialTheme.typography.headlineSmall)
          Text("허용하지 않은 항목은 나중에 홈 화면의 '준비 상태'에서 다시 켤 수 있어요.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
          Column {
            PermissionLine("위치", "도움 요청 문자에 지금 있는 곳을 넣어요.")
            HorizontalDivider(color = scheme.outlineVariant)
            PermissionLine("문자", "응답이 없을 때 보호자에게 자동으로 문자를 보내요.")
            HorizontalDivider(color = scheme.outlineVariant)
            PermissionLine("알림", "화면이 꺼져 있어도 경보를 보여 줘요.")
            HorizontalDivider(color = scheme.outlineVariant)
            PermissionLine("마이크 (선택)", "경보 중 \"괜찮아\"라고 말해서 끌 수 있어요. 녹음하거나 저장하지 않아요.")
          }
        }
      }
    }
    HorizontalDivider(color = scheme.outlineVariant)
    Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      when (step) {
        0 -> AppButton("시작하기", { step = 1 }, Modifier.fillMaxWidth().testTag("start_setup"), height = 56.dp)
        1 -> AppButton("다음", {
          if (contactsValid(name, gName, gPhone, extra)) { showErrors = false; step = 2 } else showErrors = true
        }, Modifier.fillMaxWidth().testTag("next_contacts"), height = 56.dp)
        2 -> AppButton("다음", {
          val t = if (target == CallTarget.CUSTOM && extra.isBlank()) CallTarget.FIRE else target
          onSaveContacts(name.trim(), gName.trim(), gPhone.trim(), extra.trim(), t)
          step = 3
        }, Modifier.fillMaxWidth().testTag("next_target"), height = 56.dp)
        else -> {
          AppButton("허용하고 감지 시작", { permissions.launch(onboardingPermissions()) }, Modifier.fillMaxWidth().testTag("grant_and_start"), height = 56.dp)
          Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AppTextButton("나중에 허용하기", { onFinish(true) }, color = scheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

/** 실제 앱 아이콘 (원 안에 아이콘을 넣는 장식 대신) */
@Composable
fun AppIcon(size: Int) {
  Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.22f).dp))) {
    Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
  }
}

@Composable
private fun HowItWorks(no: Int, title: String, desc: String) {
  Row(verticalAlignment = Alignment.Top) {
    Text("$no", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
    Column {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun PermissionLine(title: String, desc: String) {
  Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    Caption(desc)
  }
}
