package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 어르신도 읽기 편하도록 기본보다 한 단계 크게 잡았습니다.
val Typography = Typography(
  displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
  headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
  headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
  titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
  titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
  titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
  bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp),
  bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
  bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
  labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
  labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
  labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)
