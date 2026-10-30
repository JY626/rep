package com.baoji.fitness.core.theme

import androidx.compose.ui.graphics.Color

// Apple OLED Dark Mode Palette
val AppleBlack = Color(0xFF000000)
val AppleGray6 = Color(0xFF1C1C1E) // Primary Card Surface
val AppleGray5 = Color(0xFF2C2C2E) // Input / Secondary Surface
val AppleGray4 = Color(0xFF3A3A3C) // Active States / Borders
val AppleHairline = Color(0x1AFFFFFF) // 10% White subtle border
// 35% White：纯黑上 10% 白只有 1.22:1，未勾选圆环/输入框边框几乎看不见。
// 凡是「未勾选圆环」「输入框边框」这类需要被看见的描边用这个，分隔线继续用 AppleHairline。
val AppleStrokeStrong = Color(0x59FFFFFF)
// 只用于装饰（分隔线/图标底/轨道），禁止用于文字。文字最低用 AppleTextSecondary。
val AppleDecor = Color(0xFF636366)

// Apple HIG Accents
val AppleFitnessGreen = Color(0xFF30D158) // Workout Completed / Overload
val AppleCautionOrange = Color(0xFFFF9F0A) // Warning / Challenge Weight
val AppleSystemBlue = Color(0xFF0A84FF) // Navigation / System Actions
val AppleSystemPurple = Color(0xFFBF5AF2) // AI / Intelligence Accent
val AppleSystemRed = Color(0xFFFF453A) // Delete / Drop

// Text Colors
val AppleTextPrimary = Color(0xFFFFFFFF)
val AppleTextSecondary = Color(0xFF8E8E93)
val AppleTextTertiary = Color(0xFF636366)
