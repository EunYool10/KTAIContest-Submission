package com.example.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.App
import com.example.engine.UpdateCheckResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 화면이 꺼져 있어도 충격을 감지하도록 붙잡아 두는 포그라운드 서비스.
 * 실제 감지·경보 로직은 [com.example.engine.GuardEngine] 에 있습니다.
 */
class MonitorService : Service() {
  private var wakeLock: PowerManager.WakeLock? = null
  private val app get() = application as App
  private var jobs: List<Job> = emptyList()

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    goForeground()
    // 화면이 꺼지면 CPU 가 잠들어 센서 값을 못 받으므로 부분 wake lock 을 잡습니다.
    wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
      .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AiShockGuard:monitor")
      .apply { setReferenceCounted(false); acquire() }
    app.engine.onMonitoringStarted()
    jobs = listOf(
      // 보호자 이름·확인 시간을 바꾸면 '충격 감지 중' 알림 문구도 바로 바꿉니다.
      app.scope.launch {
        app.settings.state.map { it.guardianName to it.countdownSeconds to it.extraContact }.distinctUntilChanged().drop(1).collect {
          runCatching {
            NotificationManagerCompat.from(this@MonitorService).notify(Notifications.ID_MONITOR, Notifications.monitoring(this@MonitorService, app.settings.value))
          }
        }
      },
      // 앱을 열지 않아도 새 버전이 나오면 알림으로 알려 줍니다 (6시간마다 확인, 버전마다 한 번).
      // 앱을 연 직후에는 화면의 업데이트 안내가 먼저 뜨도록 30분 뒤부터 확인합니다.
      app.scope.launch {
        delay(30 * 60 * 1000L)
        while (isActive) {
          if (app.updates.check() == UpdateCheckResult.UPDATE) {
            app.updates.available.value?.let { info ->
              val alreadySeen = info.versionCode <= app.updates.dismissedCode.value
              // 알림을 실제로 보낼 수 있을 때만 '보냈다'고 기록합니다 (알림 권한이 없으면 나중에 다시 시도).
              if (!alreadySeen && Notifications.canPost(this@MonitorService) && app.updates.claimNotification(info)) {
                Notifications.showUpdate(this@MonitorService, info)
              }
            }
          }
          delay(6 * 60 * 60 * 1000L)
        }
      },
    )
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }
    // 앱을 다시 열었을 때 위치 권한이 새로 생겼으면 위치 갱신도 시작합니다.
    goForeground()
    app.engine.location.start()
    return START_STICKY
  }

  private fun goForeground() {
    val notification = Notifications.monitoring(this, app.settings.value)
    val location = app.engine.location.hasPermission()
    val types = when {
      Build.VERSION.SDK_INT >= 34 ->
        ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or (if (location) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
        if (location) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
      else -> 0
    }
    try {
      ServiceCompat.startForeground(this, Notifications.ID_MONITOR, notification, types)
    } catch (e: Exception) {
      // 재부팅 직후처럼 위치를 쓸 수 없는 때에는 위치 없이 시작합니다.
      Log.w("MonitorService", "위치 포함 시작 실패, 위치 없이 다시 시도", e)
      val fallback = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
      ServiceCompat.startForeground(this, Notifications.ID_MONITOR, notification, fallback)
    }
  }

  override fun onDestroy() {
    jobs.forEach { it.cancel() }
    jobs = emptyList()
    app.engine.onMonitoringStopped()
    wakeLock?.let { if (it.isHeld) it.release() }
    wakeLock = null
    super.onDestroy()
  }

  companion object {
    private const val ACTION_STOP = "com.example.STOP_MONITOR"

    fun start(context: Context) {
      try {
        ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
      } catch (e: Exception) {
        Log.e("MonitorService", "감지 서비스 시작 실패", e)
      }
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, MonitorService::class.java))
    }
  }
}

/** 재부팅하거나 앱을 업데이트한 뒤, 사용자가 켜 둔 감지를 다시 켭니다. */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
        val s = (context.applicationContext as App).settings.value
        if (s.setupComplete && s.monitoringEnabled) MonitorService.start(context)
      }
    }
  }
}

/** 알림의 "괜찮아요" / "도움 요청" 버튼. */
class AlertActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val engine = (context.applicationContext as App).engine
    when (intent.action) {
      ACTION_OK -> engine.imOkay()
      ACTION_HELP -> engine.requestHelp()
    }
  }

  companion object {
    const val ACTION_OK = "com.example.ALERT_OK"
    const val ACTION_HELP = "com.example.ALERT_HELP"
  }
}
