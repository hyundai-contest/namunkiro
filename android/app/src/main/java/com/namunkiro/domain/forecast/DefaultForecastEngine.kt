package com.namunkiro.domain.forecast

import com.namunkiro.domain.*
import com.namunkiro.domain.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToLong

class DefaultForecastEngine : ForecastEngine {
    override fun forecast(contract: Contract, history: List<DailyDistance>, trips: List<PlannedTrip>, scenarios: List<LifeScenario>, today: LocalDate): ForecastResult {
        val rows = history.filter { !it.date.isBefore(contract.startDate) && !it.date.isAfter(today) && !it.date.isAfter(contract.endDate) }
        val recent = rows.filter { !it.date.isBefore(today.minusDays(28)) && it.date.isBefore(today) }
        fun weekend(date: LocalDate) = date.dayOfWeek.value >= 6
        fun avg(isWeekend: Boolean): Double {
            val selected = recent.filter { weekend(it.date) == isWeekend && (it.km > it.gapKm || it.gapKm == 0.0) }
            return if (selected.isEmpty()) 0.0 else selected.sumOf { maxOf(0.0, it.km - it.gapKm) } / selected.map { it.date }.distinct().size
        }
        val weekday = avg(false)
        val weekend = avg(true)
        fun prediction(date: LocalDate): Pair<Double, DayKind> {
            val planned = trips.filter { it.date == date }
            val baseline = if (weekend(date)) weekend else weekday
            val km = if (planned.isNotEmpty()) planned.filterNot { it.otherCar }.sumOf { it.km }.toDouble() else baseline
            val delta = scenarios.filter { !date.isBefore(it.from) && (it.until == null || !date.isAfter(it.until)) }.sumOf { it.dailyDeltaKm }
            return maxOf(0.0, km + delta) to if (planned.isNotEmpty()) DayKind.PLANNED else DayKind.BASELINE
        }
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val week = (0L..6L).map { offset ->
            val date = monday.plusDays(offset)
            val actual = rows.filter { it.date == date }
            if (date.isBefore(contract.startDate) || date.isAfter(contract.endDate)) DayForecast(date, 0.0, DayKind.BASELINE)
            else if (!date.isAfter(today)) DayForecast(date, actual.sumOf { it.km }, DayKind.ACTUAL)
            else prediction(date).let { DayForecast(date, it.first, it.second) }
        }
        val used = rows.sumOf { it.km }
        val elapsed = ChronoUnit.DAYS.between(contract.startDate, today).coerceAtLeast(0)
        val remaining = ChronoUnit.DAYS.between(maxOf(today.plusDays(1), contract.startDate), contract.endDate.plusDays(1)).coerceAtLeast(0)
        var recentEnd = used
        var adjustment = 0.0
        repeat(remaining.toInt()) { index ->
            val date = maxOf(today.plusDays(1), contract.startDate).plusDays(index.toLong())
            val predicted = prediction(date).first
            recentEnd += predicted
            adjustment += predicted - if (weekend(date)) weekend else weekday
        }
        val longEnd = if (elapsed > 0) used * (ChronoUnit.DAYS.between(contract.startDate, contract.endDate) + 1) / elapsed + adjustment else recentEnd
        fun fee(km: Double) = if (contract.type in listOf(ContractType.FINANCE_LEASE, ContractType.UNLIMITED)) 0L else
            (maxOf(0.0, km - contract.totalKm - (contract.deductKm ?: 0)) * contract.unitPricePerKm).roundToLong()
        val low = minOf(recentEnd, longEnd)
        val high = maxOf(recentEnd, longEnd)
        val impacts = trips.map { trip ->
            if (!trip.date.isAfter(today) || trip.date.isBefore(contract.startDate) || trip.date.isAfter(contract.endDate)) {
                TripImpact(trip.id, 0.0, 0L)
            } else {
                val baseline = if (weekend(trip.date)) weekend else weekday
                val extra = (if (trip.otherCar) 0.0 else trip.km.toDouble()) - baseline
                // 같은 이동을 평소 거리로 돌렸을 때와 비교해 약정 초과 진입분만 요금으로 환산한다.
                TripImpact(trip.id, extra, fee(longEnd) - fee(longEnd - extra))
            }
        }
        val dailyAllowance = contract.totalKm.toDouble() / (ChronoUnit.DAYS.between(contract.startDate, contract.endDate) + 1)
        val classifiedWeekday = rows.filterNot { weekend(it.date) }.sumOf { maxOf(0.0, it.km - it.gapKm - dailyAllowance) }
        val classifiedWeekend = rows.filter { weekend(it.date) }.sumOf { maxOf(0.0, it.km - it.gapKm - dailyAllowance) }
        val gaps = rows.sumOf { it.gapKm }
        val total = classifiedWeekday + classifiedWeekend + gaps
        // 만기 범위 전체가 초과 정산 0원이면 과거의 특정 요일을 초과 원인으로 안내하지 않는다.
        val showExcessShares = total > 0 && fee(high) > 0
        return ForecastResult(rows.map { it.date }.distinct().size < 14, weekday, weekend, week, week.sumOf { it.km }, low, high, fee(low), fee(high),
            if (showExcessShares) classifiedWeekday / total else 0.0, if (showExcessShares) classifiedWeekend / total else 0.0, if (showExcessShares) gaps / total else 0.0,
            tripImpacts = impacts, endKmPoint = longEnd, endFeePoint = fee(longEnd))
    }
}


