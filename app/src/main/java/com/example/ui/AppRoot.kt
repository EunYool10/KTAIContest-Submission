@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Phone
import com.example.data.Settings
import com.example.engine.AlertState
import com.example.engine.SentReport

@Composable
fun AppRoot(vm: AppViewModel) {
  val settings by vm.settings.collectAsStateWithLifecycle()
  val alert by vm.alert.collectAsStateWithLifecycle()
  val calibration by vm.calibration.collectAsStateWithLifecycle()
  val live by vm.live.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var listening by remember { mutableStateOf(false) }
  var dialedFor by rememberSaveable { mutableLongStateOf(0L) }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.ensureMonitoring() }

  // Surface 가 글자색(onBackground)을 정해 줘야 어두운 테마에서도 글자가 보입니다.
  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
   Box(Modifier.fillMaxSize()) {
    when {
      !settings.setupComplete -> OnboardingScreen(settings, vm::completeSetup, vm::finishOnboarding)
      calibration.step != CalStep.IDLE -> CalibrationScreen(calibration, live, settings, vm::calibrationNext, vm::retryImpact, vm::endCalibration)
      else -> MainTabs(vm, settings)
    }

    val a = alert
    VoiceCancelEffect(
      active = a is AlertState.Countdown && settings.voiceCancelEnabled,
      onOkay = vm::imOkay, onHelp = vm::requestHelp, onListening = { listening = it },
    )
    if (a != null) {
      AlertScreen(
        state = a, settings = settings, listening = listening,
        onOkay = vm::imOkay, onHelp = vm::requestHelp, onClose = vm::closeSent,
        onCall = { dial(context, it) }, onSmsApp = { openSmsApp(context, it) },
      )
      // 사용자가 직접 도움을 요청했으면 전화 화면을 한 번 열어 줍니다.
      if (a is AlertState.Sent && a.report.byUser && !a.report.test && a.report.callNumber.isNotBlank()) {
        LaunchedEffect(a.report.sendId) {
          if (dialedFor != a.report.sendId) {
            dialedFor = a.report.sendId
            dial(context, a.report.callNumber)
          }
        }
      }
    }
   }
  }
}

@Composable
private fun MainTabs(vm: AppViewModel, settings: Settings) {
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var openContacts by rememberSaveable { mutableStateOf(false) }
  val status by vm.status.collectAsStateWithLifecycle()
  val live by vm.live.collectAsStateWithLifecycle()
  val place by vm.place.collectAsStateWithLifecycle()
  val notice by vm.notice.collectAsStateWithLifecycle()
  val testing by vm.testing.collectAsStateWithLifecycle()
  val logs by vm.logs.collectAsStateWithLifecycle()
  val readiness = rememberReadiness(settings, vm.hasAccelerometer, vm.geminiConfigured, vm.modelName) {
    tab = 2
    openContacts = true
  }

  val tabs = listOf(
    Triple("홈", Icons.Filled.Home, Icons.Outlined.Home),
    Triple("기록", Icons.Filled.History, Icons.Outlined.History),
    Triple("설정", Icons.Filled.Settings, Icons.Outlined.Settings),
  )

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      Column {
        TopAppBar(
          title = { Text(if (tab == 0) "AI 충격 감지" else tabs[tab].first, style = MaterialTheme.typography.titleLarge) },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      }
    },
    bottomBar = {
      Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // 선택 표시는 색만 바꿉니다 (알약 모양 강조 없음).
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, modifier = Modifier.testTag("bottom_navigation")) {
          tabs.forEachIndexed { i, (label, filled, outlined) ->
            NavigationBarItem(
              selected = tab == i,
              onClick = { tab = i },
              icon = { Icon(if (tab == i) filled else outlined, contentDescription = null) },
              label = { Text(label, style = MaterialTheme.typography.labelMedium) },
              colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent,
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
              ),
            )
          }
        }
      }
    },
  ) { padding ->
    Box(Modifier.padding(padding)) {
      when (tab) {
        0 -> HomeScreen(
          settings = settings, status = status, live = live, place = place,
          hasLocationPermission = readiness.checks.firstOrNull { it.id == CheckId.LOCATION }?.ok == true,
          notice = notice, testing = testing, readiness = readiness, recent = logs,
          onMonitoring = vm::setMonitoring, onHelp = vm::requestHelp, onTest = vm::startTest,
          onDismissNotice = vm::dismissNotice, onOpenHistory = { tab = 1 },
        )
        1 -> HistoryScreen(logs, vm::deleteLog, vm::clearLogs)
        else -> SettingsScreen(
          settings = settings, geminiConfigured = vm.geminiConfigured, modelName = vm.modelName,
          openContacts = openContacts, onContactsShown = { openContacts = false },
          onUpdate = vm::update, onSensitivity = vm::setSensitivity, onThresholds = vm::setThresholds,
          onCalibrate = vm::startCalibration, onReset = vm::resetAll,
          hasBuiltInKey = vm.hasBuiltInKey, onSaveKey = vm::saveGeminiKey, onCheckKey = vm::checkGeminiKey,
        )
      }
    }
  }
}

fun dial(context: Context, number: String) {
  tryStart(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Phone.digits(number))))
}

fun openSmsApp(context: Context, report: SentReport) {
  val to = report.recipients.joinToString(";") { Phone.digits(it.number) }
  tryStart(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$to")).putExtra("sms_body", report.message))
}
