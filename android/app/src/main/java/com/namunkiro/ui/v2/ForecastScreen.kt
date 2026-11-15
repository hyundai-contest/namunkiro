package com.namunkiro.ui.v2

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.namunkiro.domain.model.LifeScenario
import com.namunkiro.ui.components.km
import com.namunkiro.ui.components.number
import com.namunkiro.ui.preview.PreviewFixtures
import com.namunkiro.ui.state.*
import com.namunkiro.ui.theme.NamunkiroTheme
import com.namunkiro.ui.v2.components.RangeBand
import kotlinx.coroutines.delay

/** 미리 준비한 생활 변화 시나리오 칩. 누르면 적용/해제, 직후 5초 되돌리기. */
private data class PresetScenario(val label: String, val dailyDeltaKm: Double)
private val Presets = listOf(
    PresetScenario("통근 +20km/일", 20.0),
    PresetScenario("재택 −15km/일", -15.0),
)

@Composable fun ForecastScreenV2(state: AppUiState, actions: AppActions, onWhy: () -> Unit = {}) {
    val s = state.budget; val c = state.contract; val f = state.forecast
    if (s == null || c == null || f == null) { V2Caption("예측할 주행 기록을 기다리고 있어요"); return }
    val lowKm = minOf(f.endKmLow, f.endKmHigh); val highKm = maxOf(f.endKmLow, f.endKmHigh)
    val lowFee = CostModel.distanceFee(lowKm, c); val highFee = CostModel.distanceFee(highKm, c)
    val range = v2MoneyRange(lowFee, highFee)

    // 되돌리기: 직전 조작 1건과 이전 범위를 기억(5초).
    var undoLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var undoWasAdd by rememberSaveable { mutableStateOf(false) }
    var undoDelta by rememberSaveable { mutableStateOf(0.0) }
    var prevRange by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(undoLabel) { if (undoLabel != null) { delay(V2Tokens.UndoVisibleMs); undoLabel = null; prevRange = null } }
    fun toggle(label: String, delta: Double, existing: LifeScenario?) {
        prevRange = range
        if (existing == null) actions.addScenario(label, delta, s.today, c.endDate) else actions.removeScenario(existing.id)
        undoLabel = label; undoWasAdd = existing == null; undoDelta = delta
    }

    // 범위 양 끝의 가정 이름: 계약 평균(홈 히어로와 같은 값)에 가까운 쪽 vs 최근 4주 습관.
    val (average, recent) = forecastAssumptionKm(f, s)

    V2Title("습관이 바뀌면, 범위도 바뀌어요")
    V2Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            V2Caption(if (f.basedOnPlanOnly) "주행 2주 미만 · 대략적인 예측" else "만기 거리 정산 예상 범위", Modifier.weight(1f))
            if (state.isSimulated()) V2SimTag()
            V2Button("왜?", onWhy)
        }
        Text(
            range.replace(" ", " "),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = V2Tokens.HeroSize, fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        prevRange?.takeIf { it != range }?.let { V2Caption("$it → $range", color = MaterialTheme.colorScheme.onSurface) }
        RangeBand(s, c, f, usedSourceLabel = if (state.isSimulated()) "모의" else "관측")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            V2Caption("${forecastBasisLabel(state, "최근 4주")} ${km(recent)} · ${v2Money(CostModel.distanceFee(recent, c))}")
            V2Caption("${forecastBasisLabel(state, "계약 평균")} ${km(average)} · ${v2Money(CostModel.distanceFee(average, c))}")
        }
        TripPatternLine(state, onWhy)
    }
    V2Panel {
        Text("생활이 바뀐다면", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Presets.forEach { p ->
                val existing = state.scenarios.firstOrNull { it.label == p.label }
                V2Button(if (existing != null) "✓ ${p.label}" else p.label, { toggle(p.label, p.dailyDeltaKm, existing) }, primary = existing != null)
            }
            state.scenarios.filter { sc -> Presets.none { it.label == sc.label } }.forEach { sc ->
                V2Button("✓ ${sc.label} · ${number(sc.dailyDeltaKm)} km/일", { toggle(sc.label, sc.dailyDeltaKm, sc) }, primary = true)
            }
        }
        undoLabel?.let { label ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                V2Caption(if (undoWasAdd) "$label 적용" else "$label 해제", Modifier.weight(1f))
                V2Button("되돌리기", {
                    val now = state.scenarios.firstOrNull { it.label == label }
                    if (undoWasAdd) now?.let { actions.removeScenario(it.id) }
                    else if (now == null) actions.addScenario(label, undoDelta, s.today, c.endDate)
                    undoLabel = null; prevRange = null
                }, primary = true)
            }
        }
    }
}

@Preview(name = "V2 예측", widthDp = 1682, heightDp = 1210)
@Composable fun V2ForecastPreview() { NamunkiroTheme(true) { Surface { Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { ForecastScreenV2(PreviewFixtures.home, NoopActions) } } } }

@Preview(name = "V2 예측 · 반쪽 · 시나리오 적용", widthDp = 840, heightDp = 1400)
@Composable fun V2CompactForecastPreview() {
    val st = PreviewFixtures.home.copy(scenarios = listOf(LifeScenario("commute", "통근 +20km/일", 20.0, PreviewFixtures.budget.today, PreviewFixtures.contract.endDate)))
    NamunkiroTheme(true) { Surface { Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { ForecastScreenV2(st, NoopActions) } } }
}

@Preview(name = "V2 예측 · 덜 탐", widthDp = 1682, heightDp = 1210)
@Composable fun V2UnderuseForecastPreview() { NamunkiroTheme(true) { Surface { Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { ForecastScreenV2(PreviewFixtures.jung, NoopActions) } } } }

@Preview(name = "V2 예측 · 주행 패턴", widthDp = 1682, heightDp = 1400)
@Composable fun V2PatternForecastPreview() { NamunkiroTheme(true) { Surface { Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { ForecastScreenV2(PreviewNavi, NoopActions) } } } }
