package com.namunkiro.ui.v2.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.namunkiro.domain.*
import com.namunkiro.domain.model.Contract
import com.namunkiro.ui.components.km
import com.namunkiro.ui.components.number
import com.namunkiro.ui.v2.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.round

/** 스크롤 위치. 바뀌면 열린 말풍선을 닫는다(원칙 13e). */
val LocalChartDismissKey = staticCompositionLocalOf { 0 }
/** 지금 열린 말풍선의 대상 id. 한 번에 1개만 띄운다. 앱 최상위에서 rememberSaveable로 복원한다. */
val LocalActiveChartHint = staticCompositionLocalOf<MutableState<String?>> { mutableStateOf(null) }

/** 스크롤 시 닫기. 첫 표시·복원 때는 닫지 않는다. */
@Composable private fun DismissOnScroll(onDismiss: () -> Unit) {
    val key = LocalChartDismissKey.current
    var last by remember { mutableIntStateOf(key) }
    LaunchedEffect(key) { if (key != last) { last = key; onDismiss() } }
}

/** 탭 말풍선. 3초 무조작·바깥 탭·스크롤·[닫기]로 닫힌다. */
@Composable private fun Hint(text: String, dismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), fontSize = V2Tokens.BodySize, lineHeight = 34.sp, style = V2Numeric)
            V2Button("닫기", dismiss)
        }
    }
}

private fun DrawScope.textPaint(sizeSp: Float = 24f, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = sizeSp.sp.toPx()
    typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    fontFeatureSettings = "tnum"
}

/** 앵커 x에 맞춰 글자를 놓되 캔버스 밖으로 나가지 않게 한다. */
private fun DrawScope.anchoredText(text: String, anchorX: Float, baseline: Float, paint: Paint, color: Color, alignRight: Boolean, pad: Float) {
    paint.color = color.toArgb(); paint.textAlign = Paint.Align.LEFT
    val w = paint.measureText(text)
    val left = (if (alignRight) anchorX - w else anchorX).coerceIn(pad, (size.width - pad - w).coerceAtLeast(pad))
    drawContext.canvas.nativeCanvas.drawText(text, left, baseline, paint)
}

/**
 * 홈 거리 막대: 사용 → 만기 예상, 약정선, 초과(또는 여유) 구간과 리더선 말풍선.
 * 축 = max(만기 예상, 약정) × 1.15. 가로 드래그·탭·←/→ 키로 '만기에 X km면 정산 Y'를 스크럽한다(1,000km 스냅).
 */
