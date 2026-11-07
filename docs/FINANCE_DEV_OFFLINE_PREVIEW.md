# Finance DEV offline preview — Phases 2A and 2B

**Base:** `feature/finance-isolated-dev-2026-11-06`, commit `d6a054a664e203b2d9bdc83dc8769bada1899470` plus the previously applied Phase-1 finance-domain patch.

## Safety and scope

This code affects **only** `app/src/financeDev` and `app/src/testFinanceDev` plus this document. It does not alter the production launcher or `AppDatabase`, does not add INTERNET permission, and does not create, post, alter or reverse any financial ledger entries. No backend, Functions, rules, Firebase config, or release build settings change.

A private Room v1 `finance_dev_synthetic_drafts_v1.db` holds only fictional test drafts in fixed demo tenant, hotel and property identifiers. The UI intentionally does not offer free-text entry of real vendor or hotel data. Draft amount is stored as `Long` paise and displayed through the finance-domain Money type. No `Double`, SQLite REAL or JS arithmetic is used in this store.

The existing startup fail-closed `FinanceDevApplication` and INTERNET manifest removal remain unchanged. This development preview does not make Firebase requests; reading configured Firebase project ID for the safety label is not a network request.

## Phase 2A UI

Five destinations: Dashboard, Expenses, Payments, Vendors, Reports. Only Dashboard and Expenses contain read-only demo summaries and a control to create a fictional local draft. The remaining tabs clearly say that they are not implemented. Nothing is ever labelled as an actual posted expense, payable, profit or GST balance.

## What this is not

This is **not** the future production finance database, chart of accounts, tax engine, vendor master, outbox, reconciler or posting engine. Its schema is a disposable demo v1: do not migrate real user financial records into it. No deletion/reset of real data is present. No posted/immutable journal lives here.

## Validation required on Windows

Run from the correct local worktree after verifying the Finance branch (do not run instrumented tests against the production app installed on the device):

```
.\gradlew.bat :finance-domain:test :app:testFinanceDevUnitTest :app:assembleDebug :app:assembleFinanceDev :app:lintFinanceDev
```

Check the Finance DEV merged manifest for absence of INTERNET. Inspect `app/build/reports/lint-results-financeDev.html` for any warnings. Ensure exported Room schema is generated and track it before evolving this database. Do not install until APK package has been verified as `com.bookingregister.app.finance.dev`.

**Next after this preview:** define production-grade Finance database/API contracts, server posting/audit and accountant-approved tax treatment; only then connect to a dedicated, secured backend after explicit approval.

## Phase 2B offline expense drafts

Phase 2B evolves the private preview database from version 1 to version 2 without
changing its filename or destroying existing rows. The explicit `MIGRATION_1_2`
adds payment status, integer amount-paid paise, update/discard timestamps and a
monotonic draft revision. It also creates an append-only local draft-event table.
Every v1 row is preserved and receives a `MIGRATED` history event.

The Expenses screen now supports:

- controlled fictional vendor and expense-category selection;
- total amount and amount marked paid as integer paise;
- an ISO invoice date with accounting month derived from that date;
- Not paid, Partially paid and Paid draft descriptions;
- editing an active draft only with a mandatory reason;
- discarding only with a mandatory reason.

Discard never performs a SQL delete. It marks the local draft as discarded and
retains its history. Paid/part-paid labels describe a fictional draft only: they
do not create a payment, payable, bill, journal or outbox command.

### Phase 2B verification

Unit tests:

```powershell
.\gradlew.bat '-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false' `
  :finance-domain:test :app:testFinanceDevUnitTest
```

The opt-in `financeDevAndroidTest` property changes only the instrumentation
test target; normal debug test configuration remains the default:

```powershell
.\gradlew.bat -PfinanceDevAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.example.bookingregister.finance.dev.FinanceDemoDraftDatabaseTest' `
  :app:connectedFinanceDevAndroidTest
```

The two device tests validate v1-to-v2 preservation and create/edit/discard
persistence across database reopen. Run connected tests on an emulator or a
disposable Finance DEV installation: Android's connected-test runner can remove
and reinstall the Finance DEV package during cleanup, clearing that package's
fictional private preview data. It does not target the production application ID,
but it must never be run against a build whose application ID is
`com.bookingregister.app`.

Phase 2B remains an offline manager-workflow prototype, not the future production
Finance database or financial posting engine.
