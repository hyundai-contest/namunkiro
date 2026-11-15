package com.namunkiro.ui.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.namunkiro.domain.BudgetSnapshot
import com.namunkiro.domain.ForecastResult
import com.namunkiro.domain.model.PlannedTrip
import com.namunkiro.ui.components.km
import com.namunkiro.ui.components.number
import com.namunkiro.ui.preview.PreviewFixtures
import com.namunkiro.ui.state.*
import com.namunkiro.ui.theme.NamunkiroTheme
import com.namunkiro.ui.v2.components.WeekBars
import kotlinx.coroutines.delay
import java.time.LocalDate

private val DayNames = listOf("월", "화", "수", "목", "금", "토", "일")

/** 그날 평소 거리: 관측 주행 패턴의 요일별 값 우선, 없으면 예측 엔진의 주중/주말 평균. */
private fun usualKm(f: ForecastResult, date: LocalDate, pattern: com.namunkiro.domain.TripPattern?) =
    pattern?.usualKmByDay?.get(date.dayOfWeek) ?: if (date.dayOfWeek.value >= 6) f.weekendAvgKm else f.weekdayAvgKm

/**
 * 평소 대비 추가 km. 내비 제안 카드(usualKmFor)와 같은 기준(관측 패턴 요일값 → 없으면 예측 평균)으로 계산해
 * 수락 전 카드와 수락 후 이번 주 화면의 금액·'매주 N km'가 같게 한다. 금액은 항상 CostModel.
 */
private fun extraKm(f: ForecastResult, trip: PlannedTrip, pattern: com.namunkiro.domain.TripPattern? = null): Double =
    if (trip.otherCar) 0.0 else (trip.km - usualKm(f, trip.date, pattern)).coerceAtLeast(0.0)

/** 긴 이동 판정: 예정 거리 ≥ 그날 평소 × 2 또는 평소보다 +100km 이상. */
fun isLongTrip(f: ForecastResult, trip: PlannedTrip, pattern: com.namunkiro.domain.TripPattern? = null): Boolean {
    val usual = usualKm(f, trip.date, pattern)
    return (usual > 0 && trip.km >= usual * 2) || trip.km - usual >= 100
}

/** 추가 km를 남은 기간에 흡수할 때의 0원 목표 페이스. */
private fun zeroAfter(s: BudgetSnapshot, c: com.namunkiro.domain.model.Contract, extra: Double) =
    CostModel.zeroFeePace(s.copy(usedKm = s.usedKm + extra), c)

/** 예정 이동(이 차·오늘 이후)의 추가 km를 흡수한 0원 목표 페이스. 홈 링과 이번 주 화면이 같은 값을 쓴다. 예정 없으면 기본 목표. */
fun plannedZeroFeePace(state: AppUiState): Int? {
    val s = state.budget ?: return null; val c = state.contract ?: return null
    val f = state.forecast ?: return CostModel.zeroFeePace(s, c)
    val extra = state.trips.filter { !it.otherCar && !it.date.isBefore(s.today) && !it.date.isAfter(c.endDate) }
        .sumOf { extraKm(f, it, state.tripPattern) }
    return zeroAfter(s, c, extra)
}

