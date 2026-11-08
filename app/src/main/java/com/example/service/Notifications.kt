package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.Phone
import com.example.data.Settings
import com.example.engine.SentReport
import com.example.engine.SmsStatus

object Notifications {
  const val CH_MONITOR = "monitor"
  const val CH_ALERT = "alert"
  const val CH_NOTICE = "notice"
  const val CH_UPDATE = "update"

  const val ID_MONITOR = 1
  private const val ID_ALERT = 2
  private const val ID_NOTICE = 3
  private const val ID_UPDATE = 4

  fun createChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = context.getSystemService(NotificationManager::class.java) ?: return
    nm.createNotificationChannels(
      listOf(
        NotificationChannel(CH_MONITOR, "감지 상태", NotificationManager.IMPORTANCE_LOW).apply {
          description = "충격 감지가 켜져 있는 동안 계속 표시돼요."
          setShowBadge(false)
        },
        NotificationChannel(CH_ALERT, "긴급 경보", NotificationManager.IMPORTANCE_HIGH).apply {
          description = "큰 충격 뒤 괜찮은지 묻고, 도움 요청 결과를 알려 줘요."
          setSound(null, null) // 경보음은 앱이 직접 울립니다.
          enableVibration(false)
          lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        },
        NotificationChannel(CH_NOTICE, "충격 안내", NotificationManager.IMPORTANCE_DEFAULT).apply {
          description = "약한 충격이 감지됐을 때 안내해요."
        },
        NotificationChannel(CH_UPDATE, "업데이트 안내", NotificationManager.IMPORTANCE_DEFAULT).apply {
          description = "새 버전이 나오면 알려 줘요."
          setShowBadge(true)
        },
      )
    )
  }

  private fun openApp(context: Context, request: Int = 0): PendingIntent =
    PendingIntent.getActivity(
      context, request,
      Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

  private fun action(context: Context, action: String, request: Int): PendingIntent =
    PendingIntent.getBroadcast(
      context, request,
      Intent(context, AlertActionReceiver::class.java).setAction(action),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

  fun monitoring(context: Context, settings: Settings): Notification {
    return NotificationCompat.Builder(context, CH_MONITOR)
      .setSmallIcon(R.drawable.ic_stat_guard)
      .setContentTitle("충격 감지 중")
      .setContentText("큰 충격이 오면 ${settings.countdownSeconds}초 동안 확인한 뒤 ${settings.smsTargetPhrase} 알려요.")
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setContentIntent(openApp(context))
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .build()
  }

  fun showCountdown(context: Context, endsAt: Long, settings: Settings, test: Boolean) {
    notify(
      context, ID_ALERT,
      NotificationCompat.Builder(context, CH_ALERT)
        .setSmallIcon(R.drawable.ic_stat_guard)
        .setContentTitle(if (test) "[테스트] 괜찮으세요?" else "괜찮으세요?")
        .setContentText("응답이 없으면 ${settings.smsTargetPhrase} 위치를 문자로 보내요.")
        .setWhen(endsAt)
        .setShowWhen(true)
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .setContentIntent(openApp(context))
        .setFullScreenIntent(openApp(context, 1), true)
        .addAction(0, "괜찮아요", action(context, AlertActionReceiver.ACTION_OK, 10))
        .addAction(0, "도움 요청", action(context, AlertActionReceiver.ACTION_HELP, 11))
        .build()
    )
  }

  fun showSent(context: Context, report: SentReport) {
    val sent = report.recipients.filter { it.status == SmsStatus.SENDING || it.status == SmsStatus.SENT }
    val text = if (sent.isNotEmpty()) "${sent.joinToString { it.label }}에게 위치를 문자로 보냈어요."
    else "문자를 보내지 못했어요. 눌러서 직접 연락해 주세요."
    val builder = NotificationCompat.Builder(context, CH_ALERT)
      .setSmallIcon(R.drawable.ic_stat_guard)
      .setContentTitle(if (report.byUser) "도움 요청을 보냈어요" else "응답이 없어 도움 요청을 보냈어요")
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setContentIntent(openApp(context))
      .setAutoCancel(true)
    if (report.callNumber.isNotBlank()) {
      val dial = PendingIntent.getActivity(
        context, 20,
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Phone.digits(report.callNumber))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
      builder.addAction(0, "${report.callLabel} 전화", dial)
    }
    notify(context, ID_ALERT, builder.build())
  }

  fun showNotice(context: Context, title: String, text: String) {
    notify(
      context, ID_NOTICE,
      NotificationCompat.Builder(context, CH_NOTICE)
        .setSmallIcon(R.drawable.ic_stat_guard)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(openApp(context))
        .setAutoCancel(true)
        .build()
    )
  }

  /** 새 버전 알림: 누르면 앱이 열리고, '내려받기'는 설치 파일을 바로 받습니다. */
  fun showUpdate(context: Context, info: com.example.engine.UpdateInfo) {
    val download = PendingIntent.getActivity(
      context, 30,
      Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val text = "${info.versionName} 버전이 나왔어요. 업데이트해도 설정과 기록은 그대로 남아요."
    notify(
      context, ID_UPDATE,
      NotificationCompat.Builder(context, CH_UPDATE)
        .setSmallIcon(R.drawable.ic_stat_guard)
        .setContentTitle("새 업데이트가 있어요")
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(openApp(context, 2))
        .addAction(0, "내려받기", download)
        .setAutoCancel(true)
        .build()
    )
  }

  fun cancelAlert(context: Context) {
    NotificationManagerCompat.from(context).cancel(ID_ALERT)
  }

  fun canPost(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

  /** 잠금 화면 위로 경보 화면을 띄울 수 있는지 (Android 14+ 에서는 사용자가 허용해야 함) */
  fun canFullScreen(context: Context): Boolean =
    Build.VERSION.SDK_INT < 34 ||
      (context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: false)

  private fun notify(context: Context, id: Int, n: Notification) {
    if (!canPost(context)) return
    try {
      NotificationManagerCompat.from(context).notify(id, n)
    } catch (_: SecurityException) {
    }
  }
}
