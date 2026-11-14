package com.example.bookingregister.finance.dev

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** A property-scoped instruction that creates one expense bill every month. */
@Entity(
    tableName = "finance_recurring_rules",
    indices = [
        Index(value = ["tenantId", "propertyId", "active"]),
        Index(value = ["seriesId", "firstDueDate"])
    ]
)
data class FinanceRecurringRule(
    @PrimaryKey val ruleId: String,
    val seriesId: String,
    val environment: String,
    val tenantId: String,
    val hotelId: String,
    val propertyId: String,
    val expenseCategoryCode: String,
    val expenseCategoryLabel: String,
    val expenseTypeCode: String,
    val expenseTypeLabel: String,
    val amountPaise: Long,
    val firstDueDate: String,
    val endDate: String?,
    val active: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val revision: Long
)

/** A monthly bill is a permanent snapshot; changing a rule never rewrites old bills. */
@Entity(
    tableName = "finance_recurring_bills",
    indices = [
        Index(value = ["ruleId", "dueDate"], unique = true),
        Index(value = ["tenantId", "propertyId", "dueDate"])
    ]
)
data class FinanceRecurringBill(
    @PrimaryKey val billId: String,
    val ruleId: String,
    val seriesId: String,
    val environment: String,
    val tenantId: String,
    val hotelId: String,
    val propertyId: String,
    val expenseCategoryCode: String,
    val expenseCategoryLabel: String,
    val expenseTypeCode: String,
    val expenseTypeLabel: String,
    val amountPaise: Long,
    val amountPaidPaise: Long,
    val dueDate: String,
    val accountingPeriod: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val revision: Long
)

@Entity(
    tableName = "finance_recurring_payments",
    indices = [Index(value = ["billId", "paymentDate"])]
)
data class FinanceRecurringPayment(
    @PrimaryKey val paymentId: String,
    val billId: String,
    val amountPaise: Long,
    val paymentDate: String,
    val method: String,
    val reference: String,
    val createdAtMillis: Long
)

@Entity(
    tableName = "finance_recurring_events",
    indices = [Index(value = ["referenceId", "occurredAtMillis"])]
)
data class FinanceRecurringEvent(
    @PrimaryKey val eventId: String,
    val referenceId: String,
    val eventType: String,
    val description: String,
    val occurredAtMillis: Long
)

@Dao
abstract class FinanceRecurringDao {
    @Query(
        """SELECT * FROM finance_recurring_rules
           WHERE tenantId = :tenantId AND propertyId = :propertyId AND active = 1
           ORDER BY firstDueDate, expenseTypeLabel"""
    )
    abstract suspend fun activeRules(tenantId: String, propertyId: String): List<FinanceRecurringRule>

    @Query(
        """SELECT * FROM finance_recurring_rules
           WHERE tenantId = :tenantId AND propertyId = :propertyId
           ORDER BY createdAtMillis DESC"""
    )
    abstract suspend fun allRules(tenantId: String, propertyId: String): List<FinanceRecurringRule>

    @Query(
        """SELECT * FROM finance_recurring_bills
           WHERE tenantId = :tenantId AND propertyId = :propertyId
             AND amountPaidPaise < amountPaise AND dueDate <= :throughDate
           ORDER BY dueDate, billId"""
    )
    abstract suspend fun openBills(
        tenantId: String,
        propertyId: String,
        throughDate: String
    ): List<FinanceRecurringBill>

    @Query("SELECT * FROM finance_recurring_bills WHERE billId = :billId LIMIT 1")
    protected abstract suspend fun findBill(billId: String): FinanceRecurringBill?

