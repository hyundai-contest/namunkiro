package com.namunkiro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.namunkiro.core.result.Source
import com.namunkiro.domain.*
import com.namunkiro.domain.model.ContractType
import com.namunkiro.ui.components.*
import com.namunkiro.ui.state.*
import java.time.LocalDate

@Composable fun ScreenTitle(title: String, description: String? = null) {
    Text(title, style = MaterialTheme.typography.headlineLarge)
    description?.let { Note(it) }
}
@Composable private fun Missing() { AppCard("연결 확인 중") { Note("데이터가 준비되면 표시합니다. 미수신 값을 0으로 바꾸지 않아요.") } }
@Composable private fun Field(label: String, value: String, onChange: (String) -> Unit, numeric: Boolean = true, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = modifier.fillMaxWidth().heightIn(min = 76.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text), singleLine = true,
        visualTransformation = if (numeric) ThousandsTransformation else androidx.compose.ui.text.input.VisualTransformation.None,
        supportingText = if (numeric && label.contains("원")) ({ value.toLongOrNull()?.let { Text(preciseMoney(it)) } }) else null)
}

@Composable fun OnboardingScreen(state: AppUiState, actions: AppActions) {
    val draft = state.onboarding
    var date by rememberSaveable(draft.endDate) { mutableStateOf(draft.endDate?.toString() ?: "") }
    var total by rememberSaveable(draft.totalKm) { mutableStateOf(draft.totalKm?.toString() ?: "") }
    var rate by rememberSaveable(draft.unitPricePerKm) { mutableStateOf(draft.unitPricePerKm?.toString() ?: "") }
    ScreenTitle("세 가지만 알면 시작할 수 있어요", "30초 시작 · ${draft.step + 1} / 3")
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf("만기일", "총 약정거리", "초과 단가").forEachIndexed { i, text -> Text("${i + 1} $text", color = if (i == draft.step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    AdaptiveColumns(left = {
        AppCard(listOf("계약이 끝나는 날", "전체 계약의 약정거리", "초과 1km당 요금")[draft.step.coerceIn(0, 2)], source = Source.INPUT) {
            when (draft.step) {
                0 -> Field("만기일 · YYYY-MM-DD", date, { date = it; runCatching { LocalDate.parse(it) }.getOrNull()?.let(actions::onboardingSetEndDate) }, false)
                1 -> Field("총 약정거리 · km", total, { total = it; it.toIntOrNull()?.takeIf { n -> n > 0 }?.let(actions::onboardingSetTotalKm) })
                else -> Field("초과 단가 · 원/km", rate, { rate = it; it.toIntOrNull()?.takeIf { n -> n > 0 }?.let(actions::onboardingSetUnitPrice) })
            }
            Note("모르는 나머지 조건은 필요할 때 하나씩 확인해요.")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (draft.step > 0) AppButton("이전", actions::onboardingBack)
                val valid = when (draft.step) { 0 -> runCatching { LocalDate.parse(date) }.isSuccess; 1 -> (total.toIntOrNull() ?: 0) > 0; else -> (rate.toIntOrNull() ?: 0) > 0 }
                AppButton(if (draft.step >= 2) "거리 목표 시작" else "다음", { if (draft.step >= 2) actions.onboardingFinish() else actions.onboardingNext() }, primary = true, enabled = valid)
            }
        }
    }, right = {
        AppCard("차가 채운 시작 계기판", source = draft.startOdometer?.source ?: Source.UNCONFIRMED) {
            BigNumber(draft.startOdometer?.value?.let(::number) ?: "미확인", "km", true)
            Note(if (draft.startOdometer == null) "차량 연결을 확인해 주세요." else "시작값을 자동으로 채웠어요.")
            AppButton("차량 값 다시 조회", actions::retryOdometer)
        }
    })
}

@Composable fun HomeScreen(state: AppUiState, actions: AppActions) {
    val budget = state.budget ?: return Missing()
    val week = budget.thisWeek
    val early = budget.elapsedDays < 7
    val pointFee = state.forecast?.endFeePoint ?: budget.projectedDistanceFee
    val pointKm = state.forecast?.endKmPoint ?: budget.projectedEndKm
    val target = budget.zeroFeeWeeklyKm
    val remaining = target?.minus(week.usedKm)
    val hard = budget.verdict == RecoveryVerdict.HARD_TO_RECOVER || (target != null && target <= 0)
    val unusedKm = budget.projectedUnusedKm
    val underusing = pointFee == 0L && unusedKm != null && unusedKm > 0 && state.contract?.type !in listOf(ContractType.FINANCE_LEASE, ContractType.UNLIMITED)
    val headline = when {
        early -> "첫 주행을 기록하며 거리 목표를 준비해요"
        underusing -> "목표보다 여유 · 만기까지 약 ${km(unusedKm)} 남음"
        hard -> pointFee?.let { "이대로면 만기에 ${money(it)}, 다른 출구도 살펴봐요" } ?: "현재 생활에 맞는 다른 출구도 살펴봐요"
        else -> (remaining?.let { if (it >= 0) "이번 주 ${km(it)} 남음" else "이번 주 목표보다 ${km(-it)} 더 탐" } ?: "이번 주 거리 목표 확인") + (pointFee?.let { " · 만기 정산 ${money(it)} 예상" } ?: "")
    }
    ScreenTitle(headline, "계약 거리의 주간 페이스메이커")
    if (hard && !early) {
        AppCard("지금 생활을 기준으로 본 만기", source = Source.ESTIMATED) {
            pointFee?.let { BigNumber(money(it), compact = true) }
            budget.recentWeeklyAvgKm?.let { Note("최근 주 ${km(it)} 페이스가 이어질 때") }
            DistanceFeeFormula(state.contract, pointKm)
            ReductionImpact(state)
            AppButton("출구 비교 보기", { actions.navigate(Screen.EXIT) }, primary = true)
            Note("통근·돌봄처럼 필요한 이동을 무리하게 줄이지 않아도 돼요.")
        }
        AppCard("페이스 회복 어려움") { HardToRecoverScreen(state, actions, embedded = true) }
        budget.lastWeek?.let { Note("지난주 ${km(it.usedKm)} 탐") }
        return
    }
    AdaptiveColumns(left = {
        AppCard("초과 0원 주간 목표", source = Source.ESTIMATED) {
            if (target != null && target > 0) {
                Text("초과 0원 목표 주 ${km(target)}", style = MaterialTheme.typography.titleMedium)
                BoxWithConstraints(Modifier.fillMaxWidth()) { BudgetRing(target, week.usedKm, maxWidth < 600.dp) }
            } else Note("목표를 계산할 주행·계약 정보를 확인하고 있어요.")
            DetailRow("지금까지 사용", km(week.usedKm), if (state.historySimulated) Source.SIMULATED else Source.OBSERVED)
            budget.lastWeek?.let { Note("지난주 ${km(it.usedKm)} 탐") }
            if (target != null) budget.recentWeeklyAvgKm?.let { Note("최근 주 ${km(it)} · 목표보다 ${km(kotlin.math.abs(it - target))} ${if (it > target) "많음" else "여유"}") }
            if (!early) MarginalCost(state)
        }
    }, right = {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            AppCard(if (underusing && !early) "만기까지 남을 거리" else "이대로면 만기 거리 정산", source = Source.ESTIMATED) {
                val unlimited = state.contract?.type in listOf(ContractType.FINANCE_LEASE, ContractType.UNLIMITED)
                if (unlimited) Text("거리 초과료 비적용", style = MaterialTheme.typography.titleLarge)
                else if (early) Note("주행 7일 후부터 만기 정산을 예측해요.")
                else if (underusing) {
                    unusedKm?.let { BigNumber(number(it), "km", compact = true) }
                    Note("만기 거리 정산 0원")
                    Note("이번 달 장거리 여행을 가도 초과 걱정이 없어요 (현재 페이스 기준)")
                    DistanceFeeFormula(state.contract, pointKm)
                    Note("실제 여행 거리를 계획에 넣으면 남을 거리를 다시 확인할 수 있어요.")
                }
                else {
                    Note("계약 평균 주행에 등록한 계획을 반영하면")
                    pointFee?.let { BigNumber(money(it), compact = true) }
                    DistanceFeeFormula(state.contract, pointKm)
                    state.forecast?.let { AssumptionFees(it) }
                    ActionableFees(state)
                    ReductionImpact(state)
                }
                Note("손상·감가 비용은 별도이며 미확인입니다.")
                AppButton("만기 예측 보기", { actions.navigate(Screen.FORECAST) })
            }
            budget.recommendedAnnualKm?.let { recommended ->
                AppCard("다음 계약은 내 주행에 맞게", source = Source.ESTIMATED) {
                    budget.annualPaceKm?.let { Note("실제 연 ${km(it)} 페이스 → 다음 계약은 연 ${km(recommended)} 약정을 검토해 보세요.") }
                        ?: Note("다음 계약은 연 ${km(recommended)} 약정을 검토해 보세요.")
                    Note("낮은 약정은 월 납입금이 줄어들 수 있어요. 실제 요금과 변경 조건은 견적을 확인해 주세요.")
                }
            }
            AppCard("계약 페이스와 지금까지 주행 비교") {
                BigNumber(number(kotlin.math.abs(budget.cumulativeBalanceKm)), "km", true)
                Note(if (budget.cumulativeBalanceKm < 0) "계약 페이스보다 더 탔어요. 이 거리 자체를 만기 비용으로 바꾸지는 않아요." else "계약 페이스보다 덜 탔어요.")
                if (budget.verdict == RecoveryVerdict.HARD_TO_RECOVER) HardToRecoverScreen(state, actions, embedded = true)
                else AppButton("이번 주 계획 보기", { actions.navigate(Screen.WEEK) }, primary = true)
            }
        }
    })
}

@Composable fun WeekScreen(state: AppUiState, actions: AppActions) {
    val budget = state.budget ?: return Missing()
    val forecast = state.forecast
    val target = budget.zeroFeeWeeklyKm
    ScreenTitle("이번 이동의 비용을 미리 봐요", forecast?.let { "이번 주 예상 ${km(it.thisWeekForecastKm)}" }?.plus(target?.let { " · 초과 0원 목표 주 ${km(it)}" } ?: ""))
    AdaptiveColumns(left = {
        AppCard("요일별 거리", source = Source.ESTIMATED) {
            WeekBars(forecast?.thisWeekDays.orEmpty(), (target ?: 0.0).coerceAtLeast(0.0) / 7.0)
            state.trips.forEach { trip ->
                DetailRow(tripLabel(trip.label), km(trip.km), trip.source)
                Note("${readableDate(trip.date, budget.today)} · ${if (trip.otherCar) "다른 차로 이동" else "이 차의 예정 이동에 반영"}")
                val impact = forecast?.tripImpacts?.firstOrNull { it.tripId == trip.id }
                impact?.let {
                    val after = targetAfterTrip(state, if (trip.otherCar) 0 else trip.km)
                    Note("${if (trip.otherCar) "다른 차로 이동 중" else "이 차로 가면"} ${if (it.extraFee >= 0) "+" else "−"}${preciseMoney(it.extraFee)}" + (after?.let { " · 목표 주 ${km(it)}" } ?: ""))
                    Note("비용 변화는 평소 같은 날 이동과 비교한 금액이에요.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton(if (trip.otherCar) "이 차로" else "다른 차로", { actions.toggleTripOtherCar(trip.id) })
                    AppButton("삭제", { actions.removeTrip(trip.id) })
                }
                val contract = state.contract
                if (!trip.otherCar && forecast != null && contract != null && contract.type !in listOf(ContractType.FINANCE_LEASE, ContractType.UNLIMITED)) {
                    val chargeable = (forecast.endKmHigh - contract.totalKm - (contract.deductKm ?: 0)).coerceAtLeast(0.0)
                    val savings = (minOf(trip.km.toDouble(), chargeable) * contract.unitPricePerKm).toLong()
                    Note("다른 차로 가면 최대 ${preciseMoney(savings)} 절약${if (contract.deductKm == null) " · 공제 확인 전" else ""}")
                }
            }
        }
    }, right = {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            AppCard("이동 계획과 만기 비용", source = Source.ESTIMATED) {
                forecast?.endFeePoint?.let { Note("현재 계획의 만기 정산 ${money(it)}") }
                target?.let { Note("초과 0원 목표 주 ${km(it)} · 남은 약정거리를 남은 전체 주수로 나눠요.") }
                ReductionImpact(state)
                MarginalCost(state)
                Note("예정 이동과 실제 주행이 바뀌면 남은 기간의 목표를 다시 계산해요.")
            }
            AppCard("예정 이동 추가", source = Source.INPUT) {
                var label by rememberSaveable { mutableStateOf("") }; var distance by rememberSaveable { mutableStateOf("") }
                var date by rememberSaveable(budget.today) { mutableStateOf(budget.today.toString()) }
                Field("이동 이름", label, { label = it }, false)
                Field("날짜 · YYYY-MM-DD", date, { date = it }, false)
                Field("왕복 거리 · km", distance, { distance = it })
                val validDate = runCatching { LocalDate.parse(date) }.getOrNull()
                AppButton("추가", { validDate?.let { actions.addTrip(it, distance.toIntOrNull() ?: 0, label); label = ""; distance = "" } }, primary = true,
                    enabled = validDate != null && (distance.toIntOrNull() ?: 0) > 0 && label.isNotBlank())
                Note("등록 목적지는 해당 날짜의 평소 예상 거리를 대체합니다.")
            }
        }
    })
}

@Composable fun ForecastScreen(state: AppUiState, actions: AppActions) {
    val forecast = state.forecast ?: return Missing()
    ScreenTitle("한 점보다, 가능한 범위로", if (forecast.basedOnPlanOnly) "주행 기록이 2주 미만이라 대략적인 예측이에요" else "최근 습관과 계약 평균을 함께 반영했어요")
    AdaptiveColumns(left = {
        AppCard("만기 거리 예상", source = Source.ESTIMATED) {
            RangeBand(forecast.endKmLow, forecast.endKmHigh, state.contract?.totalKm ?: 0,
                currentKm = state.budget?.usedKm, progress = state.budget?.let { if (it.contractDays > 0) it.elapsedDays.toDouble() / it.contractDays else null })
            forecast.endFeePoint?.let { Note("계약 평균으로 가면 만기 정산 ${money(it)}") }
            DistanceFeeFormula(state.contract, forecast.endKmPoint)
            AssumptionFees(forecast)
            DetailRow("평소 평일 하루", km(forecast.weekdayAvgKm))
            DetailRow("평소 주말 하루", km(forecast.weekendAvgKm))
            if (forecast.excessShareUnclassified <= .5 && maxOf(forecast.excessShareWeekday, forecast.excessShareWeekend) > 0) Note(if (forecast.excessShareWeekday >= forecast.excessShareWeekend) "평일 반복 이동이 초과 거리의 큰 원인이에요." else "주말 이동이 초과 거리의 큰 원인이에요.")
        }
    }, right = {
        AppCard("생활 변화 시나리오", source = Source.INPUT) {
            var beforeFee by rememberSaveable { mutableStateOf<Long?>(null) }
            var beforeCount by rememberSaveable { mutableStateOf<Int?>(null) }
            var label by rememberSaveable { mutableStateOf("통근 변화") }; var distance by rememberSaveable { mutableStateOf("20") }
            val today = state.budget?.today ?: state.contract?.startDate
            var from by rememberSaveable(today) { mutableStateOf(today?.toString() ?: "") }
            var until by rememberSaveable { mutableStateOf("") }
            Field("시나리오 이름", label, { label = it }, false)
            Field("하루 거리 가감 · km", distance, { distance = it })
            Field("시작일 · YYYY-MM-DD", from, { from = it }, false)
            Field("종료일 · 비우면 계속", until, { until = it }, false)
            val parsedFrom = runCatching { LocalDate.parse(from) }.getOrNull()
            val parsedUntil = runCatching { LocalDate.parse(until) }.getOrNull()
            AppButton("시나리오 적용", { parsedFrom?.let { beforeFee = forecast.endFeePoint; beforeCount = state.scenarios.size; actions.addScenario(label, distance.toDoubleOrNull() ?: 0.0, it, parsedUntil) } }, primary = true,
                enabled = label.isNotBlank() && distance.toDoubleOrNull()?.isFinite() == true && parsedFrom != null && (until.isBlank() || parsedUntil != null && !parsedUntil.isBefore(parsedFrom)))
            state.scenarios.forEach { scenario ->
                DetailRow(scenario.label, "${km(scenario.dailyDeltaKm)} / 일", Source.INPUT)
                AppButton("시나리오 삭제", { actions.removeScenario(scenario.id) })
            }
            val oldFee = beforeFee
            val newFee = forecast.endFeePoint
            if (oldFee != null && newFee != null && beforeCount?.let { state.scenarios.size > it } == true) {
                Note("생활 변화 반영 → 만기 정산 ${if (newFee >= oldFee) "+" else "−"}${preciseMoney(newFee - oldFee)} (${preciseMoney(oldFee)} → ${preciseMoney(newFee)})")
            }
            state.trips.forEach { trip -> AppButton("${tripLabel(trip.label)}: ${if (trip.otherCar) "다른 차" else "이 차"}", { actions.toggleTripOtherCar(trip.id) }) }
        }
    })
}

@Composable fun HardToRecoverScreen(state: AppUiState, actions: AppActions, embedded: Boolean = false) {
    val b = state.budget ?: return Missing()
    if (!embedded) ScreenTitle("현재 생활로는 페이스 회복이 어려워요")
    Text("생활에 맞는 다른 출구도 살펴봐요", style = MaterialTheme.typography.titleLarge)
    b.recentWeeklyAvgKm?.let { PaceComparison(b.requiredWeeklyKm, it) }
    b.projectedDistanceFee?.let { Note("지금 페이스면 거리 정산 ${money(it)}") }
    ActionableFees(state)
    DistanceFeeFormula(state.contract, b.projectedEndKm)
    Note("약정 변경 가능 여부와 인수 시 거리 정산 면제 조항을 확인해 주세요.")
    AppButton("계약 조건 보기", { actions.navigate(Screen.CONTRACT) })
    AppButton("출구 계획 보기", { actions.navigate(Screen.EXIT) }, primary = true)
}

fun exitLabel(kind: ExitKind) = when (kind) {
    ExitKind.RETURN_AT_END -> "만기 반납"; ExitKind.BUY_AND_KEEP -> "인수 후 보유"; ExitKind.BUY_AND_SELL -> "인수 후 매각"
    ExitKind.TRANSFER_NOW -> "지금 승계"; ExitKind.TERMINATE_NOW -> "지금 해지"
}
@Composable fun ExitScreen(state: AppUiState, actions: AppActions) {
    ScreenTitle("다음 출구를, 같은 기준으로", state.exit?.let { "비교 종료일 ${readableDate(it.compareUntil)} · 실제 부담과 그날 준비할 돈을 나눠 봐요" })
    val returnOption = state.exit?.options?.firstOrNull { it.kind == ExitKind.RETURN_AT_END && it.available }
    if (returnOption != null && (state.budget?.projectedDistanceFee == 0L || returnOption.lines.any { (label, amount) -> label.contains("거리") && label.contains("정산") && amount == 0L })) {
        Note("저주행 차량은 중고 시세 확인 가치가 있어요.")
    }
    state.budget?.let { b ->
        AppCard("주행거리로 보는 만기 비용") {
            DetailRow("지금까지 사용", km(b.usedKm), if (state.historySimulated) Source.SIMULATED else Source.OBSERVED)
            DetailRow("이 페이스면 만기", km(b.projectedEndKm), Source.ESTIMATED)
            state.contract?.let { c -> Note("약정보다 ${km((b.projectedEndKm - c.totalKm).coerceAtLeast(0.0))} 초과 예상") }
            DistanceFeeFormula(state.contract, b.projectedEndKm)
            b.projectedDistanceFee?.let { Note("만기 거리 정산 ${money(it)}") }
        }
    }
    AdaptiveColumns(left = {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            AppCard("출구 달력", source = Source.INPUT) {
                state.calendar.forEach { event -> DetailRow(readableDate(event.date, state.budget?.today), event.label) }
                if (state.calendar.isEmpty()) Note("확인한 일정이 없습니다. 해지 요율·반납 조건을 채우면 표시해요.")
            }
            AppCard("견적과 시세 수정", source = Source.INPUT) {
                var market by rememberSaveable(state.quotes.expectedMarketPrice) { mutableStateOf(state.quotes.expectedMarketPrice?.toString() ?: "") }
                var transfer by rememberSaveable(state.quotes.transferCost) { mutableStateOf(state.quotes.transferCost?.toString() ?: "") }
                var mobility by rememberSaveable(state.quotes.alternativeMobilityCost) { mutableStateOf(state.quotes.alternativeMobilityCost?.toString() ?: "") }
                Field("종료일 예상 시세 · 원", market, { market = it })
                Field("승계 견적 · 원", transfer, { transfer = it })
                Field("종료일까지 대체 이동비 · 원", mobility, { mobility = it })
                val valid = listOf(market, transfer, mobility).all { it.isBlank() || it.toLongOrNull()?.let { n -> n >= 0 } == true }
                AppButton("입력 조건으로 다시 비교", { actions.updateQuotes(state.quotes.copy(expectedMarketPrice = market.toLongOrNull(), transferCost = transfer.toLongOrNull(), alternativeMobilityCost = mobility.toLongOrNull())) }, primary = true, enabled = valid)
                Note("빈 값은 미확인으로 남습니다. 과거 납입액은 비교에서 제외합니다.")
            }
        }
    }, right = {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            val comparison = state.exit
            if (comparison == null) Missing()
            if (comparison != null) {
                ExitGroupSection(
                    state = state,
                    title = "만기까지 타고 나서",
                    explanation = "남은 월 이용료는 세 선택에 공통이라 실제 부담에서 제외했어요. 남는 차 가치는 빼고 비교해요.",
                    options = comparison.options.filter { it.kind.group == ExitGroup.KEEP_UNTIL_END },
                    cheapest = comparison.cheapest?.takeIf { it.group == ExitGroup.KEEP_UNTIL_END },
                    tentative = comparison.cheapestTentative,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                val avoidedFees = comparison.remainingFees?.let { "남은 월 이용료 ${money(it)}을 내지 않지만" }
                    ?: "남은 월 이용료를 내지 않지만 (월 이용료 미확인)"
                Text("지금 나가면 $avoidedFees, 남은 기간의 대체 이동 비용이 필요해요",
                    style = MaterialTheme.typography.titleMedium)
                val keep = comparison.options.firstOrNull { it.kind == comparison.cheapest }?.netCost
                val leave = comparison.options.firstOrNull { it.kind == comparison.cheapestLeaveNow }?.netCost
                val fees = comparison.remainingFees
                if (fees != null && state.quotes.alternativeMobilityCost != null && keep != null && leave != null) {
                    // 지금 나가기의 엔진 금액에는 대체 이동비가 이미 들어 있으므로 두 번 더하지 않는다.
                    Note("만기까지 총 부담 ${money(fees + keep)} (월료 ${money(fees)} + 종료 시 부담 ${money(keep)}) · 지금 나가기 총 부담 ${money(leave)} (대체 이동비 포함)")
                } else Note("월 이용료·대체 이동비를 넣으면 두 묶음을 총액으로 비교할 수 있어요.")
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                ExitGroupSection(
                    state = state,
                    title = "지금 나가면",
                    explanation = "승계·해지를 비교해요. 비교 종료일까지의 대체 이동비를 포함하며, 미입력 비용은 0원으로 확정하지 않아요.",
                    options = comparison.options.filter { it.kind.group == ExitGroup.LEAVE_NOW },
                    cheapest = comparison.cheapestLeaveNow?.takeIf { it.group == ExitGroup.LEAVE_NOW },
                    tentative = comparison.cheapestLeaveNowTentative,
                    mobilityUnconfirmed = state.quotes.alternativeMobilityCost == null,
                )
            }
        }
    })
}

