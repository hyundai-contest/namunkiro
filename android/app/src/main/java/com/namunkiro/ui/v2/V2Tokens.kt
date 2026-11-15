package com.namunkiro.ui.v2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.namunkiro.core.result.Source
import com.namunkiro.ui.components.number
import com.namunkiro.ui.state.AppUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** v2 화면 공통 치수·색 토큰. 색은 다크(차량 기본)·라이트 두 벌을 둔다. */
object V2Tokens {
    val Touch = 76.dp          // 모든 조작 요소 최소 크기
    val Gap = 24.dp
    val Radius = 24.dp
    val ButtonRadius = 14.dp
    val CompactBreakpoint = 900.dp   // 반쪽 창(<900dp)은 1열
    val BodySize = 24.sp             // 본문 최소
    val ButtonSize = 26.sp
    val HeroSize = 72.sp
    const val MotionMs = 300         // 값 전환 기본 길이(250~350ms)
    const val HintAutoCloseMs = 3000L
    const val UndoVisibleMs = 5000L

    /** 하위 호환: 다크 기준 녹색. 화면에서는 [v2Colors]().spare를 쓴다. */
    val Green = Color(0xFF8AC9A5)
}

/** 의미 색. over = 초과(원이 붙는 구간), spare = 여유(남는 거리), muted = 관측·보조. */
@Immutable data class V2Colors(
    val over: Color,
    val spare: Color,
    val track: Color,
    val observed: Color,
    val guide: Color,
    val chip: Color,
    val onChip: Color,
)

private val DarkV2 = V2Colors(
    over = Color(0xFF81B5FF), spare = Color(0xFF8AC9A5), track = Color(0xFF2A2F35),
    observed = Color(0xFFAFB6BF), guide = Color(0xFF8A929C), chip = Color(0xFF22262B), onChip = Color(0xFFF1F3F5),
)
private val LightV2 = V2Colors(
    over = Color(0xFF235DA8), spare = Color(0xFF2E7D55), track = Color(0xFFE1E6EC),
    observed = Color(0xFF555E69), guide = Color(0xFF6B7480), chip = Color(0xFFE9EDF2), onChip = Color(0xFF171A20),
)

/** 현재 테마(배경 밝기)에 맞는 v2 의미 색. */
@Composable fun v2Colors(): V2Colors = if (MaterialTheme.colorScheme.background.luminance() < .5f) DarkV2 else LightV2

/** 숫자는 항상 고정폭(tabular). */
val V2Numeric = TextStyle(fontFeatureSettings = "tnum")

/** 큰 금액: 만 원 정수로 통일(원칙 §6). 0은 "0원", 1만 원 미만은 원 단위(“0만 원” 금지). */
fun v2Money(value: Long): String = when {
    value == 0L -> "0원"
    kotlin.math.abs(value) < 10000 -> "${number(value)}원"
    else -> "${number(value / 10000.0)}만 원"
}

/** 금액 범위 "17만~24만 원". 같으면 한 값. */
fun v2MoneyRange(a: Long, b: Long): String {
    val lo = minOf(a, b); val hi = maxOf(a, b)
    return if (v2Money(lo) == v2Money(hi)) v2Money(hi) else "${v2Money(lo).removeSuffix(" 원")}~${v2Money(hi)}"
}

/** 증감(+3.9만 원 등): 소수 1자리까지. 1만 원 미만은 원 단위. */
fun v2Impact(value: Long): String {
    if (kotlin.math.abs(value) < 10000) return "${number(value)}원"
    val formatted = java.text.NumberFormat.getNumberInstance(Locale.KOREA).apply { maximumFractionDigits = 1 }.format(value / 10000.0)
    return "${formatted.replace('-', '−')}만 원"
}

fun v2Date(value: LocalDate): String = value.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일(E)", Locale.KOREAN))

/** 화면 값이 시드·가짜 게이트웨이에서 왔는지. 그렇다면 [모의]를 붙인다. */
fun AppUiState.isSimulated(): Boolean = historySimulated || odometer?.source == Source.SIMULATED

@Composable fun V2Panel(modifier: Modifier = Modifier, gap: androidx.compose.ui.unit.Dp = V2Tokens.Gap, padding: androidx.compose.ui.unit.Dp = 28.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(V2Tokens.Radius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(gap), content = content)
    }
}

@Composable fun V2Button(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true) {
    val size = modifier.heightIn(min = V2Tokens.Touch).widthIn(min = V2Tokens.Touch)
    val shape = RoundedCornerShape(V2Tokens.ButtonRadius)
    if (primary) Button(onClick, size, enabled = enabled, shape = shape) { Text(label, fontSize = V2Tokens.ButtonSize, style = V2Numeric) }
    else OutlinedButton(onClick, size, enabled = enabled, shape = shape) { Text(label, fontSize = V2Tokens.ButtonSize, style = V2Numeric) }
}

@Composable fun V2Title(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier, style = MaterialTheme.typography.headlineMedium)
}

@Composable fun V2Caption(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, modifier, fontSize = V2Tokens.BodySize, lineHeight = 34.sp, color = color, style = V2Numeric)
}

/** 히어로 금액. 공백은 줄바꿈 금지 공백으로 바꿔 숫자와 단위가 떨어지지 않게 한다. */
@Composable fun V2Money(value: Long, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        v2Money(value).replace(" ", " "), modifier,
        style = MaterialTheme.typography.displayLarge.copy(fontSize = V2Tokens.HeroSize, fontFeatureSettings = "tnum"),
        color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** 작은 숫자 묶음(값 + 라벨). 이번 주 상단 3칸 등에 쓴다. */
@Composable fun V2Stat(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"), color = color, maxLines = 1)
        V2Caption(label)
    }
}

/** [모의] 표시. 실제 SDK 값과 시드 값을 구분한다(시연 원칙). */
@Composable fun V2SimTag(modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = v2Colors().chip) {
        Text("[모의]", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = V2Tokens.BodySize, color = v2Colors().onChip)
    }
}

/** 값 변화 안내 칩(“+12 km → +1,200원” 등). */
@Composable fun V2Chip(text: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, Modifier.padding(horizontal = 20.dp, vertical = 16.dp), fontSize = V2Tokens.ButtonSize, style = V2Numeric, fontWeight = FontWeight.Medium)
    }
}

/** 반쪽 창(창 폭 < 900dp) 여부. 앱 최상위가 창 폭으로 제공한다. null이면 화면이 자기 폭으로 판단한다. */
val LocalV2Compact = androidx.compose.runtime.staticCompositionLocalOf<Boolean?> { null }

/** 창 폭 기준 1열/2열 판단. LocalV2Compact가 없으면(Preview 등) 주어진 폭으로 판단한다. */
@Composable fun v2Compact(fallbackWidth: androidx.compose.ui.unit.Dp): Boolean =
    LocalV2Compact.current ?: (fallbackWidth < V2Tokens.CompactBreakpoint - 64.dp)

@Composable fun V2Columns(compact: Boolean, left: @Composable () -> Unit, right: @Composable () -> Unit) {
    if (compact) Column(verticalArrangement = Arrangement.spacedBy(V2Tokens.Gap)) { left(); right() }
    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(V2Tokens.Gap), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) { left() }; Column(Modifier.weight(1f)) { right() }
    }
}
