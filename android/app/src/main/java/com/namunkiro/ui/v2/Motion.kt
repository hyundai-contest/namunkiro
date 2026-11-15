package com.namunkiro.ui.v2

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.namunkiro.ui.state.Gate

/*
 * 모션 정책(UX_PRINCIPLES §5-1).
 * - 값이 바뀔 때만 움직인다. 첫 표시·복원은 즉시 최종값.
 * - 주행 중(Gate != NORMAL)과 시스템 '애니메이션 줄이기'(ANIMATOR_DURATION_SCALE = 0)는 즉시 최종값.
 * - 길이 250~350ms(기본 300ms), ease-out. 동시에 1개(또는 km → 원 인과 체인 1개).
 */

/** true일 때만 값 전환 애니메이션을 쓴다. 기본값 false = 정책 밖(Preview 등)에서는 움직이지 않는다. */
val LocalMotionEnabled = staticCompositionLocalOf { false }

@Composable fun V2MotionPolicy(gate: Gate, content: @Composable () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    fun readScale() = runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
    var scale by remember { mutableFloatStateOf(readScale()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { scale = readScale() } }
        runCatching { resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer) }
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }
    CompositionLocalProvider(LocalMotionEnabled provides (gate == Gate.NORMAL && scale > 0f), content = content)
}

/** 정책에 맞는 기본 전환 스펙. 꺼져 있으면 snap. */
@Composable fun <T> v2Spec(delayMillis: Int = 0, durationMillis: Int = V2Tokens.MotionMs): AnimationSpec<T> =
    if (LocalMotionEnabled.current) tween(durationMillis, delayMillis, FastOutSlowInEasing) else snap()

data class CausalValues(val usedKm: Double, val endKm: Double, val fee: Long)

/**
 * km → 원 인과 체인 1개. 한 진행률(300ms)로 거리(0–180ms)를 먼저, 원(180–300ms)을 나중에 바꾼다.
 * 도중에 새 값이 오면 지금 보이는 값에서 이어 간다. 정책이 꺼지면 즉시 최종값.
 */
@Composable fun causalValues(target: CausalValues): CausalValues {
    val enabled = LocalMotionEnabled.current
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    fun at(p: Float): CausalValues {
        val distance = (p / .6f).coerceIn(0f, 1f)
        val money = ((p - .6f) / .4f).coerceIn(0f, 1f)
        return CausalValues(
            from.usedKm + (to.usedKm - from.usedKm) * distance,
            from.endKm + (to.endKm - from.endKm) * distance,
            (from.fee + (to.fee - from.fee) * money.toDouble()).toLong(),
        )
    }
    LaunchedEffect(target, enabled) {
        if (!enabled) { from = target; to = target; progress.snapTo(1f); return@LaunchedEffect }
        if (target == to) return@LaunchedEffect
        val shown = at(progress.value)
        progress.snapTo(0f)
        from = shown; to = target
        progress.animateTo(1f, tween(V2Tokens.MotionMs, easing = FastOutSlowInEasing))
    }
    // 효과가 돌기 전 한 프레임 동안 새 값이 먼저 보이지 않도록, 목표가 바뀐 직후에는 이전 값을 유지한다.
    if (!enabled) return target
    return if (target != to && progress.value >= 1f) to else at(progress.value)
}

/**
 * 원 숫자 카운트(체인의 '원' 단계). delayMillis 뒤 durationMillis 동안 이전 값에서 새 값으로 바꾼다.
 * 이번 주 '계획에서 빼기'처럼 막대(300ms)가 먼저 움직이고 금액이 뒤따를 때 쓴다(합계 600ms 이하).
 */
@Composable fun animatedMoney(target: Long, delayMillis: Int = V2Tokens.MotionMs, durationMillis: Int = 200): Long {
    val enabled = LocalMotionEnabled.current
    var from by remember { mutableLongStateOf(target) }
    var to by remember { mutableLongStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target, enabled) {
        if (!enabled) { from = target; to = target; progress.snapTo(1f); return@LaunchedEffect }
        if (target == to) return@LaunchedEffect
        val shown = from + ((to - from) * progress.value.toDouble()).toLong()
        progress.snapTo(0f)
        from = shown; to = target
        progress.animateTo(1f, tween(durationMillis, delayMillis, FastOutSlowInEasing))
    }
    if (!enabled) return target
    return if (target != to && progress.value >= 1f) to else from + ((to - from) * progress.value.toDouble()).toLong()
}
