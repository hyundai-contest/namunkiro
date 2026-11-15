package com.namunkiro.odometer

import com.namunkiro.core.result.Source
import com.namunkiro.core.time.DemoClock
import com.namunkiro.data.AppRepository
import com.namunkiro.data.JsonFileStore
import com.namunkiro.demo.DemoController
import com.namunkiro.demo.PersonaId
import com.namunkiro.demo.PersonaSeeds
import com.namunkiro.demo.SyncResult
import com.namunkiro.domain.model.ExitQuotes
import com.namunkiro.domain.model.PlannedTrip
import com.namunkiro.domain.odometer.IngestStatus
import com.namunkiro.domain.odometer.OdometerIngestor
import com.namunkiro.vehicle.Capability
import com.namunkiro.vehicle.FakeVehicleGateway
import com.namunkiro.vehicle.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class VehicleDemoDataTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun personalDataSurvivesDemoSwitchAndProcessRestart() {
        val dir = tmp.newFolder("isolated-modes")
        val repo = AppRepository(JsonFileStore(dir))
        val personal = PersonaSeeds.kim().toAppData().copy(demoPersonaId = null)
        repo.replaceAll(personal)
        val clock = DemoClock()
        val demo = DemoController(repo, clock, OdometerIngestor(clock))
        demo.applyPersona(PersonaId.LEE_499)
        demo.applyPersona(PersonaId.PARK)
        val restarted = AppRepository(JsonFileStore(dir))
        val restartedDemo = DemoController(restarted, clock, OdometerIngestor(clock))
        restartedDemo.exitDemo()
        assertEquals(personal, restarted.current)
        assertTrue(restartedDemo.resumeDemo())
        assertEquals("PARK", restarted.current.demoPersonaId)
        restartedDemo.exitDemo()
        assertEquals(personal, restarted.current)
    }

    // ───────── 페르소나 시드 ─────────

    @Test fun kimSeedMatchesSpec() {
        val s = PersonaSeeds.kim()
        val today = s.demoNow.toLocalDate()
        assertEquals(LocalDate.of(2028, 3, 16), today)
        assertEquals(DayOfWeek.THURSDAY, today.dayOfWeek)
        assertEquals(501, ChronoUnit.DAYS.between(s.contract.startDate, today))
        assertEquals(21_382.0, s.totalKm, 1e-9)
        assertEquals(21_394.0, s.lastOdometerKm, 1e-9)
        val byDate = s.history.associateBy { it.date }
        val lastWeek = (6..12).sumOf { byDate[LocalDate.of(2028, 3, it)]!!.km }
        assertEquals(330.0, lastWeek, 1e-9)
        assertEquals(listOf(48.0, 52.0, 41.0, 45.0), (13..16).map { byDate[LocalDate.of(2028, 3, it)]!!.km })
        // 누적 잔고 = 60,000 × 501/1461 − 21,382 ≈ −807
        val balance = 60_000.0 * 501 / 1461 - s.totalKm
        assertEquals(-807.0, balance, 1.0)
        assertTrue(s.history.all { it.source == Source.SIMULATED })
        assertContiguous(s.history.map { it.date }, s.contract.startDate, today)
    }

    @Test fun kimSeedDrivesEnginesToSpecNumbers() {
        val s = PersonaSeeds.kim()
        val today = s.demoNow.toLocalDate()
        val budgetEngine = com.namunkiro.domain.budget.DefaultWeeklyBudgetEngine()
        val snap = budgetEngine.snapshot(s.contract, s.history, emptyList(), today)
        assertEquals(244, snap.thisWeek.budgetKm)
        assertEquals(-807.0, snap.cumulativeBalanceKm, 1.0)
        val trip = PlannedTrip("t", LocalDate.of(2028, 3, 18), 420, "강릉")
        val f = com.namunkiro.domain.forecast.DefaultForecastEngine().forecast(s.contract, s.history, listOf(trip), emptyList(), today)
        assertEquals(46.0, f.weekdayAvgKm, 1e-9)
        assertEquals(32.0, f.weekendAvgKm, 1e-9)
        assertEquals(684.0, f.thisWeekForecastKm, 1e-9)
        val eight = budgetEngine.splitOptions(s.contract, (f.thisWeekForecastKm - snap.thisWeek.budgetKm).toInt()).first { it.weeks == 8 }
        assertEquals(232, eight.resultingWeeklyBudgetKm)
    }

    @Test fun leeSeedsMatchSpec() {
        val a = PersonaSeeds.lee90()
        assertEquals(90, ChronoUnit.DAYS.between(a.contract.startDate, a.demoNow.toLocalDate()))
        assertEquals(6_600.0, a.totalKm, 1e-9)
        assertEquals(2_052.0, a.history.takeLast(28).sumOf { it.km }, 1e-9)   // 주 513km
        val b = PersonaSeeds.lee499()
        assertEquals(LocalDate.of(2028, 3, 14), b.demoNow.toLocalDate())
        assertEquals(499, ChronoUnit.DAYS.between(b.contract.startDate, b.demoNow.toLocalDate()))
        assertEquals(31_228.0, b.totalKm, 1e-9)
        assertEquals(31_240.0, b.lastOdometerKm, 1e-9)
        assertEquals(a.history, b.history.take(90))
        assertEquals(18_000_000L, b.contract.buyoutPrice)
        assertEquals(17_500_000L, b.exitQuotes.expectedMarketPrice)
        assertEquals(3_000_000L, b.exitQuotes.transferCost)
        // 24개월(2028-11-01) 경계에서 35% → 30%
        val tiers = b.contract.terminationTiers!!
        assertEquals(0.35, tiers.first { it.untilMonth == 24 }.rate, 0.0)
        assertEquals(0.30, tiers.first { it.untilMonth == 36 }.rate, 0.0)
        assertEquals(LocalDate.of(2028, 11, 1), b.contract.startDate.plusMonths(24))
    }

    @Test fun parkSeedMatchesSpec() {
        val s = PersonaSeeds.park()
        val plan = s.insurancePlan!!
        assertEquals(listOf(10_000, 12_000), plan.tiersKm)
        assertEquals(365, ChronoUnit.DAYS.between(plan.periodStart, plan.periodEnd.plusDays(1)))
        assertEquals(91, ChronoUnit.DAYS.between(s.demoNow.toLocalDate(), plan.periodEnd.plusDays(1)))  // 13주(종료일 포함)
        assertEquals(8_820.0, s.totalKm, 1e-9)
        assertEquals(245.0, (12_000 - s.totalKm) / 13, 1.0)
    }

    private fun assertContiguous(dates: List<LocalDate>, from: LocalDate, to: LocalDate) {
        assertEquals(from, dates.first()); assertEquals(to, dates.last())
        assertEquals(ChronoUnit.DAYS.between(from, to) + 1, dates.size.toLong())
        assertEquals(dates.size, dates.toSet().size)
    }

    // ───────── 저장소 ─────────

    @Test fun repositoryRoundTripIncludesNewExitQuoteFields() {
        val dir = tmp.newFolder("store")
        val repo = AppRepository(JsonFileStore(dir))
        val quotes = ExitQuotes(
            expectedMarketPrice = 17_500_000, transferCost = 3_000_000, damageEstimate = 500_000,
            transferDepositSettlement = -200_000, quoteValidUntil = LocalDate.of(2028, 4, 1),
        )
        repo.replaceAll(PersonaSeeds.lee499().toAppData())
        repo.saveExitQuotes(quotes)
        repo.upsertPlannedTrip(PlannedTrip("t1", LocalDate.of(2028, 3, 18), 420, "강릉"))
        repo.markQuestionAnswered("DEDUCT_KM")
        val reloaded = AppRepository(JsonFileStore(dir)).current
        assertEquals(quotes, reloaded.exitQuotes)
        assertEquals(repo.current, reloaded)
        assertEquals("LEE_499", reloaded.demoPersonaId)
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    // ───────── 데모 컨트롤러 + 가짜 게이트웨이 ─────────

    @Test fun demoApplyAndSyncThenVcStepFlowsThroughIngestor() {
        val clock = DemoClock()
        val repo = AppRepository(JsonFileStore(tmp.newFolder("demo")))
        val ing = OdometerIngestor(clock).also { it.startSession() }
        val demo = DemoController(repo, clock, ing)
        val gw = FakeVehicleGateway(clock, initialOdometerKm = 30_000.0)
        gw.start(); gw.start()
        assertEquals(2, gw.startCount)
        assertEquals(Capability.SUPPORTED, gw.state.value.odometerCapability)
        assertEquals(Gear.PARK, gw.state.value.gear)

        val r = demo.applyPersona(PersonaId.KIM, gw.currentOdometerKm())
        assertTrue(r is SyncResult.Shifted)
        assertEquals(LocalDate.of(2028, 3, 16), clock.today())
        assertEquals(30_000.0, ing.ledger.lastAcceptedKm!!, 0.0)
        assertEquals(30_000.0 - 21_382.0, repo.current.contract!!.startOdometerKm, 1e-9)

        gw.addKm(12.0)
        val raw = gw.state.value.lastOdometer!!
        val res = ing.ingest(raw.km, raw.receivedMonoMs, raw.simulated)
        assertEquals(IngestStatus.ACCEPTED, res.status)
        assertEquals(57.0, ing.history.last().km, 1e-9)   // 오늘 45 + 12

        // 차량 값이 시드보다 작으면 VC를 올리라고 안내
        val small = demo.syncWithVehicle(100.0)
        assertTrue(small is SyncResult.NeedVcRaise)
    }
}
