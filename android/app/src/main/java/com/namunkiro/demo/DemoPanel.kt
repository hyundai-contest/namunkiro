package com.namunkiro.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.namunkiro.app.DemoPanelState
import com.namunkiro.vehicle.Gear

/** 데모 패널 동작. 구현은 AppViewModel. */
interface DemoPanelActions {
    fun applyPersona(id: PersonaId)
    fun sync()
    fun shiftDays(days: Long)
    fun useFake(fake: Boolean)
    fun fakeAddKm(km: Double)
    fun fakeGear(g: Gear)
    fun fakeUx(restricted: Boolean)
    fun reset()
}

/**
 * 시연 준비용 패널(debug·demo 빌드에서 앱 제목 길게 누르기). 화면의 [모의] 배지와 함께 쓴다.
 * 주행 중 잠금(DRIVING_LOCK)일 때는 열지 않는다(호출부에서 처리).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoPanel(state: DemoPanelState, actions: DemoPanelActions, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 1100.dp).padding(24.dp), shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("데모 패널 [모의]", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "페르소나 ${state.personaId ?: "없음"} · 데모 시각 ${state.now ?: "-"}${if (state.clockShifted) " (이동됨)" else ""}\n" +
                        "${if (state.usingFake) "가짜 게이트웨이" else "실제 SDK"} · 차량 계기판 ${state.vehicleKm?.let { "%,.1f km".format(it) } ?: "미수신"}" +
                        " · 승인값 ${state.acceptedKm?.let { "%,.1f km".format(it) } ?: "-"} · 기어 ${state.gear} · UX 제한 ${state.uxRestricted}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                state.vehicleError?.let { Text("차량 오류: $it", style = MaterialTheme.typography.bodyMedium) }
                state.message?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }

                HorizontalDivider()
                Text("페르소나 시드", style = MaterialTheme.typography.titleMedium)
                Buttons { PersonaId.entries.forEach { p -> Btn(p.label) { actions.applyPersona(p) } } }

                Text("에뮬레이터 계기판", style = MaterialTheme.typography.titleMedium)
                Buttons { Btn("에뮬레이터 계기판과 동기화", primary = true) { actions.sync() } }

                Text("DemoClock 날짜", style = MaterialTheme.typography.titleMedium)
                Buttons {
                    Btn("−1일") { actions.shiftDays(-1) }
                    Btn("+1일") { actions.shiftDays(1) }
                    Btn("+7일") { actions.shiftDays(7) }
                }

                Text("게이트웨이", style = MaterialTheme.typography.titleMedium)
                Buttons {
                    Btn("실제 SDK", primary = !state.usingFake) { actions.useFake(false) }
                    Btn("가짜(값 주입)", primary = state.usingFake) { actions.useFake(true) }
                }
                if (state.usingFake) Buttons {
                    Btn("+12 km") { actions.fakeAddKm(12.0) }
                    Btn("+50 km") { actions.fakeAddKm(50.0) }
                    Btn("+580 km") { actions.fakeAddKm(580.0) }
                    Btn("기어 P") { actions.fakeGear(Gear.PARK) }
                    Btn("기어 D") { actions.fakeGear(Gear.DRIVE) }
                    Btn("UX 제한 켜기") { actions.fakeUx(true) }
                    Btn("UX 제한 끄기") { actions.fakeUx(false) }
                }

                HorizontalDivider()
                Buttons {
                    Btn("데이터 초기화(데모 종료)") { actions.reset() }
                    Btn("닫기", primary = true, onClick = onClose)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Buttons(content: @Composable () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
private fun Btn(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val m = Modifier.heightIn(min = 64.dp)
    if (primary) Button(onClick, m) { Text(text) } else OutlinedButton(onClick, m) { Text(text) }
}
