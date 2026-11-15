package com.namunkiro.ui.v2

import com.namunkiro.domain.BudgetSnapshot
import com.namunkiro.domain.model.Contract
import com.namunkiro.domain.model.ContractType
import kotlin.math.max

/**
 * 화면의 모든 'km → 원' 계산이 쓰는 단일 모델(조정자 소유 공유 파일).
 * 히어로 금액·페이스 슬라이더·+km 한계 비용·0원 눈금이 같은 식을 쓰게 해서
 * "조작과 화면 숫자는 같은 계산 모델" 원칙(UX_PRINCIPLES §5-1)을 지킨다.
 *
 * 모델: 만기 예상 거리 = 지금까지 사용 + 주간 페이스 × 남은 주
 *       만기 거리 정산 = max(0, 만기 예상 − 약정 − 공제) × 단가
 * 현재 주간 페이스는 엔진의 projectedEndKm(계약 시작 이후 평균 유지)과 일치하도록 역산한다.
 */
object CostModel {

    fun remainingWeeks(s: BudgetSnapshot): Double = max(0.0, (s.contractDays - s.elapsedDays) / 7.0)

    /** 엔진 projectedEndKm과 같은 결과를 내는 현재 주간 페이스(km/주). */
    fun currentWeeklyPace(s: BudgetSnapshot): Double {
        val w = remainingWeeks(s)
        return if (w <= 0.0) 0.0 else (s.projectedEndKm - s.usedKm) / w
    }

    fun endKmAtPace(s: BudgetSnapshot, weeklyPaceKm: Double): Double = s.usedKm + weeklyPaceKm * remainingWeeks(s)

    fun allowanceKm(c: Contract): Double = (c.totalKm + (c.deductKm ?: 0)).toDouble()

    fun distanceFee(endKm: Double, c: Contract): Long {
        if (c.type == ContractType.FINANCE_LEASE || c.type == ContractType.UNLIMITED) return 0L
        return (max(0.0, endKm - allowanceKm(c)) * c.unitPricePerKm).toLong()
    }

    /** 히어로 금액. 슬라이더 시작값(currentWeeklyPace)에서 같은 값이 나온다. */
    fun heroFee(s: BudgetSnapshot, c: Contract): Long = distanceFee(s.projectedEndKm, c)

    fun feeAtPace(s: BudgetSnapshot, c: Contract, weeklyPaceKm: Double): Long = distanceFee(endKmAtPace(s, weeklyPaceKm), c)

    /** 0원 눈금 = 이 페이스 이하면 만기 거리 정산 0원(정수 km, 내림). 제목 '주 N km 줄이면 0원'도 이 값을 쓴다. */
    fun zeroFeePace(s: BudgetSnapshot, c: Contract): Int? {
        val w = remainingWeeks(s)
        if (w <= 0.0) return null
        return kotlin.math.floor((allowanceKm(c) - s.usedKm) / w).toInt()
    }

    /** 0원이 되려면 지금보다 주당 줄여야 하는 km(올림). 이미 0원이면 0. */
    fun weeklyCutToZero(s: BudgetSnapshot, c: Contract): Int? {
        val zero = zeroFeePace(s, c) ?: return null
        return max(0, kotlin.math.ceil(currentWeeklyPace(s) - zero).toInt())
    }

    /** 방금 달린 km의 한계 비용(외삽 없이 그 km만큼): 초과 상태면 km × 단가. */
    fun marginalFee(s: BudgetSnapshot, c: Contract, addedKm: Double): Long =
        distanceFee(s.projectedEndKm + addedKm, c) - distanceFee(s.projectedEndKm, c)

    /** 주당 deltaKm 바꿀 때 만기 정산 변화(음수 = 절약). */
    fun feeDeltaForWeeklyChange(s: BudgetSnapshot, c: Contract, deltaKm: Double): Long =
        feeAtPace(s, c, currentWeeklyPace(s) + deltaKm) - heroFee(s, c)
}
