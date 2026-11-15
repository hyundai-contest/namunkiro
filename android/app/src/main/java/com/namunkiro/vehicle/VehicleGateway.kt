package com.namunkiro.vehicle

import kotlinx.coroutines.flow.StateFlow

enum class Gear { PARK, DRIVE, REVERSE, NEUTRAL, OTHER, UNKNOWN }

enum class Capability { UNKNOWN, SUPPORTED, NOT_SUPPORTED, ERROR }

/** 게이트웨이가 받은 원시 주행거리. receivedMonoMs는 AppClock.monotonicMs 기준. */
data class OdometerRaw(val km: Double?, val receivedMonoMs: Long, val simulated: Boolean)

data class VehicleState(
    val odometerCapability: Capability = Capability.UNKNOWN,
    val gearCapability: Capability = Capability.UNKNOWN,
    val lastOdometer: OdometerRaw? = null,
    val gear: Gear = Gear.UNKNOWN,
    /** 주행 중 화면 제한. null = 확인 불가(잠금으로 취급). */
    val uxRestricted: Boolean? = null,
    val error: String? = null,
)

/**
 * 차량 신호의 유일한 입구. 구현: PleosVehicleGateway(실제), FakeVehicleGateway(데모·테스트).
 * - start(): 연결·capability 확인·구독. 포그라운드 진입마다 호출해도 중복 구독하지 않는다.
 * - refresh(): 현재 주행거리 재조회(세션 시작·[다시 조회]).
 */
interface VehicleGateway {
    val state: StateFlow<VehicleState>
    fun start()
    fun refresh()
    fun stop()
}
