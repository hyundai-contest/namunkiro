package com.namunkiro.demo

import com.namunkiro.core.result.Source
import com.namunkiro.data.AppData
import com.namunkiro.domain.model.Contract
import com.namunkiro.domain.model.ContractType
import com.namunkiro.domain.model.DailyDistance
import com.namunkiro.domain.model.ExitQuotes
import com.namunkiro.domain.model.InsurancePlan
import com.namunkiro.domain.model.TerminationTier
import com.namunkiro.domain.model.TripSegment
import com.namunkiro.domain.odometer.OdometerLedger
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Random

enum class PersonaId(val label: String) {
    KIM("김 대리 · 501일 차 주간 예산"),
    LEE_90("이 과장 · 인도 90일 진단"),
    LEE_499("이 과장 · 1년 4개월 차 출구"),
    PARK("박 과장 · 보험 특약 모드"),
    JUNG("정 주임 · 약정보다 덜 타는 저주행"),
}

/**
 * 시연 페르소나 한 명분의 시드. 이력은 모두 Source.SIMULATED([모의] 배지).
 * @param demoNow DemoClock을 맞출 시각
 * @param answeredQuestions 이미 답한 점진 질문(DeferredQuestionKind 이름)
 */
data class PersonaSeed(
    val id: PersonaId,
    val contract: Contract,
    val history: List<DailyDistance>,
    val demoNow: LocalDateTime,
    val exitQuotes: ExitQuotes = ExitQuotes(),
    val insurancePlan: InsurancePlan? = null,
    val answeredQuestions: Set<String> = emptySet(),
    /** 기어 P→D→P 주행 이력(최근 8주, [모의]). 일별 이력을 나눠 만든 것이라 합계가 그날 거리를 넘지 않는다. */
    val tripSegments: List<TripSegment> = emptyList(),
) {
    val totalKm: Double get() = history.sumOf { it.km }
    /** 시드 마지막 누적 계기판 값 = 시작 계기판 + 이력 합. */
    val lastOdometerKm: Double get() = contract.startOdometerKm + totalKm

    fun toAppData(): AppData = AppData(
        contract = contract,
        history = history,
        ledger = OdometerLedger(lastAcceptedKm = lastOdometerKm, lastAcceptedAt = demoNow.toString()),
        exitQuotes = exitQuotes,
        insurancePlan = insurancePlan,
        answeredQuestions = answeredQuestions,
        demoPersonaId = id.name,
        tripSegments = tripSegments,
    )
}

/**
 * 명세(design/spec_v3_content.py 시나리오 2~6, DEV_PLAN 3·5절) 예시 숫자를 재현하는 시드.
 * 이력은 고정 시드 난수로 만든 뒤 합계를 정수 km로 정확히 맞춘다(실행마다 같은 값).
 */
object PersonaSeeds {
    private val DEMO_TIME: LocalTime = LocalTime.of(18, 30)

    /** 김 대리·이 과장 공통 계약: 국산 SUV 48개월 장기렌트, 60,000km, 100원/km, 공제 없음, 인수가 1,800만 원. */
    private fun rentContract(startOdometerKm: Double, tiers: List<TerminationTier>?) = Contract(
        type = ContractType.LONG_TERM_RENT,
        startDate = LocalDate.of(2026, 11, 1),
        endDate = LocalDate.of(2030, 10, 31),
        totalKm = 60_000,
        unitPricePerKm = 100,
        startOdometerKm = startOdometerKm,
        startOdometerSource = Source.SIMULATED,
        deductKm = 0,
        buyoutPrice = 18_000_000,
        terminationTiers = tiers,
        noticeDeadline = LocalDate.of(2030, 9, 30),
    )

    /**
     * 해지 수수료율 구간(롯데캐피탈 장기렌트 약관 형태를 본뜬 예시, 잔여 대여료 대비).
     * 24개월 경계가 2028-11-01 → 35%에서 30%로 내려감(시나리오 5).
     */
    val LOTTE_RENT_EXAMPLE_TIERS = listOf(
        TerminationTier(untilMonth = 12, rate = 0.40),
        TerminationTier(untilMonth = 24, rate = 0.35),
        TerminationTier(untilMonth = 36, rate = 0.30),
        TerminationTier(untilMonth = 48, rate = 0.25),
    )

