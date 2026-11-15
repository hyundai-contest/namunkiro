package com.namunkiro.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.namunkiro.core.result.Source
import com.namunkiro.ui.components.*
import com.namunkiro.ui.screens.*
import com.namunkiro.ui.state.*
import com.namunkiro.ui.theme.*

/** 상태는 상위 ViewModel 소유, 화면은 액션을 전달하는 역할만 한다. */
@Composable fun NamunkiroApp(state: AppUiState, actions: AppActions, onTitleLongPress: (() -> Unit)? = null) {
    NamunkiroTheme {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), color = MaterialTheme.colorScheme.background) {
            if (state.gate == Gate.DRIVING_LOCK) DrivingLockScreen()
            else BoxWithConstraints(Modifier.fillMaxSize()) {
                val compact = maxWidth < Dimens.CompactBreakpoint
                Column(Modifier.fillMaxSize().padding(horizontal = if (compact) Dimens.CompactPage else Dimens.Page, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("남은키로", Modifier.weight(1f).then(if (onTitleLongPress != null) Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onTitleLongPress() }) } else Modifier), style = MaterialTheme.typography.titleLarge)
                        if (state.historySimulated || state.demoMode) SourceBadge(Source.SIMULATED)
                    }
                    if (state.gate == Gate.NORMAL && state.contract != null) Tabs(state.screen, actions::navigate)
                    state.banner?.let { banner ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(banner, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            AppButton("닫기", actions::dismissBanner)
                        }
                    }
                    // 화면마다 독립 스크롤을 보존하며 작은 창에서도 모든 동작에 접근한다.
                    androidx.compose.runtime.key(state.screen, state.gate, state.contract == null) {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            when {
                                state.gate == Gate.ANOMALY_HOLD -> AnomalyHoldScreen(state, actions)
                                state.contract == null -> OnboardingScreen(state, actions)
                                else -> when (state.screen) {
                                    Screen.HOME -> HomeScreen(state, actions)
                                    Screen.WEEK -> WeekScreen(state, actions)
                                    Screen.FORECAST -> ForecastScreen(state, actions)
                                    Screen.EXIT -> ExitScreen(state, actions)
                                    Screen.CONTRACT -> ContractScreen(state, actions)
                                    Screen.INSURANCE -> InsuranceScreen(state, actions)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
                if (state.gate == Gate.NORMAL && state.contract != null) state.deferredQuestion?.let { DeferredQuestionSheet(it, actions) }
            }
        }
    }
}
