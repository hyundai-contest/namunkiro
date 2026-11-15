package com.namunkiro.demo

import com.namunkiro.core.time.DemoClock
import com.namunkiro.data.AppData
import com.namunkiro.data.AppRepository
import com.namunkiro.domain.odometer.IngestRules
import com.namunkiro.domain.odometer.OdometerIngestor
import com.namunkiro.domain.odometer.OdometerLedger

/** 동기화 결과. */
sealed interface SyncResult {
    /** 시드를 차량 값에 맞춰 옮김(시작 계기판·보험 시작값을 shiftKm만큼 이동). */
    data class Shifted(val vehicleKm: Double, val shiftKm: Double) : SyncResult
    /** 차량 값이 시드 이력 합보다 작아 시드를 옮길 수 없음 → VC 주행거리를 targetKm 이상으로 올린 뒤 다시 동기화. */
    data class NeedVcRaise(val vehicleKm: Double, val targetKm: Double) : SyncResult
    data object NoDemo : SyncResult
}

/**
 * 데모 패널의 동작(DEV_PLAN 5절). 시드 적용·DemoClock 설정·VC 값과 동기화.
 *
 * 통합(I1) 시 주의:
 * - 데모 모드에서는 [DEMO_RULES](시연 여유분 100km)를 써야 VC로 한 번에 올린 +12km·+50km가 승인되고
 *   +580km는 보류된다. 앱 재시작 후에도 저장소의 demoPersonaId가 있으면 [restoreAfterRestart]를 불러
 *   DemoClock과 규칙을 다시 맞춘다(규칙·시계 오프셋은 저장하지 않으므로).
 */
class DemoController(
    private val repository: AppRepository,
    private val clock: DemoClock,
    private val ingestor: OdometerIngestor,
) {
    val isDemo: Boolean get() = repository.current.demoPersonaId != null

    /**
     * 페르소나 시드를 적용하고 DemoClock을 시드 시각으로 옮긴다.
     * @param vehicleOdometerKm 현재 차량(에뮬레이터) 계기판 값. 주면 곧바로 동기화를 시도한다.
     */
    fun applyPersona(id: PersonaId, vehicleOdometerKm: Double? = null): SyncResult {
        if (!isDemo) repository.savePersonalSnapshot()
        val seed = PersonaSeeds.get(id)
        clock.setNow(seed.demoNow)
        val data = seed.toAppData()
        commit(data, data.ledger.lastAcceptedKm!!)
        return if (vehicleOdometerKm != null) syncWithVehicle(vehicleOdometerKm) else SyncResult.Shifted(seed.lastOdometerKm, 0.0)
    }

    /**
     * "VC 값과 동기화": 시드의 마지막 계기판 값을 차량 현재 값과 같게 맞춘다.
     * VC는 기존보다 큰 값만 받으므로, 차량 값이 시드를 담기에 충분히 크면 시드(시작 계기판)를 옮기고,
     * 작으면 VC에 넣을 목표값을 돌려준다.
     */
    fun syncWithVehicle(vehicleKm: Double): SyncResult {
        val data = repository.current
        if (data.demoPersonaId == null) return SyncResult.NoDemo
        val contract = data.contract ?: return SyncResult.NoDemo
        val used = data.history.sumOf { it.km }
        val last = data.ledger.lastAcceptedKm ?: (contract.startOdometerKm + used)
        if (vehicleKm < used) return SyncResult.NeedVcRaise(vehicleKm, targetKm = last)
        val shift = vehicleKm - last
        val shifted = data.copy(
            contract = contract.copy(startOdometerKm = contract.startOdometerKm + shift),
            insurancePlan = data.insurancePlan?.let { it.copy(startOdometerKm = it.startOdometerKm + shift) },
        )
        commit(shifted, vehicleKm)
        return SyncResult.Shifted(vehicleKm, shift)
    }

    /** 앱 재시작 후: 데모 시드가 남아 있으면 DemoClock 날짜와 데모 규칙을 복원한다(시각은 실제 시각 유지). */
    fun restoreAfterRestart() {
        val id = repository.current.demoPersonaId?.let { runCatching { PersonaId.valueOf(it) }.getOrNull() } ?: return
        // 마지막 승인 시각(데모 시계 기준)에서 이어 간다. 날짜 이동(advanceDays)도 이 값에 반영돼 있다.
        val last = repository.current.ledger.lastAcceptedAt?.let { runCatching { java.time.LocalDateTime.parse(it) }.getOrNull() }
        val seedNow = PersonaSeeds.get(id).demoNow
        clock.setNow(if (last != null && !last.toLocalDate().isBefore(seedNow.toLocalDate())) last else seedNow)
        ingestor.rules = DEMO_RULES
    }

    /** 날짜를 n일 옮긴다(주 경계 시연 등). */
    fun advanceDays(days: Long) = clock.plusDays(days)

    /** 데모 종료: 시계 복귀, 데이터 삭제, 기본 규칙. */
    fun exitDemo() {
        if (!isDemo) return
        repository.saveDemoSnapshot()
        clock.reset()
        val personal = repository.restorePersonalSnapshot()
        repository.replaceAll(personal)
        ingestor.reset(personal.ledger, personal.history)
        ingestor.rules = IngestRules()
    }

    /** 진입 버튼은 이전 시연 상태를 이어 간다. 페르소나 선택만 시드를 새로 적용한다. */
    fun resumeDemo(): Boolean {
        if (isDemo) return true
        val saved = repository.restoreDemoSnapshot() ?: return false
        repository.savePersonalSnapshot()
        repository.replaceAll(saved)
        ingestor.reset(saved.ledger, saved.history)
        restoreAfterRestart()
        return true
    }

    /** 승인값을 [lastKm]으로 두고 '같은 세션'으로 표시해, 이어지는 VC 증가가 공백이 아닌 주행으로 잡히게 한다. */
    private fun commit(data: AppData, lastKm: Double) {
        val sessionId = ingestor.ledger.currentSessionId
        val ledger = OdometerLedger(
            lastAcceptedKm = lastKm,
            lastAcceptedAt = clock.now().toString(),
            lastAcceptedMonoMs = clock.monotonicMs(),
            lastAcceptedSessionId = sessionId,
            currentSessionId = sessionId,
            lastRawKm = lastKm,
        )
        val finalData = data.copy(ledger = ledger)
        repository.replaceAll(finalData)
        ingestor.reset(ledger, finalData.history)
        ingestor.rules = DEMO_RULES
    }

    companion object {
        const val DEMO_ALLOWANCE_KM = 100.0
        val DEMO_RULES = IngestRules(demoAllowanceKm = DEMO_ALLOWANCE_KM)
    }
}
