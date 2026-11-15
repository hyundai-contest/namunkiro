package com.namunkiro.app

import com.namunkiro.domain.model.*
import com.namunkiro.ui.state.*
import com.namunkiro.vehicle.*
import java.time.LocalDate

/** Android 없이 검증하는 입력·차량 신호 정책. ViewModel과 입력 화면이 같은 검증을 쓴다. */
object AppPolicies {
    const val SIGNAL_TIMEOUT_MS = 10_000L
    fun validEndDate(date: LocalDate?, today: LocalDate) = date != null && date.isAfter(today) && !date.isAfter(today.plusYears(20))
    fun validTotalKm(km: Int?) = km != null && km in 1..1_000_000
    fun validUnitPrice(won: Int?) = won != null && won in 1..100_000
    /** 만기 후 평균 외삽의 분모가 계속 늘며 정산이 줄어드는 것을 막는다. */
    fun forecastDate(c: Contract, today: LocalDate): LocalDate = minOf(today, c.endDate.plusDays(1))
    fun gate(vehicle: VehicleState, held: Boolean): Gate = when {
        vehicle.uxRestricted == true || vehicle.gear !in listOf(Gear.UNKNOWN, Gear.PARK) -> Gate.DRIVING_LOCK
        held -> Gate.ANOMALY_HOLD
        else -> Gate.NORMAL
    }
    fun connection(vehicle: VehicleState, elapsedMs: Long): VehicleConnectionStatus = when {
        vehicle.error != null || vehicle.odometerCapability == Capability.ERROR || vehicle.gearCapability == Capability.ERROR -> VehicleConnectionStatus.ERROR
        vehicle.odometerCapability == Capability.NOT_SUPPORTED || vehicle.gearCapability == Capability.NOT_SUPPORTED -> VehicleConnectionStatus.NOT_SUPPORTED
        vehicle.gear != Gear.UNKNOWN || vehicle.uxRestricted != null -> VehicleConnectionStatus.READY
        elapsedMs >= SIGNAL_TIMEOUT_MS -> VehicleConnectionStatus.TIMED_OUT
        else -> VehicleConnectionStatus.WAITING
    }
    fun validInsurancePlan(plan: InsurancePlan): Boolean = !plan.periodEnd.isBefore(plan.periodStart) &&
        plan.startOdometerKm.isFinite() && plan.startOdometerKm >= 0 && plan.tiersKm.isNotEmpty() &&
        plan.tiersKm.all { it in 1..1_000_000 } && plan.tiersKm == plan.tiersKm.distinct().sorted()

    /** 잘못된 수정은 저장하지 않는다. 과거에 끝난 계약도 숫자·조건은 수정할 수 있다. */
    fun editContract(c: Contract, field: ContractField, value: String?, today: LocalDate): Contract? {
        val v = value?.trim()
        return when (field) {
            ContractField.END_DATE -> v?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { validEndDate(it, today) && !it.isBefore(c.startDate) }?.let { c.copy(endDate = it) }
            ContractField.TOTAL_KM -> v?.toIntOrNull()?.takeIf(::validTotalKm)?.let { c.copy(totalKm = it) }
            ContractField.UNIT_PRICE -> v?.toIntOrNull()?.takeIf(::validUnitPrice)?.let { c.copy(unitPricePerKm = it) }
            ContractField.DEDUCT_KM -> if (v == null) c.copy(deductKm = null) else v.toIntOrNull()?.takeIf { it in 0..1_000_000 }?.let { c.copy(deductKm = it) }
            ContractField.BUYOUT_PRICE -> if (v == null) c.copy(buyoutPrice = null) else v.toLongOrNull()?.takeIf { it in 0..1_000_000_000_000L }?.let { c.copy(buyoutPrice = it) }
            ContractField.BUYOUT_WAIVES_DISTANCE_FEE -> if (v == null) c.copy(buyoutWaivesDistanceFee = null) else v.toBooleanStrictOrNull()?.let { c.copy(buyoutWaivesDistanceFee = it) }
            ContractField.UNDERUSE_REFUND -> if (v == null) c.copy(underuseRefund = null) else v.toBooleanStrictOrNull()?.let { c.copy(underuseRefund = it) }
            ContractField.TYPE -> v?.let { runCatching { ContractType.valueOf(it) }.getOrNull() }?.let { c.copy(type = it) }
        }
    }
}
