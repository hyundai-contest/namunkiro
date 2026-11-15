package com.namunkiro.ui.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.YearMonth

/** 앱 글자·76dp 터치 크기를 유지하는 날짜 선택기. 날짜를 직접 타이핑하지 않는다. */
@Composable fun V2DatePicker(initial: LocalDate, onDismiss: () -> Unit, onSelect: (LocalDate) -> Unit,
    minimum: LocalDate? = null, maximum: LocalDate? = null) {
    var selected by rememberSaveable { mutableStateOf(initial.toString()) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(initial).toString()) }
    val shown = YearMonth.parse(month)
    val picked = LocalDate.parse(selected)
    fun valid(date: LocalDate) = (minimum == null || !date.isBefore(minimum)) && (maximum == null || !date.isAfter(maximum))
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().widthIn(max = 1000.dp).padding(24.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                V2Title("날짜 선택 · ${v2CalendarDate(picked)}")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    V2Button("−1년", { month = shown.minusYears(1).toString() }, Modifier.weight(1f))
                    V2Button("${shown.year}년 ${shown.monthValue}월", {}, Modifier.weight(2f), enabled = false)
                    V2Button("+1년", { month = shown.plusYears(1).toString() }, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    V2Button("이전 달", { month = shown.minusMonths(1).toString() }, Modifier.weight(1f))
                    V2Button("다음 달", { month = shown.plusMonths(1).toString() }, Modifier.weight(1f))
                }
                Row { listOf("월", "화", "수", "목", "금", "토", "일").forEach { V2Caption(it, Modifier.weight(1f)) } }
                val offset = shown.atDay(1).dayOfWeek.value - 1
                (0 until (offset + shown.lengthOfMonth() + 6) / 7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0..6).forEach { day ->
                            val n = week * 7 + day - offset + 1
                            if (n in 1..shown.lengthOfMonth()) {
                                val date = shown.atDay(n)
                                V2Button(n.toString(), { selected = date.toString() }, Modifier.weight(1f), primary = date == picked, enabled = valid(date))
                            } else Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    V2Button("취소", onDismiss, Modifier.weight(1f))
                    V2Button("날짜 선택", { onSelect(picked); onDismiss() }, Modifier.weight(1f), primary = true, enabled = valid(picked))
                }
            }
        }
    }
}
