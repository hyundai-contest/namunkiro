package com.namunkiro.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.namunkiro.core.result.Source
import com.namunkiro.core.result.Sourced
import com.namunkiro.core.time.DemoClock
import com.namunkiro.data.AppData
import com.namunkiro.data.AppRepository
import com.namunkiro.demo.DemoController
import com.namunkiro.demo.PersonaId
import com.namunkiro.demo.SyncResult
import com.namunkiro.domain.ExitCalculator
import com.namunkiro.domain.ForecastEngine
import com.namunkiro.domain.InsuranceTierPlanner
import com.namunkiro.domain.WeeklyBudgetEngine
import com.namunkiro.domain.budget.DefaultWeeklyBudgetEngine
import com.namunkiro.domain.exit.DefaultExitCalculator
import com.namunkiro.domain.forecast.DefaultForecastEngine
import com.namunkiro.domain.insurance.DefaultInsuranceTierPlanner
import com.namunkiro.domain.model.Contract
import com.namunkiro.domain.model.ContractType
import com.namunkiro.domain.model.ExitQuotes
import com.namunkiro.domain.model.LifeScenario
import com.namunkiro.domain.model.PlannedTrip
import com.namunkiro.domain.model.SplitPlan
import com.namunkiro.domain.model.TerminationTier
import com.namunkiro.domain.odometer.OdometerIngestor
import com.namunkiro.ui.state.AnomalyInfo
import com.namunkiro.ui.state.AppActions
import com.namunkiro.ui.state.AppUiState
import com.namunkiro.ui.state.DeferredQuestion
import com.namunkiro.ui.state.DeferredQuestionKind
import com.namunkiro.ui.state.Gate
import com.namunkiro.ui.state.OnboardingDraft
import com.namunkiro.ui.state.Screen
import com.namunkiro.vehicle.Capability
import com.namunkiro.vehicle.FakeVehicleGateway
import com.namunkiro.vehicle.Gear
import com.namunkiro.vehicle.PleosVehicleGateway
import com.namunkiro.vehicle.VehicleGateway
import com.namunkiro.vehicle.VehicleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.math.roundToInt

/** 데모 패널 표시용 상태. */
data class DemoPanelState(
    val personaId: String? = null,
    val now: LocalDateTime? = null,
    val clockShifted: Boolean = false,
    val usingFake: Boolean = false,
    val vehicleKm: Double? = null,
    val acceptedKm: Double? = null,
    val gear: Gear = Gear.UNKNOWN,
    val uxRestricted: Boolean? = null,
    val vehicleError: String? = null,
    val message: String? = null,
)

/** 화면에만 필요한 휘발성 상태. */
private data class LocalUi(
    val screen: Screen = Screen.HOME,
    val onboarding: OnboardingDraft = OnboardingDraft(),
    val deferred: DeferredQuestion? = null,
    val dismissedBanners: Set<String> = emptySet(),
    /** 이번 세션에서 공백 구간을 회수했으면 그 거리(상단 배너 1회). */
    val gapNoticeKm: Double? = null,
    /** 보류(HELD_*)가 생긴 시각(데모 시계). 수신 간격을 보류 시점에 고정해 화면이 흐르지 않게 한다. */
    val heldAt: LocalDateTime? = null,
    val tick: Long = 0,
)

