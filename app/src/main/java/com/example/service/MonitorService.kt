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
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.App

/**
 * 화면이 꺼져 있어도 충격을 감지하도록 붙잡아 두는 포그라운드 서비스.
 * 실제 감지·경보 로직은 [com.example.engine.GuardEngine] 에 있습니다.
 */
class MonitorService : Service() {
  private var wakeLock: PowerManager.WakeLock? = null
  private val app get() = application as App

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    goForeground()
    // 화면이 꺼지면 CPU 가 잠들어 센서 값을 못 받으므로 부분 wake lock 을 잡습니다.
    wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
      .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AiShockGuard:monitor")
      .apply { setReferenceCounted(false); acquire() }
    app.engine.onMonitoringStarted()
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
