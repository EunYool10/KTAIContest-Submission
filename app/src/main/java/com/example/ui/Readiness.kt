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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.data.Phone
import com.example.data.Settings
import com.example.service.Notifications

enum class CheckId { SENSOR, CONTACT, NOTIFY, LOCATION, SMS, FULLSCREEN, BATTERY, MIC, AI }

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

fun buildChecks(context: Context, settings: Settings, hasAccelerometer: Boolean, geminiConfigured: Boolean, modelName: String): List<Check> {
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
    Check(CheckId.SMS, "문자 자동 전송", if (hasPhone) "응답이 없으면 보호자에게 바로 문자를 보내요." else "이 기기는 문자를 보낼 수 없어요.",
      "권한이 없으면 문자를 자동으로 보내지 못해요.", !hasPhone || context.granted(Manifest.permission.SEND_SMS), true, "허용"),
    Check(CheckId.FULLSCREEN, "잠금 화면 경보", "화면이 잠겨 있어도 경보 화면을 띄워요.", "허용하면 잠금 화면 위로 경보 화면이 바로 떠요.",
      Notifications.canFullScreen(context), false, "설정"),
    Check(CheckId.BATTERY, "배터리 최적화 제외", "절전 중에도 감지가 멈추지 않아요.", "절전 모드에서 감지가 멈출 수 있어요.",
      batteryOk, false, "설정"),
    Check(CheckId.MIC, "음성으로 취소", "경보 중 '괜찮아'라고 말하면 취소돼요.", "마이크를 허용하면 말로 경보를 끌 수 있어요.",
      !settings.voiceCancelEnabled || context.granted(Manifest.permission.RECORD_AUDIO), false, "허용"),
    // 사용자가 고칠 수 있는 항목이 아니라서 항상 '준비됨'으로 두고 설명만 바꿉니다.
    Check(CheckId.AI, "판단 방식",
      if (geminiConfigured) "AI(Gemini)가 충격 데이터를 판단해요. 연결이 안 되면 기기 안 규칙으로 판단해요."
      else "기기 안 규칙으로 판단해요. 설정에서 Gemini API 키를 넣으면 AI가 함께 판단해요.",
      "", true, false),
  )
}

/** 화면에 돌아올 때마다 다시 계산되는 준비 상태 + 고치기 동작 */
class ReadinessState(val checks: List<Check>, val fix: (CheckId) -> Unit) {
  val missingEssential get() = checks.count { it.essential && !it.ok }
  val missingOptional get() = checks.count { !it.essential && !it.ok && it.actionLabel != null }
}

@SuppressLint("BatteryLife")
@Composable
fun rememberReadiness(
  settings: Settings,
  hasAccelerometer: Boolean,
  geminiConfigured: Boolean,
  modelName: String,
  onEditContacts: () -> Unit,
): ReadinessState {
  val context = LocalContext.current
  var tick by remember { mutableIntStateOf(0) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
  val prefs = remember { context.getSharedPreferences("permission_asked", Context.MODE_PRIVATE) }

  fun request(vararg permissions: String) {
    val activity = context.findActivity()
    val askedBefore = permissions.any { prefs.getBoolean(it, false) }
    val blocked = activity != null && askedBefore && permissions.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    if (blocked) {
      openAppSettings(context)
    } else {
      prefs.edit().apply { permissions.forEach { putBoolean(it, true) } }.apply()
      launcher.launch(arrayOf(*permissions))
    }
  }

  val checks = remember(tick, settings, hasAccelerometer, geminiConfigured) {
    buildChecks(context, settings, hasAccelerometer, geminiConfigured, modelName)
  }
  return ReadinessState(checks) { id ->
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
      CheckId.SENSOR, CheckId.AI -> Unit
    }
  }
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
