package com.namunkiro.core.result

/** 화면 배지와 1:1로 대응하는 값의 출처. */
enum class Source {
    OBSERVED,    // 관측: 차량 SDK에서 수신
    INPUT,       // 입력: 사용자가 계약서 등으로 확인
    ESTIMATED,   // 추정: 앱의 계산·예측
    UNCONFIRMED, // 미확인: 값이 없거나 확인 전
    SIMULATED,   // 모의: 데모 시드·가짜 게이트웨이
}

/** 출처가 붙은 값. */
data class Sourced<T>(val value: T, val source: Source)