    fun get(id: PersonaId): PersonaSeed = when (id) {
        PersonaId.KIM -> kim()
        PersonaId.LEE_90 -> lee90()
        PersonaId.LEE_499 -> lee499()
        PersonaId.PARK -> park()
        PersonaId.JUNG -> jung()
    }.let { it.copy(tripSegments = segmentsFromHistory(it.history, it.demoNow)) }

    /** 내비 목적지 시연 경로(FakeNaviGateway에 넣는 값, [모의]): 강릉 경포대 편도 210km. */
    const val DEMO_NAVI_DESTINATION = "강릉 경포대"
    const val DEMO_NAVI_ONE_WAY_KM = 210.0
    const val DEMO_NAVI_MINUTES = 150

    /**
     * 일별 이력을 주행 단위로 나눈다(최근 [weeks]주, 지금 시각 이전 주행만).
     * 평일: 출근 08:00대·퇴근 18:00대에 각 45%(같은 요일·시각 반복 → 루틴), 나머지는 점심 무렵 1회.
     * 주말: 오전 11시 60%·오후 4시 40%. 10km 미만인 날은 정오 1회.
     */
    internal fun segmentsFromHistory(history: List<DailyDistance>, now: LocalDateTime, weeks: Long = 8): List<TripSegment> {
        val from = now.toLocalDate().minusWeeks(weeks)
        val out = mutableListOf<TripSegment>()
        history.filter { !it.date.isBefore(from) && !it.date.isAfter(now.toLocalDate()) && it.km >= 1.0 }.forEach { d ->
            val wobble = (d.date.dayOfMonth * 7 % 11).toLong() - 5          // ±5분, 실행마다 같은 값
            val weekend = d.date.dayOfWeek == DayOfWeek.SATURDAY || d.date.dayOfWeek == DayOfWeek.SUNDAY
            val parts: List<Pair<LocalTime, Double>> = when {
                d.km < 10 -> listOf(LocalTime.of(12, 0) to d.km)
                weekend -> listOf(LocalTime.of(11, 0) to d.km * 0.6, LocalTime.of(16, 0) to d.km * 0.4)
                else -> {
                    val leg = kotlin.math.floor(d.km * 0.45)
                    listOf(LocalTime.of(8, 5) to leg, LocalTime.of(18, 35) to leg, LocalTime.of(12, 30) to d.km - 2 * leg)
                }
            }
            parts.filter { it.second >= 1.0 }.forEach { (t, km) ->
                val start = LocalDateTime.of(d.date, t).plusMinutes(wobble)
                val minutes = (km * 1.6).toLong().coerceAtLeast(5)
                val end = start.plusMinutes(minutes)
                if (end.isAfter(now)) return@forEach
                out += TripSegment(
                    id = "seed-${d.date}-${t.hour}",
                    start = start, end = end,
                    km = kotlin.math.floor(km * 10) / 10.0,
                    source = Source.SIMULATED,
                )
            }
        }
        return out
    }

    /**
     * 김 대리: 2026-11-01 계약, 2028-03-16(목, 계약 501일째) 기준 누적 사용 21,382km(오늘 45km 포함,
     * 시작 계기판 12 → 계기판 21,394km, 누적 잔고 −807km),
     * 지난주(3/6~3/12) 330km, 이번 주 월~목 48/52/41/45.
     * 예측 엔진의 최근 28일 창(2/17~3/15)이 정확히 평일 평균 46·주말 평균 32km가 되도록 맞춤:
     * 이번 주 예측 = 186(월~목) + 금 46 + 토 강릉 420 + 일 32 = 684km(시나리오 3).
     */
    fun kim(): PersonaSeed {
        val start = LocalDate.of(2026, 11, 1)
        val thisMon = LocalDate.of(2028, 3, 13)
        val earlyEnd = thisMon.minusDays(26)               // 2028-02-16(수)
        val early = generate(start, earlyEnd, total = 20_161, weekday = 44, weekend = 38, jitter = 9, seed = 11)
        val bridge = listOf(45, 45, 34, 34).mapIndexed { i, km -> sim(earlyEnd.plusDays(i + 1L), km) } // 2/17~2/20(목~일)
        val w3 = week(thisMon.minusWeeks(3), listOf(41, 40, 41, 40, 41, 35, 36))   // 274
        val w2 = week(thisMon.minusWeeks(2), listOf(40, 41, 40, 41, 40, 36, 35))   // 273
        val w1 = week(thisMon.minusWeeks(1), listOf(56, 58, 57, 55, 58, 23, 23))   // 330
        val now = week(thisMon, listOf(48, 52, 41, 45))                            // 186
        return PersonaSeed(
            id = PersonaId.KIM,
            contract = rentContract(startOdometerKm = 12.0, tiers = null),
            history = early + bridge + w3 + w2 + w1 + now,
            demoNow = LocalDateTime.of(LocalDate.of(2028, 3, 16), DEMO_TIME),
            answeredQuestions = setOf("DEDUCT_KM", "CONTRACT_TYPE"),
        )
    }

