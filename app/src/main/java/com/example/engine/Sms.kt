package com.example.engine

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.App
import com.example.data.Phone
import com.example.data.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SmsStatus(val label: String) {
  SENDING("보내는 중"),
  SENT("보냄"),
  FAILED("실패"),
  SKIPPED("보내지 않음"),
}

data class Recipient(val label: String, val number: String, val status: SmsStatus)

object EmergencyMessage {
  fun build(settings: Settings, place: Place?, byUser: Boolean, time: Long = System.currentTimeMillis()): String {
    val name = settings.userName.ifBlank { "사용자" }
    val clock = SimpleDateFormat("M월 d일 a h:mm", Locale.KOREA).format(Date(time))
    val what = if (byUser) "${name}님이 도움을 요청했어요." else "${name}님 휴대폰에 큰 충격이 감지됐고 ${settings.countdownSeconds}초 동안 응답이 없었어요."
    val where = if (place == null) {
      "위치: 확인하지 못했어요. 전화로 확인해 주세요."
    } else buildString {
      append("위치: ")
      if (place.address.isNotBlank()) append(place.address).append(' ')
      append("(오차 약 ${place.accuracy.toInt()}m)\n")
      append(place.mapUrl)
    }
    return "[긴급] $what\n$where\n시각: $clock"
  }
}

/** 문자를 자동으로 보냅니다. 결과는 [SmsResultReceiver] 가 엔진에 알려 줍니다. */
class SmsSender(private val context: Context) {
  fun canSend(): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

  /** 유심이 두 개인 휴대폰은 '문자 기본 유심'으로 보냅니다. 정해져 있지 않으면 시스템 기본값을 씁니다. */
  private fun manager(): SmsManager? {
    val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
    val valid = subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val base = context.getSystemService(SmsManager::class.java)
      if (valid) base?.createForSubscriptionId(subId) else base
    } else {
      @Suppress("DEPRECATION")
      if (valid) SmsManager.getSmsManagerForSubscriptionId(subId) else SmsManager.getDefault()
    }
  }

  /** 보낼 수 있으면 true. 실제 결과는 나중에 [onResult] 로 옵니다. */
  fun send(sendId: Long, number: String, text: String): Boolean {
    if (!canSend()) return false
    val sms = manager() ?: return false
    return try {
      val parts = sms.divideMessage(text)
      val intents = ArrayList<PendingIntent>()
      parts.forEachIndexed { i, _ ->
        val intent = Intent(context, SmsResultReceiver::class.java)
          .setAction(ACTION_SENT)
          .putExtra(EXTRA_SEND_ID, sendId)
          .putExtra(EXTRA_NUMBER, number)
          .putExtra(EXTRA_LAST, i == parts.lastIndex)
        intents += PendingIntent.getBroadcast(
          context, (sendId.toInt() * 31 + number.hashCode()) * 8 + i, intent,
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
      }
      sms.sendMultipartTextMessage(Phone.digits(number), null, parts, intents, null)
      true
    } catch (e: Exception) {
      Log.e("SmsSender", "문자 전송 실패", e)
      false
    }
  }

  companion object {
    const val ACTION_SENT = "com.example.SMS_SENT"
    const val EXTRA_SEND_ID = "send_id"
    const val EXTRA_NUMBER = "number"
    const val EXTRA_LAST = "last"
  }
}

class SmsResultReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != SmsSender.ACTION_SENT) return
    val ok = resultCode == android.app.Activity.RESULT_OK
    val last = intent.getBooleanExtra(SmsSender.EXTRA_LAST, true)
    if (ok && !last) return
    val app = context.applicationContext as? App ?: return
    app.engine.onSmsResult(
      intent.getLongExtra(SmsSender.EXTRA_SEND_ID, 0L),
      intent.getStringExtra(SmsSender.EXTRA_NUMBER).orEmpty(),
      ok,
    )
  }
}
