package com.namunkiro.ui.v2

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.namunkiro.ui.preview.PreviewFixtures
import com.namunkiro.ui.state.*
import com.namunkiro.ui.theme.NamunkiroTheme
import com.namunkiro.ui.v2.components.LocalActiveChartHint
import com.namunkiro.ui.v2.components.LocalChartDismissKey

private val Tabs = listOf(Screen.HOME to "홈", Screen.WEEK to "이번 주", Screen.FORECAST to "예측", Screen.EXIT to "출구", Screen.CONTRACT to "계약")
/** 시연 설정도 앱 테마 안의 헤더 버튼으로 연다. */
val LocalDemoSettings = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * v2 앱 최상위. 화면은 [AppUiState]만 읽고 동작은 [AppActions]로만 보낸다.
 * - Gate 우선순위: DRIVING_LOCK(잠금 화면만) > ANOMALY_HOLD(보류 화면, 탭 숨김) > 계약 없음(온보딩) > 탭.
 * - 화면별 스크롤·슬라이더 미리보기·열린 말풍선·[왜?] 시트는 rememberSaveable로 잠금→정차, 탭 이동, 회전 후 즉시 복원.
 * - 창 폭 < 900dp(반쪽 창)이면 1열.
 */
@Composable fun NamunkiroAppV2(state: AppUiState, actions: AppActions) {
    NamunkiroTheme {
        V2MotionPolicy(state.gate) {
            // 인물·데이터 모드 전환 시 모든 탭의 스크롤·미리보기를 초기화한다.
            val pages = key(state.demoMode, state.demoPersonaName) { rememberSaveableStateHolder() }
            var why by rememberSaveable { mutableStateOf<String?>(null) }
            val hint = rememberSaveable { mutableStateOf<String?>(null) }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (state.gate == Gate.DRIVING_LOCK) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { DrivingLockV2(state, actions) }
                    return@Surface
                }
                BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    val compact = maxWidth < V2Tokens.CompactBreakpoint
                    CompositionLocalProvider(LocalV2Compact provides compact) {
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = if (compact) 32.dp else 48.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            V2Header(state, actions)
                            // 상단 배너 1줄: 공백 거리 회수·출구 달력 D-14(R-FINAL: v2에서 누락돼 최소 추가, UX 검토 대상)
                            if (state.gate == Gate.NORMAL && state.contract != null) state.banner?.let { b ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(b, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                                    V2Button("닫기", actions::dismissBanner)
                                }
                            }
                            if (state.gate == Gate.NORMAL && state.contract != null) V2Tabs(state.screen, actions)
                            val route = when {
                                state.gate == Gate.ANOMALY_HOLD -> "hold"
                                state.contract == null -> "onboarding"
                                state.screen == Screen.INSURANCE -> Screen.CONTRACT.name
                                else -> state.screen.name
                            }
                            pages.SaveableStateProvider(route) {
                                val scroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
                                CompositionLocalProvider(LocalChartDismissKey provides scroll.value, LocalActiveChartHint provides hint) {
                                    Column(
                                        Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).imePadding()
                                            // 바깥 탭 → 열린 말풍선 닫기(원칙 13e). 자식이 소비한 탭은 오지 않는다.
                                            .pointerInput(Unit) { detectTapGestures(onTap = { hint.value = null }) },
                                        verticalArrangement = Arrangement.spacedBy(V2Tokens.Gap),
                                    ) {
                                        when (route) {
                                            "hold" -> AnomalyHoldV2(state, actions)
                                            "onboarding" -> OnboardingScreenV2(state, actions)
                                            Screen.HOME.name -> HomeScreenV2(state, actions) { why = "home" }
                                            Screen.WEEK.name -> WeekScreenV2(state, actions) { why = "week" }
                                            Screen.FORECAST.name -> ForecastScreenV2(state, actions) { why = "forecast" }
                                            Screen.EXIT.name -> { ExitScreenV2(state, actions); V2Button("왜?", { why = "exit" }) }
                                            else -> { ContractScreenV2(state, actions); V2Button("왜?", { why = "contract" }) }
                                        }
                                        Spacer(Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        if (state.gate == Gate.NORMAL && state.contract != null) {
                            why?.let { WhySheetV2(state, actions, topic = it, onDismiss = { why = null }) }
                            val q = state.deferredQuestion
                            // 공제 질문은 기록 7일 뒤(필요한 화면에서 1개만). 나머지는 바로.
                            if (why == null && q != null && (state.screen == Screen.CONTRACT || q.kind != DeferredQuestionKind.DEDUCT_KM || (state.budget?.elapsedDays ?: 0) >= 7)) {
                                DeferredQuestionSheetV2(state, actions)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 헤더: 앱 이름 · 정차 상태 · [모의] · 시연 칩. */
@Composable private fun V2Header(state: AppUiState, actions: AppActions) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("남은키로", style = MaterialTheme.typography.titleLarge)
            V2Caption(if (state.gate == Gate.ANOMALY_HOLD) "● 정차 중 · 값 확인 중" else if (state.vehicleConnection == VehicleConnectionStatus.READY || state.demoMode) "● 정차 중" else "차량 연결 확인", Modifier.weight(1f), color = v2Colors().spare)
            if (state.isSimulated() && !state.demoMode) V2SimTag()
            if (LocalV2Compact.current != true) DemoChip(state, actions)
            if (state.demoMode) LocalDemoSettings.current?.let { open -> V2Button("⚙", open, Modifier.semantics { contentDescription = "시연 설정" }) }
            if (!state.demoMode && state.contract != null) V2Button("예시 [모의]", actions::enterDemo)
        }
        // 반쪽 창에서는 시연 칩에 한 줄 전체를 주어 이름과 복귀 행동을 보존한다.
        if (LocalV2Compact.current == true) DemoChip(state, actions)
        if (!state.demoMode && state.vehicleConnection != VehicleConnectionStatus.READY) {
            V2Caption(vehicleConnectionLabel(state.vehicleConnection))
            V2Button("차량 신호 다시 확인", actions::retryOdometer)
        }
    }
}

fun vehicleConnectionLabel(status: VehicleConnectionStatus): String = when (status) {
    VehicleConnectionStatus.WAITING -> "기어·주행 제한 신호를 기다리는 중"
    VehicleConnectionStatus.TIMED_OUT -> "차량 신호를 받지 못했어요 · 연결 확인 후 다시 시도해 주세요"
    VehicleConnectionStatus.NOT_SUPPORTED -> "지원하지 않는 차량 신호가 있어요 · 계약과 예시를 둘러볼 수 있어요"
    VehicleConnectionStatus.ERROR -> "차량 연결을 확인해 주세요 · 다시 시도할 수 있어요"
    VehicleConnectionStatus.READY -> "차량 신호 연결됨"
}

/** 탭 5개(76dp 이상). 반쪽 창에서는 가로 스크롤. */
@Composable private fun V2Tabs(current: Screen, actions: AppActions) {
    val selected = if (current == Screen.INSURANCE) Screen.CONTRACT else current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tabs.forEach { (screen, label) ->
            V2Button(
                label, { actions.navigate(screen) },
                Modifier.semantics { role = Role.Tab; this.selected = screen == selected },
                primary = screen == selected,
            )
        }
    }
}

@Preview(name = "V2 앱 · 홈 · 1682", widthDp = 1682, heightDp = 1210)
@Composable fun V2AppPreview() { NamunkiroAppV2(PreviewFixtures.home, NoopActions) }

@Preview(name = "V2 앱 · 홈 · 반쪽 840", widthDp = 840, heightDp = 1210)
@Composable fun V2CompactAppPreview() { NamunkiroAppV2(PreviewFixtures.home, NoopActions) }

@Preview(name = "V2 앱 · 이번 주", widthDp = 1682, heightDp = 1210)
@Composable fun V2AppWeekPreview() { NamunkiroAppV2(PreviewFixtures.weekState.copy(screen = Screen.WEEK), NoopActions) }

@Preview(name = "V2 앱 · 예측", widthDp = 1682, heightDp = 1210)
@Composable fun V2AppForecastPreview() { NamunkiroAppV2(PreviewFixtures.home.copy(screen = Screen.FORECAST), NoopActions) }

@Preview(name = "V2 앱 · 출구", widthDp = 1682, heightDp = 1210)
@Composable fun V2AppExitPreview() { NamunkiroAppV2(PreviewFixtures.exit, NoopActions) }

@Preview(name = "V2 앱 · 계약", widthDp = 1682, heightDp = 1210)
@Composable fun V2AppContractPreview() { NamunkiroAppV2(PreviewFixtures.home.copy(screen = Screen.CONTRACT), NoopActions) }

@Preview(name = "V2 앱 · 온보딩", widthDp = 1682, heightDp = 1210)
@Composable fun V2AppOnboardingPreview() { NamunkiroAppV2(PreviewFixtures.onboarding, NoopActions) }

@Preview(name = "V2 앱 · 주행 잠금", widthDp = 1682, heightDp = 1210)
@Composable fun V2AppDrivingPreview() { NamunkiroAppV2(PreviewFixtures.home.copy(gate = Gate.DRIVING_LOCK), NoopActions) }

@Preview(name = "V2 앱 · 이상값 보류", widthDp = 1682, heightDp = 1210)
@Composable fun V2AppAnomalyPreview() { NamunkiroAppV2(PreviewFixtures.anomaly, NoopActions) }
