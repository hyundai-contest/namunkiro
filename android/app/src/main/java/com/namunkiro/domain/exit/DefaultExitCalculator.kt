package com.namunkiro.domain.exit

import com.namunkiro.domain.*
import com.namunkiro.domain.model.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

class DefaultExitCalculator : ExitCalculator {
    override fun compare(contract: Contract, snapshot: BudgetSnapshot, quotes: ExitQuotes, today: LocalDate): ExitComparison {
        val noDistance = contract.type in listOf(ContractType.FINANCE_LEASE, ContractType.UNLIMITED)
        val months = if (today.isAfter(contract.endDate)) 0L else {
            val anchor = maxOf(today, contract.startDate)
            // 납입일 정보가 없는 계약은 남은 기간을 월 비례로 추정한다.
            val whole = ChronoUnit.MONTHS.between(anchor, contract.endDate.plusDays(1))
            whole + if (anchor.plusMonths(whole).isBefore(contract.endDate.plusDays(1))) 1 else 0
        }
        val rentals = if (months == 0L) 0L else contract.monthlyFee?.times(months)
        val distance = if (noDistance) 0L else if (snapshot.deductUnconfirmed) null else snapshot.projectedDistanceFee
        // 만기까지 이용하는 선택지의 공통 월료는 별도 비교 항목으로 제공한다.
        val common = listOf("보증금 충당·환급" to contract.deposit?.let { -it })
        fun option(kind: ExitKind, available: Boolean, lines: List<Pair<String, Long?>>, cash: Long? = null, more: List<String> = emptyList(), lowerBoundItems: Set<String> = emptySet()): ExitOption {
            val missing = lines.filter { it.second == null }.map { it.first } + more
            val total = if (lines.any { it.second == null && it.first !in lowerBoundItems }) null else lines.sumOf { it.second ?: 0L }
            return ExitOption(kind = kind, available = available, netCost = total,
                netCostIsLowerBound = total != null && lines.any { it.second == null && it.first in lowerBoundItems },
                cashNeeded = cash ?: total, lines = lines, unconfirmed = missing.distinct())
        }
        val deduct = if (!noDistance && snapshot.deductUnconfirmed) listOf("공제 거리") else emptyList()
        val returnOption = option(ExitKind.RETURN_AT_END, !noDistance,
            common + listOf("만기 거리 정산" to distance, "손상·감가" to quotes.damageEstimate), more = deduct,
            lowerBoundItems = setOf("손상·감가"))
        val tax = contract.buyoutPrice?.let { (it * quotes.acquisitionTaxRate).roundToLong() }
        val buyDistance = when { noDistance || contract.buyoutWaivesDistanceFee == true -> 0L; contract.buyoutWaivesDistanceFee == false -> distance; else -> null }
        val acquisition = common + listOf("인수가" to contract.buyoutPrice, "취등록세 추정" to tax, "인수 시 거리 정산" to buyDistance)
        val cash = if (acquisition.any { it.second == null }) null else acquisition.sumOf { it.second!! }.coerceAtLeast(0)
        val buyLines = acquisition + listOf("종료일 차량 시세" to quotes.expectedMarketPrice?.let { -it })
        val buyMissing = if (contract.buyoutWaivesDistanceFee == false) deduct else emptyList()
        val keep = option(ExitKind.BUY_AND_KEEP, true, buyLines, cash, buyMissing)
        val sell = option(ExitKind.BUY_AND_SELL, true, buyLines, cash, buyMissing)
        val transfer = option(ExitKind.TRANSFER_NOW, true, listOf("승계 수수료·지원금 견적" to quotes.transferCost,
            "보증금·선납 정산" to (quotes.transferDepositSettlement?.let { -it } ?: if (contract.deposit == 0L) 0L else null),
            "대체 이동비" to quotes.alternativeMobilityCost), lowerBoundItems = setOf("대체 이동비"))
        val month = ChronoUnit.MONTHS.between(contract.startDate, maxOf(today, contract.startDate)).toInt() + 1
        val rate = contract.terminationTiers?.sortedBy { it.untilMonth }?.firstOrNull { month <= it.untilMonth }?.rate
        val terminationFee = if (rentals != null && rate != null) (rentals * rate).roundToLong() else null
        val proratedKm = contract.totalKm * snapshot.elapsedDays.toDouble() / snapshot.contractDays
        val earlyDistance = if (noDistance) 0L else if (snapshot.deductUnconfirmed) null else (maxOf(0.0, snapshot.usedKm - proratedKm - (contract.deductKm ?: 0)) * contract.unitPricePerKm).roundToLong()
        val termination = option(ExitKind.TERMINATE_NOW, true, listOf("해지 수수료" to terminationFee, "일할 거리 정산" to earlyDistance,
            "보증금 환급" to contract.deposit?.let { -it }, "대체 이동비" to quotes.alternativeMobilityCost), more = deduct,
            lowerBoundItems = setOf("대체 이동비"))
        val options = listOf(returnOption, keep, sell, transfer, termination)
        fun cheapestIn(group: ExitGroup): Pair<ExitKind?, Boolean> {
            val candidates = options.filter { it.kind.group == group && it.available && it.netCost != null }
            val cheapest = candidates.minByOrNull { it.netCost!! }
            // 같은 묶음에서 하한값이 최저이거나 최저와 30만원 이내면 잠정 비교다.
            val tentative = cheapest != null && candidates.any {
                it.netCostIsLowerBound && it.netCost!! - cheapest.netCost!! <= 300_000L
            }
            return cheapest?.kind to tentative
        }
        val keepResult = cheapestIn(ExitGroup.KEEP_UNTIL_END)
        val leaveResult = cheapestIn(ExitGroup.LEAVE_NOW)
        return ExitComparison(contract.endDate, options, keepResult.first, keepResult.second,
            cheapestLeaveNow = leaveResult.first, cheapestLeaveNowTentative = leaveResult.second, remainingFees = rentals)
    }

    override fun calendar(contract: Contract, today: LocalDate, quotes: ExitQuotes): List<CalendarEvent> {
        val events = mutableListOf(CalendarEvent(contract.endDate, CalendarEventKind.CONTRACT_END, "계약 만기"))
        contract.freeReturnDate?.let { events += CalendarEvent(it, CalendarEventKind.FREE_RETURN_OPEN, "자유반납 가능") }
        contract.noticeDeadline?.let { events += CalendarEvent(it, CalendarEventKind.NOTICE_DEADLINE, "만기 통지 기한") }
        quotes.quoteValidUntil?.let { events += CalendarEvent(it, CalendarEventKind.QUOTE_EXPIRY, "출구 견적 만료") }
        val tiers = contract.terminationTiers.orEmpty().sortedBy { it.untilMonth }
        tiers.zipWithNext().filter { (a, b) -> b.rate < a.rate }.forEach { (a, _) ->
            events += CalendarEvent(contract.startDate.plusMonths(a.untilMonth.toLong()), CalendarEventKind.TERMINATION_RATE_DROP, "해지 요율 인하")
        }
        return events.filter { !it.date.isBefore(today) && !it.date.isAfter(contract.endDate) }.sortedBy { it.date }
    }
}

