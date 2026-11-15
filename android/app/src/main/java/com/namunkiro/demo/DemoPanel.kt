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
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    fun naviDestination(set: Boolean) {}
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
                Text("시연 설정 [모의]", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 32.sp))
                val person = PersonaId.entries.firstOrNull { it.name == state.personaId }?.label?.substringBefore(" · ") ?: "선택 전"
                val gear = when (state.gear) { Gear.PARK -> "정차"; Gear.DRIVE -> "주행"; Gear.REVERSE -> "후진"; Gear.NEUTRAL -> "중립"; else -> "확인 중" }
                val restricted = when (state.uxRestricted) { true -> "켜짐"; false -> "꺼짐"; null -> "확인 중" }
                Text("$person · ${state.now?.format(DateTimeFormatter.ofPattern("yyyy.MM.dd a h:mm", Locale.KOREAN)) ?: "시각 확인 중"}", fontSize = 24.sp)
                Text("${if (state.usingFake) "모의 차량" else "실제 차량"} · 계기판 ${state.vehicleKm?.let { "%,.1f km".format(Locale.KOREA, it) } ?: "미수신"} · $gear · 주행 제한 $restricted", fontSize = 24.sp)
                state.vehicleError?.let { Text("차량 연결을 확인해 주세요", fontSize = 24.sp) }

                HorizontalDivider()
                Text("예시 인물", style = MaterialTheme.typography.titleMedium.copy(fontSize = 24.sp))
                Buttons { PersonaId.entries.forEach { p -> Btn(p.label) { actions.applyPersona(p) } } }

                Text("에뮬레이터 계기판", style = MaterialTheme.typography.titleMedium.copy(fontSize = 24.sp))
                Buttons { Btn("에뮬레이터 계기판과 동기화", primary = true) { actions.sync() } }

                Text("예시 날짜 이동", style = MaterialTheme.typography.titleMedium.copy(fontSize = 24.sp))
                Buttons {
                    Btn("−1일") { actions.shiftDays(-1) }
                    Btn("+1일") { actions.shiftDays(1) }
                    Btn("+7일") { actions.shiftDays(7) }
                }

                Text("차량 값 연결", style = MaterialTheme.typography.titleMedium.copy(fontSize = 24.sp))
                Buttons {
                    Btn("실제 차량", primary = !state.usingFake) { actions.useFake(false) }
                    Btn("모의 차량", primary = state.usingFake) { actions.useFake(true) }
                }
                if (state.usingFake) Buttons {
                    Btn("+12 km") { actions.fakeAddKm(12.0) }
                    Btn("+50 km") { actions.fakeAddKm(50.0) }
                    Btn("+580 km") { actions.fakeAddKm(580.0) }
                    Btn("기어 P") { actions.fakeGear(Gear.PARK) }
                    Btn("기어 D") { actions.fakeGear(Gear.DRIVE) }
                    Btn("주행 제한 켜기") { actions.fakeUx(true) }
                    Btn("주행 제한 끄기") { actions.fakeUx(false) }
                }
                Text("예시 목적지 [모의]", style = MaterialTheme.typography.titleMedium.copy(fontSize = 24.sp))
                Buttons {
                    Btn("${PersonaSeeds.DEMO_NAVI_DESTINATION} 편도 ${PersonaSeeds.DEMO_NAVI_ONE_WAY_KM.toInt()} km 설정") { actions.naviDestination(true) }
                    Btn("목적지 해제") { actions.naviDestination(false) }
                }

                HorizontalDivider()
                Buttons {
                    Btn("내 데이터로 돌아가기") { actions.reset() }
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
    val m = Modifier.heightIn(min = 76.dp)
    if (primary) Button(onClick, m) { Text(text, fontSize = 24.sp) } else OutlinedButton(onClick, m) { Text(text, fontSize = 24.sp) }
}
