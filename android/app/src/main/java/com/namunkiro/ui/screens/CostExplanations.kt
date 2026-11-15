package com.namunkiro.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.namunkiro.domain.ForecastResult
import com.namunkiro.domain.model.Contract
import com.namunkiro.ui.components.*
import com.namunkiro.ui.state.AppUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

fun preciseMoney(value: Long): String = if (abs(value) >= 10000) {
    val amount = java.text.NumberFormat.getNumberInstance(Locale.KOREA).apply { maximumFractionDigits = 1 }.format(abs(value) / 10000.0)
    "약 ${amount}만 원"
} else won(abs(value))

fun readableDate(date: LocalDate, today: LocalDate? = null): String {
    val label = date.format(DateTimeFormatter.ofPattern("M월 d일(E)", Locale.KOREAN))
    val days = today?.let { ChronoUnit.DAYS.between(it, date) }
    return label + when { days == null -> ""; days > 0 -> " · ${days}일 남음"; days == 0L -> " · 오늘"; else -> "" }
}
fun tripLabel(label: String) = if (label.equals("Gangneung", ignoreCase = true)) "강릉 왕복" else label

/** 거리·원 단위 표시는 가장 가까운 정수로 반올림하며 계산 원본값은 변경하지 않는다. */
@Composable fun DistanceFeeFormula(contract: Contract?, endKm: Double?) {
    if (contract == null || endKm == null || contract.type in listOf(com.namunkiro.domain.model.ContractType.FINANCE_LEASE, com.namunkiro.domain.model.ContractType.UNLIMITED)) return
    val deduction = contract.deductKm
    val excess = (endKm - contract.totalKm - (deduction ?: 0)).coerceAtLeast(0.0)
    Note("예상 만기 ${km(endKm)} − 약정 ${km(contract.totalKm)}${deduction?.takeIf { it > 0 }?.let { " − 공제 ${km(it)}" } ?: ""} → 초과 ${km(excess)} × ${number(contract.unitPricePerKm)}원/km")
    if (deduction == null) Note("공제 거리 확인 전에는 공제 0으로 가정한 금액이에요.")
}
@Composable fun AssumptionFees(forecast: ForecastResult) {
    val pointKm = forecast.endKmPoint ?: return
    val longIsLow = abs(pointKm - forecast.endKmLow) <= abs(pointKm - forecast.endKmHigh)
    val recentFee = if (longIsLow) forecast.endFeeHigh else forecast.endFeeLow
    val longFee = forecast.endFeePoint ?: return
    Note("최근 4주 습관이면 ${money(recentFee)} · 계약 평균이면 ${money(longFee)}")
}
@Composable fun ActionableFees(state: AppUiState) {
    val b = state.budget ?: return
    b.zeroFeeWeeklyKm?.let { zero ->
        if (zero >= 0) {
            Text("초과 0원 목표 주 ${km(zero)}", style = MaterialTheme.typography.titleMedium)
            b.recentWeeklyAvgKm?.let { recent -> if (recent > zero) Note("최근보다 주 ${km(recent - zero)} 줄이는 계획이에요.") }
        } else Note("이미 탄 거리 때문에 남은 기간을 타지 않아도 거리 정산이 생길 수 있어요.")
    }
    if (b.deductUnconfirmed) Note("공제 거리를 확인하면 위 계획도 달라질 수 있어요.")
}

/** 예정 이동을 뺀 나머지 약정을 남은 전체 기간의 주 목표로 환산한다. */
fun targetAfterTrip(state: AppUiState, plannedKm: Int): Double? {
    val c = state.contract ?: return null
    val b = state.budget ?: return null
    val weeks = (b.contractDays - b.elapsedDays) / 7.0
    if (weeks <= 0 || c.type in listOf(com.namunkiro.domain.model.ContractType.FINANCE_LEASE, com.namunkiro.domain.model.ContractType.UNLIMITED)) return null
    return (c.totalKm + (c.deductKm ?: 0) - b.usedKm - plannedKm) / weeks
}

@Composable fun ReductionImpact(state: AppUiState) {
    val b = state.budget ?: return
    val c = state.contract ?: return
    if (c.type in listOf(com.namunkiro.domain.model.ContractType.FINANCE_LEASE, com.namunkiro.domain.model.ContractType.UNLIMITED)) return
    val expectedFee = state.forecast?.endFeePoint ?: b.projectedDistanceFee ?: return
    val weeks = (b.contractDays - b.elapsedDays) / 7.0
    if (expectedFee <= 0 || weeks <= 0) return
    val impact = (10 * weeks * c.unitPricePerKm).toLong().coerceAtMost(expectedFee)
    Note("주 10km 덜 타면 만기 약 −${preciseMoney(impact).removePrefix("약 ")} (현재 페이스 기준, 0원까지)")
}
@Composable fun MarginalCost(state: AppUiState) {
    val c = state.contract ?: return
    val f = state.forecast ?: return
    if (c.type in listOf(com.namunkiro.domain.model.ContractType.FINANCE_LEASE, com.namunkiro.domain.model.ContractType.UNLIMITED)) return
    val limit = c.totalKm + (c.deductKm ?: 0)
    val cost = when { f.endKmLow > limit -> "${number(c.unitPricePerKm)}원"; f.endKmHigh <= limit -> "0원"; else -> "0–${number(c.unitPricePerKm)}원" }
    Note("지금 1km 더 타면 만기 거리 정산 $cost 추가 예상${if (c.deductKm == null) " · 공제 확인 전" else ""}")
}
@Composable fun PaceComparison(required: Double, recent: Double) {
    val max = maxOf(required, recent, 1.0)
    val primary = MaterialTheme.colorScheme.primary
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    Note("0에서 같은 축으로 비교 · 회복 목표 ${km(required)}/주 / 최근 ${km(recent)}/주")
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        drawLine(primary, Offset(0f, 24.dp.toPx()), Offset(size.width * (required.coerceAtLeast(0.0) / max).toFloat(), 24.dp.toPx()), 22.dp.toPx())
        drawLine(neutral, Offset(0f, 72.dp.toPx()), Offset(size.width * (recent.coerceAtLeast(0.0) / max).toFloat(), 72.dp.toPx()), 22.dp.toPx())
    }
    if (recent > required && recent > 0) Note("최근보다 약 ${number((recent - required) / recent * 100)}% 줄여야 해요.")
}
