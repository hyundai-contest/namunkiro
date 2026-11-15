package com.namunkiro.app

import com.namunkiro.core.result.Source
import com.namunkiro.demo.PersonaId
import com.namunkiro.demo.PersonaSeeds
import com.namunkiro.vehicle.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TripSegmenterTest {
    private val t0 = LocalDateTime.of(2028, 3, 16, 8, 0)

    @Test
    fun parkDriveParkMakesOneSegmentWithAcceptedDelta() {
        val s = TripSegmenter()
        assertNull(s.onGear(Gear.PARK, 21_394.0, t0, false))
        assertNull(s.onGear(Gear.DRIVE, 21_394.0, t0.plusMinutes(1), false))
        val seg = s.onGear(Gear.PARK, 21_406.0, t0.plusMinutes(30), false)
        assertNotNull(seg)
        assertEquals(12.0, seg!!.km, 1e-9)
        assertEquals(t0.plusMinutes(1), seg.start)
        assertEquals(Source.OBSERVED, seg.source)
    }

    @Test
    fun neutralAndReverseKeepTripGoing() {
        val s = TripSegmenter()
        s.onGear(Gear.PARK, 100.0, t0, true)
        s.onGear(Gear.REVERSE, 100.0, t0, true)
        assertNull(s.onGear(Gear.NEUTRAL, 100.2, t0.plusMinutes(1), true))
        assertNull(s.onGear(Gear.DRIVE, 100.2, t0.plusMinutes(2), true))
        val seg = s.onGear(Gear.PARK, 105.0, t0.plusMinutes(10), true)!!
        assertEquals(5.0, seg.km, 1e-9)
        assertEquals(Source.SIMULATED, seg.source)
    }

    @Test
    fun noStartWithoutObservedParkFirst() {
        val s = TripSegmenter()
        // 앱을 켰을 때 이미 D: 출발 시점을 모르므로 주행으로 세지 않는다
        assertNull(s.onGear(Gear.DRIVE, 100.0, t0, false))
        assertNull(s.onGear(Gear.PARK, 150.0, t0.plusMinutes(40), false))
    }

    @Test
    fun interruptDropsActiveTrip() {
        val s = TripSegmenter()
        s.onGear(Gear.PARK, 100.0, t0, false)
        s.onGear(Gear.DRIVE, 100.0, t0, false)
        s.interrupt()                       // 앱이 화면에서 사라짐
        s.onGear(Gear.PARK, 160.0, t0.plusHours(1), false)
        assertNull(s.onGear(Gear.PARK, 160.0, t0.plusHours(1), false))
    }

    @Test
    fun tinyMovesAreIgnored() {
        val s = TripSegmenter()
        s.onGear(Gear.PARK, 100.0, t0, false)
        s.onGear(Gear.DRIVE, 100.0, t0, false)
        assertNull(s.onGear(Gear.PARK, 100.05, t0.plusMinutes(1), false))
    }

    @Test
    fun seedSegmentsNeverExceedDailyHistoryAndStopBeforeNow() {
        PersonaId.entries.forEach { id ->
            val seed = PersonaSeeds.get(id)
            assertTrue("$id has segments", seed.tripSegments.isNotEmpty())
            val byDay = seed.tripSegments.groupBy { it.start.toLocalDate() }
            byDay.forEach { (date, segs) ->
                val day = seed.history.first { it.date == date }.km
                assertTrue("$id $date ${segs.sumOf { it.km }} <= $day", segs.sumOf { it.km } <= day + 1e-9)
            }
            assertTrue(seed.tripSegments.all { !it.end.isAfter(seed.demoNow) && it.source == Source.SIMULATED })
        }
    }

    @Test
    fun kimSeedHasWeekdayCommuteRoutine() {
        val seed = PersonaSeeds.get(PersonaId.KIM)
        val pattern = com.namunkiro.domain.trip.DefaultTripPatternEngine().analyze(seed.tripSegments, seed.demoNow.toLocalDate())
        assertTrue("routines=${pattern.routines}", pattern.routines.any { it.startHour == 8 })
        assertTrue(pattern.routines.any { it.startHour == 18 })
    }
}
