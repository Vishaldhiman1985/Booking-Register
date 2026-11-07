# Finance DEV expense workflow — Phase 2B

## Delivered boundary

The separately installed `com.bookingregister.app.finance.dev` application now
contains a complete fictional offline expense-draft workflow. It remains
fail-closed: the manifest has no Android INTERNET permission and no operation can
create a financial journal, payment, payable, tax balance or cloud record.

## Draft record

Each active or discarded draft carries:

- fixed Finance DEV environment, tenant, hotel and property scope;
- a controlled fictional vendor code;
- expense category;
- invoice date and derived accounting period;
- INR total and marked-paid amounts in signed 64-bit integer paise;
- descriptive payment status;
- active/discarded state;
- created, updated and optional discarded timestamps;
- monotonically increasing local revision.

The policy requires a positive total. Not-paid drafts must have zero paid amount,
part-paid drafts require `0 < paid < total`, and paid drafts require
`paid == total`. No floating-point conversion is used.

## Changes and history

Creation, editing and discard changes are persisted atomically with a local
event. Edit and discard events require a non-blank reason. Discard changes state
and hides the row from active totals; it never deletes the row. This local event
history is useful for draft safety but is not a substitute for the future
server-authoritative financial audit ledger.

## Room migration

Database filename remains `finance_dev_synthetic_drafts_v1.db` so installed
Phase 2A data is found. Schema version 2 uses the explicit
`FinanceDemoDraftDatabase.MIGRATION_1_2` migration. The checked-in Room schema
exports for versions 1 and 2 are the migration contract.

## Evidence

- Finance-domain JVM tests pass.
- Finance DEV unit tests include deterministic exact-money and transition cases.
- A Finance-only Android test validates v1-to-v2 data preservation.
- A Finance-only Android test validates create, close/reopen, edit,
  close/reopen and discard persistence with three retained events.
- Finance DEV builds and launches under its separate package.

No Firebase, Functions, Firestore rules, production data, release branch or
production application source is changed by this phase.
