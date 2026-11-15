package com.namunkiro.core.time

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 시연용 시계(DEV_PLAN 1절 '시간 경과'). 실제 시각에 오프셋을 더해 날짜를 옮긴다.
 * - 옮긴 뒤에도 시간은 실제 속도로 흐른다(주행 중 갱신이 자연스럽게 보이도록).
 * - [monotonicMs]는 오프셋과 무관한 실제 경과만 센다(이상값 검사용).
 */
class DemoClock(
    private val base: AppClock = SystemAppClock,
) : AppClock {
    @Volatile
    private var offset: Duration = Duration.ZERO

    /** 날짜를 옮겼는지 여부(화면의 [모의] 표시용). */
    val isShifted: Boolean get() = !offset.isZero

    override fun now(): LocalDateTime = base.now().plus(offset)
    override fun monotonicMs(): Long = base.monotonicMs()

    /** 지금이 [target]이 되도록 오프셋을 맞춘다. */
    fun setNow(target: LocalDateTime) {
        offset = Duration.between(base.now(), target)
    }

    /** 날짜만 옮기고 시각은 실제 시각을 유지한다. */
    fun setDate(date: LocalDate) {
        setNow(LocalDateTime.of(date, base.now().toLocalTime()))
    }

    fun setDate(date: LocalDate, time: LocalTime) = setNow(LocalDateTime.of(date, time))

    fun plusDays(days: Long) {
        offset = offset.plusDays(days)
    }

    fun reset() {
        offset = Duration.ZERO
    }
}
