package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.FallLog
import com.example.data.LogKind
import com.example.ui.theme.AppTheme
import java.util.Calendar
import java.util.Locale

private enum class LogFilter(val title: String) { ALL("전체"), EMERGENCY("큰 충격"), WARNING("충격 주의"), SOS("도움 요청"), TEST("테스트") }

private fun LogFilter.accepts(log: FallLog): Boolean = when (this) {
  LogFilter.ALL -> true
  LogFilter.TEST -> log.isSimulated
  LogFilter.EMERGENCY -> !log.isSimulated && LogKind.of(log) == LogKind.EMERGENCY
  LogFilter.WARNING -> !log.isSimulated && LogKind.of(log) == LogKind.WARNING
  LogFilter.SOS -> !log.isSimulated && (LogKind.of(log) == LogKind.SOS || log.userResponse.contains("도움") || log.userResponse.contains("자동 문자"))
}

private fun dayKey(ms: Long): Int = Calendar.getInstance().run { timeInMillis = ms; get(Calendar.YEAR) * 1000 + get(Calendar.DAY_OF_YEAR) }

@Composable
fun HistoryScreen(logs: List<FallLog>, onDelete: (Int) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
  var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
  var expandedId by rememberSaveable { mutableIntStateOf(-1) }
  var confirmClear by rememberSaveable { mutableStateOf(false) }
  val shown = logs.filter { filter.accepts(it) }
  val monthAgo = System.currentTimeMillis() - 30L * 86_400_000L
  val recentReal = logs.filter { !it.isSimulated && it.timestamp >= monthAgo }

  LazyColumn(
    modifier.fillMaxSize().testTag("history"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Panel {
        Text("최근 30일", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
          LabelValue("큰 충격", "${recentReal.count { LogKind.of(it) == LogKind.EMERGENCY }}회", Modifier.weight(1f))
          LabelValue("충격 주의", "${recentReal.count { LogKind.of(it) == LogKind.WARNING }}회", Modifier.weight(1f))
          LabelValue("도움 요청", "${recentReal.count { LogFilter.SOS.accepts(it) }}회", Modifier.weight(1f))
        }
      }
    }
    item {
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LogFilter.entries.forEach { f ->
          FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.title) })
        }
      }
    }
    if (shown.isEmpty()) {
      item {
        Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(Icons.Outlined.History, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
          Spacer(Modifier.height(12.dp))
          Text(if (logs.isEmpty()) "아직 기록이 없어요" else "이 종류의 기록은 없어요", style = MaterialTheme.typography.titleMedium)
          Text(
            "충격이 감지되거나 도움을 요청하면 여기에 남아요.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
          )
        }
      }
    } else {
      var lastDay = -1
      shown.forEach { log ->
        val day = dayKey(log.timestamp)
        if (day != lastDay) {
          lastDay = day
          item(key = "d$day") {
            Text(dateText(log.timestamp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
          }
        }
        item(key = log.id) {
          LogCard(log, expanded = expandedId == log.id, onToggle = { expandedId = if (expandedId == log.id) -1 else log.id }, onDelete = { onDelete(log.id) })
        }
      }
      item {
        TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
          Text("기록 모두 지우기", color = MaterialTheme.colorScheme.error)
        }
      }
    }
  }

  if (confirmClear) {
    AlertDialog(
      onDismissRequest = { confirmClear = false },
      title = { Text("기록을 모두 지울까요?") },
      text = { Text("지운 기록은 되돌릴 수 없어요.") },
      confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("모두 지우기", color = MaterialTheme.colorScheme.error) } },
      dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } },
    )
  }
}

@Composable
private fun LogCard(log: FallLog, expanded: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val kind = LogKind.of(log)
  val accent = when {
    log.isSimulated -> scheme.outline
    kind == LogKind.WARNING -> AppTheme.extra.warning
    kind == LogKind.SOS || log.userResponse.contains("자동 문자") || log.userResponse.contains("도움") -> scheme.error
    else -> scheme.primary
  }
  val uri = LocalUriHandler.current
  Surface(shape = MaterialTheme.shapes.large, color = scheme.surface, modifier = Modifier.fillMaxWidth()) {
    Row(Modifier.height(IntrinsicSize.Min)) {
      Box(
        Modifier.padding(start = 12.dp, top = 16.dp, bottom = 16.dp).width(4.dp).fillMaxHeight()
          .background(accent, RoundedCornerShape(2.dp)),
      )
      Column(Modifier.clickable(onClick = onToggle).padding(14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(kind.title, style = MaterialTheme.typography.titleSmall)
          if (log.isSimulated) {
            Spacer(Modifier.width(6.dp))
            Tag("테스트", scheme.onSurfaceVariant, scheme.surfaceVariant)
          }
          Spacer(Modifier.weight(1f))
          Text(timeText(log.timestamp), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
        Text(
          listOfNotNull(
            log.userResponse.takeIf { it.isNotBlank() && it != kind.title },
            log.maxAccel.takeIf { it > 0f }?.let { "최대 ${gForce(it)}" },
            log.aiResult.takeIf { it.isNotBlank() }?.let { r -> if (log.confidence > 0) "$r ${(log.confidence * 100).toInt()}%" else r },
          ).joinToString(" · "),
          style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
        )
        AnimatedVisibility(expanded) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            HorizontalDivider(color = scheme.outlineVariant)
            if (log.reason.isNotBlank()) {
              Text("${log.analyzer.ifBlank { "판단" }} 근거", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
              Text(log.reason, style = MaterialTheme.typography.bodyMedium)
            }
            Text("위치", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(
              when {
                !log.hasLocation -> "위치를 확인하지 못했어요"
                log.address.isNotBlank() -> log.address
                else -> String.format(Locale.US, "%.5f, %.5f", log.latitude, log.longitude)
              },
              style = MaterialTheme.typography.bodyMedium,
            )
            Row {
              if (log.hasLocation) {
                TextButton(onClick = { uri.openUri(String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", log.latitude, log.longitude)) }) {
                  Icon(Icons.Outlined.Map, null, Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("지도")
                }
              }
              Spacer(Modifier.weight(1f))
              TextButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp), tint = scheme.error)
                Spacer(Modifier.width(6.dp))
                Text("삭제", color = scheme.error)
              }
            }
          }
        }
      }
    }
  }
}
