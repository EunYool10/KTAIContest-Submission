package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as SysSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.data.Phone
import com.example.data.Settings
import com.example.service.Notifications

enum class CheckId { SENSOR, CONTACT, NOTIFY, LOCATION, SMS, FULLSCREEN, BATTERY, MIC }

data class Check(
  val id: CheckId,
  val title: String,
  val okText: String,
  val needText: String,
  val ok: Boolean,
  /** 꼭 필요한 항목인지 (false 면 있으면 좋은 항목) */
  val essential: Boolean,
  val actionLabel: String? = null,
) {
  val text: String get() = if (ok) okText else needText
}

/** 안드로이드 15부터 설치 파일로 설치한 앱은 문자 권한이 '제한된 설정'으로 막혀 있습니다. */
val smsMayBeRestricted: Boolean get() = Build.VERSION.SDK_INT >= 35

fun Context.granted(permission: String) =
  ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.findActivity(): Activity? {
  var c: Context? = this
  while (c is ContextWrapper) {
    if (c is Activity) return c
    c = c.baseContext
  }
  return null
}

fun buildChecks(context: Context, settings: Settings, hasAccelerometer: Boolean): List<Check> {
  val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
  val batteryOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || pm?.isIgnoringBatteryOptimizations(context.packageName) == true
  val hasPhone = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
  val guardianOk = Phone.isValid(settings.guardianPhone)
  return listOf(
    Check(CheckId.SENSOR, "움직임 센서", "가속도 센서를 쓸 수 있어요.", "이 기기에는 가속도 센서가 없어 감지할 수 없어요.", hasAccelerometer, true),
    Check(CheckId.CONTACT, "보호자 연락처", "${settings.guardianName.ifBlank { "보호자" }} · ${Phone.pretty(settings.guardianPhone)}",
      "문자를 받을 보호자 번호가 없어요.", guardianOk, true, "입력"),
    Check(CheckId.NOTIFY, "알림", "경보와 감지 상태를 알림으로 보여 줘요.", "알림이 꺼져 있으면 화면이 꺼졌을 때 경보를 못 볼 수 있어요.",
      Notifications.canPost(context), true, "허용"),
    Check(CheckId.LOCATION, "위치", "도움 요청 문자에 현재 위치를 넣어요.", "위치 권한이 없으면 문자에 위치를 넣을 수 없어요.",
      context.granted(Manifest.permission.ACCESS_FINE_LOCATION) || context.granted(Manifest.permission.ACCESS_COARSE_LOCATION), true, "허용"),
    Check(
      CheckId.SMS, "문자 자동 전송",
      if (hasPhone) "응답이 없으면 보호자에게 바로 문자를 보내요." else "이 기기는 문자를 보낼 수 없어요.",
      if (smsMayBeRestricted) "권한이 없으면 문자를 자동으로 보내지 못해요. 안드로이드 15부터는 '제한된 설정 허용'을 먼저 켜야 해요."
      else "권한이 없으면 문자를 자동으로 보내지 못해요.",
      !hasPhone || context.granted(Manifest.permission.SEND_SMS), true, "허용",
    ),
    Check(CheckId.FULLSCREEN, "잠금 화면 경보", "화면이 잠겨 있어도 경보 화면을 띄워요.", "허용하면 잠금 화면 위로 경보 화면이 바로 떠요.",
      Notifications.canFullScreen(context), false, "설정"),
    Check(CheckId.BATTERY, "배터리 최적화 제외", "절전 중에도 감지가 멈추지 않아요.", "절전 모드에서 감지가 멈출 수 있어요.",
      batteryOk, false, "설정"),
    Check(CheckId.MIC, "음성으로 취소", "경보 중 '괜찮아'라고 말하면 취소돼요.", "마이크를 허용하면 말로 경보를 끌 수 있어요.",
      !settings.voiceCancelEnabled || context.granted(Manifest.permission.RECORD_AUDIO), false, "허용"),
  )
}

/** 화면에 돌아올 때마다 다시 계산되는 준비 상태 + 고치기 동작 */
class ReadinessState(
  val checks: List<Check>,
  val smsGuide: Boolean = false,
  val closeSmsGuide: () -> Unit = {},
  val fix: (CheckId) -> Unit,
) {
  val missingEssential get() = checks.count { it.essential && !it.ok }
}

