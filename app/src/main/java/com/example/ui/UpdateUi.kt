package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.engine.UpdateInfo

/** 설치 파일을 브라우저로 내려받습니다 (처음 설치할 때와 같은 방법). */
fun openUpdate(context: Context, info: UpdateInfo) {
  tryStart(context, Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)))
}

/** 새 버전이 나오면 한 번 띄우는 안내 */
@Composable
fun UpdateDialog(info: UpdateInfo, onUpdate: () -> Unit, onLater: () -> Unit) {
  AlertDialog(
    onDismissRequest = onLater,
    title = { Text("새 업데이트가 있어요") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${info.versionName} 버전이 나왔어요. 지금 쓰는 버전은 ${BuildConfig.VERSION_NAME}이에요.", style = MaterialTheme.typography.bodyMedium)
        if (info.notes.isNotEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            info.notes.forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) }
          }
        }
        Caption("업데이트해도 설정과 기록은 그대로 남아요. 내려받기가 끝나면 알림에서 파일을 눌러 설치하세요.")
      }
    },
    confirmButton = { AppButton("업데이트", onUpdate, height = 44.dp) },
    dismissButton = { AppTextButton("나중에", onLater, color = MaterialTheme.colorScheme.onSurfaceVariant) },
  )
}

/** 홈 맨 위에 업데이트할 때까지 남아 있는 줄 */
@Composable
fun UpdateSection(info: UpdateInfo, onUpdate: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  Section(modifier = Modifier.testTag("update_banner")) {
    Row(Modifier.fillMaxWidth().sidePadding(), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Outlined.SystemUpdate, null, tint = scheme.primary, modifier = Modifier.size(22.dp))
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text("새 업데이트가 있어요", style = MaterialTheme.typography.titleMedium)
        Caption("${info.versionName} · 지금 ${BuildConfig.VERSION_NAME}")
      }
      Spacer(Modifier.width(8.dp))
      AppButton("업데이트", onUpdate, height = 40.dp)
    }
    if (info.notes.isNotEmpty()) {
      Spacer(Modifier.height(8.dp))
      info.notes.take(3).forEach { Caption("· $it", Modifier.sidePadding()) }
    }
  }
}