@Composable fun DistanceBar(
    snapshot: BudgetSnapshot, contract: Contract,
    endKm: Double = snapshot.projectedEndKm, usedKm: Double = snapshot.usedKm,
    /** 사용 거리 출처 표기. 시연(모의 이력)이면 "모의", 실제 차량 기록이면 "관측". */
    usedSourceLabel: String = "관측",
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val v2 = v2Colors()
    val allowance = CostModel.allowanceKm(contract)
    val maximum = maxOf(endKm, allowance, 1.0) * 1.15
    var selected by rememberSaveable { mutableStateOf<Double?>(null) }
    val active = LocalActiveChartHint.current
    val shownSelected = selected?.takeIf { active.value == HINT_DISTANCE }
    DismissOnScroll { selected = null; if (active.value == HINT_DISTANCE) active.value = null }
    LaunchedEffect(shownSelected) { if (shownSelected != null) { delay(V2Tokens.HintAutoCloseMs); selected = null; if (active.value == HINT_DISTANCE) active.value = null } }
    fun pick(km: Double) { active.value = HINT_DISTANCE; selected = (round(km / 1000) * 1000).coerceIn(0.0, maximum) }
    fun pickX(x: Float, width: Float, margin: Float) = pick(((x - margin) / (width - margin * 2)).coerceIn(0f, 1f) * maximum)
    val extra = (endKm - allowance).coerceAtLeast(0.0)
    val fee = CostModel.distanceFee(endKm, contract)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(220.dp)
                .semantics {
                    contentDescription = "사용 ${km(usedKm)}, 만기 예상 ${km(endKm)}, 약정 ${km(allowance)}. " +
                        (if (extra > 0) "초과 ${km(extra)}, 정산 ${number(fee)}원." else "여유 ${km(allowance - endKm)}.") + " 좌우로 끌어 만기 거리 탐색"
                    stateDescription = shownSelected?.let { "만기에 ${km(it)}면 정산 ${v2Money(CostModel.distanceFee(it, contract))}" } ?: "만기 거리 탐색"
                    setProgress { pick(it.toDouble()); true }
                    progressBarRangeInfo = ProgressBarRangeInfo((shownSelected ?: endKm).toFloat().coerceIn(0f, maximum.toFloat()), 0f..maximum.toFloat())
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight -> { pick((shownSelected ?: endKm) + if (event.key == Key.DirectionRight) 1000 else -1000); true }
                        Key.Escape -> { selected = null; active.value = null; true }
                        else -> false
                    }
                }
                .focusable()
                .pointerInput(maximum) { detectTapGestures(onTap = { pickX(it.x, size.width.toFloat(), 12.dp.toPx()) }) }
                // 가로 드래그만 가로챈다. 세로 스크롤은 화면이 받는다.
                .pointerInput(maximum) {
                    detectHorizontalDragGestures(
                        onDragStart = { pickX(it.x, size.width.toFloat(), 12.dp.toPx()) },
                        onHorizontalDrag = { change, _ -> change.consume(); pickX(change.position.x, size.width.toFloat(), 12.dp.toPx()) },
                    )
                },
        ) {
            val pad = 12.dp.toPx(); val width = size.width - pad * 2
            fun x(v: Double) = pad + (v / maximum).toFloat().coerceIn(0f, 1f) * width
            val y = 80.dp.toPx(); val thick = 40.dp.toPx()
            val limitX = x(allowance); val endX = x(endKm); val usedX = x(usedKm)
            val fill = if (endKm > allowance) v2.over else v2.spare
            val paint = textPaint()
            val bold = textPaint(26f, bold = true)
            // 막대: 바탕 → 사용(관측) → 사용~만기(추정) → 초과/여유 구간
            drawLine(v2.track, Offset(pad, y), Offset(size.width - pad, y), thick, StrokeCap.Round)
            drawLine(v2.observed, Offset(pad, y), Offset(usedX, y), thick, StrokeCap.Round)
            drawLine(colors.primary.copy(alpha = .28f), Offset(usedX, y), Offset(endX, y), thick)
            drawLine(fill, Offset(minOf(limitX, endX), y), Offset(maxOf(limitX, endX), y), thick)
            // 약정선
            drawLine(colors.onSurface, Offset(limitX, y - thick / 2 - 14.dp.toPx()), Offset(limitX, y + thick / 2 + 10.dp.toPx()), 3.dp.toPx())
            // 1층: 만기 예상(리더선) · 2층: 약정(리더선). 두 라벨이 겹치지 않게 층을 나눈다.
            drawLine(fill, Offset(endX, y - thick / 2), Offset(endX, 26.dp.toPx()), 2.dp.toPx())
            anchoredText("만기 예상 ${km(endKm)}", endX + if (endX > size.width / 2) -8.dp.toPx() else 8.dp.toPx(), 24.dp.toPx(), bold, colors.onSurface, endX > size.width / 2, pad)
            drawLine(colors.onSurfaceVariant, Offset(limitX, y - thick / 2 - 14.dp.toPx()), Offset(limitX, 54.dp.toPx()), 2.dp.toPx())
            anchoredText("약정 ${km(allowance)}", limitX + if (limitX > endX) 8.dp.toPx() else -8.dp.toPx(), 52.dp.toPx(), paint, colors.onSurfaceVariant, limitX <= endX, pad)
            // 끌 수 있음을 알리는 손잡이 점
            drawCircle(colors.onSurface, 9.dp.toPx(), Offset(endX, y))
            // 사용 라벨
            anchoredText("● ${km(usedKm)} 사용 · $usedSourceLabel", pad, y + thick / 2 + 36.dp.toPx(), paint, v2.observed, false, pad)
            // 초과/여유 말풍선 + 리더선
            val mid = (limitX + endX) / 2
            val bubbleTop = 142.dp.toPx(); val bubbleH = 76.dp.toPx()
            val line1 = if (extra > 0 && fee > 0) "초과 ${km(kotlin.math.round(extra))} × ${number(contract.unitPricePerKm)}원" else if (extra > 0) "거리 정산 적용 없음" else "여유 ${km(allowance - endKm)}"
            val line2 = if (extra > 0 && fee > 0) "= ${number(displayDistanceFee(endKm, contract))}원 · 거리 반올림" else if (extra > 0) "0원" else "만기에 남는 거리"
            val bubbleW = (maxOf(bold.measureText(line1), paint.measureText(line2)) + 32.dp.toPx()).coerceAtMost(width)
            val left = (mid - bubbleW / 2).coerceIn(pad, size.width - pad - bubbleW)
            drawLine(fill, Offset(mid, y + thick / 2), Offset(mid, bubbleTop), 2.dp.toPx())
            drawRoundRect(colors.surfaceVariant, Offset(left, bubbleTop), Size(bubbleW, bubbleH), CornerRadius(12.dp.toPx()))
            drawRoundRect(fill, Offset(left, bubbleTop), Size(bubbleW, bubbleH), CornerRadius(12.dp.toPx()), style = Stroke(2.dp.toPx()))
            anchoredText(line1, left + 16.dp.toPx(), bubbleTop + 30.dp.toPx(), bold, fill, false, pad)
            anchoredText(line2, left + 16.dp.toPx(), bubbleTop + 64.dp.toPx(), paint, colors.onSurface, false, pad)
            // 스크럽 커서
            shownSelected?.let { sel ->
                val sx = x(sel)
                drawLine(colors.onSurface, Offset(sx, y - thick / 2 - 6.dp.toPx()), Offset(sx, y + thick / 2 + 6.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)
            }
        }
        shownSelected?.let {
            val selFee = CostModel.distanceFee(it, contract)
            Hint("만기에 ${km(it)}면 정산 " + (if (selFee == 0L) "0원" else "${number(selFee)}원") + " [추정]") { selected = null; active.value = null }
        }
    }
}