/**
 * 앱 조립: VehicleGateway(실제/가짜) → OdometerIngestor → AppRepository → 엔진 4개 → StateFlow<AppUiState>.
 * 규칙은 docs/DEV_PLAN.md 3절, 시연 흐름은 5절.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app), AppActions {
    val clock = DemoClock()
    private val repo = AppRepository.create(app)
    private val ingestor = OdometerIngestor(clock, repo.current.ledger, repo.current.history)
    private val demo = DemoController(repo, clock, ingestor)

    private val budgetEngine: WeeklyBudgetEngine = DefaultWeeklyBudgetEngine()
    private val forecastEngine: ForecastEngine = DefaultForecastEngine()
    private val exitCalculator: ExitCalculator = DefaultExitCalculator()
    private val insurancePlanner: InsuranceTierPlanner = DefaultInsuranceTierPlanner()

    private val realGateway: VehicleGateway = PleosVehicleGateway(app, clock)
    private val fakeGateway = FakeVehicleGateway(clock, initialOdometerKm = repo.current.ledger.lastAcceptedKm ?: 21_394.0)
    private val gateway = MutableStateFlow<VehicleGateway>(realGateway)
    val usingFake: Boolean get() = gateway.value === fakeGateway

    private val drafts = app.getSharedPreferences("onboarding_draft", 0)
    private fun savedDraft() = OnboardingDraft(
        step = drafts.getInt("step", 0).coerceIn(0, 2),
        endDate = drafts.getString("end", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        totalKm = drafts.getInt("total", 0).takeIf { it > 0 },
        unitPricePerKm = drafts.getInt("unit", 0).takeIf { it > 0 })
    private val local = MutableStateFlow(LocalUi(onboarding = savedDraft()))
    val inputError = MutableStateFlow<String?>(null)
    private fun saveDraft() {
        val d = local.value.onboarding
        drafts.edit().putInt("step", d.step).putString("end", d.endDate?.toString())
            .putInt("total", d.totalKm ?: 0).putInt("unit", d.unitPricePerKm ?: 0).apply()
        inputError.value = null
    }
    private val demoMessage = MutableStateFlow<String?>(null)
    private var foreground = false

    private val vehicle: StateFlow<VehicleState> = gateway.flatMapLatest { it.state }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VehicleState())

    val state: StateFlow<AppUiState> = combine(repo.data, vehicle, local, gateway) { data, v, ui, gw ->
        runCatching { build(data, v, ui, gw === fakeGateway) }
            .onFailure { Log.e(TAG, "state build failed", it) }
            .getOrElse { AppUiState(contract = data.contract, screen = ui.screen, demoMode = data.demoPersonaId != null) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    val demoPanel: StateFlow<DemoPanelState> = combine(repo.data, vehicle, demoMessage, local, gateway) { data, v, msg, _, gw ->
        DemoPanelState(
            personaId = data.demoPersonaId,
            now = clock.now().withNano(0),
            clockShifted = clock.isShifted,
            usingFake = gw === fakeGateway,
            vehicleKm = v.lastOdometer?.km,
            acceptedKm = data.ledger.lastAcceptedKm,
            gear = v.gear,
            uxRestricted = v.uxRestricted,
            vehicleError = v.error,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DemoPanelState())

    init {
        // 재시작 후: 데모 시드가 남아 있으면 DemoClock·데모 규칙 복원
        demo.restoreAfterRestart()
        if (demo.isDemo) demoUseFake(true)
        // 수신값 → 수집기 → 저장. 같은 값의 주기적 재전송은 거른다.
        viewModelScope.launch {
            vehicle.map { it.lastOdometer }.distinctUntilChanged().collect { raw ->
                if (raw == null) return@collect
                val r = ingestor.ingest(raw.km, raw.receivedMonoMs, raw.simulated)
                repo.saveOdometer(ingestor.ledger, ingestor.history)
                if (r.status == com.namunkiro.domain.odometer.IngestStatus.ACCEPTED_GAP && r.deltaKm >= 0.1) {
                    local.update { it.copy(gapNoticeKm = r.deltaKm) }
                }
                val held = r.status == com.namunkiro.domain.odometer.IngestStatus.HELD_SPIKE ||
                    r.status == com.namunkiro.domain.odometer.IngestStatus.HELD_REVERSE
                if (held && local.value.heldAt == null) local.update { it.copy(heldAt = clock.now()) }
                if (ingestor.ledger.heldRawKm == null && local.value.heldAt != null) local.update { it.copy(heldAt = null) }
                Log.i(TAG, "ingest km=${raw.km} -> $r")
            }
        }
        // 날짜 경계·DemoClock 흐름 반영(1분마다 재계산)
        viewModelScope.launch {
            while (true) { delay(60_000); bump() }
        }
    }

    // ───────── 생명주기 ─────────

    /** 포그라운드 진입 = 세션 시작(DEV_PLAN 1절). */
    fun onForeground() {
        foreground = true
        ingestor.startSession()
        repo.saveLedger(ingestor.ledger)
        gateway.value.start()
        bump()
    }

    fun onBackground() { foreground = false }

    override fun onCleared() {
        realGateway.stop()
        fakeGateway.stop()
    }

    // ───────── 상태 조립 ─────────

    private fun build(data: AppData, v: VehicleState, ui: LocalUi, fake: Boolean): AppUiState {
        val today = clock.today()
        val contract = data.contract
        val simulatedOdo = fake || data.ledger.lastAcceptedKm == null && v.lastOdometer?.simulated == true
        val odometer = data.ledger.lastAcceptedKm?.let { Sourced(it, if (fake) Source.SIMULATED else Source.OBSERVED) }

        val gearGates = v.gear != Gear.UNKNOWN
        val gate = when {
            v.uxRestricted == true || (gearGates && v.gear != Gear.PARK) -> Gate.DRIVING_LOCK
            data.ledger.heldRawKm != null -> Gate.ANOMALY_HOLD
            else -> Gate.NORMAL
        }

        val anomaly = data.ledger.heldRawKm?.let { held ->
            val last = data.ledger.lastAcceptedKm ?: 0.0
            val lastAt = data.ledger.lastAcceptedAt?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            val sec = lastAt?.let { Duration.between(it, ui.heldAt ?: clock.now()).seconds.coerceAtLeast(0) } ?: 0L
            val rules = ingestor.rules
            AnomalyInfo(last, held, sec, rules.maxKmPerHour * sec / 3600.0 + rules.epsilonKm + rules.demoAllowanceKm)
        }

        // 홈·회복 카드의 "주 N km 페이스"는 함께 보이는 만기 금액(계약 시작 이후 평균 = projectedEndKm,
        // 예측 엔진 endFeePoint)과 같은 근거여야 한다(리허설 v2 F8). 최근 4주 습관 금액은 예측의 범위 값으로 따로 보인다.
        val budget = contract?.let { budgetEngine.snapshot(it, data.history, data.splitPlans, today) }?.let { b ->
            if (b.elapsedDays >= 7 && b.contractDays > 0) b.copy(recentWeeklyAvgKm = b.projectedEndKm / b.contractDays * 7.0) else b
        }
        val forecast = contract?.let { forecastEngine.forecast(it, data.history, data.plannedTrips, data.scenarios, today) }
        val excess = if (budget != null && forecast != null) (forecast.thisWeekForecastKm - budget.thisWeek.budgetKm).roundToInt() else 0
        val splitOptions = if (contract != null && excess > 0) budgetEngine.splitOptions(contract, excess) else emptyList()
        val exit = if (contract != null && budget != null) exitCalculator.compare(contract, budget, data.exitQuotes, today) else null
        val calendar = contract?.let { exitCalculator.calendar(it, today, data.exitQuotes) }.orEmpty()
        val insurance = data.insurancePlan?.let { plan ->
            insurancePlanner.status(plan, data.ledger.lastAcceptedKm ?: (plan.startOdometerKm + data.history.sumOf { it.km }), data.history, today)
        }

        // 출구 달력 배너: 14일 이내 이벤트, 같은 이벤트는 1회
        val soon = calendar.firstOrNull { ChronoUnit.DAYS.between(today, it.date) in 0..14 }
        val banner = soon?.takeIf { bannerKey(it.date, it.label) !in ui.dismissedBanners }?.let { e ->
            val d = ChronoUnit.DAYS.between(today, e.date)
            val next = calendar.firstOrNull { it.date.isAfter(e.date) && it.kind == com.namunkiro.domain.CalendarEventKind.TERMINATION_RATE_DROP }
            "출구 달력 · ${e.label} D-$d (${e.date})" + (next?.let { " · 다음 해지 요율 인하 ${it.date}" } ?: "")
        }

        val onboarding = ui.onboarding.copy(
            startOdometer = data.ledger.lastAcceptedKm?.let { Sourced(it, if (simulatedOdo) Source.SIMULATED else Source.OBSERVED) }
                ?: v.lastOdometer?.km?.let { Sourced(it, if (v.lastOdometer.simulated) Source.SIMULATED else Source.OBSERVED) },
        )

        return AppUiState(
            gate = gate,
            screen = ui.screen,
            contract = contract,
            onboarding = onboarding,
            odometer = odometer,
            lastReceivedAt = data.ledger.lastAcceptedAt?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
            budget = budget,
            forecast = forecast,
            splitOptions = splitOptions,
            trips = data.plannedTrips.sortedBy { it.date },
            scenarios = data.scenarios,
            quotes = data.exitQuotes,
            exit = exit,
            calendar = calendar,
            insurance = insurance,
            insurancePlanMissing = contract != null && data.insurancePlan == null,
            anomaly = anomaly,
            deferredQuestion = ui.deferred,
            banner = ui.gapNoticeKm?.let { "앱이 꺼져 있던 동안 달린 ${"%,.1f".format(it)} km를 회수했어요 [관측·공백] · 요일별 학습에서는 제외" } ?: banner,
            historySimulated = data.history.any { it.source == Source.SIMULATED },
            demoMode = data.demoPersonaId != null || fake,
        )
    }

    private fun bannerKey(date: LocalDate, label: String) = "$date|$label"

    private fun bump() = local.update { it.copy(tick = it.tick + 1) }

    // ───────── 점진 질문 ─────────

    /** 화면 첫 진입 때 미확인·미응답 질문 1개를 고른다. */
    private fun questionFor(screen: Screen): DeferredQuestion? {
        val c = repo.current.contract ?: return null
        val answered = repo.current.answeredQuestions
        fun open(k: DeferredQuestionKind) = k.name !in answered
        return when (screen) {
            Screen.HOME, Screen.FORECAST ->
                if (c.deductKm == null && open(DeferredQuestionKind.DEDUCT_KM) &&
                    c.type !in listOf(ContractType.FINANCE_LEASE, ContractType.UNLIMITED))
                    DeferredQuestion(DeferredQuestionKind.DEDUCT_KM, "공제 거리가 있나요?", "만기 거리 정산은 약정거리에서 공제 거리를 더 뺀 뒤 계산해요. 계약서의 '공제' 또는 '무상 초과' 항목을 확인해 주세요.")
                else null
            Screen.EXIT -> when {
                (c.buyoutPrice == null || repo.current.exitQuotes.expectedMarketPrice == null) && open(DeferredQuestionKind.BUYOUT_AND_MARKET) ->
                    DeferredQuestion(DeferredQuestionKind.BUYOUT_AND_MARKET, "인수가와 예상 시세를 알려 주세요", "인수 후 보유·매각 비용을 계산하는 데 필요해요. 형식: 인수가,시세 (원) 예) 18000000,17500000")
                c.terminationTiers == null && open(DeferredQuestionKind.TERMINATION_TIERS) ->
                    DeferredQuestion(DeferredQuestionKind.TERMINATION_TIERS, "중도 해지 수수료율 구간을 알려 주세요", "출구 달력에 요율이 내려가는 날을 표시해요. 형식: 개월:요율;개월:요율 예) 12:0.40;24:0.35")
                else -> null
            }
            Screen.CONTRACT ->
                if (c.type == ContractType.UNKNOWN && open(DeferredQuestionKind.CONTRACT_TYPE))
                    DeferredQuestion(DeferredQuestionKind.CONTRACT_TYPE, "어떤 계약인가요?", "금융리스·무제한 계약은 거리 정산이 없고 반납 선택지가 달라져요.")
                else null
            else -> null
        }
    }

    private fun showQuestionFor(screen: Screen) {
        val q = questionFor(screen)
        local.update { it.copy(deferred = q ?: it.deferred?.takeIf { d -> d.kind.name !in repo.current.answeredQuestions }) }
    }

    // ───────── AppActions ─────────

    override fun navigate(screen: Screen) {
        local.update { it.copy(screen = screen, deferred = null) }
        showQuestionFor(screen)
    }

    override fun onboardingSetEndDate(date: LocalDate) { local.update { it.copy(onboarding = it.onboarding.copy(endDate = date)) }; saveDraft() }
    override fun onboardingSetTotalKm(km: Int) { local.update { it.copy(onboarding = it.onboarding.copy(totalKm = km)) }; saveDraft() }
    override fun onboardingSetUnitPrice(won: Int) { local.update { it.copy(onboarding = it.onboarding.copy(unitPricePerKm = won)) }; saveDraft() }
    override fun onboardingNext() {
        val d = local.value.onboarding
        val valid = if (d.step == 0) d.endDate?.let { it.isAfter(clock.today()) && !it.isAfter(clock.today().plusYears(20)) } == true else d.totalKm in 1..1_000_000
        if (valid) local.update { it.copy(onboarding = it.onboarding.copy(step = (it.onboarding.step + 1).coerceAtMost(2))) }
        saveDraft()
        if (!valid) inputError.value = "만기일은 내일부터 20년 이내, 약정거리는 1~1,000,000km로 입력해 주세요."
    }
    override fun onboardingBack() { local.update { it.copy(onboarding = it.onboarding.copy(step = (it.onboarding.step - 1).coerceAtLeast(0))) }; saveDraft() }

    override fun onboardingFinish() {
        val d = local.value.onboarding
        if (d.unitPricePerKm !in 1..100_000) { inputError.value = "초과 단가는 1~100,000원/km로 입력해 주세요."; return }
        val end = d.endDate?.takeIf { it.isAfter(clock.today()) && !it.isAfter(clock.today().plusYears(20)) } ?: return
        val total = d.totalKm?.takeIf { it in 1..1_000_000 } ?: return
        val unit = d.unitPricePerKm?.takeIf { it in 1..100_000 } ?: return
        val start = state.value.onboarding.startOdometer
        val contract = Contract(
            startDate = clock.today(),
            endDate = end,
            totalKm = total,
            unitPricePerKm = unit,
            startOdometerKm = start?.value ?: 0.0,
            startOdometerSource = start?.source ?: Source.UNCONFIRMED,
        )
        repo.saveContract(contract)
        drafts.edit().clear().apply()
        local.update { it.copy(screen = Screen.HOME, onboarding = OnboardingDraft()) }
        showQuestionFor(Screen.HOME)
    }

    override fun answerDeferred(kind: DeferredQuestionKind, value: String?) {
        val c = repo.current.contract
        if (value != null && c != null) {
            val v = value.trim()
            when (kind) {
                DeferredQuestionKind.DEDUCT_KM -> v.replace(",", "").toIntOrNull()?.takeIf { it >= 0 }?.let { repo.saveContract(c.copy(deductKm = it)) }
                DeferredQuestionKind.CONTRACT_TYPE -> runCatching { ContractType.valueOf(v) }.getOrNull()?.let { repo.saveContract(c.copy(type = it)) }
                DeferredQuestionKind.BUYOUT_AND_MARKET -> {
                    val parts = v.split(",", "/", " ").map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { it.toLongOrNull() }
                    parts.getOrNull(0)?.let { repo.saveContract(c.copy(buyoutPrice = it)) }
                    parts.getOrNull(1)?.let { repo.saveExitQuotes(repo.current.exitQuotes.copy(expectedMarketPrice = it)) }
                }
                DeferredQuestionKind.TERMINATION_TIERS -> {
                    val tiers = v.split(";").mapNotNull { seg ->
                        val (m, r) = seg.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
                        val month = m.trim().toIntOrNull() ?: return@mapNotNull null
                        val rate = r.trim().removeSuffix("%").toDoubleOrNull()?.let { if (it > 1.0) it / 100 else it } ?: return@mapNotNull null
                        TerminationTier(month, rate)
                    }.sortedBy { it.untilMonth }
                    if (tiers.isNotEmpty()) repo.saveContract(c.copy(terminationTiers = tiers))
                }
            }
        }
        repo.markQuestionAnswered(kind.name)   // null(나중에)도 다시 묻지 않음
        local.update { it.copy(deferred = null) }
    }

    override fun addTrip(date: LocalDate, km: Int, label: String) {
        if (km <= 0 || label.isBlank()) return
        repo.upsertPlannedTrip(PlannedTrip(UUID.randomUUID().toString(), date, km, label.trim()))
    }

    override fun toggleTripOtherCar(id: String) {
        val trip = repo.current.plannedTrips.firstOrNull { it.id == id } ?: return
        repo.upsertPlannedTrip(trip.copy(otherCar = !trip.otherCar))
    }

    override fun removeTrip(id: String) = repo.removePlannedTrip(id)

    /** 이번 주 예상 초과를 다음 주부터 [weeks]주에 나눠 반영. */
    override fun chooseSplit(weeks: Int) {
        val s = state.value
        val budget = s.budget ?: return
        val forecast = s.forecast ?: return
        val excess = (forecast.thisWeekForecastKm - budget.thisWeek.budgetKm).roundToInt()
        if (excess <= 0 || weeks <= 0) return
        val nextMonday = clock.today().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val others = repo.current.splitPlans.filterNot { it.firstWeekStart == nextMonday }
        repo.saveSplitPlans(others + SplitPlan(excess, weeks, nextMonday))
        demoMessage.value = null
    }

    override fun addScenario(label: String, dailyDeltaKm: Double, from: LocalDate, until: LocalDate?) {
        repo.saveScenarios(repo.current.scenarios + LifeScenario(UUID.randomUUID().toString(), label.trim(), dailyDeltaKm, from, until))
    }

    override fun removeScenario(id: String) = repo.saveScenarios(repo.current.scenarios.filterNot { it.id == id })

    override fun updateQuotes(quotes: ExitQuotes) = repo.saveExitQuotes(quotes)

    override fun retryOdometer() {
        gateway.value.refresh()
    }

    override fun dismissBanner() {
        if (local.value.gapNoticeKm != null) { local.update { it.copy(gapNoticeKm = null) }; return }
        val s = state.value
        val today = clock.today()
        val keys = s.calendar.filter { ChronoUnit.DAYS.between(today, it.date) in 0..14 }.map { bannerKey(it.date, it.label) }
        local.update { it.copy(dismissedBanners = it.dismissedBanners + keys) }
    }

    // ───────── 데모 패널 ─────────

    fun demoApplyPersona(id: PersonaId) {
        demoUseFake(true)
        val r = demo.applyPersona(id)
        fakeGateway.setOdometer(repo.current.ledger.lastAcceptedKm!!)
        local.update { it.copy(screen = if (id == PersonaId.PARK) Screen.INSURANCE else Screen.HOME, deferred = null, dismissedBanners = emptySet()) }
        demoMessage.value = "${id.label} 시드 적용 · " + describe(r)
        bump()
    }

    fun demoSync() {
        val km = vehicle.value.lastOdometer?.km
        demoMessage.value = if (km == null) "차량 계기판 값을 아직 받지 못했어요" else describe(demo.syncWithVehicle(km))
        bump()
    }

    private fun describe(r: SyncResult): String = when (r) {
        is SyncResult.Shifted -> "계기판 ${"%,.0f".format(r.vehicleKm)} km 기준 (시드 이동 ${"%+,.0f".format(r.shiftKm)} km)"
        is SyncResult.NeedVcRaise -> "차량 값(${"%,.0f".format(r.vehicleKm)} km)이 시드보다 작음 → VC 주행거리를 ${"%,.0f".format(r.targetKm)} km 이상으로 올린 뒤 다시 동기화"
        SyncResult.NoDemo -> "데모 시드가 없어요"
    }

    fun demoShiftDays(days: Long) { demo.advanceDays(days); bump() }

    fun demoUseFake(useFake: Boolean) {
        val target = if (useFake) fakeGateway else realGateway
        if (gateway.value === target) return
        gateway.value.stop()
        if (useFake) fakeGateway.setOdometer(repo.current.ledger.lastAcceptedKm ?: fakeGateway.currentOdometerKm())
        gateway.value = target
        ingestor.startSession()
        target.start()
        demoMessage.value = if (useFake) "가짜 게이트웨이 사용 중 [모의]" else "실제 차량 SDK 사용 중"
    }

    fun fakeAddKm(km: Double) = fakeGateway.addKm(km)
    fun fakeSetGear(g: Gear) = fakeGateway.setGear(g)
    fun fakeSetUx(r: Boolean) = fakeGateway.setUxRestricted(r)

    fun demoReset() {
        demo.exitDemo()
        demoUseFake(false)
        local.value = LocalUi(onboarding = savedDraft())
        demoMessage.value = "데이터를 초기화했어요"
        bump()
    }

    fun enterDemo() {
        if (!demo.resumeDemo()) demoApplyPersona(PersonaId.KIM)
        demoUseFake(true)
        repo.current.ledger.lastAcceptedKm?.let(fakeGateway::setOdometer)
        local.value = LocalUi()
    }

    /** 큰 계기판 차이를 주행으로 승인하지 않고 사용 이력을 유지한 채 기준을 옮긴다. */
    fun rebaseOdometer() {
        val data = repo.current
        val km = data.ledger.heldRawKm?.takeIf { it.isFinite() && it >= 0 } ?: return
        val previous = data.ledger.lastAcceptedKm ?: return
        val shift = km - previous
        val c = data.contract
        if (c != null && c.startOdometerKm + shift < 0) return
        val ledger = com.namunkiro.domain.odometer.OdometerLedger(
            lastAcceptedKm = km, lastAcceptedAt = clock.now().toString(),
            lastAcceptedMonoMs = clock.monotonicMs(), currentSessionId = data.ledger.currentSessionId,
            lastAcceptedSessionId = data.ledger.currentSessionId, lastRawKm = km)
        val next = data.copy(ledger = ledger, contract = c?.copy(startOdometerKm = c.startOdometerKm + shift),
            insurancePlan = data.insurancePlan?.let { it.copy(startOdometerKm = it.startOdometerKm + shift) })
        ingestor.reset(ledger, next.history)
        repo.replaceAll(next)
        local.update { it.copy(heldAt = null) }
    }

    private companion object { const val TAG = "AppViewModel" }
}
