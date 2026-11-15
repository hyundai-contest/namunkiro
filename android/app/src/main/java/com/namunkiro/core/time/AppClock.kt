package com.namunkiro.core.time

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 현재 시각의 유일한 출처. 모든 계산은 이 인터페이스로 "지금"을 얻는다.
 * 시연에서는 DemoClock으로 날짜를 옮긴다(DEV_PLAN 1절 '시간 경과').
 */
interface AppClock {
    fun now(): LocalDateTime
    fun today(): LocalDate = now().toLocalDate()
    /** 이상값 검사용 단조 시간(ms). 날짜 이동과 무관하게 실제 경과만 센다. */
    fun monotonicMs(): Long
    val zone: ZoneId get() = ZoneId.systemDefault()
}

object SystemAppClock : AppClock {
    override fun now(): LocalDateTime = LocalDateTime.now()
    override fun monotonicMs(): Long = System.nanoTime() / 1_000_000
}
