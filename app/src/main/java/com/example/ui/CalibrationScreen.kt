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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
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
  val scheme = MaterialTheme.colorScheme
  val stepNo = when (cal.step) {
    CalStep.INTRO -> 0
    CalStep.DAILY -> 1
    CalStep.IMPACT_READY, CalStep.IMPACT -> 2
    CalStep.RESULT, CalStep.IDLE -> 3
  }
  Column(Modifier.fillMaxSize().background(scheme.surface).systemBarsPadding().testTag("calibration")) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "닫기") }
      Text("내 휴대폰에 맞추기", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
      Text("$stepNo / 3", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp))
    }
    LinearProgressIndicator(
      progress = { stepNo / 3f }, strokeCap = StrokeCap.Butt, gapSize = 0.dp, drawStopIndicator = {},
      trackColor = scheme.outlineVariant, modifier = Modifier.fillMaxWidth().height(2.dp),
    )
    Column(
      Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
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
          StatRow(listOf("일상 움직임 최대" to gForce2(cal.dailyPeak)))
        }
        CalStep.IMPACT -> Measuring("지금 떨어뜨려 주세요", "푹신한 곳 위로 가볍게 떨어뜨리면 돼요.", cal.secondsLeft, cal.impactPeak, live, settings)
        CalStep.RESULT -> {
          val rec = cal.recommendation
          Text(if (rec != null) "측정이 끝났어요" else "충격이 충분히 측정되지 않았어요", style = MaterialTheme.typography.headlineSmall)
          StatRow(listOf("일상 움직임 최대" to gForce2(cal.dailyPeak), "충격 최대" to gForce2(cal.impactPeak)))
          HorizontalDivider(color = scheme.outlineVariant)
          if (rec != null) {
            StatRow(listOf("추천 긴급 기준" to gForce(rec.second), "추천 주의 기준" to gForce(rec.first)))
            Caption("지금 기준: 긴급 ${gForce(settings.emergencyThreshold)} · 주의 ${gForce(settings.warningThreshold)}")
          } else {
            Text("일상 움직임과 충격의 차이가 작아요. 조금 더 높은 곳에서 다시 떨어뜨려 주세요.", style = MaterialTheme.typography.bodyMedium, color = AppTheme.extra.warning)
          }
        }
        CalStep.IDLE -> Unit
      }
    }
    HorizontalDivider(color = scheme.outlineVariant)
    Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      when (cal.step) {
        CalStep.INTRO -> AppButton("측정 시작", onNext, Modifier.fillMaxWidth(), height = 56.dp)
        CalStep.IMPACT_READY -> AppButton("시작", onNext, Modifier.fillMaxWidth(), height = 56.dp)
        CalStep.RESULT -> {
          if (cal.recommendation != null) AppButton("추천 기준 적용", onNext, Modifier.fillMaxWidth(), height = 56.dp)
          AppOutlinedButton("충격 다시 재기", onRetry, Modifier.fillMaxWidth())
        }
        else -> Box(Modifier.fillMaxWidth().height(4.dp))
      }
      Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AppTextButton(if (cal.step == CalStep.RESULT) "적용하지 않고 닫기" else "그만하기", onClose, color = scheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun Measuring(title: String, desc: String, secondsLeft: Int, peak: Float, live: Live, settings: Settings) {
  Text(title, style = MaterialTheme.typography.headlineSmall)
  Text(desc, style = MaterialTheme.typography.bodyLarge)
  StatRow(listOf("남은 시간" to "${secondsLeft}초", "지금까지 최대" to gForce2(peak)))
  Spacer(Modifier.height(4.dp))
  MotionChart(live.history, settings.warningThreshold, settings.emergencyThreshold, emptyText = "센서를 준비하고 있어요…")
}
