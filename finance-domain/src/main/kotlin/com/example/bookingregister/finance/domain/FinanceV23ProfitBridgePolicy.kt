package com.example.bookingregister.finance.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

/** Read-only, incomplete-source departmental bridge. NEVER an audited or final P&L. */
object FinanceV23ProfitBridgePolicy {
    enum class Center(val label: String) { ROOMS("Rooms"), KITCHEN("Kitchen"), SERVICES("Services") }
    data class Split(val roomsBps: Int, val kitchenBps: Int, val servicesBps: Int) {
        init {
            require(roomsBps in 0..10000 && kitchenBps in 0..10000 && servicesBps in 0..10000 &&
                roomsBps + kitchenBps + servicesBps == 10000) { "Cost allocation must total exactly 100.00%" }
        }
        fun parts(amount: Long): Map<Center, Long> {
            require(amount >= 0) { "Negative costs require a separate adjustment" }
            fun allocate(bps: Int): Long = BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(bps.toLong()))
                .divide(BigDecimal.valueOf(10000L), 0, RoundingMode.HALF_UP).longValueExact()
            val rooms = allocate(roomsBps)
            val kitchen = minOf(allocate(kitchenBps), Math.subtractExact(amount, rooms))
            val services = Math.subtractExact(Math.subtractExact(amount, rooms), kitchen)
            require(services >= 0) { "Split cannot conserve monetary amount" }
            return mapOf(Center.ROOMS to rooms, Center.KITCHEN to kitchen, Center.SERVICES to services)
        }
    }
    data class Expense(val id: String, val month: String, val department: String,
                       val amountPaise: Long, val inventoryPurchase: Boolean = false)
    data class Income(val id: String, val month: String, val department: String, val amountPaise: Long)
    data class StockCost(val itemId: String, val month: String, val consumedPaise: Long)
    data class Assignment(val sourceKey: String, val amountPaise: Long, val split: Split)
    data class Row(val center: Center, val revenuePaise: Long, val costPaise: Long,
                   val recordedDifferencePaise: Long)
    data class Result(val month: String, val rows: List<Row>, val importedRevenuePaise: Long,
                      val allocatedCostPaise: Long, val excludedInventoryPurchasesPaise: Long,
                      val excludedInventoryPurchaseCount: Int, val unallocatedPaise: Long,
                      val issues: List<String>, val hasAnyRevenue: Boolean) {
        val possibleToInterpret: Boolean get() = issues.isEmpty() && hasAnyRevenue
    }

    /** Source labels are not arbitrary revenue categories. Unknown names are NOT silently mapped. */
    fun center(raw: String): Center? = when (raw.trim().lowercase()) {
        "room", "rooms", "room operations", "housekeeping", "front office" -> Center.ROOMS
        "kitchen", "food", "restaurant", "f&b", "food & beverage", "food and beverage" -> Center.KITCHEN
        "service", "services", "activities", "guest services" -> Center.SERVICES
        else -> null
    }
    fun single(center: Center) = when (center) {
        Center.ROOMS -> Split(10000, 0, 0)
        Center.KITCHEN -> Split(0, 10000, 0)
        Center.SERVICES -> Split(0, 0, 10000)
    }

    fun calculate(month: String, incomes: List<Income>, expenses: List<Expense>,
                  stockCosts: List<StockCost>, overrides: List<Assignment>, externalIssues: List<String> = emptyList()): Result {
        YearMonth.parse(month)
        require(incomes.all { it.month == month } && expenses.all { it.month == month } && stockCosts.all { it.month == month }) {
            "Source rows belong to a different accounting period"
        }
        require(incomes.map { it.id }.distinct().size == incomes.size) { "Duplicate imported revenue ID" }
        require(expenses.map { it.id }.distinct().size == expenses.size) { "Duplicate payable bill ID" }
        require(stockCosts.map { it.itemId }.distinct().size == stockCosts.size) { "Duplicate consumed stock item" }
        require(expenses.all { it.amountPaise > 0 } && stockCosts.all { it.consumedPaise >= 0 }) { "Invalid source cost amount" }
        require(incomes.all { it.amountPaise != 0L }) { "Invalid zero-valued revenue event" }
        require(overrides.map { it.sourceKey }.distinct().size == overrides.size) { "Conflicting allocation instructions" }
        val assignments = overrides.associateBy { it.sourceKey }
        val issues = externalIssues.toMutableList()
        val revenue = Center.entries.associateWith { 0L }.toMutableMap()
        val costs = Center.entries.associateWith { 0L }.toMutableMap()
        var excluded = 0L
        var excludedCount = 0
        var unallocated = 0L
        incomes.forEach { event ->
            val center = center(event.department)
            if (center == null) issues.add("Revenue ${event.id}: department '${event.department}' needs classification")
            else revenue[center] = Math.addExact(revenue.getValue(center), event.amountPaise)
        }
        fun addCost(source: String, amount: Long, department: String?) {
            val stored = assignments[source]
            if (stored != null && stored.amountPaise != amount) {
                unallocated = Math.addExact(unallocated, amount)
                issues.add("$source: saved allocation is stale because the cost amount changed")
                return
            }
            val split = stored?.split ?: department?.let(::center)?.let(::single)
            if (split == null) {
                unallocated = Math.addExact(unallocated, amount)
                issues.add("$source: allocation to Rooms, Kitchen or Services is required")
                return
            }
            split.parts(amount).forEach { (center, part) ->
                costs[center] = Math.addExact(costs.getValue(center), part)
            }
        }
        expenses.forEach { bill ->
            if (bill.inventoryPurchase) {
                excluded = Math.addExact(excluded, bill.amountPaise)
                excludedCount++
            } else addCost("bill:${bill.id}", bill.amountPaise, bill.department)
        }
        stockCosts.forEach { stock ->
            if (stock.consumedPaise > 0L) addCost("stock:${stock.itemId}", stock.consumedPaise, null)
        }
        if (incomes.isEmpty()) issues.add("No locally imported revenue events for this month; booking source coverage is unverified")
        val rows = Center.entries.map { center ->
            val r = revenue.getValue(center)
            val c = costs.getValue(center)
            Row(center, r, c, Math.subtractExact(r, c))
        }
        val imported = incomes.fold(0L) { total, i -> Math.addExact(total, i.amountPaise) }
        return Result(month, rows, imported,
            costs.values.fold(0L, Math::addExact), excluded, excludedCount, unallocated,
            issues.distinct(), incomes.isNotEmpty())
    }
}
