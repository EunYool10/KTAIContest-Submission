package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.Settings
import com.example.engine.Live
import com.example.ui.theme.AppTheme

@Composable
fun CalibrationScreen(
  cal: Calibration,
  live: Live,
  settings: Settings,
  onNext: () -> Unit,
  onRetry: () -> Unit,
  onClose: () -> Unit,
) {
  BackHandler(onBack = onClose)
  val stepNo = when (cal.step) {
    CalStep.INTRO -> 0
    CalStep.DAILY -> 1
    CalStep.IMPACT_READY, CalStep.IMPACT -> 2
    CalStep.RESULT, CalStep.IDLE -> 3
  }
  Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().testTag("calibration")) {
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row {
        Text("내 휴대폰에 맞추기", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text("$stepNo / 3", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      LinearProgressIndicator(progress = { stepNo / 3f }, modifier = Modifier.fillMaxWidth())

      when (cal.step) {
        CalStep.INTRO -> {
          Text("휴대폰마다 센서가 조금씩 달라요", style = MaterialTheme.typography.headlineSmall)
          Text(
            "1. 평소처럼 10초 동안 걸어 다니며 일상 움직임을 재요.\n2. 휴대폰을 손에 쥐고 이불·소파 위에 가볍게 떨어뜨려 충격을 재요.\n3. 두 값을 비교해 알맞은 기준을 추천해요.",
            style = MaterialTheme.typography.bodyLarge,
          )
          Text("측정하는 동안에는 경보가 울리지 않아요. 딱딱한 바닥에 떨어뜨리지 마세요.", style = MaterialTheme.typography.bodyMedium, color = AppTheme.extra.warning)
        }
        CalStep.DAILY -> Measuring("평소처럼 걸어 다녀 주세요", "휴대폰을 주머니에 넣거나 손에 들고 걸으면 돼요.", cal.secondsLeft, cal.dailyPeak, live, settings)
        CalStep.IMPACT_READY -> {
          Text("이제 가벼운 충격을 줘 볼게요", style = MaterialTheme.typography.headlineSmall)
          Text("시작을 누르고 6초 안에 휴대폰을 이불이나 소파 위로 30cm쯤 높이에서 떨어뜨려 주세요.", style = MaterialTheme.typography.bodyLarge)
          Text("일상 움직임 최대: ${gForce2(cal.dailyPeak)}", style = MaterialTheme.typography.titleMedium)
        }
        CalStep.IMPACT -> Measuring("지금 떨어뜨려 주세요", "푹신한 곳 위로 가볍게 떨어뜨리면 돼요.", cal.secondsLeft, cal.impactPeak, live, settings)
        CalStep.RESULT -> {
          val rec = cal.recommendation
          Row {
            LabelValue("일상 움직임 최대", gForce2(cal.dailyPeak), Modifier.weight(1f))
            LabelValue("충격 최대", gForce2(cal.impactPeak), Modifier.weight(1f))
          }
          if (rec != null) {
            Panel(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
              Text("추천 기준", style = MaterialTheme.typography.titleMedium)
              Text("긴급 ${gForce(rec.second)} · 주의 ${gForce(rec.first)}", style = MaterialTheme.typography.headlineSmall)
              Text(
                "지금 기준: 긴급 ${gForce(settings.emergencyThreshold)} · 주의 ${gForce(settings.warningThreshold)}",
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          } else {
            Panel(color = AppTheme.extra.warningContainer, contentColor = AppTheme.extra.onWarningContainer) {
              Text("충격이 충분히 측정되지 않았어요", style = MaterialTheme.typography.titleMedium)
              Text("일상 움직임과 충격의 차이가 작아요. 조금 더 높은 곳에서 다시 떨어뜨려 주세요.", style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
        CalStep.IDLE -> Unit
      }

      Spacer(Modifier.height(8.dp))
      when (cal.step) {
        CalStep.INTRO -> Button(onClick = onNext, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium) { Text("측정 시작") }
        CalStep.IMPACT_READY -> Button(onClick = onNext, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium) { Text("시작") }
        CalStep.RESULT -> {
          if (cal.recommendation != null) {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium) { Text("추천 기준 적용") }
          }
          OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) { Text("충격 다시 재기") }
        }
        else -> Unit
      }
      TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(if (cal.step == CalStep.RESULT) "적용하지 않고 닫기" else "그만하기") }
    }
  }
}

@Composable
private fun Measuring(title: String, desc: String, secondsLeft: Int, peak: Float, live: Live, settings: Settings) {
  Text(title, style = MaterialTheme.typography.headlineSmall)
  Text(desc, style = MaterialTheme.typography.bodyLarge)
  Row {
    LabelValue("남은 시간", "${secondsLeft}초", Modifier.weight(1f))
    LabelValue("지금까지 최대", gForce2(peak), Modifier.weight(1f))
  }
  MotionChart(live.history, settings.warningThreshold, settings.emergencyThreshold, emptyText = "센서를 준비하고 있어요…")
}