@SuppressLint("BatteryLife")
@Composable
fun rememberReadiness(
  settings: Settings,
  hasAccelerometer: Boolean,
  onEditContacts: () -> Unit,
): ReadinessState {
  val context = LocalContext.current
  var tick by remember { mutableIntStateOf(0) }
  var smsGuide by remember { mutableStateOf(false) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
    tick++
    // 안드로이드 15+: 설치 파일로 설치한 앱은 문자 권한 요청이 바로 거절됩니다. 켜는 방법을 알려 줍니다.
    if (smsMayBeRestricted && result[Manifest.permission.SEND_SMS] == false) smsGuide = true
  }
  val prefs = remember { context.getSharedPreferences("permission_asked", Context.MODE_PRIVATE) }

  fun request(vararg permissions: String) {
    val activity = context.findActivity()
    val askedBefore = permissions.any { prefs.getBoolean(it, false) }
    val blocked = activity != null && askedBefore && permissions.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    if (blocked) {
      if (smsMayBeRestricted && Manifest.permission.SEND_SMS in permissions) smsGuide = true else openAppSettings(context)
    } else {
      prefs.edit().apply { permissions.forEach { putBoolean(it, true) } }.apply()
      launcher.launch(arrayOf(*permissions))
    }
  }

  val checks = remember(tick, settings, hasAccelerometer) { buildChecks(context, settings, hasAccelerometer) }
  return ReadinessState(checks, smsGuide, { smsGuide = false }) { id ->
    when (id) {
      CheckId.CONTACT -> onEditContacts()
      CheckId.NOTIFY -> if (Build.VERSION.SDK_INT >= 33) request(Manifest.permission.POST_NOTIFICATIONS) else openAppSettings(context)
      CheckId.LOCATION -> request(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
      CheckId.SMS -> request(Manifest.permission.SEND_SMS)
      CheckId.MIC -> request(Manifest.permission.RECORD_AUDIO)
      CheckId.FULLSCREEN -> if (Build.VERSION.SDK_INT >= 34) {
        tryStart(context, Intent(SysSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
      }
      CheckId.BATTERY -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (!tryStart(context, Intent(SysSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))) {
          tryStart(context, Intent(SysSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
      }
      CheckId.SENSOR -> Unit
    }
  }
}

/** 안드로이드 15+ 에서 막힌 문자 권한을 켜는 방법 */
@Composable
fun SmsPermissionGuide(onDismiss: () -> Unit) {
  val context = LocalContext.current
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("문자 권한 켜는 방법") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "안드로이드 15부터는 설치 파일로 설치한 앱의 문자 권한이 처음에 막혀 있어요. 아래 순서대로 한 번만 풀어 주세요.",
          style = MaterialTheme.typography.bodyMedium,
        )
        Text(
          "1. '앱 정보 열기'를 눌러요.\n2. 오른쪽 위 ⋮(더보기)를 누르고 '제한된 설정 허용'을 눌러요.\n3. '권한' → 'SMS'에서 '허용'을 골라요.",
          style = MaterialTheme.typography.bodyMedium,
        )
        Caption("'제한된 설정 허용'이 안 보이면 문자 권한을 한 번 더 요청한 뒤 다시 확인해 주세요.")
      }
    },
    confirmButton = { AppTextButton("앱 정보 열기", { onDismiss(); openAppSettings(context) }) },
    dismissButton = { AppTextButton("닫기", onDismiss, color = MaterialTheme.colorScheme.onSurfaceVariant) },
  )
}

/** 처음 설정 마지막 단계에서 한 번에 요청할 권한 */
fun onboardingPermissions(): Array<String> = buildList {
  add(Manifest.permission.ACCESS_FINE_LOCATION)
  add(Manifest.permission.ACCESS_COARSE_LOCATION)
  add(Manifest.permission.SEND_SMS)
  add(Manifest.permission.RECORD_AUDIO)
  if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

fun openAppSettings(context: Context) {
  tryStart(context, Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}

fun tryStart(context: Context, intent: Intent): Boolean = try {
  if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  context.startActivity(intent)
  true
} catch (_: Exception) {
  false
}
