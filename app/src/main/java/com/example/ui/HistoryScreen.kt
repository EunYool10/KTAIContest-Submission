package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
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
import java.util.Calendar
import java.util.Locale

private enum class LogFilter(val title: String) { ALL("전체"), EMERGENCY("큰 충격"), WARNING("충격 주의"), SOS("도움 요청"), TEST("테스트") }

private fun isHelp(log: FallLog) =
  LogKind.of(log) == LogKind.SOS || log.userResponse.contains("자동 문자") || log.userResponse.startsWith("도움 요청")

private fun LogFilter.accepts(log: FallLog): Boolean = when (this) {
  LogFilter.ALL -> true
  LogFilter.TEST -> log.isSimulated
  LogFilter.EMERGENCY -> !log.isSimulated && LogKind.of(log) == LogKind.EMERGENCY
  LogFilter.WARNING -> !log.isSimulated && LogKind.of(log) == LogKind.WARNING
  LogFilter.SOS -> !log.isSimulated && isHelp(log)
}

private fun dayKey(ms: Long): Int = Calendar.getInstance().run { timeInMillis = ms; get(Calendar.YEAR) * 1000 + get(Calendar.DAY_OF_YEAR) }

@Composable
fun HistoryScreen(logs: List<FallLog>, onDelete: (Int) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
  var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
  var expandedId by rememberSaveable { mutableIntStateOf(-1) }
  var confirmClear by rememberSaveable { mutableStateOf(false) }
  val scheme = MaterialTheme.colorScheme
  val shown = logs.filter { filter.accepts(it) }
  val monthAgo = System.currentTimeMillis() - 30L * 86_400_000L
  val recentReal = logs.filter { !it.isSimulated && it.timestamp >= monthAgo }

  Column(modifier.fillMaxSize().testTag("history")) {
    ScrollableTabRow(
      selectedTabIndex = filter.ordinal,
      containerColor = scheme.surface,
      contentColor = scheme.onSurface,
      edgePadding = 8.dp,
      indicator = { positions ->
        TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[filter.ordinal]), height = 2.dp, color = scheme.primary)
      },
      divider = { HorizontalDivider(color = scheme.outlineVariant) },
    ) {
      LogFilter.entries.forEach { f ->
        Tab(
          selected = filter == f, onClick = { filter = f },
          selectedContentColor = scheme.primary, unselectedContentColor = scheme.onSurfaceVariant,
          text = { Text(f.title, style = MaterialTheme.typography.labelLarge) },
        )
      }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
      item {
        Section("최근 30일") {
          StatRow(
            listOf(
              "큰 충격" to "${recentReal.count { LogKind.of(it) == LogKind.EMERGENCY }}회",
              "충격 주의" to "${recentReal.count { LogKind.of(it) == LogKind.WARNING }}회",
              "도움 요청" to "${recentReal.count { isHelp(it) }}회",
            ),
            Modifier.padding(horizontal = 6.dp),
          )
        }
      }
      if (shown.isEmpty()) {
        item {
          Column(Modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.History, null, Modifier.size(40.dp), tint = scheme.outline)
            Spacer(Modifier.height(10.dp))
            Text(if (logs.isEmpty()) "아직 기록이 없어요" else "이 종류의 기록은 없어요", style = MaterialTheme.typography.titleMedium)
            Caption("충격이 감지되거나 도움을 요청하면 여기에 남아요.", Modifier.padding(top = 4.dp))
          }
        }
      } else {
        var lastDay = -1
        shown.forEach { log ->
          val day = dayKey(log.timestamp)
          if (day != lastDay) {
            lastDay = day
            item(key = "d$day") {
              Text(
                dateText(log.timestamp), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().background(scheme.background).padding(start = SidePadding, top = 18.dp, bottom = 6.dp),
              )
            }
          }
          item(key = log.id) {
            LogRow(
              log, expanded = expandedId == log.id,
              onToggle = { expandedId = if (expandedId == log.id) -1 else log.id },
              onDelete = { onDelete(log.id) },
            )
          }
        }
        item {
          Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
            AppTextButton("기록 모두 지우기", { confirmClear = true }, color = scheme.error)
          }
        }
      }
    }
  }

  if (confirmClear) {
    AlertDialog(
      onDismissRequest = { confirmClear = false },
      title = { Text("기록을 모두 지울까요?") },
      text = { Text("지운 기록은 되돌릴 수 없어요.") },
      confirmButton = { AppTextButton("모두 지우기", { confirmClear = false; onClear() }, color = scheme.error) },
      dismissButton = { AppTextButton("취소", { confirmClear = false }, color = scheme.onSurfaceVariant) },
    )
  }
}

@Composable
private fun LogRow(log: FallLog, expanded: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val kind = LogKind.of(log)
  val uri = LocalUriHandler.current
  Column(Modifier.fillMaxWidth().background(scheme.surface)) {
    Row(
      Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = SidePadding, vertical = 12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Text(timeText(log.timestamp), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.width(76.dp).padding(top = 2.dp))
      Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Dot(logColor(log))
          Spacer(Modifier.width(8.dp))
          Text(kind.title, style = MaterialTheme.typography.bodyLarge)
          if (log.isSimulated) {
            Spacer(Modifier.width(6.dp))
            Tag("테스트", scheme.onSurfaceVariant, scheme.surfaceVariant)
          }
        }
        Caption(
          listOfNotNull(
            log.userResponse.takeIf { it.isNotBlank() && it != kind.title },
            log.maxAccel.takeIf { it > 0f }?.let { "최대 ${gForce(it)}" },
          ).joinToString(" · "),
        )
      }
    }
    AnimatedVisibility(expanded) {
      Column(Modifier.fillMaxWidth().padding(start = SidePadding + 76.dp, end = SidePadding, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (log.aiResult.isNotBlank()) {
          Detail("판단", buildString {
            append(log.aiResult)
          })
        }
        if (log.reason.isNotBlank()) Detail("근거", log.reason)
        Detail(
          "위치",
          when {
            !log.hasLocation -> "확인하지 못했어요"
            log.address.isNotBlank() -> log.address
            else -> String.format(Locale.US, "%.5f, %.5f", log.latitude, log.longitude)
          },
        )
        Row(Modifier.padding(start = 0.dp)) {
          if (log.hasLocation) {
            AppTextButton(
              "지도", { uri.openUri(String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", log.latitude, log.longitude)) },
              icon = Icons.Outlined.Map,
            )
          }
          AppTextButton("삭제", onDelete, color = scheme.error, icon = Icons.Outlined.DeleteOutline)
        }
      }
    }
    HorizontalDivider(Modifier.padding(start = SidePadding), color = scheme.outlineVariant)
  }
}

@Composable
private fun Detail(label: String, value: String) {
  Column {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
  }
}
