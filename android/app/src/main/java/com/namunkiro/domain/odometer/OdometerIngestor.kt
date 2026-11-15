package com.namunkiro.domain.odometer

import com.namunkiro.core.result.Source
import com.namunkiro.core.time.AppClock
import com.namunkiro.domain.model.DailyDistance
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 승인 규칙 상수(DEV_PLAN 3.1).
 * @param demoAllowanceKm 시연 전용 여유분. VC는 주행거리를 한 번에 올리므로(+12km, +50km) 실제 시간 기준
 *   250km/h 상한에 걸린다. 데모 모드에서만 100km 정도로 두어 +12·+50은 통과, +580은 보류되게 한다.
 *   실사용 기본값은 0.
 */
data class IngestRules(
    val epsilonKm: Double = 0.1,
    val maxKmPerHour: Double = 250.0,
    val demoAllowanceKm: Double = 0.0,
)

/**
 * 수집기의 지속 상태(저장 대상).
 * - lastAcceptedAt: 벽시계(AppClock.now) 기준. 세션 사이 공백 시간 계산에 쓴다.
 * - lastAcceptedMonoMs: 같은 세션 안의 급증 검사용. 프로세스가 바뀌면 의미 없으므로 세션 id와 함께 본다.
 */
@Serializable
data class OdometerLedger(
    val lastAcceptedKm: Double? = null,
    val lastAcceptedAt: String? = null,          // LocalDateTime ISO 문자열
    val lastAcceptedMonoMs: Long? = null,
    val lastAcceptedSessionId: Long = 0,
    val currentSessionId: Long = 0,
    val heldRawKm: Double? = null,
    val heldStatus: IngestStatus? = null,
    val lastRawKm: Double? = null,
    val lastStatus: IngestStatus? = null,
    /** 가장 최근 공백 회수량(화면 표시용: "공백 구간 50km 회수"). */
    val lastGapKm: Double? = null,
)

/**
 * 원시 주행거리 → 승인값(DEV_PLAN 3.1). 순수 Kotlin, 스레드 안전(synchronized).
 *
 * 일별 집계 규칙: DailyDistance.km는 그날 승인된 전체 증가량(공백 회수분 포함),
 * gapKm는 그중 공백 구간에서 회수한 몫(요일 학습에서는 km − gapKm만 쓴다).
 * 공백 거리는 여러 날에 걸쳤더라도 회수한 날에 몰아서 기록한다("미분류").
 */
class OdometerIngestor(
    private val clock: AppClock,
    ledger: OdometerLedger = OdometerLedger(),
    history: List<DailyDistance> = emptyList(),
    var rules: IngestRules = IngestRules(),
) {
    private var _ledger = ledger
    private val days = history.associateBy { it.date }.toMutableMap()

    val ledger: OdometerLedger @Synchronized get() = _ledger
    val history: List<DailyDistance> @Synchronized get() = days.values.sortedBy { it.date }

    /** 상태를 통째로 교체(데모 시드 적용·저장소 재로드). */
    @Synchronized
    fun reset(ledger: OdometerLedger, history: List<DailyDistance>) {
        _ledger = ledger
        days.clear()
        history.forEach { days[it.date] = it }
    }

    /**
     * 새 세션 시작(앱 포그라운드 진입). 이후 첫 수신값은 직전 세션 승인값과 비교해 공백 회수로 처리한다.
     * @return 새 세션 id
     */
    @Synchronized
    fun startSession(): Long {
        val id = _ledger.currentSessionId + 1
        _ledger = _ledger.copy(currentSessionId = id)
        return id
    }

    @Synchronized
    fun ingest(km: Double?, receivedMonoMs: Long, simulated: Boolean = false): IngestResult {
        val prev = _ledger.lastAcceptedKm
        if (km == null || km.isNaN() || km.isInfinite() || km < 0.0) {
            _ledger = _ledger.copy(lastRawKm = km, lastStatus = IngestStatus.REJECTED_INVALID)
            return IngestResult(IngestStatus.REJECTED_INVALID, prev, 0.0)
        }
        val now = clock.now()

        // 최초 수신: 기준점으로 승인(증가 0)
        if (prev == null) {
            accept(km, now, receivedMonoMs, gap = false, delta = 0.0, simulated = simulated, status = IngestStatus.ACCEPTED)
            return IngestResult(IngestStatus.ACCEPTED, km, 0.0)
        }

        val delta = km - prev
        if (delta < -rules.epsilonKm) return hold(km, IngestStatus.HELD_REVERSE, prev)

        val sameSession = _ledger.lastAcceptedSessionId == _ledger.currentSessionId &&
            _ledger.lastAcceptedMonoMs != null
        val hours = if (sameSession) {
            (receivedMonoMs - _ledger.lastAcceptedMonoMs!!).coerceAtLeast(0) / 3_600_000.0
        } else {
            val last = _ledger.lastAcceptedAt?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            if (last == null) 0.0 else Duration.between(last, now).toMillis().coerceAtLeast(0) / 3_600_000.0
        }
        val limit = rules.maxKmPerHour * hours + rules.epsilonKm + rules.demoAllowanceKm
        if (delta > limit) return hold(km, IngestStatus.HELD_SPIKE, prev)

        val effective = delta.coerceAtLeast(0.0)    // ε 이내 역행은 0으로 본다(승인값을 되돌리지 않음)
        val status = if (sameSession) IngestStatus.ACCEPTED else IngestStatus.ACCEPTED_GAP
        val newKm = if (delta > 0) km else prev
        accept(newKm, now, receivedMonoMs, gap = !sameSession, delta = effective, simulated = simulated, status = status)
        return IngestResult(status, newKm, effective)
    }

    private fun hold(raw: Double, status: IngestStatus, prev: Double): IngestResult {
        _ledger = _ledger.copy(heldRawKm = raw, heldStatus = status, lastRawKm = raw, lastStatus = status)
        return IngestResult(status, prev, 0.0, heldRawKm = raw)
    }

    private fun accept(
        km: Double, now: LocalDateTime, mono: Long, gap: Boolean, delta: Double, simulated: Boolean, status: IngestStatus,
    ) {
        if (delta > 0.0) addToDay(now.toLocalDate(), delta, gap, simulated)
        _ledger = _ledger.copy(
            lastAcceptedKm = km,
            lastAcceptedAt = now.toString(),
            lastAcceptedMonoMs = mono,
            lastAcceptedSessionId = _ledger.currentSessionId,
            heldRawKm = null,
            heldStatus = null,
            lastRawKm = km,
            lastStatus = status,
            lastGapKm = if (gap) delta else _ledger.lastGapKm,
        )
    }

    private fun addToDay(date: LocalDate, delta: Double, gap: Boolean, simulated: Boolean) {
        val old = days[date]
        val src = when {
            simulated -> Source.SIMULATED
            old == null -> Source.OBSERVED
            else -> old.source
        }
        days[date] = DailyDistance(
            date = date,
            km = round3((old?.km ?: 0.0) + delta),
            gapKm = round3((old?.gapKm ?: 0.0) + if (gap) delta else 0.0),
            source = src,
        )
    }

    private fun round3(v: Double) = Math.round(v * 1000.0) / 1000.0
}
