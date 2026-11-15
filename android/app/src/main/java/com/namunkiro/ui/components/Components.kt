package com.namunkiro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.namunkiro.core.result.Source
import com.namunkiro.domain.DayForecast
import com.namunkiro.domain.DayKind
import com.namunkiro.ui.state.Screen
import com.namunkiro.ui.theme.Dimens
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

fun number(value: Number): String = if (value.toDouble().isFinite())
    NumberFormat.getIntegerInstance(Locale.KOREA).format(value.toDouble().roundToLong()).replace('-', '−') else "미확인"
fun km(value: Number?): String = value?.let { "${number(it)} km" } ?: "미확인"
fun won(value: Long?): String = value?.let { "${number(it)} 원" } ?: "미확인"
fun money(value: Long?): String = value?.let { if (kotlin.math.abs(it) >= 10000) "약 ${number(it / 10000.0)}만 원" else won(it) } ?: "미확인"

/** 입력 원본은 숫자로 유지하고 화면에만 천 단위 쉼표를 넣는다. */
object ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty() || !raw.all(Char::isDigit)) return TransformedText(text, OffsetMapping.Identity)
        val formatted = raw.reversed().chunked(3).joinToString(",").reversed()
        return TransformedText(AnnotatedString(formatted), object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset == 0) return 0
                var digits = 0
                formatted.forEachIndexed { index, char -> if (char != ',') { digits++; if (digits == offset) return index + 1 } }
                return formatted.length
            }
            override fun transformedToOriginal(offset: Int) = formatted.take(offset.coerceIn(0, formatted.length)).count { it != ',' }.coerceAtMost(raw.length)
        })
    }
}

@Composable fun SourceBadge(source: Source) {
    val label = when (source) {
        Source.OBSERVED -> "관측"; Source.INPUT -> "입력"; Source.ESTIMATED -> "추정"
        Source.UNCONFIRMED -> "미확인"; Source.SIMULATED -> "모의"
    }
    Surface(shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), color = MaterialTheme.colorScheme.surface) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall,
            color = if (source == Source.OBSERVED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun AppCard(title: String, modifier: Modifier = Modifier, source: Source? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(Dimens.CardRadius), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                source?.let { SourceBadge(it) }
            }
            content()
        }
    }
}
@Composable fun BigNumber(value: String, unit: String = "", compact: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(value, style = MaterialTheme.typography.displayLarge.copy(fontSize = if (compact) 58.sp else 78.sp), color = MaterialTheme.colorScheme.primary)
        if (unit.isNotEmpty()) Text(unit, Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.titleLarge)
    }
}
@Composable fun AppButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true) {
    if (primary) Button(onClick, modifier.heightIn(min = Dimens.Touch).widthIn(min = Dimens.Touch), enabled = enabled,
        shape = RoundedCornerShape(Dimens.ButtonRadius)) { Text(text) }
    else OutlinedButton(onClick, modifier.heightIn(min = Dimens.Touch).widthIn(min = Dimens.Touch), enabled = enabled,
        shape = RoundedCornerShape(Dimens.ButtonRadius)) { Text(text) }
}
@Composable fun DetailRow(label: String, value: String, source: Source? = null) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium)
            source?.let { SourceBadge(it) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}
