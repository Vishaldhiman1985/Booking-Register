package com.bookingregister.finance.domain

import java.util.Collections

/** Scope is required for every journal and chart-of-accounts record, not inferred from the device. */
enum class FinanceEnvironment { FINANCE_DEV, PRODUCTION }

data class FinanceScope(
    val environment: FinanceEnvironment,
    val tenantId: String,
    val hotelId: String,
    val propertyId: String
) {
    init {
        listOf(tenantId, hotelId, propertyId).forEach {
            require(it.isNotBlank() && '/' !in it) { "Invalid tenant/hotel/property identifier" }
        }
    }
}

enum class AccountCategory { ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE }
enum class NormalBalance { DEBIT, CREDIT }

data class LedgerAccount(
    val id: String,
    val code: String,
    val name: String,
    val scope: FinanceScope,
    val currency: CurrencyCode,
    val category: AccountCategory,
    val normalBalance: NormalBalance,
    val active: Boolean = true
) {
    init {
        require(id.isNotBlank() && code.isNotBlank() && name.isNotBlank()) {
            "Account id, code and name are required"
        }
    }
}

/** Immutable property-scoped catalogue; chart contents are not inferred from Booking Register totals. */
class ChartOfAccounts(
    val scope: FinanceScope,
    val currency: CurrencyCode,
    accounts: List<LedgerAccount>
) {
    private val byId: Map<String, LedgerAccount>

    init {
        require(accounts.isNotEmpty()) { "At least one ledger account is required" }
        require(accounts.all { it.scope == scope && it.currency == currency }) {
            "Account belongs to a different property or currency"
        }
        require(accounts.map { it.id }.distinct().size == accounts.size) {
            "Duplicate account id"
        }
        require(accounts.map { it.code }.distinct().size == accounts.size) {
            "Duplicate account code"
        }
        byId = Collections.unmodifiableMap(accounts.associateBy { it.id })
    }

    fun findAccount(accountId: String): LedgerAccount? = byId[accountId]
}
