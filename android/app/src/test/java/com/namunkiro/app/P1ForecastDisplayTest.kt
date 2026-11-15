package com.namunkiro.app

import com.namunkiro.domain.model.PlannedTrip
import com.namunkiro.ui.preview.PreviewFixtures
import com.namunkiro.ui.v2.*
import org.junit.Assert.*
import org.junit.Test

class P1ForecastDisplayTest {
    @Test fun plannedAverageMatchesHomeHeroAndSlider() {
        val state = PreviewFixtures.weekState
        val snapshot = averageDisplaySnapshot(state)!!
        val contract = state.contract!!
        val average = forecastAssumptionKm(state.forecast!!, state.budget!!).first
        assertEquals(average, snapshot.projectedEndKm, .0001)
        assertEquals(CostModel.distanceFee(average, contract), CostModel.heroFee(snapshot, contract))
        assertEquals(average, CostModel.endKmAtPace(snapshot, CostModel.currentWeeklyPace(snapshot)), .0001)
    }

    @Test fun basisOnlyNamesPlansWithinContract() {
        val state = PreviewFixtures.home.copy(trips = emptyList(), scenarios = emptyList())
        val trip = PlannedTrip("plan", state.budget!!.today.plusDays(1), 420, "예정 이동")
        assertEquals("계약 평균 기준", forecastBasisLabel(state, "계약 평균"))
        assertEquals("계약 평균 · 계획 반영", forecastBasisLabel(state.copy(trips = listOf(trip)), "계약 평균"))
        assertEquals("계약 평균 기준", forecastBasisLabel(state.copy(trips = listOf(trip.copy(date = state.contract!!.endDate.plusDays(1)))), "계약 평균"))
    }

    @Test fun missingForecastFallsBackToBudget() {
        val state = PreviewFixtures.home.copy(forecast = null)
        assertEquals(state.budget!!.projectedEndKm, averageDisplaySnapshot(state)!!.projectedEndKm, .0001)
    }
}