/** 이번 주 목표 링. 탭하면 '이번 주 X / 목표 Y' 말풍선. */
@Composable fun MiniRing(targetKm: Double, usedKm: Double, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val v2 = v2Colors()
    val active = LocalActiveChartHint.current
    val shown = open && active.value == HINT_RING
    LaunchedEffect(shown) { if (shown) { delay(V2Tokens.HintAutoCloseMs); open = false; if (active.value == HINT_RING) active.value = null } }
    DismissOnScroll { open = false }
    val over = usedKm > targetKm
    val sweep by animateFloatAsState((360 * (usedKm / targetKm.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)).toFloat(), v2Spec(), label = "ring")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(160.dp).clickable(role = Role.Button) { open = !shown; active.value = if (open) HINT_RING else null }
                .semantics { contentDescription = "이번 주 목표 ${km(targetKm)}, 사용 ${km(usedKm)}. 탭하면 상세" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                drawArc(v2.track, -90f, 360f, false, style = Stroke(12.dp.toPx()))
                drawArc(if (over) v2.over else v2.spare, -90f, sweep, false, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(number(abs(targetKm - usedKm)), fontSize = 48.sp, style = V2Numeric, color = colors.onSurface)
                V2Caption(if (over) "km 더 탐" else "km 남음")
            }
        }
        if (shown) Hint("이번 주 ${km(usedKm)} / 목표 ${km(targetKm)}") { open = false; active.value = null }
    }
}

