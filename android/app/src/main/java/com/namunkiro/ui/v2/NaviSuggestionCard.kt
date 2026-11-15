package com.namunkiro.ui.v2

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.namunkiro.core.result.Source
import com.namunkiro.ui.components.km
import com.namunkiro.ui.components.number
import com.namunkiro.ui.preview.PreviewFixtures
import com.namunkiro.ui.state.*
import com.namunkiro.ui.theme.NamunkiroTheme
import java.time.LocalDate

/** 그날 평소 거리: 관측 주행 패턴이 있으면 그 값, 없으면 예측 엔진의 평일/주말 평균. */
fun usualKmFor(state: AppUiState, date: LocalDate): Double? =
    state.tripPattern?.usualKmByDay?.get(date.dayOfWeek)
        ?: state.forecast?.let { if (date.dayOfWeek.value >= 6) it.weekendAvgKm else it.weekdayAvgKm }

/**
 * 내비 목적지 제안 카드. 수락 전에는 예정 이동이 아니다.
 * 긴 이동이면 이번 주 조정 안내와 같은 형식(0원 목표 A→B · +X만 원)을 보인다. 금액은 CostModel.
 */
@Composable fun NaviSuggestionCard(state: AppUiState, actions: AppActions) {
    val n = state.naviSuggestion ?: return
    val s = state.budget; val c = state.contract
    V2Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            V2Caption("내비 목적지 · ${v2Date(n.date)}", Modifier.weight(1f))
        }
        Text(
            "${n.destinationName} · 왕복 ${km(n.roundTripKm)} [내비 등록·예정]${if (n.source == Source.SIMULATED) " [모의]" else ""}",
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
        )
        if (n.isLongTrip == true && s != null && c != null) {
            val usual = usualKmFor(state, n.date) ?: 0.0
            val extra = (n.roundTripKm - usual).coerceAtLeast(0.0)
            val impact = CostModel.marginalFee(s, c, extra)
            val zero = CostModel.zeroFeePace(s, c)
            val after = CostModel.zeroFeePace(s.copy(usedKm = s.usedKm + extra), c)
            V2Caption("평소보다 긴 이동 · 평소보다 +${km(extra)}")
            Text(
                (if (zero != null && after != null) "0원 목표 주 ${number(zero)} → ${km(after)} · " +
                    ((zero - after).takeIf { it > 0 }?.let { "매주 ${km(it)} 덜 타면 0원 · " } ?: "") else "") +
                    (if (impact > 0) "+${v2Impact(impact)}" else "정산 변화 없음"),
                fontSize = 28.sp, lineHeight = 40.sp, style = V2Numeric,
            )
        } else if (n.isLongTrip == null) V2Caption("이 요일 평소 거리를 아직 몰라요")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            V2Button("이번 주 계획에 넣기", { actions.acceptNaviSuggestion(true) }, primary = true)
            V2Button("아니요", { actions.dismissNaviSuggestion() })
        }
    }
}

/** 예측 화면: 관측 주행 패턴 한 줄. */
@Composable fun TripPatternLine(state: AppUiState, onWhy: () -> Unit) {
    val p = state.tripPattern ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        V2Caption(
            "관측 주행 ${number(p.observedTrips)}회 · " + (p.routineShare?.let { "루틴 비중 ${number(it * 100)}%" } ?: "루틴 비중 확인 중"),
            Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
        )
        V2Button("왜?", onWhy)
    }
}

internal val PreviewNavi = PreviewFixtures.weekState.copy(
    trips = emptyList(),
    naviSuggestion = NaviSuggestion("강릉 경포대", 210.0, 420.0, PreviewFixtures.budget.today.plusDays(2), true, null, Source.OBSERVED),
    tripPattern = com.namunkiro.domain.TripPattern(46, emptyList(), .72, mapOf(java.time.DayOfWeek.SATURDAY to 32.0)),
)

@Preview(name = "V2 내비 제안 카드", widthDp = 1682, heightDp = 600)
@Composable fun V2NaviPreview() { NamunkiroTheme(true) { Surface { Column(Modifier.padding(32.dp)) { NaviSuggestionCard(PreviewNavi, NoopActions) } } } }
