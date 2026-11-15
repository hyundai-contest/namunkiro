package com.namunkiro.app

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.namunkiro.ui.v2.NamunkiroAppV2
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
            override fun naviDestination(set: Boolean) = vm.demoNaviDestination(set)
            override fun reset() = vm.demoReset()
        }
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            var panelOpen by rememberSaveable { mutableStateOf(false) }
            val openPanel = remember { { panelOpen = true } }
            val inputError by vm.inputError.collectAsStateWithLifecycle()
            androidx.compose.runtime.CompositionLocalProvider(
                com.namunkiro.ui.v2.LocalDemoSettings provides if (BuildConfig.DEMO_PANEL) openPanel else null,
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (state.gate != Gate.DRIVING_LOCK) inputError?.let {
                        Text(it, Modifier.padding(16.dp), fontSize = 24.sp)
                    }
                    Box(Modifier.weight(1f)) { NamunkiroAppV2(state, vm) }
                }
            }
            if (panelOpen && state.gate != Gate.DRIVING_LOCK) {
                val panel by vm.demoPanel.collectAsStateWithLifecycle()
                com.namunkiro.ui.theme.NamunkiroTheme { DemoPanel(panel, demoActions) { panelOpen = false } }
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
