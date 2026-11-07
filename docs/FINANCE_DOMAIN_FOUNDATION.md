# Finance domain foundation — offline, not a posting engine

Branch baseline: `feature/finance-isolated-dev-2026-11-06` at `d6a054a664e203b2d9bdc83dc8769bada1899470`.

## Scope of this step

- New `:finance-domain` Kotlin/JVM module with **no Android or Firebase dependency**.
- The existing Finance DEV build type alone gets a compile-time dependency on the module (`financeDevImplementation`).
- Neither `debug` nor `release` depends on the new module; no production application source, Firebase rules, or Functions change.
- All journal data is an **unposted proposal**; there is no local authority to post financial transactions.

## Value and journal contracts

- `Money(currency, minorUnits: Long)` holds signed minor units; current supported currency is `INR`.
- `Money.parseMajorUnits` accepts plain decimal text, at most two fractional digits, with no floating-point conversion.
- Addition and subtraction detect `Long` overflow; rates are integer basis points with BigInteger intermediates.
- `JournalProposal` carries environment, tenant, hotel, property, source, source revision, command, actor and accounting period.
- Journal lines must have positive amounts, unique line IDs, active mapped accounts and matching property/currency.
- The proposal validator requires exact debit = credit in minor units.
- `ChartOfAccounts` is a constrained model, **not an approved account catalogue** or tax posting matrix.
- Reversal proposals flip debit/credit sides, require a new command ID and mandatory reason, and explicitly require a target accounting period (no silent closed-period assumption).
- SHA-256 fingerprints are canonical and versioned; a reused command ID with a different fingerprint fails.

## Limits and required next layers

This stage **does not** deliver: server-authoritative posting, persisted applied-command records, financial transaction locking, payment allocation, draft outbox, Firestore permissions, period-close enforcement, independent Firebase DEV backend, GST eligibility logic, RCM, TDS/TCS, accounts approval, Room persistence or financial reports.

The future server must repeat all validation inside the same transaction as writing an immutable journal, audit event, applied-command record and source-state version check. A client-computed fingerprint is a comparison tool, **not a security boundary**. A `PostedJournalSnapshot` must be loaded from a trusted server; its client-side construction does not prove that a server accepted it.

The test case representing November rent + GST is **an illustrative eligible forward-charge scenario only**. It does not configure the system to claim ITC or decide whether GST is due under a particular tax arrangement. A qualified Indian accountant must approve account mapping and tax rules before any real posting.

## Non-production verification

From the repository root on the verified Finance branch:

```powershell
.\gradlew.bat :finance-domain:test :app:testDebugUnitTest :app:assembleDebug :app:assembleFinanceDev :app:lintFinanceDev
```

Do not run connected Android instrumentation against the live installed production package. Do not deploy Firebase. Inspect the Finance APK's merged manifest again before any device installation; the Finance DEV `INTERNET` permission must remain absent.