/** 각 묶음 안의 추천은 엔진 결과를 그대로 표시하고 묶음 간 순위를 만들지 않는다. */
@Composable private fun ExitGroupSection(
    state: AppUiState,
    title: String,
    explanation: String,
    options: List<ExitOption>,
    cheapest: ExitKind?,
    tentative: Boolean,
    mobilityUnconfirmed: Boolean = false,
) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Note(explanation)
    if (cheapest != null) {
        Text("이 묶음에서는 ${exitLabel(cheapest)}의 실제 부담이 낮아요.", style = MaterialTheme.typography.titleMedium)
        Note(if (tentative) "확인 전 결과 · 빠진 비용을 넣으면 가장 낮은 선택지가 바뀔 수 있어요."
            else "입력 조건 기준 · 실행 가능 여부를 확인해 주세요.")
    } else Note("입력이 부족하거나 가능한 선택지가 없어 이 묶음의 최저를 정할 수 없어요.")
    if (mobilityUnconfirmed) Note("대체 이동비 미확인 · 아래 금액에 이동비가 더 들어요.")
    if (options.isEmpty()) Note("이 묶음의 비교 데이터가 없습니다.")
    options.forEach { option ->
        var expanded by rememberSaveable(option.kind) { mutableStateOf(false) }
        AppCard(exitLabel(option.kind)) {
            if (!option.available) Note("계약 유형상 선택할 수 없어요.")
            else {
                val lowerBound = option.netCostIsLowerBound || mobilityUnconfirmed
                if (lowerBound && option.netCost != null) {
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(7.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                        Text("최소 금액 · 빠진 비용은 별도", Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) { Note("실제 부담${if (lowerBound) " · 최소" else ""}"); Text(money(option.netCost), color = if (option.kind == cheapest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                    Column(Modifier.weight(1f)) { Note("그날 준비할 돈"); Text(money(option.cashNeeded)) }
                    Column(Modifier.weight(.7f)) { Note("확인할 항목"); Text("${option.unconfirmed.size}개") }
                }
                AppButton(if (expanded) "자세한 계산 접기" else "자세한 계산 보기", { expanded = !expanded })
                if (expanded) {
                    option.lines.forEach { (label, amount) ->
                        val rate = state.contract?.unitPricePerKm
                        val isDistance = label.contains("거리") && label.contains("정산")
                        val distanceLabel = if (isDistance && amount != null && rate != null && rate > 0) "$label (${km(amount.toDouble() / rate)} × ${number(rate)}원)" else label
                        val displayLabel = if (amount != null && amount < 0 && (label.contains("시세") || label.contains("가치"))) "남는 차 가치 (빼기)" else distanceLabel
                        DetailRow(displayLabel, won(amount?.let { if (displayLabel == "남는 차 가치 (빼기)") kotlin.math.abs(it) else it }))
                    }
                    if (option.unconfirmed.isNotEmpty()) Note("확인 필요: ${option.unconfirmed.joinToString()} · 결과가 바뀔 수 있어요.")
                }
            }
        }
    }
}

@Composable fun ContractScreen(state: AppUiState, actions: AppActions) {
    val c = state.contract ?: return Missing()
    ScreenTitle("계약서가 채워질수록, 더 정확하게")
    AdaptiveColumns(left = {
        AppCard("내 계약", source = Source.INPUT) {
            DetailRow("계약 유형", when(c.type) { ContractType.LONG_TERM_RENT -> "장기렌트"; ContractType.OPERATING_LEASE -> "운용리스"; ContractType.FINANCE_LEASE -> "금융리스"; ContractType.UNLIMITED -> "무제한"; ContractType.UNKNOWN -> "미확인" })
            DetailRow("계약 기간", "${c.startDate} – ${c.endDate}")
            DetailRow("총 약정거리", km(c.totalKm)); DetailRow("초과 단가", "${number(c.unitPricePerKm)} 원/km")
            DetailRow("시작 계기판", km(c.startOdometerKm), c.startOdometerSource)
            if (c.type in listOf(ContractType.FINANCE_LEASE, ContractType.UNLIMITED)) Note("거리 초과료 비적용 · 반납 가능 여부는 계약 유형에 따릅니다.")
            Note("계약 유형 변경")
            var typeMenu by remember { mutableStateOf(false) }
            Box {
                AppButton("계약 유형 선택", { typeMenu = true })
                DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                    ContractType.entries.forEach { type -> DropdownMenuItem(
                        text = { Text(when(type) { ContractType.LONG_TERM_RENT -> "장기렌트"; ContractType.OPERATING_LEASE -> "운용리스"; ContractType.FINANCE_LEASE -> "금융리스"; ContractType.UNLIMITED -> "무제한"; ContractType.UNKNOWN -> "미확인 유지" }) },
                        onClick = { typeMenu = false; actions.answerDeferred(DeferredQuestionKind.CONTRACT_TYPE, if (type == ContractType.UNKNOWN) null else type.name) },
                        modifier = Modifier.heightIn(min = 76.dp),
                    ) }
                }
            }
        }
    }, right = {
        AppCard("필요할 때 확인할 조건") {
            DetailRow("공제 거리", km(c.deductKm), if (c.deductKm == null) Source.UNCONFIRMED else Source.INPUT)
            DetailRow("덜 탄 거리 환급", c.underuseRefund?.let { if (it) "계약에 환급 조건 있음" else "환급 없음" } ?: "미확인", if (c.underuseRefund == null) Source.UNCONFIRMED else Source.INPUT)
            Note("덜 탄 거리 환급은 일반적이지 않아요. 계약서를 확인해 주세요.")
            DetailRow("만기 인수가", money(c.buyoutPrice), if (c.buyoutPrice == null) Source.UNCONFIRMED else Source.INPUT)
            DetailRow("인수 시 거리 정산 면제", c.buyoutWaivesDistanceFee?.let { if (it) "면제" else "면제 아님" } ?: "미확인")
            DetailRow("자유반납 가능일", c.freeReturnDate?.toString() ?: "미확인")
            DetailRow("만기 통지 기한", c.noticeDeadline?.toString() ?: "미확인")
            DetailRow("해지 요율", c.terminationTiers?.joinToString { "${it.untilMonth}개월까지 ${number(it.rate * 100)}%" } ?: "미확인")
            Note("미확인은 0 또는 없음과 다릅니다.")
            var deduct by rememberSaveable(c.deductKm) { mutableStateOf(c.deductKm?.toString() ?: "") }
            Field("공제 거리 · km", deduct, { deduct = it })
            AppButton("공제 거리 확인", { actions.answerDeferred(DeferredQuestionKind.DEDUCT_KM, deduct) }, enabled = deduct.toIntOrNull()?.let { it >= 0 } == true)
        }
    })
}

@Composable fun InsuranceScreen(state: AppUiState, actions: AppActions) {
    val insurance = state.insurance ?: return if (state.insurancePlanMissing)
        AppCard("보험 마일리지 특약") { Note("보험 특약 정보를 넣으면 구간 목표를 보여 드려요.") } else Missing()
    ScreenTitle("환급 구간까지, 남은 거리를 계획해요", "보험 마일리지 특약 · 렌트 초과료와 별도 계산")
    AdaptiveColumns(left = {
        AppCard("보험 기간의 거리", source = if (state.historySimulated) Source.SIMULATED else Source.OBSERVED) {
            BigNumber(number(insurance.usedKm), "km", true)
            DetailRow("최근 주간 페이스", "${km(insurance.currentWeeklyPaceKm)} / 주", Source.ESTIMATED)
            insurance.tiers.firstOrNull { it.remainingKm > 0 && it.requiredWeeklyKm > 0 }?.let { tier ->
                Note("${km(tier.tierKm)} 구간 유지 목표 주 ${km(tier.requiredWeeklyKm)}")
                BudgetRing(tier.requiredWeeklyKm, insurance.thisWeekUsedKm, compact = true)
            }
            AppButton("이동 계획 보기", { actions.navigate(Screen.WEEK) })
        }
    }, right = {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) { insurance.tiers.forEach { tier ->
            AppCard("${km(tier.tierKm)} 구간", source = Source.ESTIMATED) {
                BigNumber(number(tier.remainingKm), "km 남음", true)
                Note("구간 유지 목표 주 ${km(tier.requiredWeeklyKm)}")
                Note(tier.projectedCrossDate?.let { "$it 경계 초과 예상" } ?: "보험 기간 안에 경계 초과 예상 없음")
                Note("환급액·인정 조건은 보험사 계약 확인이 필요해요.")
            }
        } }
    })
}

/** 주행 중에는 숫자·버튼·메뉴·데모 표시를 포함하지 않는다. */
@Composable fun DrivingLockScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("주행 중에는 화면을 쉬어 갑니다", style = MaterialTheme.typography.headlineMedium)
            Note("정차하면 보던 화면에서 이어집니다 · 입력 중 내용은 저장했어요")
        }
    }
}
@Composable fun AnomalyHoldScreen(state: AppUiState, actions: AppActions) {
    ScreenTitle("평소와 다른 값은, 잠시 보류해요")
    AppCard("거리 업데이트 보류", source = Source.UNCONFIRMED) {
        state.anomaly?.let { anomaly ->
            DetailRow("마지막 승인값", km(anomaly.lastAcceptedKm), state.odometer?.source ?: Source.OBSERVED)
            DetailRow("보류 원시값", km(anomaly.heldRawKm), Source.UNCONFIRMED)
            DetailRow("수신 간격", "${number(anomaly.intervalSec)}초")
            DetailRow("이 간격의 허용 증가량", km(anomaly.allowedKm), Source.ESTIMATED)
        } ?: Note("차량 수신 상태를 확인하고 있어요.")
        Note("거리 목표와 예측은 마지막 정상값을 유지합니다. 다시 조회해 검증을 통과할 때만 반영해요.")
        state.lastReceivedAt?.let { Note("마지막 수신 $it") }
        AppButton("차량 값 다시 조회", actions::retryOdometer, primary = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DeferredQuestionSheet(question: DeferredQuestion, actions: AppActions) {
    var value by rememberSaveable(question.kind) { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { actions.answerDeferred(question.kind, null) }) {
        Column(Modifier.fillMaxWidth().padding(32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(question.title, style = MaterialTheme.typography.headlineMedium)
            Note(question.reason)
            SourceBadge(Source.INPUT)
            val label = when(question.kind) {
                DeferredQuestionKind.DEDUCT_KM -> "공제 거리 · km (없으면 0)"
                DeferredQuestionKind.BUYOUT_AND_MARKET -> "인수가 / 시세 · 원"
                DeferredQuestionKind.TERMINATION_TIERS -> "해지 요율 · 계약서 조건"
                DeferredQuestionKind.CONTRACT_TYPE -> "계약 유형"
            }
            if (question.kind == DeferredQuestionKind.CONTRACT_TYPE) {
                ContractType.entries.filter { it != ContractType.UNKNOWN }.forEach { type ->
                    val title = when (type) {
                        ContractType.LONG_TERM_RENT -> "장기렌트"
                        ContractType.OPERATING_LEASE -> "운용리스"
                        ContractType.FINANCE_LEASE -> "금융리스"
                        ContractType.UNLIMITED -> "무제한"
                        ContractType.UNKNOWN -> "미확인"
                    }
                    AppButton(title, { actions.answerDeferred(question.kind, type.name) })
                }
            } else {
                Field(label, value, { value = it }, question.kind == DeferredQuestionKind.DEDUCT_KM)
                AppButton("확인하고 적용", { actions.answerDeferred(question.kind, value) }, primary = true,
                    enabled = if (question.kind == DeferredQuestionKind.DEDUCT_KM) value.toIntOrNull()?.let { it >= 0 } == true else value.isNotBlank())
            }
            AppButton("나중에", { actions.answerDeferred(question.kind, null) })
            Spacer(Modifier.height(24.dp))
        }
    }
}
