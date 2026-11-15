package com.namunkiro.ui.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.namunkiro.domain.*
import com.namunkiro.ui.components.km
import com.namunkiro.ui.preview.PreviewFixtures
import com.namunkiro.ui.state.*
import com.namunkiro.ui.theme.NamunkiroTheme
import java.time.format.DateTimeFormatter

internal fun exitLabel(kind: ExitKind) = when (kind) {
    ExitKind.RETURN_AT_END -> "반납"
    ExitKind.BUY_AND_KEEP -> "인수 후 보유"
    ExitKind.BUY_AND_SELL -> "인수 후 매각"
    ExitKind.TRANSFER_NOW -> "승계"
    ExitKind.TERMINATE_NOW -> "지금 해지"
}

@Composable fun ExitScreenV2(state: AppUiState, actions: AppActions) {
    val comparison = state.exit
    val contract = state.contract
    val snapshot = state.budget
    if (comparison == null || contract == null || snapshot == null) {
        V2Panel { V2Title("출구 비교를 준비하고 있어요"); V2Caption("계약과 주행 기록이 모이면 보여 드려요.") }
        return
    }
    V2Panel {
        val best = comparison.options.firstOrNull { it.kind == comparison.cheapest }
        V2Caption("만기까지 타기 중 최저${if (comparison.cheapestTentative) " · 확인 전" else ""}")
        if (best?.netCost != null) { V2Title(exitLabel(best.kind)); V2Money(best.netCost); if (best.netCostIsLowerBound) V2Caption("최소 금액 · 빠진 비용 별도") }
        else V2Title("조건을 확인하면 비교할 수 있어요")
        V2Caption("${km(snapshot.usedKm)} 사용 → 만기 예상 ${km(snapshot.projectedEndKm)}")
        DistanceProof(snapshot.usedKm, snapshot.projectedEndKm, CostModel.allowanceKm(contract))
        val excess = (snapshot.projectedEndKm - CostModel.allowanceKm(contract)).coerceAtLeast(0.0)
        V2Caption(if (excess > 0) "약정 대비 초과 ${km(excess)} → 거리 정산 ${v2Money(CostModel.heroFee(snapshot, contract))}" else "만기에 약정 ${km(-minOf(0.0, snapshot.projectedEndKm - CostModel.allowanceKm(contract)))} 남음 · 거리 정산 ${v2Money(CostModel.heroFee(snapshot, contract))}")
        BoxWithConstraints {
            V2Columns(v2Compact(maxWidth), left = {
                ExitGroupBars("만기까지 타고 나서", comparison.options.filter { it.kind.group == ExitGroup.KEEP_UNTIL_END }, comparison.cheapest)
            }, right = {
                ExitGroupBars("지금 나가면", comparison.options.filter { it.kind.group == ExitGroup.LEAVE_NOW }, comparison.cheapestLeaveNow)
            })
        }
        V2Caption("남은 월 이용료 ${comparison.remainingFees?.let(::v2Money) ?: "미확인"} · 지금 나가면 대체 이동비도 비교해요")
        V2Caption("▧ 최소 금액 · 묶음별 독립 축")
    }
    V2Panel {
        V2Title("출구 달력")
        V2Caption("비교 종료 ${v2CalendarDate(comparison.compareUntil)}")
        state.calendar.sortedBy { it.date }.forEach { event ->
            V2Caption("${v2CalendarDate(event.date)} · ${event.label}")
        }
    }
}

@Composable private fun DistanceProof(used: Double, end: Double, allowance: Double) {
    val colors = v2Colors()
    val max = maxOf(used, end, allowance, 1.0) * 1.1
    Canvas(Modifier.fillMaxWidth().height(64.dp).semantics { contentDescription = "주행 근거: 사용 거리에서 만기 예상 거리까지, 세로선은 약정거리" }) {
        val y = size.height / 3
        val h = size.height / 3
        drawRect(colors.track, Offset(0f, y), Size(size.width, h))
        drawRect(colors.observed, Offset(0f, y), Size((used / max * size.width).toFloat().coerceIn(0f, size.width), h))
        val from = (used / max * size.width).toFloat().coerceIn(0f, size.width)
        val to = (end / max * size.width).toFloat().coerceIn(from, size.width)
        drawRect(colors.over.copy(alpha = .45f), Offset(from, y), Size(to - from, h))
        val marker = (allowance / max * size.width).toFloat().coerceIn(0f, size.width)
        drawLine(colors.guide, Offset(marker, 0f), Offset(marker, size.height), 3.dp.toPx())
    }
}

@Composable private fun ExitGroupBars(title: String, options: List<ExitOption>, cheapest: ExitKind?) {
    val maximum = (options.filter { it.available }.mapNotNull { it.netCost }.maxOrNull() ?: 0L).coerceAtLeast(0).toDouble() * 1.15
    val colors = v2Colors()
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        V2Title(title)
        HorizontalDivider()
        options.forEach { option ->
            val label = when {
                !option.available -> "해당 없음"
                option.netCost == null -> "미확인"
                option.netCostIsLowerBound -> "최소 ${v2Money(option.netCost)} +"
                else -> v2Money(option.netCost)
            }
            V2Caption("${exitLabel(option.kind)} · $label")
            val value = option.netCost
            Canvas(Modifier.fillMaxWidth().height(44.dp).semantics { contentDescription = "${exitLabel(option.kind)} $label" }) {
                drawRect(colors.track)
                if (value != null && option.available) {
                    val width = (value.coerceAtLeast(0) / maximum.coerceAtLeast(1.0) * size.width).toFloat().coerceIn(0f, size.width)
                    val fill = if (option.kind == cheapest) colors.over else colors.observed
                    drawRect(fill, size = Size(width, size.height))
                    if (option.netCostIsLowerBound) {
                        // 빗금은 막대 내부에서만 그린다.
                        var x = -size.height
                        while (x < width) {
                            val startX = maxOf(0f, x)
                            val endX = minOf(width, x + size.height)
                            if (endX > startX) drawLine(colors.track, Offset(startX, size.height - (startX - x)), Offset(endX, size.height - (endX - x)), 3.dp.toPx())
                            x += 12.dp.toPx()
                        }
                    }
                }
            }
        }
        if (maximum > 0) V2Caption("0원 — ${v2Money(maximum.toLong())}")
        else if (options.any { it.available && it.netCost != null }) V2Caption("확인된 비용 0원")
        else V2Caption("조건 확인 후 비교할 수 있어요")
    }
}

@Preview(widthDp = 840, heightDp = 1210)
@Composable private fun ExitV2Preview() { NamunkiroTheme { Column { ExitScreenV2(PreviewFixtures.exit, NoopActions) } } }
