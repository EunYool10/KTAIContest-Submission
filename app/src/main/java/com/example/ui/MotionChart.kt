package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.detect.GRAVITY
import com.example.engine.GuardEngine
import com.example.ui.theme.AppTheme
import kotlin.math.max

/**
 * 최근 10초 동안의 움직임 크기 그래프. 점선은 주의/긴급 기준선입니다.
 * [history] 는 0.1초마다의 최대 가속도(m/s²).
 */
@Composable
fun MotionChart(
  history: List<Float>,
  warning: Float,
  emergency: Float,
  modifier: Modifier = Modifier,
  emptyText: String = "감지를 켜면 움직임이 여기에 보여요.",
) {
  val line = MaterialTheme.colorScheme.primary
  val grid = MaterialTheme.colorScheme.outlineVariant
  val warnColor = AppTheme.extra.warning
  val dangerColor = MaterialTheme.colorScheme.error
  val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
  val measurer = rememberTextMeasurer()
  val labelStyle = TextStyle(fontSize = 11.sp, color = labelColor)
  val peak = history.maxOrNull() ?: 0f

  Box(
    modifier
      .fillMaxWidth()
      .height(150.dp)
      .semantics { contentDescription = "최근 10초 움직임 그래프, 최대 ${gForce(peak)}" },
  ) {
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
      val top = max(emergency * 1.25f, max(peak * 1.1f, GRAVITY * 3f))
      val w = size.width
      val h = size.height
      fun y(v: Float) = h - (v / top).coerceIn(0f, 1f) * h

      // 1G 바닥선
      drawLine(grid, Offset(0f, y(GRAVITY)), Offset(w, y(GRAVITY)), strokeWidth = 1.dp.toPx())
      drawText(measurer, "1G 정지", Offset(4.dp.toPx(), y(GRAVITY) + 2.dp.toPx()), labelStyle)

      val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
      drawLine(warnColor, Offset(0f, y(warning)), Offset(w, y(warning)), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
      drawLine(dangerColor, Offset(0f, y(emergency)), Offset(w, y(emergency)), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
      // 기준선 이름은 왼쪽(오래된 쪽)에 둬서 새로 들어오는 그래프와 겹치지 않게 합니다.
      drawText(measurer, "주의 ${gForce(warning)}", Offset(4.dp.toPx(), y(warning) - 16.dp.toPx()), labelStyle.copy(color = warnColor))
      drawText(measurer, "긴급 ${gForce(emergency)}", Offset(4.dp.toPx(), y(emergency) - 16.dp.toPx()), labelStyle.copy(color = dangerColor))

      if (history.size >= 2) {
        val n = GuardEngine.HISTORY_POINTS
        val step = w / (n - 1)
        val startX = w - step * (history.size - 1)
        val path = Path()
        history.forEachIndexed { i, v ->
          val x = startX + i * step
          if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
        }
        val fill = Path().apply {
          addPath(path)
          lineTo(w, h)
          lineTo(startX, h)
          close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.22f), Color.Transparent)))
        drawPath(path, line, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        history.forEachIndexed { i, v ->
          if (v >= warning) {
            drawCircle(if (v >= emergency) dangerColor else warnColor, 4.dp.toPx(), Offset(startX + i * step, y(v)))
          }
        }
      }
    }
    if (history.size < 2) {
      Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = labelColor, modifier = Modifier.align(Alignment.Center))
    }
  }
}
