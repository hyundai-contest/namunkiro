package com.namunkiro.app

import com.namunkiro.core.result.Source
import com.namunkiro.domain.model.TripSegment
import com.namunkiro.vehicle.Gear
import java.time.LocalDateTime
import java.util.UUID

/**
 * 기어로 주행 단위를 나눈다(앱이 화면에 있는 동안만).
 * - 시작: 기어 P → D 또는 R (UNKNOWN·N에서 바로 D가 되면 언제 출발했는지 모르므로 시작하지 않는다).
 * - 끝: 주행 중 → P. 그 사이 '승인된' 계기판 증가분이 주행 거리(보류된 급증은 포함되지 않음).
 * - 앱이 백그라운드로 가거나 세션이 새로 시작되면 진행 중 주행은 버린다(공백 구간은 TripSegment가 아니라 DailyDistance.gapKm로만 남음).
 * - [minKm] 미만은 주차장 이동 등으로 보고 버린다.
 */
class TripSegmenter(private val minKm: Double = 0.1) {
    private data class Active(val start: LocalDateTime, val startKm: Double, val simulated: Boolean)

    private var lastGear: Gear = Gear.UNKNOWN
    private var active: Active? = null

    val inTrip: Boolean get() = active != null

    /**
     * 기어 변화 1건. [acceptedKm] = 그 시점의 마지막 승인 계기판(null이면 시작하지 않음).
     * 주행이 끝나면 TripSegment를 돌려준다.
     */
    fun onGear(gear: Gear, acceptedKm: Double?, now: LocalDateTime, simulated: Boolean): TripSegment? {
        val prev = lastGear
        lastGear = gear
        if (gear == prev) return null
        val moving = gear == Gear.DRIVE || gear == Gear.REVERSE
        if (prev == Gear.PARK && moving) {
            active = acceptedKm?.let { Active(now, it, simulated) }
            return null
        }
        if (gear == Gear.PARK) {
            val a = active ?: return null
            active = null
            val end = acceptedKm ?: return null
            val km = end - a.startKm
            if (km < minKm || !km.isFinite()) return null
            return TripSegment(
                id = UUID.randomUUID().toString(),
                start = a.start, end = now,
                km = Math.round(km * 10) / 10.0,
                source = if (a.simulated || simulated) Source.SIMULATED else Source.OBSERVED,
            )
        }
        // N·OTHER·UNKNOWN 중간 상태는 주행을 이어간다
        return null
    }

    /** 앱이 화면에서 사라짐·세션 재시작·게이트웨이 전환: 진행 중 주행을 버리고 기어 이력도 잊는다. */
    fun interrupt() {
        active = null
        lastGear = Gear.UNKNOWN
    }
}
