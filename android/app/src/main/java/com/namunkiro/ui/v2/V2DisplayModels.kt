package com.namunkiro.ui.v2

import com.namunkiro.domain.model.Contract
import kotlin.math.round
import com.namunkiro.domain.ForecastResult
import com.namunkiro.domain.BudgetSnapshot

/** 말풍선의 정수 거리와 산식을 일치시킨다. 대표 금액 계산은 원래 CostModel 그대로다. */
fun displayDistanceFee(endKm: Double, contract: Contract): Long {
    val extra = round((endKm - CostModel.allowanceKm(contract)).coerceAtLeast(0.0))
    return CostModel.distanceFee(CostModel.allowanceKm(contract) + extra, contract)
}

/** 엔진의 계약 평균(계획 포함) 점과 다른 끝을 짝지어 범위 라벨이 같은 끝값을 쓰게 한다. */
fun forecastAssumptionKm(result: ForecastResult, snapshot: BudgetSnapshot): Pair<Double, Double> {
    val average = result.endKmPoint ?: snapshot.projectedEndKm
    val recent = if (kotlin.math.abs(result.endKmHigh - average) >= kotlin.math.abs(result.endKmLow - average)) result.endKmHigh else result.endKmLow
    return average to recent
}

/** 홈과 예측의 계약 평균을 같은 점추정치로 표시한다. */
fun averageDisplaySnapshot(state: com.namunkiro.ui.state.AppUiState): BudgetSnapshot? =
    state.budget?.let { it.copy(projectedEndKm = state.forecast?.endKmPoint ?: it.projectedEndKm) }

fun forecastBasisLabel(state: com.namunkiro.ui.state.AppUiState, base: String): String {
    val today = state.budget?.today ?: state.today
    val end = state.contract?.endDate ?: today
    val hasPlan = state.trips.any { !it.date.isBefore(today) && !it.date.isAfter(end) } ||
        state.scenarios.any { !it.from.isAfter(end) && (it.until == null || !it.until.isBefore(today)) }
    return if (hasPlan) "$base · 계획 반영" else "$base 기준"
}