@Composable fun WeekScreenV2(state: AppUiState, actions: AppActions, onWhy: () -> Unit = {}) {
    val s = state.budget; val c = state.contract; val f = state.forecast
    if (s == null || c == null || f == null) { V2Caption("이번 주 기록을 준비하고 있어요"); return }
    val zero = CostModel.zeroFeePace(s, c)
    // 아직 오지 않은 이 차의 예정 이동(다른 차로 가는 이동 제외)
    val planned = state.trips.filter { !it.otherCar && !it.date.isBefore(s.today) }
    val longTrips = planned.filter { isLongTrip(f, it, state.tripPattern) }
    val totalExtra = planned.sumOf { extraKm(f, it, state.tripPattern) }
    val totalImpact = CostModel.marginalFee(s, c, totalExtra)
    val zeroWithTrips = zeroAfter(s, c, totalExtra)
    val plannedFees = planned.groupBy { it.date }.mapValues { (_, trips) -> CostModel.marginalFee(s, c, trips.sumOf { extraKm(f, it, state.tripPattern) }) }

    // 계획에서 빼기 → 되돌리기(5초)
    var removedLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var removedDate by rememberSaveable { mutableStateOf<String?>(null) }
    var removedKm by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(removedLabel) { if (removedLabel != null) { delay(V2Tokens.UndoVisibleMs); removedLabel = null } }

    // 막대(300ms)가 먼저, 원 숫자는 그 뒤(원칙 13a).
    val impactShown = animatedMoney(totalImpact)

    V2Title(
        longTrips.firstOrNull()?.let { "${it.label} 다녀오면, 이렇게 조정돼요" }
            ?: if (removedLabel != null) "평소 이동으로 돌아왔어요" else "이번 주 이동과 만기 정산"
    )
    NaviSuggestionCard(state, actions)
    V2Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            V2Caption("이번 주 · 월~일", Modifier.weight(1f))
            if (state.isSimulated()) V2SimTag()
            V2Button("왜?", onWhy)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = v2Compact(maxWidth)
            val stats: List<@Composable (Modifier) -> Unit> = listOf(
                { m ->
                    val target = when {
                        zero == null -> "미확인"
                        zeroWithTrips != null && zeroWithTrips != zero -> "${number(zero)} → ${number(zeroWithTrips)}"
                        else -> number(zero)
                    }
                    V2Stat(target, if (zeroWithTrips != zero) "0원 목표 km/주 · 다녀오면" else "0원 목표 km/주", m)
                },
                { m -> V2Stat(number(f.thisWeekForecastKm), "이번 주 예상 km", m) },
                { m -> V2Stat(if (impactShown == 0L) "0원" else "+${v2Impact(impactShown)}", "평소 대비 만기 정산", m, color = if (totalImpact > 0) v2Colors().over else MaterialTheme.colorScheme.onSurface) },
            )
            if (compact) Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { stats.forEach { it(Modifier) } }
            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) { stats.forEach { it(Modifier.weight(1f)) } }
        }
        WeekBars(f.thisWeekDays, zero?.toDouble(), plannedFees, f.weekdayAvgKm, f.weekendAvgKm)
    }
    longTrips.forEach { trip ->
        val extra = extraKm(f, trip, state.tripPattern)
        val after = zeroAfter(s, c, extra)
        val impact = CostModel.marginalFee(s, c, extra)
        V2Panel {
            V2Caption("평소보다 긴 이동 · " + if (trip.source == com.namunkiro.core.result.Source.OBSERVED) "[내비 등록·예정]" else "[예정]")
            Text(
                "${DayNames[trip.date.dayOfWeek.value - 1]}요일 ${trip.label} ${km(trip.km)} · 평소보다 +${km(extra)}",
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            )
            if (zero != null && after != null) {
                val less = (zero - after).coerceAtLeast(0)
                Text(
                    "0원 목표 주 ${number(zero)} → ${km(after)} · " +
                        (if (less > 0) "매주 ${km(less)} 덜 타면 0원" else "목표 그대로") +
                        " · " + (if (impact > 0) "+${v2Impact(impact)}" else "정산 변화 없음"),
                    fontSize = 28.sp, lineHeight = 40.sp, style = V2Numeric,
                )
            } else V2Caption(if (impact > 0) "만기 정산 +${v2Impact(impact)}" else "만기 정산 변화 없음")
            V2Button("계획에서 빼기", {
                removedLabel = trip.label; removedDate = trip.date.toString(); removedKm = trip.km
                actions.removeTrip(trip.id)
            })
        }
    }
    removedLabel?.let { label ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            V2Caption("$label 계획을 뺐어요", Modifier.weight(1f))
            V2Button("되돌리기", {
                removedDate?.let { actions.addTrip(LocalDate.parse(it), removedKm, label) }
                removedLabel = null
            }, primary = true)
        }
    }
    TripEntryV2(state, actions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TripEntryV2(state: AppUiState, actions: AppActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    V2Button("예정 이동 추가", { open = true })
    if (!open) return
    val today = state.budget?.today ?: state.contract?.startDate ?: return
    var label by rememberSaveable { mutableStateOf(if (state.demoMode) com.namunkiro.demo.PersonaSeeds.DEMO_NAVI_DESTINATION else "") }
    var distance by rememberSaveable { mutableStateOf(if (state.demoMode) "420" else "") }
    var picked by rememberSaveable { mutableStateOf((if (state.demoMode) today.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SATURDAY)) else today).toString()) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { open = false }) {
        Column(Modifier.fillMaxWidth().imePadding().padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("예정 이동 추가", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(label, { label = it }, label = { Text("이동 이름", fontSize = 24.sp) }, textStyle = MaterialTheme.typography.bodyLarge, singleLine = true, modifier = Modifier.fillMaxWidth().heightIn(min = V2Tokens.Touch))
            OutlinedTextField(distance, { distance = it.filter(Char::isDigit).take(5) }, label = { Text("왕복 km", fontSize = 24.sp) }, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().heightIn(min = V2Tokens.Touch))
            V2Button(v2Date(LocalDate.parse(picked)), { calendar = true })
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                V2Button("취소", { open = false })
                V2Button("계획 추가", { actions.addTrip(LocalDate.parse(picked), distance.toIntOrNull() ?: 0, label.trim()); open = false }, primary = true, enabled = label.isNotBlank() && (distance.toIntOrNull() ?: 0) > 0)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (calendar) V2DatePicker(LocalDate.parse(picked), { calendar = false }, { picked = it.toString() }, minimum = today)
}

@Preview(name = "V2 이번 주 · 긴 이동", widthDp = 1682, heightDp = 1400)
@Composable fun V2WeekPreview() { NamunkiroTheme(true) { Surface { Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { WeekScreenV2(PreviewFixtures.weekState, NoopActions) } } } }

@Preview(name = "V2 이번 주 · 긴 이동 · 반쪽", widthDp = 840, heightDp = 1600)
@Composable fun V2CompactWeekPreview() { NamunkiroTheme(true) { Surface { Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { WeekScreenV2(PreviewFixtures.weekState, NoopActions) } } } }

@Preview(name = "V2 이번 주 · 평소", widthDp = 1682, heightDp = 1210)
@Composable fun V2WeekPlainPreview() { NamunkiroTheme(true) { Surface { Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { WeekScreenV2(PreviewFixtures.home, NoopActions) } } } }
