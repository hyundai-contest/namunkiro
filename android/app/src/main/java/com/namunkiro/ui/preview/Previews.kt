package com.namunkiro.ui.preview

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.namunkiro.core.result.Source
import com.namunkiro.core.result.Sourced
import com.namunkiro.domain.*
import com.namunkiro.domain.model.*
import com.namunkiro.ui.NamunkiroApp
import com.namunkiro.ui.screens.*
import com.namunkiro.ui.state.*
import com.namunkiro.ui.theme.NamunkiroTheme
import java.time.LocalDate

/** 계산 엔진과 독립적인 디자인 검토 픽스처. 모든 시드 이력은 모의 배지로 구분한다. */
object PreviewFixtures {
    private fun option(kind: ExitKind, available: Boolean, netCost: Long?, cashNeeded: Long?, lines: List<Pair<String, Long?>>, unconfirmed: List<String>) = ExitOption(
        kind = kind, available = available, netCost = netCost, cashNeeded = cashNeeded,
        netCostIsLowerBound = unconfirmed.isNotEmpty(), lines = lines, unconfirmed = unconfirmed,
    )
    private val today = LocalDate.of(2028, 3, 16)
    val contract = Contract(type = ContractType.LONG_TERM_RENT, startDate = LocalDate.of(2026, 11, 1),
        endDate = LocalDate.of(2030, 10, 31), totalKm = 60000, unitPricePerKm = 100,
        startOdometerKm = 12.0, startOdometerSource = Source.SIMULATED, deductKm = 0, buyoutPrice = 18000000)
    private val week = WeekBudget(today.minusDays(3), 287, -43, 0, 244, 186.0)
    val budget = BudgetSnapshot(today, 1461, 499, 21300.0, -807.0, week,
        WeekBudget(week.weekStart.minusWeeks(1), 287, 0, 0, 287, 330.0),
        281.6, 294.0, RecoveryVerdict.RECOVERABLE, 62353.5, 235350, false,
        onBudgetEndFee = 74200, zeroFeeWeeklyKm = 281.6)
    val forecast = ForecastResult(false, 46.0, 32.0,
        listOf(48.0, 52.0, 41.0, 45.0, 46.0, 420.0, 32.0).mapIndexed { i, n -> DayForecast(week.weekStart.plusDays(i.toLong()), n, when { i < 4 -> DayKind.ACTUAL; i == 5 -> DayKind.PLANNED; else -> DayKind.BASELINE }) },
        684.0, 62048.0, 62741.5, 204800, 274150, .7, .25, .05,
        tripImpacts = listOf(TripImpact("gangneung", 388.0, 38800)), endKmPoint = 62741.5, endFeePoint = 274150)
    private val noTripForecast = forecast.copy(thisWeekDays = forecast.thisWeekDays.map { if (it.kind == DayKind.PLANNED) it.copy(km = 32.0, kind = DayKind.BASELINE) else it },
        thisWeekForecastKm = 296.0, endKmLow = 61660.0, endKmHigh = 62353.5, endFeeLow = 166000, endFeeHigh = 235350,
        tripImpacts = emptyList(), endKmPoint = 62353.5, endFeePoint = 235350)
    val home = AppUiState(contract = contract, odometer = Sourced(21312.0, Source.SIMULATED), budget = budget, forecast = noTripForecast,
        historySimulated = true, demoMode = true)
    val weekState = home.copy(forecast = forecast, trips = listOf(PlannedTrip("gangneung", week.weekStart.plusDays(5), 420, "강릉 왕복", Source.SIMULATED)))
    /** 정 주임: 연 8천 km 페이스의 저주행 시나리오. 환급 조건은 미확인이다. */
    val jung = home.copy(
        contract = contract.copy(underuseRefund = null),
        budget = budget.copy(usedKm = 10930.0, cumulativeBalanceKm = 9563.0,
            thisWeek = week.copy(carryInKm = 87, budgetKm = 374, usedKm = 105.0),
            lastWeek = week.copy(carryInKm = 0, budgetKm = 287, usedKm = 200.0),
            recentWeeklyAvgKm = 154.0, verdict = RecoveryVerdict.ON_TRACK,
            projectedEndKm = 32000.0, projectedDistanceFee = 0,
            projectedUnusedKm = 28000.0, annualPaceKm = 8000.0, recommendedAnnualKm = 10000,
            onBudgetEndFee = 0, zeroFeeWeeklyKm = 357.0),
        forecast = noTripForecast.copy(endKmLow = 30800.0, endKmHigh = 32000.0, endFeeLow = 0, endFeeHigh = 0,
            endKmPoint = 32000.0, endFeePoint = 0),
        splitOptions = emptyList(),
    )
    val hard = home.copy(budget = budget.copy(elapsedDays = 90, usedKm = 6600.0, cumulativeBalanceKm = -2904.0,
        requiredWeeklyKm = 273.0, recentWeeklyAvgKm = 513.0, verdict = RecoveryVerdict.HARD_TO_RECOVER, projectedDistanceFee = 4710000,
        projectedEndKm = 107100.0, onBudgetEndFee = 290400, zeroFeeWeeklyKm = 273.0),
        forecast = forecast.copy(endKmLow = 103000.0, endKmHigh = 107100.0, endFeeLow = 4300000, endFeeHigh = 4710000, endKmPoint = 107100.0, endFeePoint = 4710000))
    val exit = home.copy(screen = Screen.EXIT, quotes = ExitQuotes(17500000, 3000000, alternativeMobilityCost = null),
        budget = budget.copy(usedKm = 31228.0, projectedEndKm = 91400.0, projectedDistanceFee = 3140000),
        calendar = listOf(CalendarEvent(LocalDate.of(2028, 11, 1), CalendarEventKind.TERMINATION_RATE_DROP, "해지 수수료율 35% → 30%"),
            CalendarEvent(contract.endDate, CalendarEventKind.CONTRACT_END, "계약 만기")),
        exit = ExitComparison(contract.endDate, listOf(
            option(ExitKind.RETURN_AT_END, true, 3140000, 3140000, listOf("거리 정산" to 3140000L, "손상·감가" to null), listOf("손상·감가")),
            option(ExitKind.BUY_AND_KEEP, true, 1760000, 19260000, listOf("인수가" to 18000000L, "취등록세" to 1260000L, "자산가치 차감" to -17500000L), listOf("인수 면제 조항")),
            option(ExitKind.TRANSFER_NOW, true, 3000000, 3000000, listOf("입력 견적" to 3000000L, "대체 이동비" to null), listOf("대체 이동비")),
            option(ExitKind.TERMINATE_NOW, true, null, null, emptyList(), listOf("해지 요율")),
            option(ExitKind.BUY_AND_SELL, true, null, 19260000, emptyList(), listOf("매각 수수료"))) , cheapest = ExitKind.BUY_AND_KEEP, cheapestTentative = true,
            cheapestLeaveNow = ExitKind.TRANSFER_NOW, cheapestLeaveNowTentative = true,
            remainingFees = null))
    val exitUnconfirmed = exit.copy(
        quotes = exit.quotes.copy(alternativeMobilityCost = null),
        exit = exit.exit?.let { comparison -> comparison.copy(
            remainingFees = null, cheapestLeaveNowTentative = true,
            options = comparison.options.map { option ->
                if (option.kind.group == ExitGroup.LEAVE_NOW) option.copy(
                    netCostIsLowerBound = true,
                    unconfirmed = option.unconfirmed + "대체 이동비",
                    lines = option.lines + ("대체 이동비" to null),
                ) else option
            },
        ) },
    )
    val insurance = home.copy(screen = Screen.INSURANCE, insurance = InsuranceStatus(192, 8820.0, 225.0,
        listOf(TierStatus(10000, 1180.0, 90.8, LocalDate.of(2028, 11, 3)), TierStatus(12000, 3180.0, 245.0, null)), thisWeekUsedKm = 150.0))
    val onboarding = AppUiState(onboarding = OnboardingDraft(0, contract.endDate, 60000, 100, Sourced(12.0, Source.SIMULATED)), demoMode = true)
    val anomaly = home.copy(gate = Gate.ANOMALY_HOLD, anomaly = AnomalyInfo(31240.0, 32240.0, 60, 4.17))
}

