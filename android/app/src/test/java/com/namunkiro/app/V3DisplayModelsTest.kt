package com.namunkiro.app

import com.namunkiro.ui.preview.PreviewFixtures
import com.namunkiro.ui.v2.*
import com.namunkiro.domain.model.ContractType
import org.junit.Assert.*
import org.junit.Test

class V3DisplayModelsTest {
    @Test fun formulaUsesTheSameRoundedKmThatTheUserReads() {
        val c = PreviewFixtures.contract
        assertEquals(235300L, displayDistanceFee(62353.49, c))
        assertEquals(238800L, displayDistanceFee(62388.49, c))
        assertEquals(235350L, displayDistanceFee(62353.49, c.copy(unitPricePerKm = 150, deductKm = 784)))
    }
    @Test fun noFeeContractStillUsesCostModelRules() {
        val c = PreviewFixtures.contract.copy(type = ContractType.FINANCE_LEASE)
        assertEquals(0L, displayDistanceFee(90000.0, c))
    }
    @Test fun planRangeLabelsAreTheActualEndpoints() {
        val st = PreviewFixtures.weekState
        val (average, recent) = forecastAssumptionKm(st.forecast!!, st.budget!!)
        assertEquals(st.forecast!!.endKmHigh, average, .0001)
        assertEquals(st.forecast!!.endKmLow, recent, .0001)
        assertNotEquals(st.budget!!.projectedEndKm, average)
    }
    @Test fun recentPaceCanBeTheUpperEndpoint() {
        val f = PreviewFixtures.forecast.copy(endKmPoint = PreviewFixtures.forecast.endKmLow)
        val (average, recent) = forecastAssumptionKm(f, PreviewFixtures.budget)
        assertEquals(f.endKmLow, average, .0001)
        assertEquals(f.endKmHigh, recent, .0001)
    }
}
