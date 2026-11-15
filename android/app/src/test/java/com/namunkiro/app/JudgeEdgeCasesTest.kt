package com.namunkiro.app

import com.namunkiro.core.time.AppClock
import com.namunkiro.data.AppRepository
import com.namunkiro.data.JsonFileStore
import com.namunkiro.domain.budget.DefaultWeeklyBudgetEngine
import com.namunkiro.domain.forecast.DefaultForecastEngine
import com.namunkiro.domain.exit.DefaultExitCalculator
import com.namunkiro.domain.insurance.DefaultInsuranceTierPlanner
import com.namunkiro.domain.model.*
import com.namunkiro.ui.components.number
import com.namunkiro.ui.state.*
import com.namunkiro.ui.v2.*
import com.namunkiro.vehicle.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.*
import java.util.Locale
import java.util.TimeZone

class JudgeEdgeCasesTest {
    @get:Rule val tmp = TemporaryFolder()
    private val today = LocalDate.of(2028, 3, 16)
    private val clock = object : AppClock {
        override fun now(): LocalDateTime = today.atTime(21, 45)
        override fun monotonicMs() = 0L
    }
    private val contract = Contract(startDate = today.minusDays(100), endDate = today.plusDays(100), totalKm = 10_000, unitPricePerKm = 100, startOdometerKm = 0.0)

    @Test fun nullSignalsBecomeTimedOutWithoutDrivingLock() {
        val v = VehicleState()
        assertEquals(VehicleConnectionStatus.WAITING, AppPolicies.connection(v, 9_999))
        for (elapsed in listOf(10_000L, 60_000L, 604_800_000L)) {
            val status = AppPolicies.connection(v, elapsed)
            assertEquals(VehicleConnectionStatus.TIMED_OUT, status)
            assertEquals(Gate.NORMAL, AppPolicies.gate(v, false))
            assertTrue(vehicleConnectionLabel(status).contains("다시 시도"))
        }
    }

    @Test fun unsupportedFakeGatewayKeepsDisplayableStateAfterRefresh() {
        val gateway = FakeVehicleGateway(clock, initialOdometerKm = null, initialGear = Gear.UNKNOWN)
        gateway.start()
        gateway.setUxRestricted(null)
        gateway.setOdometerCapability(Capability.NOT_SUPPORTED)
        gateway.setGearCapability(Capability.NOT_SUPPORTED)
        repeat(3) { gateway.refresh() }
        val v = gateway.state.value
        assertEquals(VehicleConnectionStatus.NOT_SUPPORTED, AppPolicies.connection(v, 100_000))
        assertEquals(Gate.NORMAL, AppPolicies.gate(v, false))
        assertTrue(vehicleConnectionLabel(AppPolicies.connection(v, 100_000)).contains("지원하지 않는"))
        assertNull(v.lastOdometer?.km)
    }

    @Test fun serviceErrorIsShownAndRetryCanRecover() {
        val gateway = FakeVehicleGateway(clock)
        gateway.start()
        gateway.setError("service disconnected")
        assertEquals(VehicleConnectionStatus.ERROR, AppPolicies.connection(gateway.state.value, 0))
        assertTrue(vehicleConnectionLabel(VehicleConnectionStatus.ERROR).contains("다시 시도"))
        gateway.setError(null)
        gateway.refresh()
        assertEquals(VehicleConnectionStatus.READY, AppPolicies.connection(gateway.state.value, 0))
    }

    @Test fun explicitDrivingSignalAlwaysWinsEvenWithUnsupportedCapability() {
        val v = VehicleState(odometerCapability = Capability.NOT_SUPPORTED, uxRestricted = true)
        assertEquals(Gate.DRIVING_LOCK, AppPolicies.gate(v, true))
        assertEquals(Gate.DRIVING_LOCK, AppPolicies.gate(VehicleState(gear = Gear.DRIVE), false))
    }

    @Test fun parkAloneIsReadyAndAnomalyDoesNotBecomeDriving() {
        val v = VehicleState(gear = Gear.PARK, uxRestricted = null)
        assertEquals(VehicleConnectionStatus.READY, AppPolicies.connection(v, 100_000))
        assertEquals(Gate.NORMAL, AppPolicies.gate(v, false))
        assertEquals(Gate.ANOMALY_HOLD, AppPolicies.gate(v, true))
    }