    /** 이 과장 첫 90일(2026-11-01~2027-01-29): 6,600km, 최근 28일 2,052km(주 513km). */
    private fun lee90History(): List<DailyDistance> {
        val start = LocalDate.of(2026, 11, 1)
        val early = generate(start, start.plusDays(61), total = 4_548, weekday = 80, weekend = 57, jitter = 14, seed = 21)
        val recent = generate(start.plusDays(62), start.plusDays(89), total = 2_052, weekday = 78, weekend = 62, jitter = 12, seed = 22)
        return early + recent
    }

    /**
     * 명세 예시(반납 314만·인수 순비용 176만·승계 300만). 만기까지 타는 묶음은 공통 월료를 비교에서 빼고,
     * 지금 나가는 묶음은 남은 월료(월 60만 원)로 해지 수수료를 계산한다.
     * 대체 이동비는 사용자가 넣기 전이라 미확인(null) → 승계·해지는 하한값·잠정 비교로 표시된다(0원으로 확정하지 않음).
     */
    private fun leeQuotes() = ExitQuotes(expectedMarketPrice = 17_500_000, transferCost = 3_000_000, alternativeMobilityCost = null)

    private fun leeContract() = rentContract(startOdometerKm = 12.0, tiers = LOTTE_RENT_EXAMPLE_TIERS)
        .copy(monthlyFee = 600_000, deposit = 0, buyoutWaivesDistanceFee = true)

    private val LEE_ANSWERED = setOf("DEDUCT_KM", "BUYOUT_AND_MARKET", "TERMINATION_TIERS", "CONTRACT_TYPE")

    /** 이 과장 90일 버전: DemoClock 2027-01-30(경과 90일), 누적 6,600km. */
    fun lee90(): PersonaSeed = PersonaSeed(
        id = PersonaId.LEE_90,
        contract = leeContract(),
        history = lee90History(),
        demoNow = LocalDateTime.of(LocalDate.of(2027, 1, 30), DEMO_TIME),
        exitQuotes = leeQuotes(),
        answeredQuestions = LEE_ANSWERED,
    )

    /**
     * 이 과장 499일 버전: DemoClock 2028-03-14(경과 499일), 계기판 31,240km(시작 12 → 사용 31,228km).
     * 첫 90일은 90일 버전과 같고, 이후 409일 24,628km. 인수가 1,800만·시세 1,750만·승계 300만.
     */
    fun lee499(): PersonaSeed {
        val start = LocalDate.of(2026, 11, 1)
        val rest = generate(start.plusDays(90), start.plusDays(498), total = 24_628, weekday = 66, weekend = 46, jitter = 12, seed = 23)
        return PersonaSeed(
            id = PersonaId.LEE_499,
            contract = leeContract(),
            history = lee90History() + rest,
            demoNow = LocalDateTime.of(start.plusDays(499), DEMO_TIME),
            exitQuotes = leeQuotes().copy(quoteValidUntil = LocalDate.of(2028, 3, 25)), // 견적 만료 D-11 → 출구 달력 배너
            answeredQuestions = LEE_ANSWERED,
        )
    }

