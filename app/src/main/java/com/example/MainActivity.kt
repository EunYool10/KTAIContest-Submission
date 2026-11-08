package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppRoot
import com.example.ui.AppViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    // 경보 중에는 잠금 화면 위에 띄우고 화면을 켜 둡니다.
    val engine = (application as App).engine
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.CREATED) {
        engine.alert.collect { alertMode(it != null) }
      }
    }

    setContent {
      MyApplicationTheme {
        val vm: AppViewModel = viewModel()
        AppRoot(vm)
      }
    }
  }

  private fun alertMode(on: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(on)
      setTurnScreenOn(on)
    } else {
      @Suppress("DEPRECATION")
      val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
      if (on) window.addFlags(flags) else window.clearFlags(flags)
    }
    if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }
}