    @Test fun onboardingDateBoundariesMatchSavePolicy() {
        for (date in listOf(null, today.minusDays(1), today, today.plusYears(20).plusDays(1))) assertFalse("$date", AppPolicies.validEndDate(date, today))
        assertTrue(AppPolicies.validEndDate(today.plusDays(1), today))
        assertTrue(AppPolicies.validEndDate(today.plusYears(20), today))
    }

    @Test fun onboardingNumericBoundariesRejectZeroNegativeAndExcess() {
        for (n in listOf(null, Int.MIN_VALUE, -1, 0, 1_000_001, Int.MAX_VALUE)) assertFalse("km=$n", AppPolicies.validTotalKm(n))
        for (n in listOf(null, Int.MIN_VALUE, -1, 0, 100_001, Int.MAX_VALUE)) assertFalse("price=$n", AppPolicies.validUnitPrice(n))
        assertTrue(AppPolicies.validTotalKm(1)); assertTrue(AppPolicies.validTotalKm(1_000_000))
        assertTrue(AppPolicies.validUnitPrice(1)); assertTrue(AppPolicies.validUnitPrice(100_000))
    }

    @Test fun invalidContractEditsNeverReplacePersistedContract() {
        val dir = tmp.newFolder()
        val repo = AppRepository(JsonFileStore(dir)); repo.saveContract(contract)
        val settings = AppSettings(repo, clock)
        val invalid = listOf(ContractField.END_DATE to today.toString(), ContractField.END_DATE to "not-a-date", ContractField.TOTAL_KM to "0", ContractField.TOTAL_KM to "1000001", ContractField.UNIT_PRICE to "-1", ContractField.UNIT_PRICE to "100001", ContractField.DEDUCT_KM to "-1", ContractField.BUYOUT_PRICE to "999999999999999999999", ContractField.TYPE to "invalid")
        invalid.forEach { (field, value) -> assertFalse(settings.updateContractField(field, value)) }
        assertEquals(contract, AppRepository(JsonFileStore(dir)).current.contract)
    }

    @Test fun everySupportedContractEditSurvivesRepositoryRestart() {
        val dir = tmp.newFolder(); val repo = AppRepository(JsonFileStore(dir)); repo.saveContract(contract)
        val settings = AppSettings(repo, clock)
        listOf(ContractField.END_DATE to today.plusDays(200).toString(), ContractField.TOTAL_KM to "20000", ContractField.UNIT_PRICE to "200", ContractField.DEDUCT_KM to "500", ContractField.BUYOUT_PRICE to "18000000", ContractField.BUYOUT_WAIVES_DISTANCE_FEE to "true", ContractField.UNDERUSE_REFUND to "false", ContractField.TYPE to "LONG_TERM_RENT").forEach { (field, value) -> assertTrue(settings.updateContractField(field, value)) }
        val saved = AppRepository(JsonFileStore(dir)).current.contract!!
        assertEquals(today.plusDays(200), saved.endDate); assertEquals(20_000, saved.totalKm)
        assertEquals(200, saved.unitPricePerKm); assertEquals(500, saved.deductKm)
        assertEquals(18_000_000L, saved.buyoutPrice); assertEquals(true, saved.buyoutWaivesDistanceFee)
        assertEquals(false, saved.underuseRefund); assertEquals(ContractType.LONG_TERM_RENT, saved.type)
        assertEquals(saved, repo.data.value.contract)
    }

    @Test fun editsRecomputeDistanceFeeAndInsuranceTargetsFromSavedValues() {
        val repo = AppRepository(JsonFileStore(tmp.newFolder())); repo.saveContract(contract)
        val settings = AppSettings(repo, clock)
        val history = (1L..100).map { DailyDistance(today.minusDays(it), 100.0) }
        val engine = DefaultWeeklyBudgetEngine()
        val before = engine.snapshot(repo.current.contract!!, history, emptyList(), today)
        assertTrue(CostModel.heroFee(before, contract) > 0)
        assertTrue(settings.updateContractField(ContractField.TOTAL_KM, "30000"))
        val after = engine.snapshot(repo.current.contract!!, history, emptyList(), today)
        assertEquals(0L, CostModel.heroFee(after, repo.current.contract!!))
        val plan = InsurancePlan(today.minusDays(100), today.plusDays(100), 0.0, listOf(12000))
        assertTrue(settings.setInsurancePlan(plan))
        val status = DefaultInsuranceTierPlanner().status(repo.data.value.insurancePlan!!, 10000.0, history, today)
        assertEquals(2000.0, status.tiers.single().remainingKm, .001)
        assertTrue(status.tiers.single().requiredWeeklyKm.isFinite())
        assertTrue(settings.setInsurancePlan(null)); assertNull(repo.current.insurancePlan)
    }