/** 부모 폭 안에서 frac 위치(트랙 기준)에 가운데 정렬. 트랙은 손잡이 폭/2만큼 안쪽에서 시작한다. */
private fun Modifier.atTrack(frac: Float, inset: Dp) = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0))
    val w = constraints.maxWidth
    val center = inset.roundToPx() + ((w - inset.roundToPx() * 2) * frac).toInt()
    layout(w, p.height) { p.place((center - p.width / 2).coerceIn(0, (w - p.width).coerceAtLeast(0)), 0) }
}

/**
 * 주간 페이스 슬라이더(미리보기, 저장하지 않음).
 * 시작값 = CostModel.currentWeeklyPace → 히어로 금액과 같은 값에서 출발(연속).
 * 0원 눈금 = CostModel.zeroFeePace(제목 '주 N km 줄이면 0원'과 같은 값). 5km 스냅, 0원·현재 눈금에는 자석처럼 붙는다.
 * 손잡이 76dp. value == null 이면 '현재'.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PaceSlider(snapshot: BudgetSnapshot, contract: Contract, value: Double?, onChange: (Double?) -> Unit) {
    val current = CostModel.currentWeeklyPace(snapshot)
    val zero = CostModel.zeroFeePace(snapshot, contract)
    val minimum = minOf(current - 100, (zero ?: current.toInt()) - 40.0).coerceAtLeast(0.0).let { round(it / 10) * 10 }
    val maximum = maxOf(current + 100, (zero ?: 0) + 40.0).let { round(it / 10) * 10 }
    val shown = value ?: current
    val colors = MaterialTheme.colorScheme
    val v2 = v2Colors()
    fun frac(v: Double) = ((v - minimum) / (maximum - minimum)).toFloat().coerceIn(0f, 1f)
    val inset = V2Tokens.Touch / 2
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("주 몇 km로 타면?", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Text(if (value == null) "현재 주 ${number(current)} km" else "주 ${number(shown)} km 가정", fontSize = V2Tokens.ButtonSize, style = V2Numeric, color = colors.primary)
        }
        // 눈금 라벨: 0원(제목과 같은 값)과 현재
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            zero?.takeIf { it > 0 && it >= minimum }?.let { z ->
                Text("0원 · ${number(z)}", Modifier.atTrack(frac(z.toDouble()), inset), fontSize = V2Tokens.BodySize, style = V2Numeric, color = v2.spare)
            }
        }
        Box(Modifier.fillMaxWidth()) {
            Slider(
                value = shown.toFloat().coerceIn(minimum.toFloat(), maximum.toFloat()),
                onValueChange = { raw ->
                    val next = when {
                        zero != null && abs(raw - zero) < 2.5 -> zero.toDouble()
                        abs(raw - current) < 2.5 -> null
                        else -> (round(raw / 5) * 5.0).coerceIn(minimum, maximum)
                    }
                    onChange(next)
                },
                valueRange = minimum.toFloat()..maximum.toFloat(),
                modifier = Modifier.fillMaxWidth().heightIn(min = V2Tokens.Touch).semantics {
                    stateDescription = "주 ${number(shown)} km, 만기 정산 ${v2Money(CostModel.feeAtPace(snapshot, contract, shown))}"
                },
                thumb = {
                    Box(Modifier.size(V2Tokens.Touch), contentAlignment = Alignment.Center) {
                        Surface(Modifier.size(34.dp), shape = CircleShape, color = colors.primary, border = BorderStroke(3.dp, colors.surface)) {}
                    }
                },
            )
            // 눈금 표시(트랙 위 세로선)
            Canvas(Modifier.matchParentSize()) {
                val ins = inset.toPx(); val w = size.width - ins * 2
                fun tick(v: Double, color: Color) {
                    val x = ins + w * frac(v)
                    drawLine(color, Offset(x, size.height / 2 - 22.dp.toPx()), Offset(x, size.height / 2 + 22.dp.toPx()), 3.dp.toPx())
                }
                zero?.takeIf { it > 0 && it >= minimum }?.let { tick(it.toDouble(), v2.spare) }
                tick(current, colors.onSurfaceVariant)
            }
        }
        Box(Modifier.fillMaxWidth().height(40.dp)) {
            Text("현재", Modifier.atTrack(frac(current), inset), fontSize = V2Tokens.BodySize, color = colors.onSurfaceVariant)
        }
        val delta = shown - current
        val feeDelta = CostModel.feeAtPace(snapshot, contract, shown) - CostModel.heroFee(snapshot, contract)
        if (value != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                V2Caption("${signed(delta)} km/주 → 만기 정산 ${signedMoney(feeDelta)}", Modifier.weight(1f), color = colors.onSurface)
                V2Button("현재로", { onChange(null) })
            }
        } else V2Caption("끌어서 미리보기 · 저장하지 않아요")
    }
}

private fun signed(v: Double): String = (if (v > 0) "+" else if (v < 0) "−" else "") + number(abs(v))
private fun signedMoney(v: Long): String = if (v == 0L) "0원" else (if (v > 0) "+" else "−") + v2Impact(abs(v))

/**
 * 이번 주 요일 막대. 관측(채움)·평소(외곽선)·예정(강조 채움).
 * 0원 목표를 하루 평균 점선으로, 예정 이동 위에 '+X만' 칩. 막대 탭 → 말풍선. 값 변화는 300ms 보간.
 */
