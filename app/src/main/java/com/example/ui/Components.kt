package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.detect.GRAVITY
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 화면 섹션을 감싸는 기본 판. 테두리 없이 면 색만 살짝 다르게. */
@Composable
fun Panel(
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.surface,
  contentColor: Color = MaterialTheme.colorScheme.onSurface,
  padding: PaddingValues = PaddingValues(18.dp),
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = color, contentColor = contentColor) {
    Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
  }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
  Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
    trailing()
  }
}

/** 동그라미 안 아이콘 */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, background: Color, size: Int = 40) {
  Box(Modifier.size(size.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
  }
}

@Composable
fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
  Column(modifier) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
  }
}

/** 작은 꼬리표 (예: 테스트, 긴급) */
@Composable
fun Tag(text: String, color: Color, background: Color) {
  Surface(color = background, contentColor = color, shape = MaterialTheme.shapes.small) {
    Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
  }
}

fun gForce(accel: Float): String = String.format(Locale.US, "%.1fG", accel / GRAVITY)
fun gForce2(accel: Float): String = String.format(Locale.US, "%.2fG", accel / GRAVITY)
fun ms2(accel: Float): String = String.format(Locale.US, "%.1f m/s²", accel)

private fun sameYear(ms: Long): Boolean {
  val y = SimpleDateFormat("yyyy", Locale.KOREA)
  return y.format(Date(ms)) == y.format(Date())
}

fun timeText(ms: Long): String = SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(ms))
fun dateText(ms: Long): String =
  SimpleDateFormat(if (sameYear(ms)) "M월 d일 EEEE" else "yyyy년 M월 d일 EEEE", Locale.KOREA).format(Date(ms))
fun dateTimeText(ms: Long): String =
  SimpleDateFormat(if (sameYear(ms)) "M월 d일 a h:mm" else "yyyy년 M월 d일 a h:mm", Locale.KOREA).format(Date(ms))

/** "방금", "3분 전" 처럼 */
fun agoText(ms: Long, now: Long = System.currentTimeMillis()): String {
  val s = (now - ms) / 1000
  return when {
    s < 60 -> "방금"
    s < 3600 -> "${s / 60}분 전"
    s < 86400 -> "${s / 3600}시간 전"
    else -> dateTimeText(ms)
  }
}
