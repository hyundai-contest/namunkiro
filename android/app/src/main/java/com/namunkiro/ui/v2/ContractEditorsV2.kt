package com.namunkiro.ui.v2

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.namunkiro.app.AppPolicies
import com.namunkiro.domain.model.InsurancePlan
import com.namunkiro.ui.state.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ContractFieldEditorV2(state: AppUiState, actions: AppActions, field: ContractField, onClose: () -> Unit) {
    val c = state.contract ?: return
    val initial = when (field) {
        ContractField.END_DATE -> c.endDate.toString()
        ContractField.TOTAL_KM -> c.totalKm.toString()
        ContractField.UNIT_PRICE -> c.unitPricePerKm.toString()
        ContractField.DEDUCT_KM -> c.deductKm?.toString()
        ContractField.BUYOUT_PRICE -> c.buyoutPrice?.toString()
        ContractField.BUYOUT_WAIVES_DISTANCE_FEE -> c.buyoutWaivesDistanceFee?.toString()
        ContractField.UNDERUSE_REFUND -> c.underuseRefund?.toString()
        ContractField.TYPE -> c.type.name
    }
    var value by rememberSaveable(field) { mutableStateOf(initial.orEmpty()) }
    val title = when (field) {
        ContractField.END_DATE -> "만기일"
        ContractField.TOTAL_KM -> "총 약정거리 (km)"
        ContractField.UNIT_PRICE -> "초과 단가 (원/km)"
        ContractField.DEDUCT_KM -> "공제 거리 (km)"
        ContractField.BUYOUT_PRICE -> "만기 인수가 (원)"
        ContractField.BUYOUT_WAIVES_DISTANCE_FEE -> "만기 인수 시 거리 정산 면제"
        ContractField.UNDERUSE_REFUND -> "덜 탄 거리 환급"
        ContractField.TYPE -> "계약 유형"
    }
    val optional = field in listOf(ContractField.DEDUCT_KM, ContractField.BUYOUT_PRICE, ContractField.BUYOUT_WAIVES_DISTANCE_FEE, ContractField.UNDERUSE_REFUND)
    val answer = value.takeUnless { optional && it.isBlank() }
    val valid = AppPolicies.editContract(c, field, answer, state.today) != null
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            V2Title(title)
            when (field) {
                ContractField.END_DATE -> {
                    DateFieldV2("만기일", LocalDate.parse(value)) { value = it.toString() }
                    V2Caption("내일부터 20년 이내 날짜를 선택해 주세요.")
                }
                ContractField.BUYOUT_WAIVES_DISTANCE_FEE, ContractField.UNDERUSE_REFUND -> {
                    listOf("true" to "있음", "false" to "없음", "" to "미확인").forEach { (v, label) -> V2Button(if (value == v) "✓ $label · 선택됨" else label, { value = v }, Modifier.fillMaxWidth(), primary = value == v) }
                }
                else -> V2Input(value, { value = it }, title)
            }
            if (optional && field !in listOf(ContractField.BUYOUT_WAIVES_DISTANCE_FEE, ContractField.UNDERUSE_REFUND)) V2Button("미확인으로", { value = "" })
            if (!valid) V2Caption("입력 범위를 확인해 주세요.", color = MaterialTheme.colorScheme.error)
            V2Button("저장", { actions.updateContractField(field, answer); onClose() }, Modifier.fillMaxWidth(), primary = true, enabled = valid)
            V2Button("취소", onClose, Modifier.fillMaxWidth())
        }
    }
}

@Composable private fun DateFieldV2(label: String, date: LocalDate, onDate: (LocalDate) -> Unit) {
    var picker by rememberSaveable { mutableStateOf(false) }
    V2Button("$label · ${v2CalendarDate(date)}", { picker = true }, Modifier.fillMaxWidth())
    if (picker) V2DatePicker(date, { picker = false }, onDate)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun InsurancePlanEditorV2(state: AppUiState, actions: AppActions, onClose: () -> Unit) {
    val old = state.insurancePlan
    var start by rememberSaveable { mutableStateOf((old?.periodStart ?: state.today).toString()) }
    var end by rememberSaveable { mutableStateOf((old?.periodEnd ?: state.today.plusYears(1).minusDays(1)).toString()) }
    var odometer by rememberSaveable { mutableStateOf((old?.startOdometerKm ?: state.odometer?.value)?.toString().orEmpty()) }
    var tiers by rememberSaveable { mutableStateOf(old?.tiersKm?.joinToString(",").orEmpty()) }
    val parts = tiers.split(',').map { it.trim().toIntOrNull() }
    val plan = odometer.toDoubleOrNull()?.let { km -> if (parts.any { it == null }) null else InsurancePlan(LocalDate.parse(start), LocalDate.parse(end), km, parts.filterNotNull()) }
    val valid = plan != null && AppPolicies.validInsurancePlan(plan)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            V2Title("보험 마일리지 조건")
            DateFieldV2("보험 시작", LocalDate.parse(start)) { start = it.toString() }
            DateFieldV2("보험 종료", LocalDate.parse(end)) { end = it.toString() }
            V2Input(odometer, { odometer = it }, "보험 시작 계기판 (km)")
            V2Input(tiers, { tiers = it }, "거리 구간 (km) · 예: 10000,12000", numeric = false)
            V2Caption("계약 약정과 별도로 보험 기간의 구간 유지 목표를 계산해요.")
            if (!valid) V2Caption("기간·시작 계기판과 오름차순 거리 구간을 확인해 주세요.")
            V2Button("저장하고 켜기", { actions.setInsurancePlan(plan); onClose() }, Modifier.fillMaxWidth(), primary = true, enabled = valid)
            V2Button("취소", onClose, Modifier.fillMaxWidth())
        }
    }
}
