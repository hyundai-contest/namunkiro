package com.namunkiro.domain

import com.namunkiro.domain.budget.DefaultWeeklyBudgetEngine
import com.namunkiro.domain.forecast.DefaultForecastEngine
import com.namunkiro.domain.exit.DefaultExitCalculator
import com.namunkiro.domain.insurance.DefaultInsuranceTierPlanner
import com.namunkiro.domain.model.*
import com.namunkiro.demo.PersonaSeeds
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DomainEnginesTest {
    private val start = LocalDate.of(2026, 11, 1)
    private val contract = Contract(ContractType.LONG_TERM_RENT, start, LocalDate.of(2030, 10, 31), 60000, 100, 0.0, deductKm = 0)
    private val engine = DefaultWeeklyBudgetEngine()
    private fun history(days: Int, total: Double) = (0 until days).map { DailyDistance(start.plusDays(it.toLong()), total / days) }

    private fun jeongHistory(): List<DailyDistance> {
        val today = LocalDate.of(2028, 3, 16)
        val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        val lastWeek = LocalDate.of(2028, 3, 6)..LocalDate.of(2028, 3, 12)
        val totalWeight = dates.filterNot { it in lastWeek }.sumOf { if (it.dayOfWeek.value >= 6) 4 else 1 }
        return dates.map { date ->
            val km = if (date in lastWeek) { if (date.dayOfWeek.value >= 6) 100.0 else 0.0 }
                else 11800.0 * (if (date.dayOfWeek.value >= 6) 4 else 1) / totalWeight
            DailyDistance(date, km)
        }
    }

    @Test fun jeongUnderuseMetricsAndPositiveCarry() {
        val snapshot = engine.snapshot(contract, jeongHistory(), emptyList(), LocalDate.of(2028, 3, 16))
        assertEquals(12000.0, snapshot.usedKm, 0.000001)
        assertEquals(0L, snapshot.projectedDistanceFee)
        assertEquals(34994.011976048, snapshot.projectedEndKm, 0.000001)
        assertEquals(25005.988023952, snapshot.projectedUnusedKm!!, 0.000001)
        assertEquals(8742.51497006, snapshot.annualPaceKm!!, 0.000001)
        assertEquals(10000, snapshot.recommendedAnnualKm)
        assertEquals(RecoveryVerdict.ON_TRACK, snapshot.verdict)
        assertEquals(200.0, snapshot.lastWeek!!.usedKm, 0.0)
        assertEquals(87, snapshot.thisWeek.carryInKm)
        assertEquals(374, snapshot.thisWeek.budgetKm)
        for (refund in listOf(null, false, true)) {
            val changed = engine.snapshot(contract.copy(underuseRefund = refund), jeongHistory(), emptyList(), snapshot.today)
            assertEquals(0L, changed.projectedDistanceFee)
        }
    }

    @Test fun forecastDoesNotAttributeExcessWhenSettlementIsZero() {
        val planner = DefaultForecastEngine()
        val seed = PersonaSeeds.jung()
        val today = seed.demoNow.toLocalDate()
        val underuse = planner.forecast(seed.contract, seed.history, emptyList(), emptyList(), today)
        assertTrue(underuse.weekendAvgKm > seed.contract.totalKm.toDouble() / 1461)
        assertTrue(underuse.endKmHigh < seed.contract.totalKm)
        assertEquals(0L, underuse.endFeeHigh)
        assertEquals(0.0, underuse.excessShareWeekday, 0.0)
        assertEquals(0.0, underuse.excessShareWeekend, 0.0)
        assertEquals(0.0, underuse.excessShareUnclassified, 0.0)
        val withGap = seed.history + DailyDistance(today, 50.0, gapKm = 50.0)
        val gapResult = planner.forecast(seed.contract, withGap, emptyList(), emptyList(), today)
        assertEquals(0L, gapResult.endFeeHigh)
        assertEquals(0.0, gapResult.excessShareUnclassified, 0.0)
        val kim = PersonaSeeds.kim()
        val excess = planner.forecast(kim.contract, kim.history, emptyList(), emptyList(), today)
        assertTrue(excess.endFeeHigh > 0)
        assertEquals(1.0, excess.excessShareWeekday + excess.excessShareWeekend + excess.excessShareUnclassified, 0.000001)
        val deducted = planner.forecast(kim.contract.copy(deductKm = 100000), kim.history, emptyList(), emptyList(), today)
        assertEquals(0.0, deducted.excessShareWeekday + deducted.excessShareWeekend + deducted.excessShareUnclassified, 0.0)
        for (type in listOf(ContractType.FINANCE_LEASE, ContractType.UNLIMITED)) {
            val free = planner.forecast(kim.contract.copy(type = type), kim.history, emptyList(), emptyList(), today)
            assertEquals(0.0, free.excessShareWeekday + free.excessShareWeekend + free.excessShareUnclassified, 0.0)
        }
    }

    @Test fun jeongReturnWithZeroDistanceFeeStillHandlesDamage() {
        val today = LocalDate.of(2028, 3, 16)
        val c = contract.copy(monthlyFee = 600000, deposit = 0)
        val snapshot = engine.snapshot(c, jeongHistory(), emptyList(), today)
        val calculator = DefaultExitCalculator()
        val unconfirmed = calculator.compare(c, snapshot, ExitQuotes(), today).options.first { it.kind == ExitKind.RETURN_AT_END }
        assertEquals(0L, unconfirmed.lines.first { it.first == "만기 거리 정산" }.second)
        assertEquals(0L, unconfirmed.netCost)
        assertTrue(unconfirmed.netCostIsLowerBound)
        val confirmed = calculator.compare(c, snapshot, ExitQuotes(damageEstimate = 0), today).options.first { it.kind == ExitKind.RETURN_AT_END }
        assertEquals(0L, confirmed.netCost)
        assertFalse(confirmed.netCostIsLowerBound)
    }

    @Test fun annualRecommendationNeedsNinetyDaysAndUnusedIsPositiveOnly() {
        val early = engine.snapshot(contract, history(89, 1400.0), emptyList(), start.plusDays(89))
        assertNotNull(early.annualPaceKm)
        assertNull(early.recommendedAnnualKm)
        assertNotNull(engine.snapshot(contract, history(90, 1400.0), emptyList(), start.plusDays(90)).recommendedAnnualKm)
        val first = engine.snapshot(contract, emptyList(), emptyList(), start)
        assertNull(first.annualPaceKm)
        assertNull(first.recommendedAnnualKm)
        assertNull(first.projectedUnusedKm)
        val exceeded = engine.snapshot(contract, history(90, 6600.0), emptyList(), start.plusDays(90))
        assertNull(exceeded.projectedUnusedKm)
        // 연간 페이스10,000km × 1.1 = 11,000km는 15,000km로 올림한다.
        val annualContract = contract.copy(endDate = start.plusDays(729))
        val annual = engine.snapshot(annualContract, history(365, 10000.0), emptyList(), start.plusDays(365))
        assertEquals(15000, annual.recommendedAnnualKm)
    }

    @Test fun annualRecommendationOnlyOffersALowerAllowance() {
        val c = contract.copy(endDate = start.plusDays(364))
        val rows = history(100, 10000.0 * 100 / 365)
        val today = start.plusDays(100)
        // 추천 후보15,000km가 현재 약정과 같거나 더 크면 카드를 숨긴다.
        assertNull(engine.snapshot(c.copy(totalKm = 15000), rows, emptyList(), today).recommendedAnnualKm)
        assertNull(engine.snapshot(c.copy(totalKm = 10000), rows, emptyList(), today).recommendedAnnualKm)
        assertEquals(15000, engine.snapshot(c.copy(totalKm = 20000), rows, emptyList(), today).recommendedAnnualKm)
        // 기존14,000km 페이스는 현재48개월60,000km보다 낮은 구간을 제안하지 못한다.
        assertNull(engine.snapshot(contract, history(501, 14000.0), emptyList(), LocalDate.of(2028, 3, 16)).recommendedAnnualKm)
    }

    @Test fun contractNumbersAndRecovery() {
        val kim = engine.snapshot(contract, history(499, 21300.0), emptyList(), start.plusDays(499))
        assertEquals(1461, kim.contractDays)
        assertEquals(499, kim.elapsedDays)
        assertEquals(287, engine.splitOptions(contract, 0).first().resultingWeeklyBudgetKm)
        assertEquals(-807.0, kim.cumulativeBalanceKm, 1.0)
        assertEquals(236333.0, kim.projectedDistanceFee!!.toDouble(), 1000.0)
        val leeHistory = history(62, 4548.0) + (62 until 90).map { DailyDistance(start.plusDays(it.toLong()), 2052.0 / 28) }
        val lee = engine.snapshot(contract, leeHistory, emptyList(), start.plusDays(90))
        assertEquals(3696.0, lee.cumulativeBalanceKm + lee.usedKm, 1.0)
        assertEquals(-2904.0, lee.cumulativeBalanceKm, 1.0)
        assertEquals(273.0, lee.requiredWeeklyKm, 1.0)
        assertEquals(513.0, lee.recentWeeklyAvgKm!!, 0.01)
        assertEquals(RecoveryVerdict.HARD_TO_RECOVER, lee.verdict)
        assertEquals(4714000.0, lee.projectedDistanceFee!!.toDouble(), 1000.0)
    }

    @Test fun kimDemoOnMarch16() {
        val today = LocalDate.of(2028, 3, 16)
        val snapshot = engine.snapshot(contract, history(501, 21382.0), emptyList(), today)
        assertEquals(501, snapshot.elapsedDays)
        assertEquals(21382.0, snapshot.usedKm, 0.001)
        assertEquals(-807.0, snapshot.cumulativeBalanceKm, 1.0)
        // 명세의 약 235,335원과 산식의 반올림 결과 235,350원 차이는 15원이다.
        assertEquals(235335.0, snapshot.projectedDistanceFee!!.toDouble(), 100.0)
    }

    @Test fun kimDecisionMetricsAndTripCost() {
        val seed = PersonaSeeds.kim()
        val today = seed.demoNow.toLocalDate()
        val budget = engine.snapshot(seed.contract, seed.history, emptyList(), today)
        assertEquals(74200L, budget.onBudgetEndFee)
        assertEquals(281.5895833333, budget.zeroFeeWeeklyKm!!, 0.000001)
        val planner = DefaultForecastEngine()
        val noTrip = planner.forecast(seed.contract, seed.history, emptyList(), emptyList(), today)
        assertEquals(235350L, noTrip.endFeePoint)
        assertEquals(166000L, noTrip.endFeeLow)
        assertEquals(235350L, noTrip.endFeeHigh)
        val trip = PlannedTrip("gangneung", today.plusDays(2), 420, "강릉 왕복")
        val planned = planner.forecast(seed.contract, seed.history, listOf(trip), emptyList(), today)
        assertEquals(684.0, planned.thisWeekForecastKm, 0.000001)
        assertEquals("gangneung", planned.tripImpacts.single().tripId)
        assertEquals(388.0, planned.tripImpacts.single().extraKm, 0.000001)
        assertEquals(38800L, planned.tripImpacts.single().extraFee)
        assertEquals(62741.497005988, planned.endKmPoint!!, 0.000001)
        assertEquals(274150L, planned.endFeePoint)
        assertEquals(204800L, planned.endFeeLow)
        assertEquals(274150L, planned.endFeeHigh)
        assertTrue(planned.endKmPoint in planned.endKmLow..planned.endKmHigh)
        val otherCar = planner.forecast(seed.contract, seed.history, listOf(trip.copy(otherCar = true)), emptyList(), today)
        assertEquals(-32.0, otherCar.tripImpacts.single().extraKm, 0.0)
        assertEquals(-3200L, otherCar.tripImpacts.single().extraFee)
        assertEquals(42000L, planned.endFeePoint!! - otherCar.endFeePoint!!)
    }

    @Test fun decisionMetricsRespectAllowanceDeductionAndContractType() {
        val today = start.plusDays(14)
        val rows = history(14, 140.0)
        val c = contract.copy(endDate = start.plusDays(27), totalKm = 330)
        val trip = PlannedTrip("crossing", today.plusDays(1), 110, "예정 이동")
        val planner = DefaultForecastEngine()
        val crossing = planner.forecast(c, rows, listOf(trip), emptyList(), today)
        assertEquals(100.0, crossing.tripImpacts.single().extraKm, 0.0)
        assertEquals(5000L, crossing.tripImpacts.single().extraFee)
        val within = planner.forecast(c.copy(totalKm = 1000), rows, listOf(trip), emptyList(), today)
        assertEquals(0L, within.tripImpacts.single().extraFee)
        val deducted = planner.forecast(c.copy(deductKm = 40), rows, listOf(trip), emptyList(), today)
        assertEquals(1000L, deducted.tripImpacts.single().extraFee)
        val past = planner.forecast(c, rows, listOf(trip.copy(date = today)), emptyList(), today)
        assertEquals(0L, past.tripImpacts.single().extraFee)
        for (type in listOf(ContractType.FINANCE_LEASE, ContractType.UNLIMITED)) {
            val free = c.copy(type = type)
            assertEquals(0L, planner.forecast(free, rows, listOf(trip), emptyList(), today).tripImpacts.single().extraFee)
            assertEquals(0L, engine.snapshot(free, rows, emptyList(), today).onBudgetEndFee)
        }
        val deductedBudget = engine.snapshot(c.copy(deductKm = 40), rows, emptyList(), today)
        assertEquals(115.0, deductedBudget.zeroFeeWeeklyKm!!, 0.0)
        val unknown = engine.snapshot(c.copy(deductKm = null), rows, emptyList(), today)
        assertTrue(unknown.deductUnconfirmed)
        assertNotNull(unknown.onBudgetEndFee)
        val exhausted = engine.snapshot(c, history(14, 500.0), emptyList(), today)
        assertTrue(exhausted.zeroFeeWeeklyKm!! < 0)
        val ended = engine.snapshot(c, rows, emptyList(), c.endDate.plusDays(1))
        assertNull(ended.zeroFeeWeeklyKm)
    }

    @Test fun kimSeedKeepsWeeklyCarrySeparateFromCumulativeDebt() {
        val seed = PersonaSeeds.kim()
        val snapshot = engine.snapshot(seed.contract, seed.history, emptyList(), seed.demoNow.toLocalDate())
        assertEquals(LocalDate.of(2028, 3, 16), snapshot.today)
        assertEquals(21382.0, snapshot.usedKm, 0.0)
        assertEquals(330.0, snapshot.lastWeek!!.usedKm, 0.0)
        assertEquals(14, snapshot.lastWeek.carryInKm)
        assertEquals(-43, snapshot.thisWeek.carryInKm)
        assertEquals(244, snapshot.thisWeek.budgetKm)
        assertEquals(-807.0, snapshot.cumulativeBalanceKm, 1.0)
    }

    @Test fun carryAndSplitAndPartialWeeks() {
        // 첫 일요일 41km, 다음 완전한 주 330km: 이월 -43km.
        val rows = listOf(DailyDistance(start, 41.0), DailyDistance(start.plusDays(1), 330.0))
        val today = start.plusDays(8)
        assertEquals(244, engine.snapshot(contract, rows, emptyList(), today).thisWeek.budgetKm)
        val options = engine.splitOptions(contract, 440)
        assertEquals(listOf(1, 4, 8), options.map { it.weeks })
        assertEquals(55, options.last().perWeekKm)
        assertEquals(232, options.last().resultingWeeklyBudgetKm)
        // 직전 주가 기본 예산과 같으면 이월 0, 분할 상환 55km만 차감한다.
        val debt = listOf(DailyDistance(start, 41.0), DailyDistance(start.plusDays(1), 287.0))
        val split = SplitPlan(440, 8, today)
        assertEquals(232, engine.snapshot(contract, debt, listOf(split), today).thisWeek.budgetKm)
        val short = contract.copy(endDate = start.plusDays(9), totalKm = 100)
        val starts = listOf(start, start.plusDays(1), start.plusDays(8))
        val bases = starts.map { engine.snapshot(short, emptyList(), emptyList(), it).thisWeek.baseKm }
        assertEquals(100, bases.sum())
    }

    @Test fun carryDoesNotChainPriorCarryOrSplitDeduction() {
        val monday = start.plusDays(1)
        val rows = listOf(DailyDistance(start, 80.0), DailyDistance(monday, 330.0), DailyDistance(monday.plusWeeks(1), 287.0))
        assertEquals(0, engine.snapshot(contract, rows, emptyList(), start).thisWeek.carryInKm)
        val second = engine.snapshot(contract, rows, emptyList(), monday.plusWeeks(1))
        assertEquals(-39, second.lastWeek!!.carryInKm)
        assertEquals(-43, second.thisWeek.carryInKm)
        val third = engine.snapshot(contract, rows, emptyList(), monday.plusWeeks(2))
        assertEquals(0, third.thisWeek.carryInKm)
        assertEquals(287, third.thisWeek.budgetKm)
        // 지난주 분할 상환이 있어도 이월은 기본 예산 287 - 실제 287 = 0이다.
        val split = SplitPlan(440, 8, monday.plusWeeks(1))
        val splitThird = engine.snapshot(contract, rows, listOf(split), monday.plusWeeks(2))
        assertEquals(55, splitThird.lastWeek!!.splitDeductKm)
        assertEquals(0, splitThird.thisWeek.carryInKm)
        assertEquals(232, splitThird.thisWeek.budgetKm)
    }

    @Test fun kimSplitReplacesCarryThenExpiresAfterEightWeeks() {
        val seed = PersonaSeeds.kim()
        val nextMonday = LocalDate.of(2028, 3, 20)
        // 월~목 186km에 일요일까지 498km를 더해 이번 주 실제 684km로 마감한다.
        val completedWeek = seed.history + DailyDistance(nextMonday.minusDays(1), 498.0)
        val split = SplitPlan(440, 8, nextMonday)
        val withoutSplit = engine.snapshot(seed.contract, completedWeek, emptyList(), nextMonday)
        assertEquals(-397, withoutSplit.thisWeek.carryInKm)
        assertEquals(-110, withoutSplit.thisWeek.budgetKm)
        val first = engine.snapshot(seed.contract, completedWeek, listOf(split), nextMonday)
        assertEquals(684.0, first.lastWeek!!.usedKm, 0.0)
        assertEquals(0, first.thisWeek.carryInKm)
        assertEquals(55, first.thisWeek.splitDeductKm)
        assertEquals(232, first.thisWeek.budgetKm)
        assertEquals(withoutSplit.cumulativeBalanceKm, first.cumulativeBalanceKm, 0.0)
        // 각 주 실제를 기본값 287km로 두어 그 사이 새 이월이 없는 조건을 만든다.
        val rows = completedWeek + (0L..7L).map { DailyDistance(nextMonday.plusWeeks(it), 287.0) }
        for (index in 0L..7L) {
            val week = engine.snapshot(seed.contract, rows, listOf(split), nextMonday.plusWeeks(index)).thisWeek
            assertEquals(0, week.carryInKm)
            assertEquals(55, week.splitDeductKm)
            assertEquals(232, week.budgetKm)
        }
        val ninth = engine.snapshot(seed.contract, rows, listOf(split), nextMonday.plusWeeks(8)).thisWeek
        assertEquals(0, ninth.carryInKm)
        assertEquals(0, ninth.splitDeductKm)
        assertEquals(287, ninth.budgetKm)
        // 시작 다음 주부터 발생한 새 초과에는 다시 한 주 이월 규칙을 적용한다.
        val newExcess = completedWeek + DailyDistance(nextMonday, 300.0)
        val second = engine.snapshot(seed.contract, newExcess, listOf(split), nextMonday.plusWeeks(1)).thisWeek
        assertEquals(-13, second.carryInKm)
        assertEquals(219, second.budgetKm)
    }

    @Test fun forecastReplacesBaselineAndIgnoresGaps() {
        val today = LocalDate.of(2028, 3, 16)
        val monday = today.minusDays(3)
        val rows = (1L..28).map { offset ->
            val date = today.minusDays(offset)
            DailyDistance(date, if (date.dayOfWeek.value >= 6) 26.0 else 52.0)
        }.filter { it.date.isBefore(monday) } + (0L..3).map { DailyDistance(monday.plusDays(it), 46.5) }
        val trip = PlannedTrip("gangneung", today.plusDays(2), 420, "강릉")
        // 최근 평일 평균을 52로 맞추되 월~목 실제 합은 186으로 유지한다.
        val fixed = rows.map { if (it.date.isBefore(monday) && it.date.dayOfWeek.value < 6) it.copy(km = (1040.0 - 139.5) / 17) else it }
        val result = DefaultForecastEngine().forecast(contract, fixed, listOf(trip), emptyList(), today)
        assertEquals(684.0, result.thisWeekForecastKm, 0.001)
        assertEquals(440.0, result.thisWeekForecastKm - 244, 0.001)
        assertEquals(420.0, result.thisWeekDays[5].km, 0.0)
        val otherCar = DefaultForecastEngine().forecast(contract, fixed, listOf(trip.copy(otherCar = true)), emptyList(), today)
        assertEquals(0.0, otherCar.thisWeekDays[5].km, 0.0)
        val gap = DefaultForecastEngine().forecast(contract, listOf(DailyDistance(today.minusDays(1), 900.0, 900.0)), emptyList(), emptyList(), today)
        assertEquals(0.0, gap.weekdayAvgKm, 0.0)
        assertTrue(gap.basedOnPlanOnly)
        val scenario = LifeScenario("commute", "통근", 20.0, today.plusDays(1), today.plusDays(1))
        val changed = DefaultForecastEngine().forecast(contract, fixed, listOf(trip), listOf(scenario), today)
        assertEquals(704.0, changed.thisWeekForecastKm, 0.001)
    }

    @Test fun exitCostCashAndUnconfirmed() {
        val today = start.plusDays(499)
        val configured = contract.copy(buyoutPrice = 18000000, monthlyFee = 0, deposit = 0, buyoutWaivesDistanceFee = true)
        val snapshot = engine.snapshot(configured, history(499, 31228.0), emptyList(), today)
        assertEquals(3140000.0, snapshot.projectedDistanceFee!!.toDouble(), 10000.0)
        val result = DefaultExitCalculator().compare(configured, snapshot, ExitQuotes(17500000, 3000000, alternativeMobilityCost = 0), today)
        assertEquals(contract.endDate, result.compareUntil)
        val buy = result.options.first { it.kind == ExitKind.BUY_AND_KEEP }
        assertEquals(1760000L, buy.netCost)
        assertEquals(19260000L, buy.cashNeeded)
        assertEquals(3000000L, result.options.first { it.kind == ExitKind.TRANSFER_NOW }.netCost)
        val returned = result.options.first { it.kind == ExitKind.RETURN_AT_END }
        assertEquals(snapshot.projectedDistanceFee, returned.netCost)
        assertTrue(returned.netCostIsLowerBound)
        assertTrue(returned.unconfirmed.contains("손상·감가"))
        assertEquals(ExitKind.BUY_AND_KEEP, result.cheapest)
        assertFalse(result.cheapestTentative)
        val cheaperMarket = DefaultExitCalculator().compare(configured, snapshot,
            ExitQuotes(15000000, 3000000, alternativeMobilityCost = 0), today)
        assertEquals(ExitKind.RETURN_AT_END, cheaperMarket.cheapest)
        assertTrue(cheaperMarket.cheapestTentative)
        val missing = DefaultExitCalculator().compare(contract, snapshot, ExitQuotes(), today)
        assertNull(missing.options.first { it.kind == ExitKind.BUY_AND_KEEP }.netCost)
        assertNull(missing.cheapest)
    }

    @Test fun returnDamageAndTentativeBoundary() {
        val today = start.plusDays(499)
        val configured = contract.copy(buyoutPrice = 18000000, monthlyFee = 0, deposit = 0, buyoutWaivesDistanceFee = true)
        val snapshot = engine.snapshot(configured, history(499, 31228.0), emptyList(), today)
        val calculator = DefaultExitCalculator()
        val quotes = ExitQuotes(expectedMarketPrice = 14000000, transferCost = 4000000, alternativeMobilityCost = 0)
        val lower = calculator.compare(configured, snapshot, quotes, today)
        assertEquals(ExitKind.RETURN_AT_END, lower.cheapest)
        assertTrue(lower.cheapestTentative)
        val confirmed = calculator.compare(configured, snapshot, quotes.copy(damageEstimate = 500000), today)
        val returned = confirmed.options.first { it.kind == ExitKind.RETURN_AT_END }
        assertEquals(snapshot.projectedDistanceFee!! + 500000, returned.netCost)
        assertFalse(returned.netCostIsLowerBound)
        assertFalse(returned.unconfirmed.contains("손상·감가"))
        assertFalse(confirmed.cheapestTentative)
        val boundary = quotes.copy(expectedMarketPrice = 19260000 - (snapshot.projectedDistanceFee - 300000))
        assertTrue(calculator.compare(configured, snapshot, boundary, today).cheapestTentative)
        assertFalse(calculator.compare(configured, snapshot, boundary.copy(expectedMarketPrice = boundary.expectedMarketPrice!! + 1), today).cheapestTentative)
        val withDeposit = configured.copy(deposit = 100000, monthlyFee = 10000)
        val comparison = calculator.compare(withDeposit, snapshot, quotes, today)
        val remaining = comparison.options.first()
        assertEquals(320000L, comparison.remainingFees)
        assertFalse(remaining.lines.any { it.first == "남은 월료" })
        assertEquals(snapshot.projectedDistanceFee - 100000, remaining.netCost)
        assertTrue(remaining.netCostIsLowerBound)
    }

    @Test fun exitGroupsSeparateCommonFeesAndUnknownMobility() {
        val seed = PersonaSeeds.lee499()
        val today = seed.demoNow.toLocalDate()
        val configured = seed.contract.copy(monthlyFee = 600000)
        val snapshot = engine.snapshot(configured, seed.history, emptyList(), today)
        val calculator = DefaultExitCalculator()
        val quotes = seed.exitQuotes.copy(alternativeMobilityCost = null)
        val result = calculator.compare(configured, snapshot, quotes, today)
        assertEquals(19200000L, result.remainingFees)
        assertEquals(ExitKind.BUY_AND_KEEP, result.cheapest)
        assertFalse(result.cheapestTentative)
        assertEquals(1760000L, result.options.first { it.kind == ExitKind.BUY_AND_KEEP }.netCost)
        assertEquals(19260000L, result.options.first { it.kind == ExitKind.BUY_AND_KEEP }.cashNeeded)
        assertEquals(ExitKind.TRANSFER_NOW, result.cheapestLeaveNow)
        assertTrue(result.cheapestLeaveNowTentative)
        val leave = result.options.filter { it.kind.group == ExitGroup.LEAVE_NOW }
        assertEquals(3000000L, leave.first { it.kind == ExitKind.TRANSFER_NOW }.netCost)
        assertEquals(7793519L, leave.first { it.kind == ExitKind.TERMINATE_NOW }.netCost)
        assertTrue(leave.all { it.netCostIsLowerBound && "대체 이동비" in it.unconfirmed })
        val confirmed = calculator.compare(configured, snapshot, quotes.copy(alternativeMobilityCost = 5000000), today)
        assertEquals(ExitKind.TRANSFER_NOW, confirmed.cheapestLeaveNow)
        assertFalse(confirmed.cheapestLeaveNowTentative)
        assertEquals(8000000L, confirmed.options.first { it.kind == ExitKind.TRANSFER_NOW }.netCost)
        assertFalse(confirmed.options.filter { it.kind.group == ExitGroup.LEAVE_NOW }.any { it.netCostIsLowerBound })
        val unknownFee = calculator.compare(configured.copy(monthlyFee = null), snapshot, quotes, today)
        assertNull(unknownFee.remainingFees)
        assertEquals(ExitKind.BUY_AND_KEEP, unknownFee.cheapest)
        assertNull(unknownFee.options.first { it.kind == ExitKind.TERMINATE_NOW }.netCost)
        val demo = calculator.compare(seed.contract, snapshot, seed.exitQuotes, today)
        assertEquals(ExitKind.BUY_AND_KEEP, demo.cheapest)
        // 데모 시드도 월료 60만·대체 이동비 미확인 → 지금 나가기는 승계가 하한값·잠정 최저(리허설 v2 F1).
        assertEquals(ExitKind.TRANSFER_NOW, demo.cheapestLeaveNow)
        assertTrue(demo.cheapestLeaveNowTentative)
        assertEquals(7793519L, demo.options.first { it.kind == ExitKind.TERMINATE_NOW }.netCost)
        assertTrue(demo.options.filter { it.kind.group == ExitGroup.LEAVE_NOW }.all { it.netCostIsLowerBound })
    }

    @Test fun transferDepositSettlementAndQuoteExpiry() {
        val today = start.plusDays(499)
        val configured = contract.copy(deposit = 1000000)
        val snapshot = engine.snapshot(configured, history(499, 31228.0), emptyList(), today)
        val calculator = DefaultExitCalculator()
        val quotes = ExitQuotes(transferCost = 3000000, alternativeMobilityCost = 0,
            transferDepositSettlement = 200000, quoteValidUntil = today.plusDays(7))
        val refund = calculator.compare(configured, snapshot, quotes, today).options.first { it.kind == ExitKind.TRANSFER_NOW }
        assertEquals(2800000L, refund.netCost)
        val charge = calculator.compare(configured, snapshot, quotes.copy(transferDepositSettlement = -200000), today)
            .options.first { it.kind == ExitKind.TRANSFER_NOW }
        assertEquals(3200000L, charge.netCost)
        val missing = calculator.compare(configured, snapshot, quotes.copy(transferDepositSettlement = null), today)
            .options.first { it.kind == ExitKind.TRANSFER_NOW }
        assertNull(missing.netCost)
        assertTrue(missing.unconfirmed.contains("보증금·선납 정산"))
        val expiry = calculator.calendar(configured, today, quotes).first { it.kind == CalendarEventKind.QUOTE_EXPIRY }
        assertEquals(today.plusDays(7), expiry.date)
        assertFalse(calculator.calendar(configured, today, quotes.copy(quoteValidUntil = today.minusDays(1)))
            .any { it.kind == CalendarEventKind.QUOTE_EXPIRY })
        assertFalse(calculator.calendar(configured, today).any { it.kind == CalendarEventKind.QUOTE_EXPIRY })
    }

    @Test fun insuranceNumbers() {
        val plan = InsurancePlan(start, start.plusDays(364), 1000.0, listOf(10000, 12000))
        val today = plan.periodEnd.plusDays(1).minusWeeks(13)
        val result = DefaultInsuranceTierPlanner().status(plan, 9820.0, emptyList(), today)
        assertEquals(192, result.weeklyBudgetKm)
        assertEquals(8820.0, result.usedKm, 0.0)
        assertEquals(1180.0, result.tiers[0].remainingKm, 0.0)
        assertEquals(245.0, result.tiers[1].requiredWeeklyKm, 0.5)
    }

    @Test fun parkInsuranceWeekIncludesMondayAndTodayOnly() {
        val today = LocalDate.of(2027, 10, 6)
        val plan = InsurancePlan(LocalDate.of(2027, 1, 6), LocalDate.of(2028, 1, 5), 10.0, listOf(10000, 12000))
        val rows = listOf(
            DailyDistance(plan.periodStart, 8625.0),
            DailyDistance(LocalDate.of(2027, 10, 3), 100.0),
            DailyDistance(LocalDate.of(2027, 10, 4), 40.0),
            DailyDistance(LocalDate.of(2027, 10, 5), 35.0),
            DailyDistance(today, 20.0, gapKm = 5.0),
            DailyDistance(today.plusDays(1), 999.0),
        )
        val planner = DefaultInsuranceTierPlanner()
        val status = planner.status(plan, 8830.0, rows, today)
        assertEquals(95.0, status.thisWeekUsedKm, 0.0)
        assertEquals(192, status.weeklyBudgetKm)
        assertEquals(8820.0, status.usedKm, 0.0)
        assertEquals(1180.0, status.tiers[0].remainingKm, 0.0)
        // 월요일이 보험 시작 전이면 제외하고, 보험 종료 뒤 주행도 제외한다.
        assertEquals(55.0, planner.status(plan.copy(periodStart = today.minusDays(1)), 8830.0, rows, today).thisWeekUsedKm, 0.0)
        assertEquals(75.0, planner.status(plan.copy(periodEnd = today.minusDays(1)), 8830.0, rows, today).thisWeekUsedKm, 0.0)
    }

    @Test fun strictRecoveryThresholdAndCalendar() {
        val c = contract.copy(endDate = start.plusDays(97), totalKm = 1000)
        // 28일 600km 사용, 잔여 70일: 필요 40km/주, 최근 150km/주.
        val difficult = engine.snapshot(c, history(28, 600.0), emptyList(), start.plusDays(28))
        assertEquals(RecoveryVerdict.HARD_TO_RECOVER, difficult.verdict)
        val exhausted = engine.snapshot(c, history(28, 1000.0), emptyList(), start.plusDays(28))
        assertEquals(RecoveryVerdict.HARD_TO_RECOVER, exhausted.verdict)
        // 정확히 60%이면 HARD_TO_RECOVER가 아니다: 28일 400km, 잔여70일 600km.
        val boundary = c.copy(totalKm = 1000)
        val normal = engine.snapshot(boundary, history(28, 400.0), emptyList(), start.plusDays(28))
        assertEquals(0.6, normal.requiredWeeklyKm / normal.recentWeeklyAvgKm!!, 0.00001)
        assertEquals(RecoveryVerdict.RECOVERABLE, normal.verdict)
        val calendarContract = contract.copy(terminationTiers = listOf(TerminationTier(12, 0.3), TerminationTier(48, 0.1)),
            freeReturnDate = start.plusMonths(24), noticeDeadline = contract.endDate.minusDays(30))
        val events = DefaultExitCalculator().calendar(calendarContract, start)
        assertEquals(listOf(CalendarEventKind.TERMINATION_RATE_DROP, CalendarEventKind.FREE_RETURN_OPEN,
            CalendarEventKind.NOTICE_DEADLINE, CalendarEventKind.CONTRACT_END), events.map { it.kind })
        assertEquals(start.plusMonths(12), events.first().date)
    }
    @Test fun unlimitedAndFinanceAndUnknownDeduction() {
        val today = start.plusDays(90)
        for (type in listOf(ContractType.FINANCE_LEASE, ContractType.UNLIMITED)) {
            val c = contract.copy(type = type)
            val snapshot = engine.snapshot(c, history(90, 6600.0), emptyList(), today)
            assertEquals(0L, snapshot.projectedDistanceFee)
            assertFalse(DefaultExitCalculator().compare(c, snapshot, ExitQuotes(), today).options.first().available)
        }
        val unknown = engine.snapshot(contract.copy(deductKm = null), history(90, 6600.0), emptyList(), today)
        assertTrue(unknown.deductUnconfirmed)
        assertEquals(4714000.0, unknown.projectedDistanceFee!!.toDouble(), 1000.0)
        val future = history(90, 6600.0) + DailyDistance(today.plusDays(1), 9999.0)
        assertEquals(6600.0, engine.snapshot(contract, future, emptyList(), today).usedKm, 0.001)
    }
}


