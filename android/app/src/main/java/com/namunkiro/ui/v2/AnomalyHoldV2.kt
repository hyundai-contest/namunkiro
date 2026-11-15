package com.namunkiro.ui.v2

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.namunkiro.ui.components.km
import com.namunkiro.ui.preview.PreviewFixtures
import com.namunkiro.ui.state.*
import com.namunkiro.ui.theme.NamunkiroTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun AnomalyHoldV2(state: AppUiState, actions: AppActions) {
    val anomaly = state.anomaly ?: return
    var confirm by rememberSaveable { mutableStateOf(false) }
    V2Panel {
        V2Title("계기판 값이 크게 바뀌었어요")
        V2Caption("${anomaly.intervalSec}초 만에 ${km(anomaly.lastAcceptedKm)} → ${km(anomaly.heldRawKm)}")
        V2Chip("주행으로 세지 않았어요")
        V2Caption("이 간격의 허용 증가량 ${String.format(Locale.KOREA, "%.1f", anomaly.allowedKm)} km 이내")
        state.lastReceivedAt?.let { V2Caption("마지막 수신 ${v2ReceivedTime(it)}") }
        V2Button("다시 확인", actions::retryOdometer, Modifier.fillMaxWidth())
        V2Button("${km(anomaly.heldRawKm)}를 새 기준으로", { confirm = true }, Modifier.fillMaxWidth(), primary = true)
        V2Caption("주행으로 더하지 않아요 · 기록 유지")
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { V2Title("새 계기판 기준으로 이어갈까요?") }, text = { V2Caption("${km(anomaly.heldRawKm)}는 주행 거리로 더하지 않아요. 지금까지 기록을 유지하고 앞으로 여기서부터 셉니다.") },
        confirmButton = { V2Button("새 기준 설정", { actions.rebaseOdometer(); confirm = false }, primary = true) }, dismissButton = { V2Button("취소", { confirm = false }) })
}

@Preview(widthDp = 840, heightDp = 1210)
@Composable private fun AnomalyHoldPreview() { NamunkiroTheme { AnomalyHoldV2(PreviewFixtures.anomaly, NoopActions) } }