    @Query("SELECT * FROM finance_recurring_payments WHERE billId = :billId ORDER BY paymentDate, createdAtMillis")
    abstract suspend fun paymentsFor(billId: String): List<FinanceRecurringPayment>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRule(rule: FinanceRecurringRule)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertBills(bills: List<FinanceRecurringBill>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertPayment(payment: FinanceRecurringPayment)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertEvent(event: FinanceRecurringEvent)

    @Update(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun updateBill(bill: FinanceRecurringBill): Int

    @Query(
        """UPDATE finance_recurring_rules
           SET active = 0, updatedAtMillis = :atMillis, revision = revision + 1
           WHERE ruleId = :ruleId AND active = 1"""
    )
    protected abstract suspend fun stopRuleRow(ruleId: String, atMillis: Long): Int

    @Transaction
    open suspend fun createRule(rule: FinanceRecurringRule, event: FinanceRecurringEvent) {
        FinanceRecurringPolicy.validateRule(rule)
        require(event.referenceId == rule.ruleId && event.eventType == "RULE_CREATED")
        insertRule(rule)
        insertEvent(event)
    }

    @Transaction
    open suspend fun stopRule(ruleId: String, nowMillis: Long) {
        require(ruleId.isNotBlank() && nowMillis > 0L)
        check(stopRuleRow(ruleId, nowMillis) == 1) { "Recurring expense is already stopped" }
        insertEvent(
            FinanceRecurringEvent(
                eventId = UUID.randomUUID().toString(),
                referenceId = ruleId,
                eventType = "RULE_STOPPED",
                description = "Future recurring bills stopped",
                occurredAtMillis = nowMillis
            )
        )
    }

    @Transaction
    open suspend fun reviseRule(
        oldRuleId: String,
        pendingOldBills: List<FinanceRecurringBill>,
        replacement: FinanceRecurringRule,
        nowMillis: Long
    ) {
        FinanceRecurringPolicy.validateRule(replacement)
        if (pendingOldBills.isNotEmpty()) insertBills(pendingOldBills)
        check(stopRuleRow(oldRuleId, nowMillis) == 1) { "Recurring expense is no longer active" }
        insertEvent(FinanceRecurringEvent(
            eventId = UUID.randomUUID().toString(), referenceId = oldRuleId,
            eventType = "RULE_REVISED", description = "Replaced from ${replacement.firstDueDate}",
            occurredAtMillis = nowMillis
        ))
        insertRule(replacement)
        insertEvent(FinanceRecurringEvent(
            eventId = UUID.randomUUID().toString(), referenceId = replacement.ruleId,
            eventType = "RULE_CREATED", description = "Effective-dated replacement created",
            occurredAtMillis = nowMillis
        ))
    }

    @Transaction
    open suspend fun recordPayment(
        payment: FinanceRecurringPayment,
        event: FinanceRecurringEvent
    ) {
        val current = findBill(payment.billId) ?: error("Recurring bill no longer exists")
        FinanceRecurringPolicy.validatePayment(current, payment)
        require(event.referenceId == current.billId && event.eventType == "PAYMENT_RECORDED")
        val nextPaid = Math.addExact(current.amountPaidPaise, payment.amountPaise)
        check(updateBill(current.copy(
            amountPaidPaise = nextPaid,
            updatedAtMillis = payment.createdAtMillis,
            revision = Math.addExact(current.revision, 1L)
        )) == 1) { "Recurring bill could not be updated" }
        insertPayment(payment)
        insertEvent(event)
    }
}

object FinanceRecurringPolicy {
    enum class DisplayStatus { UPCOMING, DUE, OVERDUE, PARTLY_PAID, PAID }

    fun newRule(
        categoryCode: String,
        categoryLabel: String,
        typeCode: String,
        typeLabel: String,
        amountPaise: Long,
        firstDueDate: String,
        endDate: String?,
        nowMillis: Long,
        id: String = UUID.randomUUID().toString(),
        seriesId: String = id
    ): FinanceRecurringRule = FinanceRecurringRule(
        ruleId = id,
        seriesId = seriesId,
        environment = FinanceDemoDraftPolicy.ENVIRONMENT,
        tenantId = FinanceDemoDraftPolicy.TENANT,
        hotelId = FinanceDemoDraftPolicy.HOTEL,
        propertyId = FinanceDemoDraftPolicy.PROPERTY,
        expenseCategoryCode = categoryCode,
        expenseCategoryLabel = categoryLabel.trim(),
        expenseTypeCode = typeCode,
        expenseTypeLabel = typeLabel.trim(),
        amountPaise = amountPaise,
        firstDueDate = firstDueDate.trim(),
        endDate = endDate?.trim()?.takeIf(String::isNotBlank),
        active = true,
        createdAtMillis = nowMillis,
        updatedAtMillis = nowMillis,
        revision = 1L
    ).also(::validateRule)

    fun validateRule(rule: FinanceRecurringRule) {
        require(rule.environment == FinanceDemoDraftPolicy.ENVIRONMENT)
        require(rule.tenantId == FinanceDemoDraftPolicy.TENANT && rule.hotelId == FinanceDemoDraftPolicy.HOTEL &&
            rule.propertyId == FinanceDemoDraftPolicy.PROPERTY) { "Recurring expense belongs to another property" }
        require(rule.ruleId.isNotBlank() && rule.seriesId.isNotBlank())
        require(Regex("HC[0-9]{2}").matches(rule.expenseCategoryCode)) { "Choose a valid expense category" }
        require(Regex("HE[0-9]{4}").matches(rule.expenseTypeCode) &&
            rule.expenseTypeCode.substring(2, 4) == rule.expenseCategoryCode.substring(2, 4)) {
            "Expense type does not belong to the selected category"
        }
        require(rule.expenseCategoryLabel.isNotBlank() && rule.expenseTypeLabel.isNotBlank())
        require(rule.amountPaise > 0L) { "Recurring amount must be more than zero" }
        val first = parseDate(rule.firstDueDate, "first payment date")
        val end = rule.endDate?.let { parseDate(it, "ending date") }
        require(end == null || !end.isBefore(first)) { "Ending date cannot be before the first payment date" }
        require(rule.createdAtMillis > 0L && rule.updatedAtMillis >= rule.createdAtMillis && rule.revision > 0L)
    }

    fun billsThrough(rule: FinanceRecurringRule, throughDate: LocalDate, nowMillis: Long): List<FinanceRecurringBill> {
        validateRule(rule)
        require(nowMillis > 0L)
        val first = LocalDate.parse(rule.firstDueDate)
        val end = rule.endDate?.let(LocalDate::parse)
        if (throughDate.isBefore(first)) return emptyList()
        val result = mutableListOf<FinanceRecurringBill>()
        var month = YearMonth.from(first)
        val lastMonth = YearMonth.from(minOf(throughDate, end ?: throughDate))
        while (month <= lastMonth) {
            val due = month.atDay(first.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
            if (!due.isBefore(first) && !due.isAfter(throughDate) && (end == null || !due.isAfter(end))) {
                result += FinanceRecurringBill(
                    billId = "recurring:${rule.seriesId}:$due",
                    ruleId = rule.ruleId,
                    seriesId = rule.seriesId,
                    environment = rule.environment,
                    tenantId = rule.tenantId,
                    hotelId = rule.hotelId,
                    propertyId = rule.propertyId,
                    expenseCategoryCode = rule.expenseCategoryCode,
                    expenseCategoryLabel = rule.expenseCategoryLabel,
                    expenseTypeCode = rule.expenseTypeCode,
                    expenseTypeLabel = rule.expenseTypeLabel,
                    amountPaise = rule.amountPaise,
                    amountPaidPaise = 0L,
                    dueDate = due.toString(),
                    accountingPeriod = month.toString(),
                    createdAtMillis = nowMillis,
                    updatedAtMillis = nowMillis,
                    revision = 1L
                )
            }
            month = month.plusMonths(1)
        }
        return result
    }

    fun displayStatus(bill: FinanceRecurringBill, today: LocalDate): DisplayStatus = when {
        bill.amountPaidPaise >= bill.amountPaise -> DisplayStatus.PAID
        bill.amountPaidPaise > 0L -> DisplayStatus.PARTLY_PAID
        LocalDate.parse(bill.dueDate).isBefore(today) -> DisplayStatus.OVERDUE
        LocalDate.parse(bill.dueDate).isEqual(today) -> DisplayStatus.DUE
        else -> DisplayStatus.UPCOMING
    }

    fun validatePayment(bill: FinanceRecurringBill, payment: FinanceRecurringPayment) {
        require(payment.paymentId.isNotBlank() && payment.billId == bill.billId)
        require(payment.amountPaise > 0L) { "Payment must be more than zero" }
        require(payment.amountPaise <= bill.amountPaise - bill.amountPaidPaise) {
            "Payment exceeds the amount due"
        }
        parseDate(payment.paymentDate, "payment date")
        require(payment.method in setOf("UPI", "BANK_TRANSFER", "CASH")) { "Choose a payment method" }
        require(payment.reference.length <= 120 && payment.createdAtMillis > 0L)
    }

    private fun parseDate(value: String, label: String): LocalDate =
        runCatching { LocalDate.parse(value) }.getOrElse { throw IllegalArgumentException("Enter a valid $label") }
}
