package com.namunkiro.ui.v2

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 앱의 한국어 날짜·시각은 기기 Locale에 따라 ISO 원문으로 바뀌지 않는다. */
fun v2CalendarDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREAN))
fun v2ReceivedTime(time: LocalDateTime): String = time.format(DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN))
