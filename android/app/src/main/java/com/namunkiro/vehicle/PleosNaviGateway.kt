package com.namunkiro.vehicle

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.namunkiro.core.time.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * NaviHelper 3.0.5 구현(docs/sdk-src/navi 소스로 시그니처 확인).
 * - initialize(onConnected, onFailed) → addListener → getDestinationInfo·getRouteStateInfo 조회.
 * - 단위: RouteStartInfo.distance·DestinationInfo.remainDistance·DrivingState.distance = m, duration·remainTime = 초.
 * - 이벤트는 바인더 스레드에서 온다 → MutableStateFlow.update로만 반영.
 * - 순정 내비가 없거나 연결 실패면 capability=ERROR/NOT_SUPPORTED. 앱은 수동 예정 이동으로 계속 동작한다.
 * - 권한: pleos.car.permission.NAVI_ROUTE(Normal).
 */
@SuppressLint("MissingPermission") // NAVI_ROUTE는 Manifest에 선언(Normal 보호 수준)
class PleosNaviGateway(
    context: Context,
    private val clock: AppClock,
) : NaviGateway {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(NaviState())
    override val state: StateFlow<NaviState> = _state.asStateFlow()

    private val lock = Any()
    private var navi: NaviHelper? = null
    private var connected = false
    private var lastDrivingLogMs = 0L

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            Log.i(TAG, "onRouteStarted $routeStartInfo")
            setDestination(
                name = routeStartInfo.name.ifBlank { null },
                lat = null, lon = null,
                remainingM = routeStartInfo.distance, remainingSec = routeStartInfo.duration,
                keepCoordinates = true,
            )
            // 좌표·주소는 목적지 조회로 보충
            runCatching { navi?.getDestinationInfo() }.onFailure { onError("getDestinationInfo", it) }
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            Log.i(TAG, "onDestinationInfo $destinationInfo")
            val d = destinationInfo
            // 목적지가 없을 때 빈 이름·0m로 오는 경우를 '목적지 없음'으로 본다
            if (d.name.isBlank() && d.remainDistance <= 0) {
                _state.update { it.copy(capability = Capability.SUPPORTED, destination = null) }
                return
            }
            setDestination(d.name.ifBlank { null }, d.latitude, d.longitude, d.remainDistance, d.remainTime, keepCoordinates = false)
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            val dst = drivingInfo.destination
            val now = clock.monotonicMs()
            if (now - lastDrivingLogMs > 10_000) { lastDrivingLogMs = now; Log.i(TAG, "onDrivingInfo dest=$dst") }
            val cur = _state.value.destination
            // 같은 목적지의 남은 거리·시간만 갱신(0.5km 이상 바뀔 때만 상태를 흔든다)
            val km = dst.distance / 1000.0
            // 실측(11-15): 안내 종료 직전 distance=0·duration=0인 DrivingInfo가 한 번 온다 → 무시
            if (dst.distance <= 0) return
            if (cur == null) {
                setDestination(dst.name.ifBlank { null }, null, null, dst.distance, dst.duration, keepCoordinates = true)
            } else if (cur.remainingKm == null || kotlin.math.abs(cur.remainingKm - km) >= 0.5) {
                _state.update { s -> s.copy(destination = s.destination?.copy(remainingKm = km, remainingMinutes = dst.duration / 60)) }
            }
        }

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            Log.i(TAG, "onRouteStateInfo $routeStateInfo")
            // 실측(11-15): 안내 중에도 isRouting=false로 온다 → isRouting은 쓰지 않고 naviMode로만 판단
            val m = routeStateInfo.naviMode
            if (m == NaviMode.SAFE_GUIDING || m == NaviMode.NOT_WORKING) _state.update { it.copy(destination = null) }
        }

        override fun onNavigationStatus(status: NavigationStatus) {
            Log.i(TAG, "onNavigationStatus $status")
            if (status == NavigationStatus.FREE_DRIVE || status == NavigationStatus.INACTIVE) _state.update { it.copy(destination = null) }
        }

        override fun onDestinationChanged() {
            Log.i(TAG, "onDestinationChanged")
            runCatching { navi?.getDestinationInfo() }.onFailure { onError("getDestinationInfo", it) }
        }

        override fun onRouteCancelled() { Log.i(TAG, "onRouteCancelled"); _state.update { it.copy(destination = null) } }
        override fun onRoutingCompleted() { Log.i(TAG, "onRoutingCompleted"); _state.update { it.copy(destination = null) } }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            Log.i(TAG, "onDestinationArrived $destinationArrivedInfo"); _state.update { it.copy(destination = null) }
        }
        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "onError $errorCode"); _state.update { it.copy(error = "navi:$errorCode") }
        }
    }

    override fun start() {
        val n: NaviHelper
        synchronized(lock) {
            if (navi != null) { if (connected) query(); return }
            n = try { NaviHelper(appContext) } catch (t: Throwable) {
                onError("create", t); _state.update { it.copy(capability = Capability.NOT_SUPPORTED) }; return
            }
            navi = n
        }
        try {
            n.addListener(listener)
            n.initialize(
                onConnected = {
                    Log.i(TAG, "connected")
                    synchronized(lock) { connected = true }
                    _state.update { it.copy(capability = Capability.SUPPORTED, error = null) }
                    query()
                },
                onFailed = { t ->
                    onError("initialize", t)
                    synchronized(lock) { connected = false; navi = null }
                    runCatching { n.removeListener(listener) }
                    _state.update { it.copy(capability = Capability.NOT_SUPPORTED, destination = null) }
                },
            )
        } catch (t: Throwable) {
            onError("initialize", t)
            synchronized(lock) { navi = null }
            _state.update { it.copy(capability = Capability.ERROR) }
        }
    }

    override fun refresh() { if (synchronized(lock) { connected }) query() }

    override fun stop() {
        val n = synchronized(lock) { navi.also { navi = null; connected = false } } ?: return
        runCatching { n.removeListener(listener) }
        runCatching { n.release() }
    }

    private fun query() {
        val n = synchronized(lock) { navi } ?: return
        runCatching { n.getDestinationInfo() }.onFailure { onError("getDestinationInfo", it) }
        runCatching { n.getRouteStateInfo() }.onFailure { onError("getRouteStateInfo", it) }
    }

    private fun setDestination(name: String?, lat: Double?, lon: Double?, remainingM: Int, remainingSec: Int, keepCoordinates: Boolean) {
        _state.update { s ->
            val prev = s.destination?.takeIf { keepCoordinates && (name == null || it.name == name) }
            s.copy(
                capability = Capability.SUPPORTED,
                destination = NaviDestination(
                    name = name ?: prev?.name,
                    latitude = lat ?: prev?.latitude,
                    longitude = lon ?: prev?.longitude,
                    remainingKm = remainingM.takeIf { it > 0 }?.let { it / 1000.0 } ?: prev?.remainingKm,
                    remainingMinutes = remainingSec.takeIf { it > 0 }?.let { it / 60 } ?: prev?.remainingMinutes,
                    receivedMonoMs = clock.monotonicMs(),
                ),
            )
        }
    }

    private fun onError(where: String, t: Throwable) {
        Log.w(TAG, "$where failed: ${t.message}", t)
        _state.update { it.copy(error = "$where: ${t.message ?: t.javaClass.simpleName}") }
    }

    private companion object { const val TAG = "NaviGw" }
}

/** 데모·테스트용. 목적지를 직접 넣는다([모의]). */
class FakeNaviGateway(private val clock: AppClock) : NaviGateway {
    private val _state = MutableStateFlow(NaviState())
    override val state: StateFlow<NaviState> = _state.asStateFlow()
    var started = false
        private set

    override fun start() { started = true; _state.update { it.copy(capability = Capability.SUPPORTED) } }
    override fun refresh() {}
    override fun stop() { started = false }

    fun setDestination(name: String, oneWayKm: Double, minutes: Int? = null, lat: Double? = null, lon: Double? = null) {
        _state.update {
            it.copy(capability = Capability.SUPPORTED,
                destination = NaviDestination(name, lat, lon, oneWayKm, minutes, clock.monotonicMs()))
        }
    }

    fun clearDestination() { _state.update { it.copy(destination = null) } }
}
