package com.namunkiro.domain.insurance

import com.namunkiro.domain.*
import com.namunkiro.domain.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.roundToInt

class DefaultInsuranceTierPlanner : InsuranceTierPlanner {
    override fun status(plan: InsurancePlan, currentOdometerKm: Double, history: List<DailyDistance>, today: LocalDate): InsuranceStatus {
        val days = ChronoUnit.DAYS.between(plan.periodStart, plan.periodEnd) + 1
        require(days > 0 && plan.tiersKm.isNotEmpty())
        val used = maxOf(0.0, currentOdometerKm - plan.startOdometerKm)
        val start = maxOf(plan.periodStart, today.minusDays(28))
        val count = ChronoUnit.DAYS.between(start, today).coerceAtLeast(0)
        val pace = if (count > 0) history.filter { !it.date.isBefore(start) && it.date.isBefore(today) }.sumOf { it.km } * 7 / count else 0.0
        val remainingDays = ChronoUnit.DAYS.between(today, plan.periodEnd.plusDays(1)).coerceAtLeast(0)
        val tiers = plan.tiersKm.sorted().map { tier ->
            val remaining = tier - used
            val crossing = when { remaining <= 0 -> today; pace > 0 -> today.plusDays(ceil(remaining * 7 / pace).toLong()); else -> null }
            TierStatus(tier, remaining, if (remainingDays > 0) remaining * 7 / remainingDays else 0.0, crossing?.takeIf { !it.isAfter(plan.periodEnd) })
        }
        val weekStart = maxOf(plan.periodStart, today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
        val weekEnd = minOf(today, plan.periodEnd)
        val thisWeekUsed = history.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }.sumOf { it.km }
        return InsuranceStatus((plan.tiersKm.min() * 7.0 / days).roundToInt(), used, pace, tiers, thisWeekUsedKm = thisWeekUsed)
    }
}
