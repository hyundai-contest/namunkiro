package com.namunkiro.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import com.namunkiro.core.time.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pleos Vehicle SDK(Odometer·Gear) + AAOS CarUxRestrictionsManager 구현.
 * - 콜백은 SDK 백그라운드 스레드에서 온다 → MutableStateFlow.update로만 반영.
 * - VehicleException은 난독화돼 errorCode를 못 쓴다 → message 문자열만 state.error에 남긴다.
 * - start()는 여러 번 불러도 리스너를 한 번만 등록하고, 이미 시작됐으면 refresh()만 한다.
 */
@Suppress("DEPRECATION") // Gear API는 GEAR_SELECTION 속성 권장으로 deprecated(WARNING)지만 DEV_PLAN 결정상 SDK 우선
class PleosVehicleGateway(
    context: Context,
    private val clock: AppClock,
) : VehicleGateway {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    private val lock = Any()
    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var odometerRegistered = false
    private var gearRegistered = false

    private val distanceListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) = onOdometer(value)
        override fun onFailed(e: Exception) = onError("odometer.listener", e)
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            _state.update { it.copy(gear = gear.toGear(), gearCapability = Capability.SUPPORTED) }
        }
        override fun onFailed(e: Exception) = onError("gear.listener", e)
    }

    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r -> onUx(r) }

    override fun start() {
        val v: Vehicle
        synchronized(lock) {
            if (vehicle != null) {
                refreshInternal(vehicle!!)
                return
            }
            v = try {
                Vehicle(appContext).also { it.initialize() }
            } catch (t: Throwable) {
                onError("vehicle.initialize", t)
                _state.update { it.copy(odometerCapability = Capability.ERROR, gearCapability = Capability.ERROR) }
                null
            } ?: run { startUx(); return }
            vehicle = v
        }
        startOdometer(v)
        startGear(v)
        startUx()
    }

    override fun refresh() {
        val v = synchronized(lock) { vehicle } ?: return start()
        refreshInternal(v)
    }

    override fun stop() {
        synchronized(lock) {
            val v = vehicle
            if (v != null) {
                runCatching { if (odometerRegistered) v.getOdometer().unregisterDistanceDriven(distanceListener) }
                runCatching { if (gearRegistered) v.getGear().unregisterGearSelection(gearListener) }
                runCatching { v.release() }
            }
            odometerRegistered = false
            gearRegistered = false
            vehicle = null
            runCatching { uxManager?.unregisterListener() }
            runCatching { car?.disconnect() }
            uxManager = null
            car = null
        }
    }

    // ───────── Odometer ─────────

    private fun startOdometer(v: Vehicle) {
        val odo = try { v.getOdometer() } catch (t: Throwable) { onError("odometer.get", t); return }
        try {
            odo.checkCurrentDistanceDrivenCapabilities(
                onComplete = { config ->
                    val readable = config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE
                    _state.update {
                        it.copy(odometerCapability = if (readable) Capability.SUPPORTED else Capability.NOT_SUPPORTED)
                    }
                    if (readable) {
                        fetchOdometer(v)
                        registerOdometer(v)
                    }
                },
                onFailed = { e ->
                    onError("odometer.capability", e)
                    _state.update { it.copy(odometerCapability = Capability.ERROR) }
                },
            )
        } catch (t: Throwable) {
            onError("odometer.capability", t)
            _state.update { it.copy(odometerCapability = Capability.ERROR) }
        }
    }

    private fun registerOdometer(v: Vehicle) {
        synchronized(lock) {
            if (odometerRegistered || vehicle !== v) return
            try {
                v.getOdometer().registerDistanceDriven(distanceListener)
                odometerRegistered = true
            } catch (t: Throwable) {
                onError("odometer.register", t)
            }
        }
    }

    private fun fetchOdometer(v: Vehicle) {
        try {
            v.getOdometer().getCurrentDistanceDriven(
                onComplete = { value -> onOdometer(value) },
                onFailed = { e -> onError("odometer.current", e) },
            )
        } catch (t: Throwable) {
            onError("odometer.current", t)
        }
    }

    private fun onOdometer(value: Float?) {
        val raw = OdometerRaw(km = value?.toDouble(), receivedMonoMs = clock.monotonicMs(), simulated = false)
        Log.d(TAG, "odometer=$value")
        _state.update { it.copy(lastOdometer = raw, odometerCapability = Capability.SUPPORTED) }
    }

    // ───────── Gear ─────────

    private fun startGear(v: Vehicle) {
        fetchGear(v, register = true)
    }

    private fun fetchGear(v: Vehicle, register: Boolean) {
        try {
            v.getGear().getCurrentGearSelectionState(
                onComplete = { g ->
                    _state.update { it.copy(gear = g.toGear(), gearCapability = Capability.SUPPORTED) }
                },
                onFailed = { e ->
                    onError("gear.current", e)
                    if (_state.value.gearCapability != Capability.SUPPORTED) {
                        _state.update { it.copy(gearCapability = Capability.ERROR) }
                    }
                },
            )
        } catch (t: Throwable) {
            onError("gear.current", t)
        }
        if (register) {
            synchronized(lock) {
                if (gearRegistered || vehicle !== v) return
                try {
                    v.getGear().registerGearSelection(gearListener)
                    gearRegistered = true
                } catch (t: Throwable) {
                    onError("gear.register", t)
                }
            }
        }
    }

    private fun refreshInternal(v: Vehicle) {
        if (_state.value.odometerCapability == Capability.SUPPORTED) {
            fetchOdometer(v)
            registerOdometer(v)
        } else {
            startOdometer(v)
        }
        fetchGear(v, register = true)
        synchronized(lock) { uxManager }?.let { m -> runCatching { onUx(m.currentCarUxRestrictions) } }
    }

    // ───────── UX 제한 ─────────

    private fun startUx() {
        synchronized(lock) {
            if (uxManager != null) return
            try {
                val c = Car.createCar(appContext) ?: throw IllegalStateException("Car.createCar returned null")
                val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                m.registerListener(uxListener)
                car = c
                uxManager = m
                onUx(m.currentCarUxRestrictions)
            } catch (t: Throwable) {
                onError("uxr", t)
                _state.update { it.copy(uxRestricted = null) }
            }
        }
    }

    private fun onUx(r: CarUxRestrictions?) {
        val restricted = r?.isRequiresDistractionOptimization
        Log.d(TAG, "uxRestricted=$restricted active=${r?.activeRestrictions}")
        _state.update { it.copy(uxRestricted = restricted) }
    }

    private fun onError(where: String, t: Throwable) {
        val msg = "$where: ${t.message ?: t.javaClass.simpleName}"
        Log.w(TAG, msg)
        _state.update { it.copy(error = msg) }
    }

    private companion object {
        const val TAG = "PleosVehicleGateway"
    }
}

internal fun VehicleGear?.toGear(): Gear = when (this) {
    null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
    VehicleGear.GEAR_PARK -> Gear.PARK
    VehicleGear.GEAR_DRIVE -> Gear.DRIVE
    VehicleGear.GEAR_REVERSE -> Gear.REVERSE
    VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
    else -> Gear.OTHER // GEAR_1~9(수동 단수)
}
