package com.namunkiro.domain.budget

import com.namunkiro.domain.*
import com.namunkiro.domain.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.ceil

class DefaultWeeklyBudgetEngine : WeeklyBudgetEngine {
    companion object { const val RECOVERY_THRESHOLD = 0.60 }

    override fun snapshot(contract: Contract, history: List<DailyDistance>, splits: List<SplitPlan>, today: LocalDate): BudgetSnapshot {
        val days = (ChronoUnit.DAYS.between(contract.startDate, contract.endDate) + 1).toInt()
        require(days > 0 && contract.totalKm >= 0)
        val elapsed = ChronoUnit.DAYS.between(contract.startDate, today).toInt().coerceIn(0, days)
        val rows = history.filter { !it.date.isBefore(contract.startDate) && !it.date.isAfter(today) && !it.date.isAfter(contract.endDate) }
        val used = rows.sumOf { it.km }
        val base = (contract.totalKm * 7.0 / days).roundToInt()
        val currentStart = monday(today)
        var cursor = monday(contract.startDate)
        var carry = 0
        var allocated = 0
        var previous: WeekBudget? = null
        var current = WeekBudget(currentStart, 0, 0, 0, 0, 0.0)
        while (!cursor.isAfter(currentStart) && !cursor.isAfter(contract.endDate)) {
            val first = maxOf(cursor, contract.startDate)
            val last = minOf(cursor.plusDays(6), contract.endDate)
            val count = ChronoUnit.DAYS.between(first, last).toInt() + 1
            val weeklyBase = if (last == contract.endDate) contract.totalKm - allocated else (base * count / 7.0).roundToInt()
            allocated += weeklyBase
            val active = splits.filter { it.weeks > 0 && !cursor.isBefore(it.firstWeekStart) && cursor.isBefore(it.firstWeekStart.plusWeeks(it.weeks.toLong())) }
            // 직전 주 초과를 분할로 맡겼으면 시작 주에는 이월로 다시 차감하지 않는다.
            if (active.any { it.firstWeekStart == cursor }) carry = 0
            val deduction = active.sumOf {
                val index = ChronoUnit.WEEKS.between(it.firstWeekStart, cursor).toInt()
                ((index + 1) * it.totalKm.toDouble() / it.weeks).roundToInt() - (index * it.totalKm.toDouble() / it.weeks).roundToInt()
            }
            val actual = rows.filter { !it.date.isBefore(cursor) && it.date.isBefore(cursor.plusWeeks(1)) }.sumOf { it.km }
            val week = WeekBudget(cursor, weeklyBase, carry, deduction, weeklyBase + carry - deduction, actual)
            if (cursor == currentStart) current = week else previous = week
            // 직전 주 기본 예산과 실주행 차이만 이월하고, 과거 이월·분할 상환은 연쇄하지 않는다.
            carry = (week.baseKm - actual).roundToInt()
            cursor = cursor.plusWeeks(1)
        }
        val remaining = days - elapsed
        val required = if (remaining > 0) (contract.totalKm - used) * 7 / remaining else 0.0
        val recentStart = maxOf(contract.startDate, today.minusDays(28))
        val recentDays = ChronoUnit.DAYS.between(recentStart, today).toInt()
        val recentRows = rows.filter { !it.date.isBefore(recentStart) && it.date.isBefore(today) }
        val recent = if (recentDays > 0 && recentRows.isNotEmpty()) recentRows.sumOf { it.km } * 7 / recentDays else null
        val balance = contract.totalKm * elapsed.toDouble() / days - used
        val verdict = when {
            required <= 0 || (recent != null && required < recent * RECOVERY_THRESHOLD) -> RecoveryVerdict.HARD_TO_RECOVER
            balance >= 0 -> RecoveryVerdict.ON_TRACK
            else -> RecoveryVerdict.RECOVERABLE
        }
        val projected = if (elapsed > 0) used * days / elapsed else used
        val fee = if (contract.type in listOf(ContractType.FINANCE_LEASE, ContractType.UNLIMITED)) 0L
            else (maxOf(0.0, projected - contract.totalKm - (contract.deductKm ?: 0)) * contract.unitPricePerKm).roundToLong()
        val onBudgetEndKm = used + base.toDouble() * remaining / 7.0
        val onBudgetFee = if (contract.type in listOf(ContractType.FINANCE_LEASE, ContractType.UNLIMITED)) 0L else
            (maxOf(0.0, onBudgetEndKm - contract.totalKm - (contract.deductKm ?: 0)) * contract.unitPricePerKm).roundToLong()
        val zeroFeePace = if (remaining > 0) (contract.totalKm + (contract.deductKm ?: 0) - used) * 7 / remaining else null
        val unused = if (elapsed > 0) (contract.totalKm - projected).takeIf { it > 0 } else null
        val annualPace = if (elapsed > 0) used * 365.0 / elapsed else null
        val currentAnnualKm = contract.totalKm * 365.0 / days
        val recommended = if (elapsed >= 90 && annualPace != null) {
            (ceil(annualPace * 1.1 / 5000) * 5000).toInt().takeIf { it < currentAnnualKm }
        } else null
        return BudgetSnapshot(today, days, elapsed, used, balance, current, previous, required, recent, verdict, projected, fee, contract.deductKm == null,
            onBudgetEndFee = onBudgetFee, zeroFeeWeeklyKm = zeroFeePace,
            projectedUnusedKm = unused, annualPaceKm = annualPace, recommendedAnnualKm = recommended)
    }

    override fun splitOptions(contract: Contract, excessKm: Int): List<SplitOption> {
        require(excessKm >= 0)
        val days = ChronoUnit.DAYS.between(contract.startDate, contract.endDate) + 1
        val base = (contract.totalKm * 7.0 / days).roundToInt()
        return listOf(1, 4, 8).map { val part = (excessKm.toDouble() / it).roundToInt(); SplitOption(it, part, base - part) }
    }

    private fun monday(date: LocalDate) = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
