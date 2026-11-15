package com.namunkiro.vehicle

import com.namunkiro.core.time.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 데모·테스트용 가짜 게이트웨이. 모든 값은 simulated=true로 나간다([모의] 배지).
 * 값 주입 API로 VC 없이 화면·엔진을 돌린다.
 */
class FakeVehicleGateway(
    private val clock: AppClock,
    initialOdometerKm: Double? = 0.0,
    private val initialGear: Gear = Gear.PARK,
) : VehicleGateway {
    private val _state = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    private var odometerKm: Double? = initialOdometerKm
    var started = false
        private set
    /** 테스트에서 중복 구독 여부 확인용. */
    var startCount = 0
        private set

    @Synchronized
    override fun start() {
        startCount++
        if (!started) {
            started = true
            _state.update {
                it.copy(
                    odometerCapability = Capability.SUPPORTED,
                    gearCapability = Capability.SUPPORTED,
                    gear = if (it.gear == Gear.UNKNOWN) initialGear else it.gear,
                    uxRestricted = it.uxRestricted ?: false,
                )
            }
        }
        emitOdometer()
    }

    override fun refresh() {
        if (!started) return start()
        emitOdometer()
    }

    @Synchronized
    override fun stop() {
        started = false
    }

    // ───────── 값 주입 ─────────

    @Synchronized
    fun setOdometer(km: Double?) {
        odometerKm = km
        if (started) emitOdometer()
    }

    @Synchronized
    fun addKm(deltaKm: Double) = setOdometer((odometerKm ?: 0.0) + deltaKm)

    fun currentOdometerKm(): Double? = odometerKm

    fun setGear(gear: Gear) = _state.update { it.copy(gear = gear) }

    fun setUxRestricted(restricted: Boolean?) = _state.update { it.copy(uxRestricted = restricted) }

    fun setOdometerCapability(c: Capability) = _state.update { it.copy(odometerCapability = c) }

    fun setGearCapability(c: Capability) = _state.update { it.copy(gearCapability = c) }

    fun setError(message: String?) = _state.update { it.copy(error = message) }

    private fun emitOdometer() {
        if (_state.value.odometerCapability != Capability.SUPPORTED) return
        val raw = OdometerRaw(km = odometerKm, receivedMonoMs = clock.monotonicMs(), simulated = true)
        _state.update { it.copy(lastOdometer = raw) }
    }
}
