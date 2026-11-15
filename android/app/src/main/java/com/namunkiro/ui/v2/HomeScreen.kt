package com.namunkiro.ui.v2

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.namunkiro.core.result.Source
import com.namunkiro.domain.BudgetSnapshot
import com.namunkiro.domain.RecoveryVerdict
import com.namunkiro.domain.model.Contract
import com.namunkiro.ui.components.km
import com.namunkiro.ui.components.number
import com.namunkiro.ui.preview.PreviewFixtures
import com.namunkiro.ui.state.*
import com.namunkiro.ui.theme.NamunkiroTheme
import com.namunkiro.ui.v2.components.*
import kotlinx.coroutines.delay

/** 홈의 상태 3종(+기록 초기). 슬라이더 미리보기와 무관하게 '현재' 값으로만 판정한다. */
enum class HomeMode { EARLY, OVER, HARD, SPARE, ON_TRACK, ENDED }

fun homeMode(s: BudgetSnapshot, c: Contract): HomeMode {
    if (s.today.isAfter(c.endDate)) return HomeMode.ENDED
    if (s.elapsedDays < 7) return HomeMode.EARLY
    val zero = CostModel.zeroFeePace(s, c)
    val fee = CostModel.heroFee(s, c)
    return when {
        fee == 0L && s.projectedEndKm < CostModel.allowanceKm(c) -> HomeMode.SPARE
        s.verdict == RecoveryVerdict.HARD_TO_RECOVER || (zero != null && zero <= 0) -> HomeMode.HARD
        fee > 0L -> HomeMode.OVER
        else -> HomeMode.ON_TRACK
    }
}

