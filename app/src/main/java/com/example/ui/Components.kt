package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.detect.GRAVITY
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 버튼 모서리. 알약 모양 대신 8dp 로 통일합니다. */
val ButtonShape = RoundedCornerShape(8.dp)
val SidePadding = 20.dp

/**
 * 화면 너비를 꽉 채우는 흰 구역. 구역 사이 회색 틈은 [SectionGap] 으로 둡니다.
 * 제목은 구역 안 왼쪽 위에, [trailing] 은 제목 줄 오른쪽에 붙습니다.
 */
@Composable
fun Section(
  title: String? = null,
  modifier: Modifier = Modifier,
  trailing: @Composable RowScope.() -> Unit = {},
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
    Column(Modifier.padding(vertical = 16.dp)) {
      if (title != null) {
        Row(Modifier.fillMaxWidth().padding(horizontal = SidePadding).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
          trailing()
        }
        Spacer(Modifier.height(8.dp))
      }
      content()
    }
  }
}

@Composable
fun SectionGap() = Spacer(Modifier.height(10.dp))

/** 구역 안 본문용 기본 여백 */
fun Modifier.sidePadding() = this.padding(horizontal = SidePadding)

@Composable
fun InsetDivider() = HorizontalDivider(Modifier.padding(start = SidePadding), color = MaterialTheme.colorScheme.outlineVariant)

/** 설정 앱처럼 왼쪽 제목, 오른쪽 값, 끝에 화살표가 있는 한 줄 */
@Composable
fun RowItem(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  value: String? = null,
  valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  titleColor: Color = MaterialTheme.colorScheme.onSurface,
  onClick: (() -> Unit)? = null,
  chevron: Boolean = onClick != null,
  leading: (@Composable () -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier
      .fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .heightIn(min = 56.dp)
      .padding(horizontal = SidePadding, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (leading != null) {
      leading()
      Spacer(Modifier.width(14.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
      if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    if (value != null) {
      Spacer(Modifier.width(12.dp))
      Text(
        value, style = MaterialTheme.typography.bodyMedium, color = valueColor, textAlign = TextAlign.End,
        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 190.dp),
      )
    }
    if (trailing != null) {
      Spacer(Modifier.width(12.dp))
      trailing()
    }
    if (chevron) {
      Spacer(Modifier.width(4.dp))
      Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
    }
  }
}

@Composable
fun AppButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
  container: Color = MaterialTheme.colorScheme.primary,
  content: Color = MaterialTheme.colorScheme.onPrimary,
  height: Dp = 52.dp,
) {
  Button(
    onClick = onClick, enabled = enabled, shape = ButtonShape,
    colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    elevation = null,
    contentPadding = PaddingValues(horizontal = 16.dp),
    modifier = modifier.heightIn(min = height),
  ) {
    if (icon != null) {
      Icon(icon, null, Modifier.size(20.dp))
      Spacer(Modifier.width(8.dp))
    }
    Text(text, style = MaterialTheme.typography.titleSmall)
  }
}

@Composable
fun AppOutlinedButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
  content: Color = MaterialTheme.colorScheme.onSurface,
  border: Color = MaterialTheme.colorScheme.outlineVariant,
  height: Dp = 52.dp,
) {
  OutlinedButton(
    onClick = onClick, enabled = enabled, shape = ButtonShape,
    border = BorderStroke(1.dp, border),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
    contentPadding = PaddingValues(horizontal = 16.dp),
    modifier = modifier.heightIn(min = height),
  ) {
    if (icon != null) {
      Icon(icon, null, Modifier.size(20.dp))
      Spacer(Modifier.width(8.dp))
    }
    Text(text, style = MaterialTheme.typography.titleSmall)
  }
}

@Composable
fun AppTextButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.primary,
  icon: ImageVector? = null,
) {
  TextButton(onClick = onClick, shape = ButtonShape, modifier = modifier, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
    if (icon != null) {
      Icon(icon, null, Modifier.size(18.dp), tint = color)
      Spacer(Modifier.width(6.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLarge, color = color)
  }
}

/** 사각형 구간 선택 (알약 모양 SegmentedButton 대신) */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  Row(
    modifier
      .fillMaxWidth()
      .height(44.dp)
      .clip(ButtonShape)
      .background(scheme.surfaceVariant)
      .padding(3.dp),
  ) {
    options.forEachIndexed { i, label ->
      val on = i == selected
      Box(
        Modifier
          .weight(1f)
          .fillMaxHeight()
          .clip(RoundedCornerShape(6.dp))
          .background(if (on) scheme.surface else Color.Transparent)
          .then(if (on) Modifier.border(1.dp, scheme.outlineVariant, RoundedCornerShape(6.dp)) else Modifier)
          .selectable(selected = on, role = Role.RadioButton) { onSelect(i) },
        contentAlignment = Alignment.Center,
      ) {
        Text(
          label, style = MaterialTheme.typography.labelLarge,
          fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
          color = if (on) scheme.onSurface else scheme.onSurfaceVariant,
        )
      }
    }
  }
}

/** 작은 동그라미 상태 표시 */
@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
  Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
  Column(modifier) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleMedium)
  }
}

/** 작은 꼬리표 (예: 테스트) */
@Composable
fun Tag(text: String, color: Color, background: Color) {
  Text(
    text, style = MaterialTheme.typography.labelSmall, color = color,
    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(background).padding(horizontal = 6.dp, vertical = 2.dp),
  )
}

/** 표처럼 칸을 나눠 숫자를 보여 줍니다 (세로 구분선). */
@Composable
fun StatRow(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
  Row(modifier.fillMaxWidth()) {
    items.forEachIndexed { i, (label, value) ->
      if (i > 0) Box(Modifier.width(1.dp).height(44.dp).background(MaterialTheme.colorScheme.outlineVariant).align(Alignment.CenterVertically))
      Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
      }
    }
  }
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
  Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier)
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
