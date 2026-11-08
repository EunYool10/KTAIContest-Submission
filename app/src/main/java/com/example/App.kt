package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.SettingsStore
import com.example.engine.GuardEngine
import com.example.service.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  lateinit var settings: SettingsStore
    private set
  lateinit var engine: GuardEngine
    private set

  override fun onCreate() {
    super.onCreate()
    settings = SettingsStore(this)
    engine = GuardEngine(this, settings, AppDatabase.get(this).fallLogDao(), scope)
    Notifications.createChannels(this)
  }
}
