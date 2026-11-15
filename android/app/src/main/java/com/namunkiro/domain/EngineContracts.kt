package com.namunkiro.domain

import com.namunkiro.domain.model.Contract
import com.namunkiro.domain.model.DailyDistance
import com.namunkiro.domain.model.ExitQuotes
import com.namunkiro.domain.model.InsurancePlan
import com.namunkiro.domain.model.LifeScenario
import com.namunkiro.domain.model.PlannedTrip
import com.namunkiro.domain.model.SplitPlan
import java.time.LocalDate

/*
 * 도메인 엔진 계약. 구현은 각 하위 패키지(budget/forecast/exit/insurance)에 둔다.
 * 순수 Kotlin(Android 의존 금지). 규칙은 docs/DEV_PLAN.md 3절.
 * 모든 거리 단위 km, 금액 단위 원.
 */

// ───────── 주간 예산 ─────────
data class WeekBudget(
    val weekStart: LocalDate,          // 월요일
    val baseKm: Int,                   // 기본 주간 예산(B)
    val carryInKm: Int,                // 지난주 이월(+ 미달, − 초과)
    val splitDeductKm: Int,            // 분할 상환 중인 몫(양수)
    val budgetKm: Int,                 // 이번 주 예산 = base + carryIn − splitDeduct
    val usedKm: Double,                // 이번 주 실제 주행(오늘까지)
) { val remainingKm: Double get() = budgetKm - usedKm }

enum class RecoveryVerdict { ON_TRACK, RECOVERABLE, HARD_TO_RECOVER }

data class BudgetSnapshot(
    val today: LocalDate,
    val contractDays: Int,
    val elapsedDays: Int,
    val usedKm: Double,                       // 계약 시작 이후 사용
    val cumulativeBalanceKm: Double,          // K×경과/전체 − 사용 (음수 = 초과)
    val thisWeek: WeekBudget,
    val lastWeek: WeekBudget?,
    val requiredWeeklyKm: Double,             // 남은 기간 필요 주간 페이스
    val recentWeeklyAvgKm: Double?,           // 최근 4주 평균(데이터 부족 시 null)
    val verdict: RecoveryVerdict,
    val projectedEndKm: Double,               // 계약 시작 이후 평균 유지 시 만기 사용 거리
    val projectedDistanceFee: Long?,          // 만기 거리 정산(공제 미확인이면 공제 0으로 계산하되 deductUnconfirmed=true)
    val deductUnconfirmed: Boolean,
    /** 오늘부터 매주 기본 예산(B)대로 탈 때의 만기 거리 정산. '예산대로 타면 약 X원' 문구용. */
    val onBudgetEndFee: Long? = null,
    /** 만기 거리 정산을 0원으로 만드는 최대 주간 페이스 = (K + 공제 − 사용) ÷ 남은 주. 음수면 이미 0원 불가. */
    val zeroFeeWeeklyKm: Double? = null,
    /** 덜 타는 경우: 계약 시작 이후 평균 페이스 유지 시 만기에 남는 약정 거리(양수일 때만, 아니면 null). */
    val projectedUnusedKm: Double? = null,
    /** 실제 연간 페이스(계약 시작 이후 평균, km/년). 다음 계약 약정 추천 근거. */
    val annualPaceKm: Double? = null,
    /** 다음 계약 추천 연간 약정 = 연간 페이스 × 1.1(여유 10%)을 5,000km 단위로 올림. 데이터 90일 미만이면 null. */
    val recommendedAnnualKm: Int? = null,
)

/** 이번 주 예상 초과를 다음 주/4주/8주에 나눌 때의 미리보기. */
data class SplitOption(val weeks: Int, val perWeekKm: Int, val resultingWeeklyBudgetKm: Int)

interface WeeklyBudgetEngine {
    fun snapshot(contract: Contract, history: List<DailyDistance>, splits: List<SplitPlan>, today: LocalDate): BudgetSnapshot
    /** excessKm(양수)를 나누는 선택지: 1, 4, 8주. */
    fun splitOptions(contract: Contract, excessKm: Int): List<SplitOption>
}

// ───────── 예측 ─────────
data class DayForecast(val date: LocalDate, val km: Double, val kind: DayKind)
enum class DayKind { ACTUAL, BASELINE, PLANNED }

data class ForecastResult(
    val basedOnPlanOnly: Boolean,             // 데이터 14일 미만
    val weekdayAvgKm: Double,
    val weekendAvgKm: Double,
    val thisWeekDays: List<DayForecast>,      // 월~일
    val thisWeekForecastKm: Double,
    val endKmLow: Double,                     // 만기 사용 거리 범위
    val endKmHigh: Double,
    val endFeeLow: Long,
    val endFeeHigh: Long,
    val excessShareWeekday: Double,           // 초과분 중 평일 반복 비중(0~1)
    val excessShareWeekend: Double,
    val excessShareUnclassified: Double,
    /** 예정 이동별 비용 영향: 그날 평소 값 대비 추가 km와 만기 정산 증가액(반납 기준). */
    val tripImpacts: List<TripImpact> = emptyList(),
    /** 만기 범위의 중심값(점추정)과 그 정산액. 홈의 대표 숫자로 쓰고, 범위는 endFeeLow~endFeeHigh. */
    val endKmPoint: Double? = null,
    val endFeePoint: Long? = null,
)