    @Test fun invalidInsurancePlanCannotEraseValidSavedPlan() {
        val dir = tmp.newFolder(); val repo = AppRepository(JsonFileStore(dir)); val settings = AppSettings(repo, clock)
        val plan = InsurancePlan(today, today.plusYears(1), 0.0, listOf(10000, 12000))
        assertTrue(settings.setInsurancePlan(plan))
        for (invalid in listOf(plan.copy(periodEnd = today.minusDays(1)), plan.copy(startOdometerKm = Double.NaN), plan.copy(startOdometerKm = -1.0), plan.copy(tiersKm = emptyList()), plan.copy(tiersKm = listOf(12000, 10000)), plan.copy(tiersKm = listOf(0)))) assertFalse(settings.setInsurancePlan(invalid))
        assertEquals(plan, AppRepository(JsonFileStore(dir)).current.insurancePlan)
        assertTrue(settings.setInsurancePlan(null)); assertNull(AppRepository(JsonFileStore(dir)).current.insurancePlan)
    }

    @Test fun expiredContractHasStableSettlementAndEndedHomeState() {
        val c = contract.copy(endDate = today.minusDays(10), totalKm = 1000)
        val history = listOf(DailyDistance(c.endDate, 1500.0), DailyDistance(today, 999.0))
        val budget = DefaultWeeklyBudgetEngine().snapshot(c, history, emptyList(), today)
        assertEquals(1500.0, budget.usedKm, .001)
        assertEquals(50_000L, CostModel.heroFee(budget, c)); assertNull(CostModel.zeroFeePace(budget, c))
        assertEquals(HomeMode.ENDED, homeMode(budget, c))
        val engine = DefaultForecastEngine()
        val first = engine.forecast(c, history, emptyList(), emptyList(), AppPolicies.forecastDate(c, today))
        val later = engine.forecast(c, history, emptyList(), emptyList(), AppPolicies.forecastDate(c, today.plusYears(1)))
        assertEquals(1500.0, first.endKmLow, .001); assertEquals(first, later)
        assertNotNull(DefaultExitCalculator().compare(c, budget, ExitQuotes(), today))
    }

    @Test fun optionalFalseAndUnknownAreStoredSeparately() {
        val repo = AppRepository(JsonFileStore(tmp.newFolder())); repo.saveContract(contract)
        val settings = AppSettings(repo, clock)
        assertTrue(settings.updateContractField(ContractField.UNDERUSE_REFUND, "false"))
        assertEquals(false, repo.current.contract!!.underuseRefund)
        assertTrue(settings.updateContractField(ContractField.UNDERUSE_REFUND, null))
        assertNull(repo.current.contract!!.underuseRefund)
        assertFalse(settings.updateContractField(ContractField.TOTAL_KM, null))
    }

    @Test fun koreanFormatsStayStableUnderUsLocaleAndDifferentTimeZones() {
        val oldLocale = Locale.getDefault(); val oldZone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val instant = Instant.parse("2028-03-16T00:30:00Z")
            for (zone in listOf("America/Los_Angeles", "Asia/Seoul")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val zoned = object : AppClock {
                    override val zone = ZoneId.of(zone)
                    override fun now() = LocalDateTime.ofInstant(instant, this.zone)
                    override fun monotonicMs() = 0L
                }
                assertEquals(if (zone == "Asia/Seoul") "2028.03.16" else "2028.03.15", v2CalendarDate(zoned.today()))
                assertEquals("1,234", number(1234)); assertEquals("24만 원", v2Money(235350))
                assertEquals("오후 9:45", v2ReceivedTime(today.atTime(21, 45)))
                assertEquals("2028년 3월 16일(목)", v2Date(today))
            }
        } finally { Locale.setDefault(oldLocale); TimeZone.setDefault(oldZone) }
    }
}