@Preview(name = "홈 · 1682×1210", widthDp = 1682, heightDp = 1210, showBackground = true)
@Composable fun HomePreview() { NamunkiroApp(PreviewFixtures.home, NoopActions) }
@Preview(name = "홈 · 반쪽 840×1210", widthDp = 840, heightDp = 1210, showBackground = true)
@Composable fun CompactHomePreview() { NamunkiroApp(PreviewFixtures.home, NoopActions) }
@Preview(name = "30초 시작", widthDp = 1682, heightDp = 1210)
@Composable fun OnboardingPreview() { NamunkiroApp(PreviewFixtures.onboarding, NoopActions) }
@Preview(name = "이번 주 · 강릉 비용과 주279km 목표", widthDp = 1682, heightDp = 1210)
@Composable fun WeekPreview() { NamunkiroApp(PreviewFixtures.weekState.copy(screen = Screen.WEEK), NoopActions) }
@Preview(name = "만기 예측", widthDp = 1682, heightDp = 1210)
@Composable fun ForecastPreview() { NamunkiroApp(PreviewFixtures.home.copy(screen = Screen.FORECAST), NoopActions) }
@Preview(name = "회복 어려움 · 471만원", widthDp = 1682, heightDp = 1210)
@Composable fun HardToRecoverPreview() { NamunkiroApp(PreviewFixtures.hard, NoopActions) }
@Preview(name = "출구 달력·비교", widthDp = 1682, heightDp = 1210)
@Composable fun ExitPreview() { NamunkiroApp(PreviewFixtures.exit, NoopActions) }
@Preview(name = "계약 카드", widthDp = 1682, heightDp = 1210)
@Composable fun ContractPreview() { NamunkiroApp(PreviewFixtures.home.copy(screen = Screen.CONTRACT), NoopActions) }
@Preview(name = "보험 특약", widthDp = 1682, heightDp = 1210)
@Composable fun InsurancePreview() { NamunkiroApp(PreviewFixtures.insurance, NoopActions) }
@Preview(name = "주행 중 잠금", widthDp = 1682, heightDp = 1210)
@Composable fun DrivingLockPreview() { NamunkiroApp(PreviewFixtures.home.copy(gate = Gate.DRIVING_LOCK), NoopActions) }
@Preview(name = "이상값 보류", widthDp = 1682, heightDp = 1210)
@Composable fun AnomalyPreview() { NamunkiroApp(PreviewFixtures.anomaly, NoopActions) }
@Preview(name = "공제 거리 지연 질문", widthDp = 1682, heightDp = 1210)
@Composable fun DeferredQuestionPreview() { NamunkiroApp(PreviewFixtures.home.copy(deferredQuestion = DeferredQuestion(DeferredQuestionKind.DEDUCT_KM, "공제 거리를 알면 더 정확해져요", "계약서의 총거리 공제를 확인해 주세요. 모르면 나중에 채울 수 있어요.")), NoopActions) }
@Preview(name = "라이트 토큰", widthDp = 840, heightDp = 1210)
@Composable fun LightTokensPreview() { NamunkiroTheme(darkTheme = false) { Surface { Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { HomeScreen(PreviewFixtures.home, NoopActions) } } } }

