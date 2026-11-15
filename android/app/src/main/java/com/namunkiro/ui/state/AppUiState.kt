package com.namunkiro.ui.state

import com.namunkiro.core.result.Sourced
import com.namunkiro.domain.BudgetSnapshot
import com.namunkiro.domain.CalendarEvent
import com.namunkiro.domain.ExitComparison
import com.namunkiro.domain.ForecastResult
import com.namunkiro.domain.InsuranceStatus
import com.namunkiro.domain.SplitOption
import com.namunkiro.domain.model.Contract
import com.namunkiro.domain.model.ExitQuotes
import com.namunkiro.domain.model.LifeScenario
import com.namunkiro.domain.model.PlannedTrip
import com.namunkiro.domain.model.InsurancePlan
import java.time.LocalDate
import java.time.LocalDateTime

/** 화면 위에 덮이는 상태. DRIVING_LOCK이 최우선. */
enum class Gate { NORMAL, DRIVING_LOCK, ANOMALY_HOLD }

enum class Screen { HOME, WEEK, FORECAST, EXIT, CONTRACT, INSURANCE }
enum class ContractField { END_DATE, TOTAL_KM, UNIT_PRICE, DEDUCT_KM, BUYOUT_PRICE, BUYOUT_WAIVES_DISTANCE_FEE, UNDERUSE_REFUND, TYPE }
enum class VehicleConnectionStatus { WAITING, READY, TIMED_OUT, NOT_SUPPORTED, ERROR }

/** 30초 시작 입력 초안. */
data class OnboardingDraft(
    val step: Int = 0,                         // 0: 만기일, 1: 총 약정거리, 2: 단가
    val endDate: LocalDate? = null,
    val totalKm: Int? = null,
    val unitPricePerKm: Int? = null,
    val startOdometer: Sourced<Double>? = null, // 차가 채움
)

/** 점진 질문(필요한 화면 첫 진입 시 1개). */
enum class DeferredQuestionKind { DEDUCT_KM, BUYOUT_AND_MARKET, TERMINATION_TIERS, CONTRACT_TYPE }
data class DeferredQuestion(val kind: DeferredQuestionKind, val title: String, val reason: String)

data class AnomalyInfo(val lastAcceptedKm: Double, val heldRawKm: Double, val intervalSec: Long, val allowedKm: Double)

/** 앱 전체 화면 상태. 화면은 이 값만 읽고, 동작은 [AppActions]로만 보낸다. */
data class AppUiState(
    val gate: Gate = Gate.NORMAL,
    val screen: Screen = Screen.HOME,
    val contract: Contract? = null,            // null이면 온보딩 표시
    val onboarding: OnboardingDraft = OnboardingDraft(),
    val odometer: Sourced<Double>? = null,
    val lastReceivedAt: LocalDateTime? = null,
    val budget: BudgetSnapshot? = null,
    val forecast: ForecastResult? = null,
    val splitOptions: List<com.namunkiro.domain.SplitOption> = emptyList(),
    val trips: List<PlannedTrip> = emptyList(),
    val scenarios: List<LifeScenario> = emptyList(),
    val quotes: ExitQuotes = ExitQuotes(),
    val exit: ExitComparison? = null,
    val calendar: List<CalendarEvent> = emptyList(),
    val insurance: InsuranceStatus? = null,
    /** 계약은 있으나 보험 마일리지 특약 정보를 넣지 않음 → 보험 탭은 '연결 확인 중'이 아니라 빈 상태. */
    val insurancePlanMissing: Boolean = false,
    val anomaly: AnomalyInfo? = null,
    val deferredQuestion: DeferredQuestion? = null,
    val banner: String? = null,                // 출구 달력 등 상단 배너 1회
    val historySimulated: Boolean = false,     // 시드 이력 사용 중이면 [모의] 표시
    val demoMode: Boolean = false,
    val naviSuggestion: NaviSuggestion? = null,
    val tripPattern: com.namunkiro.domain.TripPattern? = null,
    val today: LocalDate = LocalDate.now(),
    val demoPersonaName: String? = null,
    val insurancePlan: InsurancePlan? = null,
    val vehicleConnection: VehicleConnectionStatus = VehicleConnectionStatus.WAITING,
)

/** 내비 목적지 기반 예정 이동 제안. 사용자가 수락해야 예정 이동이 된다. */
data class NaviSuggestion(
    val destinationName: String,
    val oneWayKm: Double,
    val roundTripKm: Double,
    val date: LocalDate,
    val isLongTrip: Boolean?,
    val extraFee: Long?,            // CostModel 기준 만기 정산 증가(왕복 기준)
    val source: com.namunkiro.core.result.Source,
)

/** 화면 → 로직 동작. 구현은 app/AppViewModel. */
interface AppActions {
    fun enterDemo()
    fun exitDemo()
    fun rebaseOdometer()
    fun openDeferredQuestion(kind: DeferredQuestionKind)
    fun setInsurancePlan(plan: InsurancePlan?)
    /** null은 선택 조건을 미확인으로 되돌린다. 필수 조건은 null을 허용하지 않는다. */
    fun updateContractField(field: ContractField, value: String?)
    fun navigate(screen: Screen)
    fun onboardingSetEndDate(date: LocalDate)
    fun onboardingSetTotalKm(km: Int)
    fun onboardingSetUnitPrice(won: Int)
    fun onboardingNext()
    fun onboardingBack()
    fun onboardingFinish()
    fun answerDeferred(kind: DeferredQuestionKind, value: String?)  // null = 나중에
    fun addTrip(date: LocalDate, km: Int, label: String)
    fun toggleTripOtherCar(id: String)
    fun removeTrip(id: String)
    fun chooseSplit(weeks: Int)
    fun addScenario(label: String, dailyDeltaKm: Double, from: LocalDate, until: LocalDate?)
    fun removeScenario(id: String)
    fun updateQuotes(quotes: ExitQuotes)
    fun retryOdometer()
    fun dismissBanner()
    fun acceptNaviSuggestion(roundTrip: Boolean) {}
    fun dismissNaviSuggestion() {}
}

/** Preview·테스트용 빈 구현. */
object NoopActions : AppActions {
    override fun enterDemo() {}
    override fun exitDemo() {}
    override fun rebaseOdometer() {}
    override fun openDeferredQuestion(kind: DeferredQuestionKind) {}
    override fun setInsurancePlan(plan: InsurancePlan?) {}
    override fun updateContractField(field: ContractField, value: String?) {}
    override fun navigate(screen: Screen) {}
    override fun onboardingSetEndDate(date: LocalDate) {}
    override fun onboardingSetTotalKm(km: Int) {}
    override fun onboardingSetUnitPrice(won: Int) {}
    override fun onboardingNext() {}
    override fun onboardingBack() {}
    override fun onboardingFinish() {}
    override fun answerDeferred(kind: DeferredQuestionKind, value: String?) {}
    override fun addTrip(date: LocalDate, km: Int, label: String) {}
    override fun toggleTripOtherCar(id: String) {}
    override fun removeTrip(id: String) {}
    override fun chooseSplit(weeks: Int) {}
    override fun addScenario(label: String, dailyDeltaKm: Double, from: LocalDate, until: LocalDate?) {}
    override fun removeScenario(id: String) {}
    override fun updateQuotes(quotes: ExitQuotes) {}
    override fun retryOdometer() {}
    override fun dismissBanner() {}
}
