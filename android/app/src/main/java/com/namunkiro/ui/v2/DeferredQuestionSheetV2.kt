package com.namunkiro.ui.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.namunkiro.domain.model.ContractType
import com.namunkiro.ui.preview.PreviewFixtures
import com.namunkiro.ui.state.*
import com.namunkiro.ui.theme.NamunkiroTheme

@Composable fun DeferredQuestionSheetV2(state: AppUiState, actions: AppActions) {
    state.deferredQuestion?.let { question -> QuestionInputV2(question, actions) {} }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun QuestionInputV2(question: DeferredQuestion, actions: AppActions, onClose: () -> Unit) {
    var value by rememberSaveable(question.kind) { mutableStateOf("") }
    var second by rememberSaveable(question.kind) { mutableStateOf("") }
    val kind = question.kind
    val number = value.toLongOrNull()
    val valid = when (kind) {
        DeferredQuestionKind.DEDUCT_KM -> number != null && number in 0..1_000_000
        DeferredQuestionKind.BUYOUT_AND_MARKET -> number != null && number >= 0 && second.toLongOrNull()?.let { it >= 0 } == true
        DeferredQuestionKind.CONTRACT_TYPE -> ContractType.entries.any { it.name == value }
        DeferredQuestionKind.TERMINATION_TIERS -> validTerminationTiers(value)
    }
    val dismiss = { actions.answerDeferred(kind, null); onClose() }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            V2Title(question.title)
            V2Caption(question.reason)
            when (kind) {
                DeferredQuestionKind.DEDUCT_KM -> { V2Input(value, { value = it }, "공제 거리 (km)"); V2Caption("공제 없음이 확인되면 0을 입력해 주세요.") }
                DeferredQuestionKind.BUYOUT_AND_MARKET -> {
                    V2Input(value, { value = it }, "만기 인수가 (원)")
                    V2Input(second, { second = it }, "예상 중고차 가치 (원)")
                }
                DeferredQuestionKind.TERMINATION_TIERS -> {
                    V2Input(value, { value = it }, "개월:요율%;개월:요율%", numeric = false)
                    V2Caption("예: 12:35%;24:30% · 계약서의 구간별 요율")
                }
                DeferredQuestionKind.CONTRACT_TYPE -> listOf(ContractType.LONG_TERM_RENT to "장기렌트", ContractType.OPERATING_LEASE to "운용리스", ContractType.FINANCE_LEASE to "금융리스", ContractType.UNLIMITED to "거리 무제한").forEach { (type, label) ->
                    V2Button(if (value == type.name) "✓ $label · 선택됨" else label, { value = type.name }, Modifier.fillMaxWidth(), primary = value == type.name)
                }
            }
            if (value.isNotEmpty() && !valid) V2Caption("입력 범위와 형식을 확인해 주세요.", color = MaterialTheme.colorScheme.error)
            V2Button("적용", {
                val answer = when (kind) {
                    DeferredQuestionKind.BUYOUT_AND_MARKET -> "$value,$second"
                    // ViewModel은 1 이하를 비율로 해석하므로, 1%도 0.01로 보낸다.
                    DeferredQuestionKind.TERMINATION_TIERS -> value.split(';').joinToString(";") { segment ->
                        val parts = segment.trim().split(':')
                        "${parts[0]}:${parts[1].removeSuffix("%").toDouble() / 100.0}"
                    }
                    else -> value
                }
                actions.answerDeferred(kind, answer); onClose()
            }, Modifier.fillMaxWidth(), primary = true, enabled = valid)
            V2Button("나중에", dismiss, Modifier.fillMaxWidth())
        }
    }
}

private fun validTerminationTiers(value: String): Boolean {
    val segments = value.split(';')
    return value.isNotBlank() && segments.all { segment ->
        val parts = segment.trim().split(':')
        parts.size == 2 && parts[0].toIntOrNull()?.let { it in 1..240 } == true &&
            parts[1].removeSuffix("%").toDoubleOrNull()?.let { it.isFinite() && it in 0.0..100.0 } == true
    }
}

@Preview(widthDp = 840, heightDp = 1210)
@Composable private fun DeferredV2Preview() { NamunkiroTheme { DeferredQuestionSheetV2(PreviewFixtures.home.copy(deferredQuestion = DeferredQuestion(DeferredQuestionKind.DEDUCT_KM, "공제 거리가 있나요?", "확인한 공제만 정산에 반영해요.")), NoopActions) } }
