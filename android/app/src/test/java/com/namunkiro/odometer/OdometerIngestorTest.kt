package com.namunkiro.odometer

import com.namunkiro.core.result.Source
import com.namunkiro.core.time.AppClock
import com.namunkiro.domain.odometer.IngestRules
import com.namunkiro.domain.odometer.IngestStatus
import com.namunkiro.domain.odometer.OdometerIngestor
import com.namunkiro.domain.odometer.OdometerLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** 테스트용 시계: 벽시계와 단조 시간을 따로 움직인다. */
class TestClock(var dt: LocalDateTime = LocalDateTime.of(2028, 3, 16, 9, 0), var mono: Long = 0) : AppClock {
    override fun now() = dt
    override fun monotonicMs() = mono
    fun advanceMinutes(m: Long) { dt = dt.plusMinutes(m); mono += m * 60_000 }
}

class OdometerIngestorTest {
    private val clock = TestClock()

    private fun ingestor(rules: IngestRules = IngestRules()) = OdometerIngestor(clock, rules = rules).also { it.startSession() }

    @Test fun firstReadingBecomesBaseline() {
        val ing = ingestor()
        val r = ing.ingest(1000.0, clock.mono)
        assertEquals(IngestStatus.ACCEPTED, r.status)
        assertEquals(1000.0, r.acceptedKm!!, 0.0)
        assertEquals(0.0, r.deltaKm, 0.0)
        assertEquals(0, ing.history.size)
    }

    @Test fun normalIncreaseAccumulatesDaily() {
        val ing = ingestor()
        ing.ingest(1000.0, clock.mono)
        clock.advanceMinutes(30)
        val r = ing.ingest(1020.0, clock.mono)              // 30분 20km(40km/h)
        assertEquals(IngestStatus.ACCEPTED, r.status)
        assertEquals(20.0, r.deltaKm, 1e-9)
        clock.advanceMinutes(30)
        ing.ingest(1045.5, clock.mono)
        val day = ing.history.single()
        assertEquals(LocalDate.of(2028, 3, 16), day.date)
        assertEquals(45.5, day.km, 1e-9)
        assertEquals(0.0, day.gapKm, 0.0)
        assertEquals(Source.OBSERVED, day.source)
    }

    @Test fun reverseIsHeld() {
        val ing = ingestor()
        ing.ingest(1000.0, clock.mono)
        clock.advanceMinutes(5)
        val r = ing.ingest(990.0, clock.mono)
        assertEquals(IngestStatus.HELD_REVERSE, r.status)
        assertEquals(1000.0, r.acceptedKm!!, 0.0)
        assertEquals(990.0, r.heldRawKm!!, 0.0)
        assertEquals(1000.0, ing.ledger.lastAcceptedKm!!, 0.0)
    }

    @Test fun tinyReverseWithinEpsilonIsIgnored() {
        val ing = ingestor()
        ing.ingest(1000.0, clock.mono)
        clock.advanceMinutes(1)
        val r = ing.ingest(999.95, clock.mono)
        assertEquals(IngestStatus.ACCEPTED, r.status)
        assertEquals(0.0, r.deltaKm, 0.0)
        assertEquals(1000.0, ing.ledger.lastAcceptedKm!!, 0.0)
    }

    @Test fun spikeInSameSessionIsHeldUntilTimeAllows() {
        val ing = ingestor()
        ing.ingest(1000.0, clock.mono)
        clock.advanceMinutes(10)                              // 10분 상한 = 41.7km
        val r = ing.ingest(1580.0, clock.mono)
        assertEquals(IngestStatus.HELD_SPIKE, r.status)
        assertEquals(1580.0, ing.ledger.heldRawKm!!, 0.0)
        // 사용자 확인으로 승인하지 않는다: 같은 값을 다시 받아도 시간이 모자라면 계속 보류
        clock.advanceMinutes(10)
        assertEquals(IngestStatus.HELD_SPIKE, ing.ingest(1580.0, clock.mono).status)
        // 충분한 시간이 흐른 뒤 [다시 조회]로 규칙을 통과하면 승인되고 보류 해제
        clock.advanceMinutes(130)                             // 총 150분 → 625km 상한
        val ok = ing.ingest(1580.0, clock.mono)
        assertEquals(IngestStatus.ACCEPTED, ok.status)
        assertEquals(580.0, ok.deltaKm, 1e-9)
        assertNull(ing.ledger.heldRawKm)
    }

