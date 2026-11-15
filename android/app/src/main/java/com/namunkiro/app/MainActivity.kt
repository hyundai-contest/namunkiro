package com.namunkiro.app

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.namunkiro.BuildConfig
import com.namunkiro.demo.DemoPanel
import com.namunkiro.demo.DemoPanelActions
import com.namunkiro.demo.PersonaId
import com.namunkiro.ui.NamunkiroApp
import com.namunkiro.ui.state.Gate
import com.namunkiro.vehicle.Gear

/**
 * 진입점. 포그라운드 진입마다 세션 시작(startSession → gateway.start)으로 공백 거리를 회수한다.
 * 재시작 후 데모 상태 복원(restoreAfterRestart)은 AppViewModel 생성 시 처리.
 */
class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val demoActions = object : DemoPanelActions {
            override fun applyPersona(id: PersonaId) = vm.demoApplyPersona(id)
            override fun sync() = vm.demoSync()
            override fun shiftDays(days: Long) = vm.demoShiftDays(days)
            override fun useFake(fake: Boolean) = vm.demoUseFake(fake)
            override fun fakeAddKm(km: Double) = vm.fakeAddKm(km)
            override fun fakeGear(g: Gear) = vm.fakeSetGear(g)
            override fun fakeUx(restricted: Boolean) = vm.fakeSetUx(restricted)
            override fun reset() = vm.demoReset()
        }
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            var panelOpen by rememberSaveable { mutableStateOf(false) }
            val openPanel = remember { { panelOpen = true } }
            val panel by vm.demoPanel.collectAsStateWithLifecycle()
            val inputError by vm.inputError.collectAsStateWithLifecycle()
            var confirmRebase by rememberSaveable { mutableStateOf(false) }
            val missingSignal = !panel.usingFake && panel.gear == Gear.UNKNOWN && panel.uxRestricted != false
            Column(Modifier.fillMaxSize()) {
                if (state.gate != Gate.DRIVING_LOCK) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (BuildConfig.DEMO_PANEL) {
                            Button(onClick = { if (state.demoMode) vm.demoReset() else vm.enterDemo() }) {
                                Text(if (state.demoMode) "내 데이터로 돌아가기 · 현재 [모의]" else "시연 데이터로 둘러보기")
                            }
                            TextButton(onClick = openPanel) { Text("시연 설정") }
                        }
                        if (state.gate == Gate.ANOMALY_HOLD) Button(onClick = { confirmRebase = true }) { Text("계기판 기준 다시 설정") }
                    }
                    if (missingSignal) {
                        Text("차량 신호 확인 중 · 정차(P) 상태와 차량 연결을 확인해 주세요", Modifier.padding(20.dp))
                        Button(onClick = vm::retryOdometer, modifier = Modifier.padding(horizontal = 20.dp)) { Text("차량 신호 다시 확인") }
                    } else {
                        inputError?.let { Text(it, Modifier.padding(16.dp)) }
                        if (state.contract != null && !state.demoMode && state.budget?.elapsedDays == 0)
                            Text("오늘부터 기록을 모아요 · 첫날 예상 비용은 아직 확정할 수 없어요", Modifier.padding(16.dp))
                        Box(Modifier.weight(1f)) { NamunkiroApp(state, vm, onTitleLongPress = if (BuildConfig.DEMO_PANEL) openPanel else null) }
                    }
                } else Box(Modifier.weight(1f)) { NamunkiroApp(state, vm) }
            }
            if (confirmRebase && state.gate == Gate.ANOMALY_HOLD) AlertDialog(
                onDismissRequest = { confirmRebase = false }, title = { Text("현재 계기판을 새 기준으로 설정할까요?") },
                text = { Text("보류된 차이는 주행 기록에 더하지 않고 기존 사용 거리와 이력을 유지합니다. 차량이나 에뮬레이터의 계기판 기준이 바뀐 경우에만 선택해 주세요.") },
                confirmButton = { TextButton(onClick = { vm.rebaseOdometer(); confirmRebase = false }) { Text("새 기준 설정") } },
                dismissButton = { TextButton(onClick = { confirmRebase = false }) { Text("취소") } })
            if (panelOpen && state.gate != Gate.DRIVING_LOCK) {
                val panel by vm.demoPanel.collectAsStateWithLifecycle()
                DemoPanel(panel, demoActions) { panelOpen = false }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        vm.onBackground()
        super.onStop()
    }
}
