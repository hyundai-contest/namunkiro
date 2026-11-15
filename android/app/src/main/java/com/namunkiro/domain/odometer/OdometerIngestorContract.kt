package com.namunkiro.domain.odometer

/** 수신값 판정 결과(DEV_PLAN 3.1). */
enum class IngestStatus { ACCEPTED, ACCEPTED_GAP, HELD_SPIKE, HELD_REVERSE, REJECTED_INVALID }

data class IngestResult(
    val status: IngestStatus,
    val acceptedKm: Double?,      // 승인 후 최신 승인값
    val deltaKm: Double,          // 직전 승인값 대비 증가(보류·거부면 0)
    val heldRawKm: Double? = null,
)