@Composable fun HomeScreenV2(state: AppUiState, actions: AppActions, onWhy: () -> Unit = {}) {
    val s = averageDisplaySnapshot(state); val c = state.contract
    if (s == null || c == null) { V2Caption("주행·계약 정보를 확인하고 있어요"); return }
    val mode = homeMode(s, c)
    if (mode == HomeMode.ENDED) {
        V2Panel(gap = 8.dp, padding = 20.dp) {
            V2Title("계약 만기가 지났어요")
            V2Caption("계약 기간 사용 ${km(s.usedKm)} · 거리 정산 참고액")
            V2Money(CostModel.distanceFee(s.usedKm, c))
            V2Caption("최종 청구액은 계약 조건과 반납 정산을 확인해 주세요.")
            V2Button("계약 확인", { actions.navigate(Screen.CONTRACT) }, primary = true)
            V2Button("왜?", onWhy)
        }
        return
    }
    // 슬라이더 미리보기(저장 안 함). 잠금·탭 이동·회전 후 복원. 계약이 바뀌면 초기화.
    var pace by rememberSaveable(c, state.trips, state.scenarios) { mutableStateOf<Double?>(null) }
    val zero = CostModel.zeroFeePace(s, c)
    val cut = CostModel.weeklyCutToZero(s, c)
    val allowance = CostModel.allowanceKm(c)

    // +km 수신: 한계 비용 칩(외삽 없이 그 km × 단가). km 막대가 먼저 움직인 뒤 뜬다.
    val motion = LocalMotionEnabled.current
    var previous by remember { mutableStateOf(s) }
    var marginal by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(s.usedKm) {
        val delta = s.usedKm - previous.usedKm
        val before = previous
        previous = s
        if (delta > 0 && delta <= 100) {
            val cost = CostModel.marginalFee(before, c, delta)
            val tag = if (state.odometer?.source == Source.OBSERVED) "관측" else "[모의]"
            if (motion) delay(180)
            marginal = "+${km(delta)} → +${number(cost)}원 · $tag"
        }
    }
    LaunchedEffect(marginal) { if (marginal != null) { delay(4000); marginal = null } }

    // 히어로 값: 현재는 heroFee(= 슬라이더 '현재' 위치 값). 미리보기 중에는 즉시 반영(직접 조작은 지연 없음).
    val base = causalValues(CausalValues(s.usedKm, s.projectedEndKm, CostModel.heroFee(s, c)))
    val endKm = pace?.let { CostModel.endKmAtPace(s, it) } ?: base.endKm
    val fee = pace?.let { CostModel.feeAtPace(s, c, it) } ?: base.fee

    val title = when (mode) {
        HomeMode.EARLY -> "오늘부터 주행을 기록해요"
        HomeMode.SPARE -> "만기에 ${km(allowance - s.projectedEndKm)} 남아요"
        HomeMode.HARD -> "거리 조정이 어렵다면, 출구 비교"
        HomeMode.OVER -> if (cut != null && cut > 0) "주 ${number(cut)} km 줄이면 0원" else "지금 페이스면 초과 0원"
        HomeMode.ON_TRACK -> "지금 페이스면 초과 0원"
        HomeMode.ENDED -> "계약 만기가 지났어요"
    }
    V2Title(title)
    NaviSuggestionCard(state, actions)
    V2Panel(gap = 8.dp, padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            V2Caption(
                when {
                    mode == HomeMode.EARLY -> "주행 7일 뒤부터 만기 정산을 예측해요"
                    pace != null -> "주 ${number(pace!!)} km 가정 · 미리보기"
                    mode == HomeMode.SPARE -> "만기까지 남을 거리 · ${forecastBasisLabel(state, "계약 평균")}"
                    else -> "이대로면 만기 거리 정산 · ${forecastBasisLabel(state, "계약 평균")}"
                },
                Modifier.weight(1f),
            )
            if (state.isSimulated()) V2SimTag()
            V2Button("왜?", onWhy)
        }
        if (mode == HomeMode.EARLY) {
            V2Caption("차가 센 누적거리 ${km(state.odometer?.value)}")
            return@V2Panel
        }
        if (mode == HomeMode.SPARE && pace == null) {
            Text(km(allowance - base.endKm).replace(" ", " "), style = MaterialTheme.typography.displayLarge.copy(fontSize = V2Tokens.HeroSize, fontFeatureSettings = "tnum"), color = v2Colors().spare)
            V2Caption("만기 거리 정산 0원")
        } else V2Money(fee)
        Spacer(Modifier.height(4.dp))
        DistanceBar(s, c, endKm = endKm, usedKm = base.usedKm, usedSourceLabel = if (state.isSimulated()) "모의" else "관측")
        marginal?.let { V2Chip(it) }
    }
    if (mode == HomeMode.EARLY) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        V2Columns(v2Compact(maxWidth), left = {
            V2Panel(gap = 8.dp, padding = 20.dp) {
                when {
                    mode == HomeMode.HARD -> {
                        Text("만기 이후 선택", style = MaterialTheme.typography.titleLarge)
                        V2Caption("줄일 수 없는 이동이 많다면 다른 선택이 더 쌀 수 있어요")
                        V2Button("출구 비교 →", { actions.navigate(Screen.EXIT) }, primary = true)
                    }
                    zero != null && zero > 0 -> {
                        // 예정 이동을 넣었으면 이번 주 화면과 같은 '계획 반영' 목표를 보여 준다.
                        val target = plannedZeroFeePace(state)?.takeIf { it > 0 } ?: zero
                        Text(if (mode == HomeMode.SPARE) "초과 0원 목표" else "이번 주 목표", style = MaterialTheme.typography.titleLarge)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            MiniRing(target.toDouble(), s.thisWeek.usedKm)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("주 ${km(target)}", fontSize = 34.sp, style = V2Numeric)
                                V2Caption(if (target != zero) "계획 반영 · 이번 주 ${km(s.thisWeek.usedKm)} 사용" else "이번 주 ${km(s.thisWeek.usedKm)} 사용")
                            }
                        }
                    }
                    else -> V2Caption("계약 조건을 확인하면 목표가 보여요")
                }
            }
        }, right = {
            V2Panel(gap = 8.dp, padding = 20.dp) {
                if (mode == HomeMode.SPARE) {
                    Text("다음 계약", style = MaterialTheme.typography.titleLarge)
                    val annual = s.annualPaceKm
                    val rec = s.recommendedAnnualKm
                    if (rec != null) {
                        Text(if (annual != null) "현재 연 ${km(annual)} → 연 ${km(rec)} 검토" else "연 ${km(rec)} 검토", fontSize = 30.sp, style = V2Numeric)
                        V2Button("계약 보기 →", { actions.navigate(Screen.CONTRACT) })
                    } else V2Caption("주행 90일 뒤 다음 계약 약정을 추천해요")
                } else PaceSlider(s, c, pace) { pace = it }
            }
        })
    }
}

@Preview(name = "V2 홈 · 초과(김 대리)", widthDp = 1682, heightDp = 1210)
@Composable fun V2HomePreview() { NamunkiroTheme(true) { Surface { Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { HomeScreenV2(PreviewFixtures.home, NoopActions) } } } }

@Preview(name = "V2 홈 · 회복 어려움", widthDp = 1682, heightDp = 1210)
@Composable fun V2HardPreview() { NamunkiroTheme(true) { Surface { Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { HomeScreenV2(PreviewFixtures.hard, NoopActions) } } } }

@Preview(name = "V2 홈 · 덜 탐 · 반쪽", widthDp = 840, heightDp = 1210)
@Composable fun V2UnderusePreview() { NamunkiroTheme(true) { Surface { Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { HomeScreenV2(PreviewFixtures.jung, NoopActions) } } } }

@Preview(name = "V2 홈 · 초과 · 라이트 반쪽", widthDp = 840, heightDp = 1400)
@Composable fun V2HomeLightCompactPreview() { NamunkiroTheme(false) { Surface { Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { HomeScreenV2(PreviewFixtures.home, NoopActions) } } } }

@Preview(name = "V2 홈 · 내비 제안", widthDp = 1682, heightDp = 1600)
@Composable fun V2HomeNaviPreview() { NamunkiroTheme(true) { Surface { Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { HomeScreenV2(PreviewNavi, NoopActions) } } } }