data class TripImpact(val tripId: String, val extraKm: Double, val extraFee: Long)

interface ForecastEngine {
    fun forecast(
        contract: Contract,
        history: List<DailyDistance>,
        trips: List<PlannedTrip>,
        scenarios: List<LifeScenario>,
        today: LocalDate,
    ): ForecastResult
}

// ───────── 출구 ─────────
enum class ExitKind { RETURN_AT_END, BUY_AND_KEEP, BUY_AND_SELL, TRANSFER_NOW, TERMINATE_NOW }

data class ExitOption(
    val kind: ExitKind,
    val available: Boolean,                   // 계약 유형상 불가하면 false
    val netCost: Long?,                       // 순비용(null = 핵심 입력 부족)
    val cashNeeded: Long?,                    // 당장/만기에 필요한 현금
    val lines: List<Pair<String, Long?>>,     // 계산 내역(항목명, 금액. null = 미확인)
    val unconfirmed: List<String>,            // 결론을 바꿀 수 있는 미확인 항목
    val netCostIsLowerBound: Boolean = false, // 미확인 비용(손상·감가 등)을 뺀 하한값이면 true (기본값 필드는 마지막에 둔다)
)

/** cheapest: netCost가 있는 선택지 중 최저(하한값 포함). 하한값 선택지가 최저이거나 근소 차이면 cheapestTentative=true. */
data class ExitComparison(
    val compareUntil: LocalDate,
    val options: List<ExitOption>,
    val cheapest: ExitKind?,
    val cheapestTentative: Boolean = false,
    /** '지금 나가기'(승계·해지) 묶음 안에서의 최저. cheapest는 '만기까지 타기'(반납·인수 보유·인수 매각) 묶음 안에서의 최저. */
    val cheapestLeaveNow: ExitKind? = null,
    val cheapestLeaveNowTentative: Boolean = false,
    /** 지금 나가면 내지 않아도 되는 남은 월 이용료 합계(입력 없으면 null). 두 묶음을 잇는 비교 항목. */
    val remainingFees: Long? = null,
)

/** 출구 선택지 묶음: 만기까지 이 차를 타는가, 지금 나가는가. 묶음 사이 비교에는 남은 월료와 대체 이동비가 필요하다. */
enum class ExitGroup { KEEP_UNTIL_END, LEAVE_NOW }
val ExitKind.group: ExitGroup
    get() = if (this == ExitKind.TRANSFER_NOW || this == ExitKind.TERMINATE_NOW) ExitGroup.LEAVE_NOW else ExitGroup.KEEP_UNTIL_END

enum class CalendarEventKind { TERMINATION_RATE_DROP, FREE_RETURN_OPEN, NOTICE_DEADLINE, QUOTE_EXPIRY, CONTRACT_END }
data class CalendarEvent(val date: LocalDate, val kind: CalendarEventKind, val label: String)

interface ExitCalculator {
    fun compare(contract: Contract, snapshot: BudgetSnapshot, quotes: ExitQuotes, today: LocalDate): ExitComparison
    fun calendar(contract: Contract, today: LocalDate, quotes: ExitQuotes = ExitQuotes()): List<CalendarEvent>
}

// ───────── 보험 특약 ─────────
data class TierStatus(val tierKm: Int, val remainingKm: Double, val requiredWeeklyKm: Double, val projectedCrossDate: LocalDate?)
data class InsuranceStatus(
    val weeklyBudgetKm: Int,
    val usedKm: Double,
    val currentWeeklyPaceKm: Double,
    val tiers: List<TierStatus>,
    val thisWeekUsedKm: Double = 0.0,   // 이번 주(월~오늘) 주행, 보험 주간 링용
)

interface InsuranceTierPlanner {
    fun status(plan: InsurancePlan, currentOdometerKm: Double, history: List<DailyDistance>, today: LocalDate): InsuranceStatus
}

// ───────── 주행 패턴(기어 기반 주행 단위) ─────────
/** 요일·시간대 묶음별 반복 주행. 같은 요일·비슷한 시각(±90분)·비슷한 거리(±25%)가 3주 이상 반복되면 루틴. */
data class RoutineSlot(
    val dayOfWeek: java.time.DayOfWeek,
    val startHour: Int,
    val typicalKm: Double,
    val occurrences: Int,
)

data class TripPattern(
    val observedTrips: Int,
    val routines: List<RoutineSlot>,
    /** 관측 주행 중 루틴에 속한 거리 비중(0~1). 데이터 부족이면 null. */
    val routineShare: Double?,
    /** 요일별 평소 거리(관측 주행 기반). 데이터 부족한 요일은 없음. */
    val usualKmByDay: Map<java.time.DayOfWeek, Double>,
)

interface TripPatternEngine {
    fun analyze(trips: List<com.namunkiro.domain.model.TripSegment>, today: LocalDate): TripPattern
    /** 예정 이동이 그날 평소보다 긴 이동인지: 평소×2 이상 또는 +100km 이상. 평소 정보가 없으면 null. */
    fun isLongTrip(pattern: TripPattern, date: LocalDate, plannedKm: Double): Boolean?
}
