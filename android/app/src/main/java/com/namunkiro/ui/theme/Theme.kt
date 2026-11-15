package com.namunkiro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val DarkColors = darkColorScheme(
    primary = Color(0xFF81B5FF), onPrimary = Color(0xFF102039),
    background = Color(0xFF101214), onBackground = Color(0xFFF1F3F5),
    surface = Color(0xFF191C20), onSurface = Color(0xFFF1F3F5),
    surfaceVariant = Color(0xFF22262B), onSurfaceVariant = Color(0xFFAFB6BF),
    outline = Color(0xFF393E45),
)
val LightColors = lightColorScheme(
    primary = Color(0xFF235DA8), onPrimary = Color.White,
    background = Color(0xFFF4F5F7), onBackground = Color(0xFF171A20),
    surface = Color.White, onSurface = Color(0xFF171A20),
    surfaceVariant = Color(0xFFE9EDF2), onSurfaceVariant = Color(0xFF555E69),
    outline = Color(0xFFBDC5CF),
)
private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = weight, fontFeatureSettings = "tnum",
)
val AppTypography = Typography(
    displayLarge = type(78, 90, FontWeight.SemiBold), headlineLarge = type(48, 59, FontWeight.Bold),
    headlineMedium = type(38, 48, FontWeight.Bold), titleLarge = type(30, 42, FontWeight.SemiBold),
    titleMedium = type(28, 41, FontWeight.Medium), bodyLarge = type(28, 41),
    bodyMedium = type(24, 35), bodySmall = type(22, 32), labelLarge = type(28, 38, FontWeight.Medium),
    labelSmall = type(22, 32),
)
object Dimens {
    val Touch = 76.dp
    val Page = 48.dp
    val CompactPage = 32.dp
    val Gap = 24.dp
    val CardPadding = 30.dp
    val CardRadius = 20.dp
    val ButtonRadius = 12.dp
    val CompactBreakpoint = 900.dp
}

/** 기본은 차량용 다크이며 시스템 라이트 테마도 지원한다. */
@Composable fun NamunkiroTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = AppTypography, content = content)
}
