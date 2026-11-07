# Finance DEV offline preview — Phase 2A

**Base:** `feature/finance-isolated-dev-2026-11-06`, commit `d6a054a664e203b2d9bdc83dc8769bada1899470` plus the previously applied Phase-1 finance-domain patch.

## Safety and scope

This code affects **only** `app/src/financeDev` and `app/src/testFinanceDev` plus this document. It does not alter the production launcher or `AppDatabase`, does not add INTERNET permission, and does not create, post, alter or reverse any financial ledger entries. No backend, Functions, rules, Firebase config, or release build settings change.

A private Room v1 `finance_dev_synthetic_drafts_v1.db` holds only fictional test drafts in fixed demo tenant, hotel and property identifiers. The UI intentionally does not offer free-text entry of real vendor or hotel data. Draft amount is stored as `Long` paise and displayed through the finance-domain Money type. No `Double`, SQLite REAL or JS arithmetic is used in this store.

The existing startup fail-closed `FinanceDevApplication` and INTERNET manifest removal remain unchanged. This development preview does not make Firebase requests; reading configured Firebase project ID for the safety label is not a network request.

## UI

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
