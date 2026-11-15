package com.namunkiro.ui.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.namunkiro.ui.components.km
import com.namunkiro.ui.components.number
import com.namunkiro.ui.preview.PreviewFixtures
import com.namunkiro.ui.state.*
import com.namunkiro.ui.theme.NamunkiroTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WhySheetV2(state: AppUiState, actions: AppActions, topic: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            V2Title("숫자 뒤의 근거")
            val c = state.contract
            val s = state.budget
            when (topic) {
                "home" -> {
                    V2Caption("만기 예상 거리 = 지금까지 사용 + 주간 페이스 × 남은 주")
                    if (s != null && c != null) {
                        V2Caption("${km(s.usedKm)} + 주 ${km(CostModel.currentWeeklyPace(s))} × ${number(CostModel.remainingWeeks(s))}주 = ${km(s.projectedEndKm)}")
                        V2Caption("거리 정산 = max(0, 만기 예상 − 약정 − 확인된 공제) × 단가")
                        V2Caption("${number(CostModel.heroFee(s, c))}원 · 공제 ${c.deductKm?.let { "$it km" } ?: "미확인"}")
                    }
                    V2Caption("계약 시작 이후 평균 페이스가 남은 기간에도 이어진다고 가정해요. 새 주행의 한계 비용과 평균 변화에 따른 예상액은 달라요.")
                }
                "week" -> {
                    V2Caption("이번 주 예상 = 오늘까지 관측 주행 + 남은 날 평소 주행 + 수락한 예정 이동")
                    V2Caption("긴 이동은 평소 대비 추가 거리로 비교해요. 비용은 같은 만기 거리 정산 모델로 계산하며, 계획에서 뺀 이동은 제외해요.")
                    s?.let { V2Caption("이번 주 관측 ${km(it.thisWeek.usedKm)} · 주간 목표 ${km(it.requiredWeeklyKm)}") }
                }
                "forecast" -> {
                    V2Caption("범위 양 끝은 최근 주행 습관과 계약 평균 등 서로 다른 가정이에요. 통계적 신뢰구간이 아니에요.")
                    V2Caption("생활 변화는 적용 기간의 추가 거리를 만기 예상에 더하고, 원 금액은 같은 거리 정산식으로 구해요.")
                    state.forecast?.let { V2Caption("만기 예상 ${km(it.endKmLow)}~${km(it.endKmHigh)}") }
                }
                "exit" -> {
                    V2Caption("만기까지 타는 선택과 지금 나가는 선택은 포함 비용이 달라 독립 축으로 보여요. 두 묶음을 비교하려면 남은 월 이용료와 대체 이동비를 함께 확인해 주세요.")
                    V2Caption("▧ 최소 금액은 확인된 항목만 더한 값이에요. 미확인 비용이 추가되면 순서가 바뀔 수 있어요.")
                    state.exit?.options?.forEach { option ->
                        V2Title(exitLabel(option.kind))
                        option.lines.forEach { (label, amount) -> V2Caption("$label · ${amount?.let { "${number(it)}원" } ?: "미확인"}") }
                        option.cashNeeded?.let { V2Caption("그날 준비할 돈 ${v2Money(it)}") }
                        if (option.unconfirmed.isNotEmpty()) V2Caption("미확인 · ${option.unconfirmed.joinToString(" · ")}")
                    }
                }
                else -> {
                    V2Caption("확인됨은 계약서나 입력으로 확인한 조건이에요. 미확인은 0이나 면제로 단정하지 않아요.")
                    V2Caption("공제는 약정거리에 더해 정산해요. 금융리스·거리 무제한은 거리 초과 정산을 적용하지 않아요.")
                    V2Caption("보험 구간은 보험 기간·계기판 기준으로 따로 계산해요. 덜 탄 거리 환급은 계약서에 명시됐는지 확인해야 해요.")
                }
            }
            V2Caption(if (state.demoMode || state.isSimulated()) "[모의] 예시 계약·시드 이력 또는 모의 차량 값이 포함돼 있어요. 실제 차량 관측과 구분해 주세요." else "출처 · 차량 관측 + 입력 계약 + 주행 가정에 따른 추정. 미래 금액은 확정 청구액이 아니에요.")
            V2Button("닫기", onDismiss, Modifier.fillMaxWidth(), primary = true)
        }
    }
}

@Preview(widthDp = 840, heightDp = 1210)
@Composable private fun WhyV2Preview() { NamunkiroTheme { WhySheetV2(PreviewFixtures.exit, NoopActions, "exit") {} } }