@Composable fun WeekBars(
    days: List<DayForecast>, targetKm: Double?, plannedFee: Map<LocalDate, Long> = emptyMap(),
    weekdayAvgKm: Double? = null, weekendAvgKm: Double? = null,
) {
    if (days.isEmpty()) { V2Caption("주행 기록을 확인하고 있어요"); return }
    val colors = MaterialTheme.colorScheme
    val v2 = v2Colors()
    val high = maxOf(50.0, days.maxOf { it.km } * 1.15, (targetKm ?: 0.0) / 7 * 1.3)
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val active = LocalActiveChartHint.current
    val shownIndex = selected?.takeIf { active.value == HINT_WEEK }
    LaunchedEffect(shownIndex) { if (shownIndex != null) { delay(V2Tokens.HintAutoCloseMs); selected = null; if (active.value == HINT_WEEK) active.value = null } }
    DismissOnScroll { selected = null }
    val names = listOf("월", "화", "수", "목", "금", "토", "일")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEachIndexed { index, day ->
            val kmShown by animateFloatAsState(day.km.toFloat(), v2Spec(), label = "bar$index")
            val kindLabel = when (day.kind) { DayKind.ACTUAL -> "관측"; DayKind.BASELINE -> "평소"; DayKind.PLANNED -> "예정" }
            Column(
                Modifier.weight(1f).height(360.dp)
                    .clickable(role = Role.Button) { selected = index; active.value = HINT_WEEK }
                    .semantics { contentDescription = "${names[day.date.dayOfWeek.value - 1]}요일 $kindLabel ${km(day.km)}" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Canvas(Modifier.fillMaxSize()) {
                        val barWidth = size.width * .58f
                        val h = (kmShown / high * size.height).toFloat().coerceIn(0f, size.height)
                        val topLeft = Offset((size.width - barWidth) / 2, size.height - h)
                        when (day.kind) {
                            DayKind.ACTUAL -> drawRect(v2.observed, topLeft, Size(barWidth, h), style = Fill)
                            DayKind.PLANNED -> drawRect(v2.over, topLeft, Size(barWidth, h), style = Fill)
                            DayKind.BASELINE -> drawRect(v2.guide, topLeft, Size(barWidth, h), style = Stroke(2.dp.toPx()))
                        }
                        if (shownIndex == index) drawRect(colors.onSurface, Offset(2.dp.toPx(), 0f), Size(size.width - 4.dp.toPx(), size.height), style = Stroke(2.dp.toPx()))
                        targetKm?.let {
                            val y = size.height * (1 - (it / 7 / high).coerceIn(0.0, 1.0)).toFloat()
                            drawLine(v2.spare, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx())))
                        }
                    }
                    plannedFee[day.date]?.takeIf { it > 0 }?.let { fee ->
                        Surface(Modifier.align(Alignment.TopCenter), shape = RoundedCornerShape(10.dp), color = v2.over) {
                            Text("+${v2Impact(fee).removeSuffix(" 원")}", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = V2Tokens.BodySize, style = V2Numeric, color = colors.surface)
                        }
                    }
                }
                Text(names[day.date.dayOfWeek.value - 1], fontSize = V2Tokens.BodySize)
                Text(number(day.km), fontSize = V2Tokens.BodySize, style = V2Numeric, color = colors.onSurfaceVariant)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        V2Caption("■ 관측  □ 평소  ■ 예정", color = colors.onSurfaceVariant)
        targetKm?.let { V2Caption("┄ 0원 목표 하루 ${number(it / 7)} km (주 ${number(it)})", color = v2.spare) }
    }
    shownIndex?.let { i ->
        days.getOrNull(i)?.let { d ->
            val usual = if (d.date.dayOfWeek.value >= 6) weekendAvgKm else weekdayAvgKm
            val kind = when (d.kind) { DayKind.ACTUAL -> "관측"; DayKind.BASELINE -> "평소"; DayKind.PLANNED -> "예정" }
            val extra = if (d.kind == DayKind.PLANNED && usual != null) " (평소 ${km(usual)})" else ""
            val fee = plannedFee[d.date]?.takeIf { it > 0 }?.let { " · 만기 정산 +${number(it)}원" } ?: ""
            Hint("${v2Date(d.date)} · $kind ${km(d.km)}$extra$fee") { selected = null; active.value = null }
        }
    }
}

