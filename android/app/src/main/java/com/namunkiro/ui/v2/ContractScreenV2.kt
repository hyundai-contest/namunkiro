package com.namunkiro.ui.v2

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.namunkiro.domain.model.ContractType
import com.namunkiro.ui.components.km
import com.namunkiro.ui.preview.PreviewFixtures
import com.namunkiro.ui.state.*
import com.namunkiro.ui.theme.NamunkiroTheme
import java.time.format.DateTimeFormatter

@Composable fun ContractScreenV2(state: AppUiState, actions: AppActions) {
    val c = state.contract ?: return
    var edit by rememberSaveable { mutableStateOf<String?>(null) }
    var insuranceEdit by rememberSaveable { mutableStateOf(false) }
    val rows = listOf(
        Triple("만기일", v2CalendarDate(c.endDate), ContractField.END_DATE),
        Triple("총 약정거리", km(c.totalKm), ContractField.TOTAL_KM),
        Triple("초과 단가", "${c.unitPricePerKm}원/km", ContractField.UNIT_PRICE),
        Triple("계약 유형", when (c.type) { ContractType.LONG_TERM_RENT -> "장기렌트"; ContractType.OPERATING_LEASE -> "운용리스"; ContractType.FINANCE_LEASE -> "금융리스"; ContractType.UNLIMITED -> "거리 무제한"; else -> null }, ContractField.TYPE),
        Triple("공제 거리", c.deductKm?.let(::km), ContractField.DEDUCT_KM),
        Triple("만기 인수가", c.buyoutPrice?.let(::v2Money), ContractField.BUYOUT_PRICE),
        Triple("만기 인수 시 거리 정산 면제", c.buyoutWaivesDistanceFee?.let { if (it) "면제 확인됨" else "면제 없음" }, ContractField.BUYOUT_WAIVES_DISTANCE_FEE),
        Triple("덜 탄 거리 환급", c.underuseRefund?.let { if (it) "환급 조건 확인됨" else "환급 없음" }, ContractField.UNDERUSE_REFUND),
    )
    V2Panel {
        V2Title("확인한 조건만 채워요")
        BoxWithConstraints {
            val compact = v2Compact(maxWidth)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                rows.chunked(if (compact) 1 else 2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        pair.forEach { (label, value, field) ->
                            V2Button("${if (value != null) "✓ 확인됨" else "○ 미확인"} · $label\n${value ?: "확인하면 더 정확해요"}", {
                                if (field == ContractField.TYPE) actions.openDeferredQuestion(DeferredQuestionKind.CONTRACT_TYPE) else edit = field.name
                            }, Modifier.weight(1f).heightIn(min = 120.dp))
                        }
                        if (!compact && pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        V2Button("예상 중고차 가치 · ${state.quotes.expectedMarketPrice?.let(::v2Money) ?: "미확인"}", { actions.openDeferredQuestion(DeferredQuestionKind.BUYOUT_AND_MARKET) }, Modifier.fillMaxWidth())
        V2Button("해지 요율 · ${c.terminationTiers?.let { "${it.size}개 구간 확인됨" } ?: "미확인"}", { actions.openDeferredQuestion(DeferredQuestionKind.TERMINATION_TIERS) }, Modifier.fillMaxWidth())
        V2Caption("보험 마일리지 모드 · ${if (state.insurancePlan != null) "켜짐" else "꺼짐"}")
        V2Button(if (state.insurancePlan == null) "보험 모드 켜기 · 조건 입력" else "보험 조건 수정", { insuranceEdit = true }, Modifier.fillMaxWidth(), primary = true)
        if (state.insurancePlan != null) V2Button("보험 모드 끄기", { actions.setInsurancePlan(null) }, Modifier.fillMaxWidth())
        state.insurance?.tiers?.forEach { tier -> V2Chip("${km(tier.tierKm)} 구간 유지 · 목표 주 ${km(tier.requiredWeeklyKm)}") }
    }
    edit?.let { ContractFieldEditorV2(state, actions, ContractField.valueOf(it)) { edit = null } }
    if (insuranceEdit) InsurancePlanEditorV2(state, actions) { insuranceEdit = false }
}

@Preview(widthDp = 840, heightDp = 1210)
@Composable private fun ContractV2Preview() { NamunkiroTheme { ContractScreenV2(PreviewFixtures.insurance, NoopActions) } }