@Preview(name = "출구 두 묶음 · 월료/대체 이동비 미확인", widthDp = 1682, heightDp = 1210)
@Composable fun ExitUnconfirmedPreview() { NamunkiroApp(PreviewFixtures.exitUnconfirmed, NoopActions) }
@Preview(name = "출구 두 묶음 · 반쪽 창", widthDp = 840, heightDp = 1210)
@Composable fun CompactExitPreview() { NamunkiroApp(PreviewFixtures.exit, NoopActions) }

@Preview(name = "첫 주 · 정산 예측 준비", widthDp = 840, heightDp = 1210)
@Composable fun FirstWeekPreview() {
    NamunkiroApp(PreviewFixtures.home.copy(
        budget = PreviewFixtures.budget.copy(elapsedDays = 1,
            thisWeek = PreviewFixtures.budget.thisWeek.copy(baseKm = 82, carryInKm = 0, budgetKm = 82, usedKm = 0.0),
            lastWeek = null, cumulativeBalanceKm = 0.0, projectedDistanceFee = 0,
            onBudgetEndFee = null, zeroFeeWeeklyKm = null),
        forecast = null,
    ), NoopActions)
}

@Preview(name = "정 주임 · 약정보다 덜 타는 홈", widthDp = 1682, heightDp = 1210)
@Composable fun UnderuseHomePreview() { NamunkiroApp(PreviewFixtures.jung, NoopActions) }
@Preview(name = "정 주임 · 반쪽 홈", widthDp = 840, heightDp = 1210)
@Composable fun CompactUnderuseHomePreview() { NamunkiroApp(PreviewFixtures.jung, NoopActions) }
@Preview(name = "정 주임 · 여유 있는 거리 목표", widthDp = 1682, heightDp = 1210)
@Composable fun UnderuseWeekPreview() { NamunkiroApp(PreviewFixtures.jung.copy(screen = Screen.WEEK), NoopActions) }
@Preview(name = "정 주임 · 덜 탄 거리 환급 미확인", widthDp = 1682, heightDp = 1210)
@Composable fun UnderuseContractPreview() { NamunkiroApp(PreviewFixtures.jung.copy(screen = Screen.CONTRACT), NoopActions) }

@Preview(name = "0원 목표가 음수 · 링 대신 출구", widthDp = 840, heightDp = 1210)
@Composable fun NonPositiveTargetPreview() {
    NamunkiroApp(PreviewFixtures.hard.copy(budget = PreviewFixtures.hard.budget?.copy(zeroFeeWeeklyKm = -1.0)), NoopActions)
}