    @Test fun invalidValuesRejected() {
        val ing = ingestor()
        ing.ingest(1000.0, clock.mono)
        for (v in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0)) {
            val r = ing.ingest(v, clock.mono)
            assertEquals(IngestStatus.REJECTED_INVALID, r.status)
            assertEquals(1000.0, r.acceptedKm!!, 0.0)
        }
    }

    @Test fun gapBetweenSessionsIsRecoveredAndMarked() {
        val ing = ingestor()
        ing.ingest(1000.0, clock.mono)
        // 앱 종료 → 다음 날 아침(프로세스가 바뀌어 단조 시간은 처음부터)
        val saved = ing.ledger
        val savedHistory = ing.history
        clock.dt = clock.dt.plusDays(1); clock.mono = 5_000
        val next = OdometerIngestor(clock, saved, savedHistory)
        next.startSession()
        val r = next.ingest(1050.0, clock.mono)
        assertEquals(IngestStatus.ACCEPTED_GAP, r.status)
        assertEquals(50.0, r.deltaKm, 1e-9)
        assertEquals(50.0, next.ledger.lastGapKm!!, 1e-9)
        val day = next.history.single { it.date == LocalDate.of(2028, 3, 17) }
        assertEquals(50.0, day.km, 1e-9)
        assertEquals(50.0, day.gapKm, 1e-9)
        // 같은 세션의 다음 증가는 일반 주행
        clock.advanceMinutes(20)
        assertEquals(IngestStatus.ACCEPTED, next.ingest(1060.0, clock.mono).status)
        val day2 = next.history.single { it.date == LocalDate.of(2028, 3, 17) }
        assertEquals(60.0, day2.km, 1e-9)
        assertEquals(50.0, day2.gapKm, 1e-9)
    }

    @Test fun gapOverSpeedLimitIsHeld() {
        val ing = ingestor()
        ing.ingest(1000.0, clock.mono)
        clock.dt = clock.dt.plusHours(1)
        ing.startSession()
        assertEquals(IngestStatus.HELD_SPIKE, ing.ingest(1300.0, clock.mono).status)   // 1시간 300km
    }

    @Test fun demoAllowanceLetsVcStepsThroughButHoldsAnomaly() {
        val ing = ingestor(IngestRules(demoAllowanceKm = 100.0))
        ing.ingest(21394.0, clock.mono)
        clock.mono += 3_000                                   // 3초 뒤 VC +12
        assertEquals(IngestStatus.ACCEPTED, ing.ingest(21406.0, clock.mono).status)
        ing.startSession()                                    // 강제 종료 후 재실행, 30초 뒤 +50
        clock.dt = clock.dt.plusSeconds(30)
        val gap = ing.ingest(21456.0, clock.mono)
        assertEquals(IngestStatus.ACCEPTED_GAP, gap.status)
        assertEquals(50.0, gap.deltaKm, 1e-9)
        clock.mono += 5_000
        assertEquals(IngestStatus.HELD_SPIKE, ing.ingest(22036.0, clock.mono).status)  // +580
    }

    @Test fun defaultRulesHoldInstantVcStep() {
        val ing = ingestor()
        ing.ingest(21394.0, clock.mono)
        clock.mono += 3_000
        assertEquals(IngestStatus.HELD_SPIKE, ing.ingest(21406.0, clock.mono).status)
    }

    @Test fun simulatedReadingsMarkDaySimulated() {
        val ing = ingestor()
        ing.ingest(10.0, clock.mono, simulated = true)
        clock.advanceMinutes(10)
        ing.ingest(15.0, clock.mono, simulated = true)
        assertEquals(Source.SIMULATED, ing.history.single().source)
    }

    @Test fun resetReplacesState() {
        val ing = ingestor()
        ing.ingest(10.0, clock.mono)
        ing.reset(OdometerLedger(lastAcceptedKm = 500.0), emptyList())
        assertEquals(500.0, ing.ledger.lastAcceptedKm!!, 0.0)
        assertEquals(0, ing.history.size)
    }
}
