package com.namunkiro.ui.v2

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.namunkiro.app.AppPolicies
import com.namunkiro.ui.preview.PreviewFixtures
import com.namunkiro.ui.state.*
import com.namunkiro.ui.theme.NamunkiroTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 유효 입력은 기존 초안 저장 액션에 즉시 보내 재실행 때 복원한다. */
@Composable fun OnboardingScreenV2(state: AppUiState, actions: AppActions) {
    val draft = state.onboarding
    var started by rememberSaveable { mutableStateOf(draft.step > 0 || draft.endDate != null || draft.totalKm != null || draft.unitPricePerKm != null) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val today = state.today
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        V2Caption("장기렌트·리스 약정거리를 매주 목표와 만기 비용으로 보여 줘요")
        if (!started) BoxWithConstraints {
            V2Columns(v2Compact(maxWidth), left = {
                V2Button("▶ 예시로 둘러보기 [모의]\n김 대리 · 만기 약 24만 원", actions::enterDemo, Modifier.fillMaxWidth().heightIn(min = 120.dp), primary = true)
            }, right = {
                V2Button("내 계약으로 시작\n3가지 · 30초", { started = true }, Modifier.fillMaxWidth().heightIn(min = 120.dp))
            })
        }
        if (started) V2Panel {
            V2Title("내 계약 · ${draft.step + 1}/3")
            val valid: Boolean
            when (draft.step) {
                0 -> {
                    val date = draft.endDate
                    valid = AppPolicies.validEndDate(date, today)
                    V2Caption("계약 만기일")
                    V2Button(date?.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) ?: "날짜 선택", {
                        datePicker = true
                    }, Modifier.fillMaxWidth())
                    if (!valid) V2Caption("내일부터 20년 이내 날짜를 선택해 주세요.")
                }
                1 -> {
                    var text by rememberSaveable { mutableStateOf(draft.totalKm?.toString().orEmpty()) }
                    val value = text.toIntOrNull()
                    valid = AppPolicies.validTotalKm(value)
                    V2Input(text, { text = it; it.toIntOrNull()?.takeIf { n -> n in 1..1_000_000 }?.let(actions::onboardingSetTotalKm) }, "총 약정거리 (km)", !valid && text.isNotEmpty())
                    V2Caption("계약 전체 거리 · 1~1,000,000 km")
                }
                else -> {
                    var text by rememberSaveable { mutableStateOf(draft.unitPricePerKm?.toString().orEmpty()) }
                    val value = text.toIntOrNull()
                    valid = AppPolicies.validUnitPrice(value)
                    V2Input(text, { text = it; it.toIntOrNull()?.takeIf { n -> n in 1..100_000 }?.let(actions::onboardingSetUnitPrice) }, "초과 단가 (원/km)", !valid && text.isNotEmpty())
                    V2Caption("1 km당 1~100,000원")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                V2Button("이전", { if (draft.step == 0) started = false else actions.onboardingBack() })
                V2Button(if (draft.step == 2) "내 계약 시작" else "다음", { if (draft.step == 2) actions.onboardingFinish() else actions.onboardingNext() }, Modifier.weight(1f), primary = true, enabled = valid)
            }
        }
    }
    if (datePicker) V2DatePicker(draft.endDate ?: today.plusYears(3), { datePicker = false }, actions::onboardingSetEndDate, today.plusDays(1), today.plusYears(20))
}

@Composable internal fun V2Input(value: String, onValueChange: (String) -> Unit, label: String, error: Boolean = false, numeric: Boolean = true) {
    OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth().heightIn(min = 76.dp), label = { Text(label, fontSize = 24.sp) }, textStyle = TextStyle(fontSize = 28.sp, fontFeatureSettings = "tnum"), isError = error, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text))
}

@Preview(widthDp = 840, heightDp = 1210)
@Composable private fun OnboardingV2Preview() { NamunkiroTheme { OnboardingScreenV2(PreviewFixtures.onboarding, NoopActions) } }