@Composable fun Note(text: String) { Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable fun Tabs(selected: Screen, onSelect: (Screen) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Screen.entries.forEach { screen ->
            AppButton(screenLabel(screen), { onSelect(screen) }, primary = screen == selected)
        }
    }
}
fun screenLabel(screen: Screen): String = when (screen) {
    Screen.HOME -> "홈"; Screen.WEEK -> "이번 주"; Screen.FORECAST -> "예측"; Screen.EXIT -> "출구"; Screen.CONTRACT -> "계약"; Screen.INSURANCE -> "보험"
}
@Composable fun AdaptiveColumns(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < Dimens.CompactBreakpoint - Dimens.CompactPage * 2) Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap)) { left(); right() }
        else Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { left() }; Column(Modifier.weight(1f)) { right() }
        }
    }
}
@Composable fun BudgetRing(targetKm: Double, usedKm: Double, compact: Boolean = false) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val remaining = targetKm - usedKm
    Box(Modifier.size(if (compact) 290.dp else 360.dp).semantics { contentDescription = "주 목표 ${km(targetKm)}, 사용 ${km(usedKm)}, 남은 ${km(remaining)}" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            drawArc(track, -90f, 360f, false, style = Stroke(18.dp.toPx()))
            val progress = if (targetKm > 0) (usedKm / targetKm).coerceIn(0.0, 1.0) else 1.0
            drawArc(primary, -90f, (progress * 360).toFloat(), false, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Note(if (remaining < 0) "이번 주 목표보다 더 탐" else "이번 주 남은 거리")
            BigNumber(number(if (remaining < 0) -remaining else remaining), "km", compact)
        }
    }
}
@Composable fun WeekBars(days: List<DayForecast>, dailyBudgetKm: Double) {
    if (days.isEmpty()) { Note("요일별 예측을 준비하고 있어요."); return }
    val high = maxOf(500.0, days.maxOf { it.km })
    val actual = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outline
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(65.dp).height(300.dp), verticalArrangement = Arrangement.SpaceBetween) { Note(number(high)); Note(number(high / 2)); Note("0 km") }
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(260.dp).semantics { contentDescription = days.joinToString { "${it.date}: ${km(it.km)}" } }) {
                val slot = size.width / days.size
                drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx())
                val by = size.height * (1 - (dailyBudgetKm / high).coerceIn(0.0, 1.0)).toFloat()
                for (x in 0..20) drawLine(line, Offset(x * size.width / 21, by), Offset((x + .5f) * size.width / 21, by), 2.dp.toPx())
                days.forEachIndexed { i, day ->
                    val h = (size.height * (day.km / high).coerceIn(0.0, 1.0)).toFloat()
                    val offset = Offset(i * slot + slot * .22f, size.height - h)
                    val barSize = Size(slot * .56f, h)
                    drawRect(if (day.kind == DayKind.PLANNED) primary else actual, offset, barSize,
                        style = if (day.kind == DayKind.BASELINE) Stroke(2.dp.toPx()) else androidx.compose.ui.graphics.drawscope.Fill)
                }
            }
            Row(Modifier.fillMaxWidth()) { days.forEach { day -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text(listOf("월", "화", "수", "목", "금", "토", "일")[day.date.dayOfWeek.value - 1], style = MaterialTheme.typography.bodySmall); Text(number(day.km), style = MaterialTheme.typography.bodySmall) } } }
        }
    }
    Note("채움: 달린 거리 · 테두리: 평소 예상 · 청색: 등록 목적지")
    Note("점선: 하루 평균 목표 ${km(dailyBudgetKm)}")
}
@Composable fun RangeBand(lowKm: Double, highKm: Double, limitKm: Int, currentKm: Double? = null, progress: Double? = null) {
    val primary = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val max = maxOf(highKm * 1.1, limitKm * 1.1, 1.0)
    Note("만기 예상 ${km(lowKm)} – ${km(highKm)}")
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(100.dp).height(270.dp), verticalArrangement = Arrangement.SpaceBetween) { Note(number(max)); Note(number(max / 2)); Note("0 km") }
        Canvas(Modifier.weight(1f).height(270.dp).semantics { contentDescription = "누적 거리 예상 범위, 약정 ${km(limitKm)} 기준선" }) {
            val yLow = size.height * (1 - lowKm / max).toFloat()
            val yHigh = size.height * (1 - highKm / max).toFloat()
            val yLimit = size.height * (1 - limitKm / max).toFloat()
            drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx())
            drawLine(line, Offset(0f, 0f), Offset(0f, size.height), 2.dp.toPx())
            for (i in 0..18) drawLine(line, Offset(size.width * i / 19, yLimit), Offset(size.width * (i + .5f) / 19, yLimit), 2.dp.toPx())
            val anchorX = size.width * (progress ?: 0.0).coerceIn(0.0, 1.0).toFloat()
            val anchorY = size.height * (1 - (currentKm ?: 0.0) / max).coerceIn(0.0, 1.0).toFloat()
            val anchor = Offset(anchorX, anchorY)
            drawPath(Path().apply { moveTo(anchorX, anchorY); lineTo(size.width, yHigh); lineTo(size.width, yLow); close() }, primary.copy(alpha = .24f))
            drawLine(primary, anchor, Offset(size.width, (yHigh + yLow) / 2), 4.dp.toPx())
            if (currentKm != null) drawCircle(primary, 7.dp.toPx(), anchor)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Note("계약 시작"); Note("만기") }
    Note("세로: 누적 km · 가로: 계약 기간 · 점선: 약정 ${km(limitKm)}")
    if (currentKm != null) Note("점: 오늘까지 사용 ${km(currentKm)} · 띠는 오늘부터 만기 예상 범위 (지난 경로는 생략)")
}