    /**
     * 박 과장: 운용리스 + 직접 가입 보험 마일리지 특약(1만·1.2만 km 구간).
     * 보험 기간 2027-01-06~2028-01-05(365일 → 주 192km), DemoClock 2027-10-07(종료일 포함 남은 91일=13주),
     * 보험 기간 누적 8,820km(주 약 225km).
     */
    fun park(): PersonaSeed {
        val periodStart = LocalDate.of(2027, 1, 6)
        val today = LocalDate.of(2027, 10, 7)
        val startOdo = 10.0
        return PersonaSeed(
            id = PersonaId.PARK,
            contract = Contract(
                type = ContractType.OPERATING_LEASE,
                startDate = periodStart,
                endDate = LocalDate.of(2030, 1, 5),
                totalKm = 45_000,
                unitPricePerKm = 120,
                startOdometerKm = startOdo,
                startOdometerSource = Source.SIMULATED,
            ),
            history = generate(periodStart, today.minusDays(1), total = 8_820, weekday = 34, weekend = 28, jitter = 8, seed = 31),
            demoNow = LocalDateTime.of(today, DEMO_TIME),
            insurancePlan = InsurancePlan(
                periodStart = periodStart,
                periodEnd = LocalDate.of(2028, 1, 5),
                startOdometerKm = startOdo,
                tiersKm = listOf(10_000, 12_000),
            ),
            answeredQuestions = setOf("CONTRACT_TYPE"),
        )
    }

    /**
     * 정 주임(FEATURE_GUIDE 저주행 픽스처): 김 대리와 같은 계약, 2028-03-16(501일째) 누적 12,000km.
     * 주말 위주 주행, 지난주(3/6~3/12) 주말 100km씩 합계 200km, 이번 주 월~목 25km.
     */
    fun jung(): PersonaSeed {
        val start = LocalDate.of(2026, 11, 1)
        val thisMon = LocalDate.of(2028, 3, 13)
        val early = generate(start, thisMon.minusWeeks(1).minusDays(1), total = 11_775, weekday = 8, weekend = 60, jitter = 6, seed = 41)
        val w1 = week(thisMon.minusWeeks(1), listOf(0, 0, 0, 0, 0, 100, 100))   // 200
        val now = week(thisMon, listOf(10, 0, 15, 0))                            // 25
        return PersonaSeed(
            id = PersonaId.JUNG,
            contract = rentContract(startOdometerKm = 12.0, tiers = null),
            history = early + w1 + now,
            demoNow = LocalDateTime.of(LocalDate.of(2028, 3, 16), DEMO_TIME),
            answeredQuestions = setOf("DEDUCT_KM", "CONTRACT_TYPE"),
        )
    }

    // ───────── 생성 도구 ─────────

    private fun week(monday: LocalDate, kms: List<Int>): List<DailyDistance> =
        kms.mapIndexed { i, km -> sim(monday.plusDays(i.toLong()), km) }

    private fun sim(date: LocalDate, km: Int) = DailyDistance(date = date, km = km.toDouble(), source = Source.SIMULATED)

    /**
     * [from]~[to](포함) 일별 거리. 평일/주말 기본값 ± jitter로 만든 뒤 합계를 [total]에 정확히 맞춘다.
     */
    internal fun generate(
        from: LocalDate, to: LocalDate, total: Int, weekday: Int, weekend: Int, jitter: Int, seed: Long,
    ): List<DailyDistance> {
        val rnd = Random(seed)
        val dates = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        val kms = dates.map { d ->
            val base = if (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) weekend else weekday
            (base + rnd.nextInt(2 * jitter + 1) - jitter).coerceAtLeast(0)
        }.toMutableList()
        var diff = total - kms.sum()
        // 큰 차이는 비례 배분, 남은 차이는 하루 1km씩
        if (dates.isNotEmpty() && kotlin.math.abs(diff) >= dates.size) {
            val each = diff / dates.size
            for (i in kms.indices) kms[i] = (kms[i] + each).coerceAtLeast(0)
            diff = total - kms.sum()
        }
        var i = 0
        var guard = 0
        while (diff != 0 && dates.isNotEmpty() && guard < 1_000_000) {
            val idx = (i * 7919) % kms.size     // 고르게 흩어 놓기
            if (diff > 0) { kms[idx]++; diff-- } else if (kms[idx] > 0) { kms[idx]--; diff++ }
            i++; guard++
        }
        return dates.mapIndexed { k, d -> sim(d, kms[k]) }
    }
}