/**
 * 예측 범위 띠. 세로축은 잘라서(≋) 범위가 선 하나로 뭉개지지 않게 한다.
 * 과거 일별 누적 원자료가 없는 구간은 점선으로 구분한다.
 * 띠 양 끝 = 서로 다른 가정(최근 4주 습관 / 계약 평균). 통계 신뢰구간이 아니다.
 */
@Composable fun RangeBand(snapshot: BudgetSnapshot, contract: Contract, result: ForecastResult, modifier: Modifier = Modifier, usedSourceLabel: String = "관측") {
    val colors = MaterialTheme.colorScheme
    val v2 = v2Colors()
    val allowance = CostModel.allowanceKm(contract)
    val lowKm = minOf(result.endKmLow, result.endKmHigh); val highKm = maxOf(result.endKmLow, result.endKmHigh)
    val axisTop = maxOf(highKm, allowance) * 1.05
    val axisBottom = (kotlin.math.floor(minOf(lowKm, allowance) * .85 / 10000) * 10000).coerceAtLeast(0.0)
    val cut = snapshot.usedKm < axisBottom
    val lowFee = CostModel.distanceFee(lowKm, contract); val highFee = CostModel.distanceFee(highKm, contract)
    val feeRange = v2MoneyRange(lowFee, highFee)
    val pointKm by animateFloatAsState((result.endKmPoint ?: snapshot.projectedEndKm).toFloat(), v2Spec(), label = "point")
    val lowShown by animateFloatAsState(lowKm.toFloat(), v2Spec(), label = "low")
    val highShown by animateFloatAsState(highKm.toFloat(), v2Spec(), label = "high")
    Canvas(
        modifier.fillMaxWidth().height(320.dp).semantics {
            contentDescription = "만기 예상 ${km(lowKm)}에서 ${km(highKm)}, 정산 $feeRange. 약정 ${km(allowance)}. 오늘까지 ${km(snapshot.usedKm)} 관측. 점선은 일별 기록 전 구간, 오늘 이후 채움은 예측 범위. 세로축 ${km(axisBottom)}부터 ${km(axisTop)}까지 확대."
        },
    ) {
        val left = 16.dp.toPx(); val right = size.width - 16.dp.toPx()
        val top = 44.dp.toPx(); val bottom = size.height - 48.dp.toPx()
        fun y(k: Double) = (bottom - (bottom - top) * ((k - axisBottom) / (axisTop - axisBottom))).toFloat().coerceIn(top, bottom)
        val nowX = left + (right - left) * (snapshot.elapsedDays.toFloat() / snapshot.contractDays.coerceAtLeast(1)).coerceIn(.08f, .85f)
        val paint = textPaint(); val bold = textPaint(26f, true)
        // 축
        drawLine(v2.guide, Offset(left, bottom), Offset(right, bottom), 2.dp.toPx())
        if (cut) {
            // 축 끊김 표시 ≋
            val bx = left + 24.dp.toPx()
            for (i in 0..1) drawLine(v2.guide, Offset(bx - 10.dp.toPx() + i * 10.dp.toPx(), bottom + 10.dp.toPx()), Offset(bx + i * 10.dp.toPx(), bottom - 10.dp.toPx()), 2.dp.toPx())
        }
        // 약정선
        val limitY = y(allowance)
        drawLine(colors.onSurfaceVariant, Offset(left, limitY), Offset(right, limitY), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
        anchoredText("약정 ${km(allowance)}", left, limitY - 10.dp.toPx(), paint, colors.onSurfaceVariant, false, left)
        // 점선은 일별 관측 경로가 아니라 시작과 오늘을 연결하는 안내선이다.
        drawLine(v2.observed, Offset(left, bottom), Offset(nowX, y(snapshot.usedKm)), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))
        anchoredText("일별 기록 전 구간", left, top - 12.dp.toPx(), paint, colors.onSurfaceVariant, false, left)
        anchoredText("오늘 이후 · 예측 범위", right, top - 12.dp.toPx(), paint, colors.primary, true, left)
        // 관측점(오늘)
        val nowY = if (cut) bottom else y(snapshot.usedKm)
        // 범위 띠: 오늘 → 만기 범위
        val path = Path().apply { moveTo(nowX, nowY); lineTo(right, y(lowShown.toDouble())); lineTo(right, y(highShown.toDouble())); close() }
        drawPath(path, colors.primary.copy(alpha = .38f))
        drawLine(colors.primary, Offset(nowX, nowY), Offset(right, y(lowShown.toDouble())), 2.dp.toPx())
        drawLine(colors.primary, Offset(nowX, nowY), Offset(right, y(highShown.toDouble())), 2.dp.toPx())
        drawLine(colors.primary, Offset(nowX, nowY), Offset(right, y(pointKm.toDouble())), 4.dp.toPx())
        drawLine(colors.primary, Offset(right, y(lowShown.toDouble())), Offset(right, y(highShown.toDouble())), 10.dp.toPx(), StrokeCap.Round)
        drawCircle(v2.observed, 9.dp.toPx(), Offset(nowX, nowY))
        anchoredText("오늘 ${km(snapshot.usedKm)} · $usedSourceLabel", nowX - 16.dp.toPx(), nowY - 36.dp.toPx(), paint, v2.observed, true, left)
        // 띠 끝 금액 라벨
        anchoredText(feeRange, right - 20.dp.toPx(), y(highShown.toDouble()) - 16.dp.toPx(), bold, colors.primary, true, left)
        // 시간축 라벨
        anchoredText("시작 0 km", left, size.height - 12.dp.toPx(), paint, colors.onSurfaceVariant, false, left)
        anchoredText("오늘", nowX - 24.dp.toPx(), size.height - 12.dp.toPx(), paint, colors.onSurfaceVariant, false, left)
        anchoredText("만기", right, size.height - 12.dp.toPx(), paint, colors.onSurfaceVariant, true, left)
    }
}

private const val HINT_DISTANCE = "distance"
private const val HINT_RING = "ring"
private const val HINT_WEEK = "week"
