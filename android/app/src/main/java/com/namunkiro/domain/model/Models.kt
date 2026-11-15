package com.namunkiro.domain.model

import com.namunkiro.core.result.Source
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** 계약 유형. FINANCE_LEASE·UNLIMITED는 거리 정산을 적용하지 않는다. */
@Serializable
enum class ContractType { LONG_TERM_RENT, OPERATING_LEASE, FINANCE_LEASE, UNLIMITED, UNKNOWN }

/** 해지 수수료율 구간: 계약 개시 후 [untilMonth]개월 이하일 때 [rate](잔여 대여료 대비). */
@Serializable
data class TerminationTier(val untilMonth: Int, val rate: Double)

/**
 * 계약. 필수 3개(endDate, totalKm, unitPricePerKm)만 있으면 동작하고,
 * 나머지는 점진 질문으로 채운다(null = 미확인).
 */
@Serializable
data class Contract(
    val type: ContractType = ContractType.UNKNOWN,
    @Serializable(with = LocalDateSerializer::class) val startDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val endDate: LocalDate,
    val totalKm: Int,
    val unitPricePerKm: Int,
    val startOdometerKm: Double,
    val startOdometerSource: Source = Source.OBSERVED,
    val deductKm: Int? = null,
    val buyoutPrice: Long? = null,
    val monthlyFee: Long? = null,
    val deposit: Long? = null,
    val terminationTiers: List<TerminationTier>? = null,
    @Serializable(with = LocalDateSerializer::class) val freeReturnDate: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val noticeDeadline: LocalDate? = null,
    val buyoutWaivesDistanceFee: Boolean? = null,
    /** 약정보다 덜 탄 거리의 환급 여부. null = 미확인(일반적이지 않음, 상품별 확인). */
    val underuseRefund: Boolean? = null,
)

/** 하루 단위 주행 거리. km = gapKm을 포함한 그날 전체 거리. gapKm = 앱이 꺼져 있던 공백 구간에서 회수한 거리. 요일 학습에는 km − gapKm만 쓴다. */
@Serializable
data class DailyDistance(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val km: Double,
    val gapKm: Double = 0.0,
    val source: Source = Source.OBSERVED,
)

/** 예정 이동. otherCar = 다른 차로 가는 이동(이 차 거리에서 제외). */
@Serializable
data class PlannedTrip(
    val id: String,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val km: Int,
    val label: String,
    val source: Source = Source.INPUT,
    val otherCar: Boolean = false,
)

/** 큰 초과를 여러 주에 나눠 반영하는 계획. */
@Serializable
data class SplitPlan(
    val totalKm: Int,
    val weeks: Int,
    @Serializable(with = LocalDateSerializer::class) val firstWeekStart: LocalDate,
)

/** 생활 변화 시나리오: 기간 동안 하루 거리 가감. */
@Serializable
data class LifeScenario(
    val id: String,
    val label: String,
    val dailyDeltaKm: Double,
    @Serializable(with = LocalDateSerializer::class) val from: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val until: LocalDate? = null,
)

/** 출구 견적(사용자 입력). */
@Serializable
data class ExitQuotes(
    val expectedMarketPrice: Long? = null,
    val transferCost: Long? = null,
    val acquisitionTaxRate: Double = 0.07,
    val alternativeMobilityCost: Long? = null,
    val damageEstimate: Long? = null,            // 반납 시 손상·감가 예상(입력). null = 미확인
    val transferDepositSettlement: Long? = null, // 승계 시 보증금·선납 정산(+환급, −추가 부담)
    @Serializable(with = LocalDateSerializer::class) val quoteValidUntil: LocalDate? = null,
)

/** 보험 마일리지 특약 계획. tiersKm = 구간 상한 목록(오름차순). */
@Serializable
data class InsurancePlan(
    @Serializable(with = LocalDateSerializer::class) val periodStart: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val periodEnd: LocalDate,
    val startOdometerKm: Double,
    val tiersKm: List<Int>,
)

/**
 * 기어 P→D→P로 나뉜 주행 한 번(앱이 화면에 있는 동안 관측한 것만).
 * 앱이 꺼져 있던 동안의 주행은 여기 들어오지 않고 DailyDistance.gapKm로만 남는다.
 */
@Serializable
data class TripSegment(
    val id: String,
    @Serializable(with = LocalDateTimeSerializer::class) val start: java.time.LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class) val end: java.time.LocalDateTime,
    val km: Double,
    val source: com.namunkiro.core.result.Source = com.namunkiro.core.result.Source.OBSERVED,
)
