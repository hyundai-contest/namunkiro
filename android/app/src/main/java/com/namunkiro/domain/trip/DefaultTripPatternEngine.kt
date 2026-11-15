package com.namunkiro.domain.trip

import com.namunkiro.domain.RoutineSlot
import com.namunkiro.domain.TripPattern
import com.namunkiro.domain.TripPatternEngine
import com.namunkiro.domain.model.TripSegment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/**
 * 기어 기반 주행 단위(TripSegment)에서 반복 루틴과 요일별 평소 거리를 찾는다. 순수 Kotlin.
 * - 루틴: 같은 요일 · 시작 시각 ±90분 · 거리 ±25%가 서로 다른 3주 이상 반복.
 * - routineShare: 루틴 거리 ÷ 관측 거리(관측 5회 미만이면 null).
 * - usualKmByDay: 요일별 '하루 관측 주행 합계'의 평균(그 요일 관측 일수 2일 미만이면 제외).
 */
class DefaultTripPatternEngine : TripPatternEngine {

    private class Cluster(first: TripSegment) {
        val members = mutableListOf(first)
        val startMin get() = members.map { minuteOfDay(it) }.average()
        val km get() = members.map { it.km }.average()
        fun accepts(t: TripSegment): Boolean =
            abs(minuteOfDay(t) - startMin) <= TIME_TOLERANCE_MIN &&
                km > 0 && abs(t.km - km) <= km * KM_TOLERANCE
        fun weeks(): Int = members.map { it.start.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toSet().size
    }

    override fun analyze(trips: List<TripSegment>, today: LocalDate): TripPattern {
        val observed = trips.filter { !it.start.toLocalDate().isAfter(today) && it.km > 0 }.sortedBy { it.start }
        val routineClusters = observed.groupBy { it.start.dayOfWeek }.flatMap { (_, dayTrips) ->
            val clusters = mutableListOf<Cluster>()
            for (t in dayTrips) {
                val home = clusters.filter { it.accepts(t) }.minByOrNull { abs(minuteOfDay(t) - it.startMin) }
                if (home != null) home.members += t else clusters += Cluster(t)
            }
            clusters.filter { it.weeks() >= MIN_WEEKS }
        }
        val routines = routineClusters.map { c ->
            RoutineSlot(
                dayOfWeek = c.members.first().start.dayOfWeek,
                startHour = (c.startMin / 60).toInt(),
                typicalKm = median(c.members.map { it.km }),
                occurrences = c.members.size,
            )
        }.sortedWith(compareBy({ it.dayOfWeek }, { it.startHour }))
        val totalKm = observed.sumOf { it.km }
        val routineKm = routineClusters.sumOf { c -> c.members.sumOf { it.km } }
        val share = if (observed.size < MIN_TRIPS_FOR_SHARE || totalKm <= 0) null else routineKm / totalKm
        val usual = observed.groupBy { it.start.toLocalDate() }
            .mapValues { (_, list) -> list.sumOf { it.km } }
            .entries.groupBy({ it.key.dayOfWeek }, { it.value })
            .filterValues { it.size >= MIN_DAYS_FOR_USUAL }
            .mapValues { (_, sums) -> sums.average() }
        return TripPattern(observed.size, routines, share, usual)
    }

    override fun isLongTrip(pattern: TripPattern, date: LocalDate, plannedKm: Double): Boolean? {
        val usual = pattern.usualKmByDay[date.dayOfWeek] ?: return null
        return (usual > 0 && plannedKm >= usual * 2) || plannedKm >= usual + LONG_EXTRA_KM
    }

    companion object {
        const val TIME_TOLERANCE_MIN = 90.0
        const val KM_TOLERANCE = 0.25
        const val MIN_WEEKS = 3
        const val MIN_TRIPS_FOR_SHARE = 5
        const val MIN_DAYS_FOR_USUAL = 2
        const val LONG_EXTRA_KM = 100.0
        private fun minuteOfDay(t: TripSegment) = t.start.hour * 60.0 + t.start.minute
        private fun median(v: List<Double>): Double {
            val s = v.sorted(); val n = s.size
            return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
        }
    }
}
