package com.namunkiro.trip

import com.namunkiro.domain.model.TripSegment
import com.namunkiro.domain.trip.DefaultTripPatternEngine
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class TripPatternEngineTest {
    private val engine = DefaultTripPatternEngine()
    private val firstMonday = LocalDate.of(2028, 2, 14)
    private val today = LocalDate.of(2028, 3, 16)   // 목요일
    private var seq = 0
    private fun trip(date: LocalDate, h: Int, m: Int, km: Double) =
        TripSegment("t${seq++}", LocalDateTime.of(date, java.time.LocalTime.of(h, m)), LocalDateTime.of(date, java.time.LocalTime.of(h, m)).plusMinutes(40), km)

    /** 김 대리: 4주간 평일 08:10 출근 23km, 18:40 퇴근 23km(시각·거리 약간 흔들림) + 가끔 주말 마트. */
    private fun kimCommute(): List<TripSegment> {
        val out = mutableListOf<TripSegment>()
        for (w in 0 until 5) for (d in 0 until 5) {
            val date = firstMonday.plusWeeks(w.toLong()).plusDays(d.toLong())
            if (date.isAfter(today)) continue
            out += trip(date, 8, 10 + (w * 7) % 30, 22.0 + w % 3)
            out += trip(date, 18, 40 - (d * 9) % 40, 23.0 + d % 2)
        }
        out += trip(firstMonday.plusDays(5), 14, 0, 30.0)
        out += trip(firstMonday.plusWeeks(1).plusDays(5), 15, 30, 34.0)
        out += trip(firstMonday.plusWeeks(2).plusDays(6), 11, 0, 12.0)
        return out
    }

    @Test fun weekdayCommuteBecomesRoutine() {
        val p = engine.analyze(kimCommute(), today)
        val monday = p.routines.filter { it.dayOfWeek == DayOfWeek.MONDAY }
        assertEquals(2, monday.size)
        assertEquals(8, monday.first().startHour)
        assertTrue(monday.all { it.occurrences >= 3 })
        assertTrue(p.routines.none { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY })
        val share = p.routineShare!!
        assertTrue("루틴 비중 $share", share > 0.85 && share < 1.0)
        assertEquals(46.0, p.usualKmByDay[DayOfWeek.MONDAY]!!, 3.0)
        assertEquals(32.0, p.usualKmByDay[DayOfWeek.SATURDAY]!!, 0.01)   // 토요일 2일 관측
        assertNull(p.usualKmByDay[DayOfWeek.SUNDAY])                       // 1일뿐 → 제외
    }

    @Test fun gangneungSaturdayIsLongTrip() {
        val p = engine.analyze(kimCommute(), today)
        val saturday = LocalDate.of(2028, 3, 18)
        assertEquals(true, engine.isLongTrip(p, saturday, 420.0))
        assertEquals(false, engine.isLongTrip(p, saturday, 40.0))
        assertNull(engine.isLongTrip(p, LocalDate.of(2028, 3, 19), 420.0))  // 일요일 평소 없음
    }

    @Test fun twoWeeksIsNotRoutine() {
        val trips = (0 until 2).map { trip(firstMonday.plusWeeks(it.toLong()), 8, 0, 20.0) } +
            (0 until 3).map { trip(firstMonday.plusDays(1).plusWeeks(it.toLong()), 9, 0, 10.0 + it * 20) }
        val p = engine.analyze(trips, today)
        assertTrue(p.routines.isEmpty())
        assertEquals(0.0, p.routineShare!!, 1e-9)
    }

    @Test fun shareNullUnderFiveTrips() {
        val p = engine.analyze((0 until 4).map { trip(firstMonday.plusWeeks(it.toLong()), 8, 0, 20.0) }, today)
        assertNull(p.routineShare)
        assertEquals(1, p.routines.size)
    }

    @Test fun plusHundredRuleWithoutDoubling() {
        val trips = (0 until 3).map { trip(firstMonday.plusWeeks(it.toLong()), 8, 0, 150.0) }
        val p = engine.analyze(trips, today)
        assertEquals(true, engine.isLongTrip(p, LocalDate.of(2028, 3, 20), 250.0))
        assertEquals(false, engine.isLongTrip(p, LocalDate.of(2028, 3, 20), 249.0))
    }
}
