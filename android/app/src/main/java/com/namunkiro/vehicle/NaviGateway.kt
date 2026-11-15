package com.namunkiro.vehicle

import kotlinx.coroutines.flow.StateFlow

/** 순정 내비에 설정된 목적지(안내 중이거나 설정된 경로). 값은 NaviHelper가 주는 만큼만 채운다. */
data class NaviDestination(
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
    /** 현재 위치에서 목적지까지 남은 거리(km). 안내 중일 때만 들어올 수 있다. */
    val remainingKm: Double?,
    val remainingMinutes: Int?,
    val receivedMonoMs: Long,
)

data class NaviState(
    val capability: Capability = Capability.UNKNOWN,
    val destination: NaviDestination? = null,
    val error: String? = null,
)

/**
 * 순정 내비 연동의 유일한 입구(조정자 소유 계약). 구현: PleosNaviGateway(NaviHelper 3.0.5), FakeNaviGateway(데모·테스트).
 * 내비가 없거나 이벤트가 오지 않아도 앱은 수동 예정 이동으로 동작해야 한다.
 */
interface NaviGateway {
    val state: StateFlow<NaviState>
    fun start()
    fun refresh()
    fun stop()
}
