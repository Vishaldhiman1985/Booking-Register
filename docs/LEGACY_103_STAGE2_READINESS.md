# Code 21 — Stage 2: Read-only property readiness inventory

**Source:** `feature/legacy-property-opt-in-code21` pinned to commit
`e80d6a4213d5bf3b7e48246b8621c9491294dc13`.

**Protected recovery:** `booking-register-legacy-recovery-closed-test`
@ `6e7735c7808b56e0c2080e50199ec20e9ba0e2d5`.

## Scope — new files only

- `app/src/main/java/com/example/bookingregister/legacy/Legacy103PropertyReadinessAudit.kt`
- `app/src/main/java/com/example/bookingregister/legacy/Legacy103PropertyReadinessPolicy.kt`
- `app/src/test/java/com/example/bookingregister/legacy/Legacy103PropertyReadinessPolicyTest.kt`
- `docs/LEGACY_103_STAGE2_READINESS.md`

No changes to existing code. No existing synchronization, recovery, database, booking, Cloud Function,
Firebase rules, or login code is changed. No Firestore writes. Stage 2 is NOT invoked
in production or from login. It does not install an app on a test device.

## Behavior

1. Requires a fully reconciled Stage 1 original booking/payment audit.
2. Reads preserved original room IDs from `legacy_v11_backup_rooms`.
3. Reads current local records and pending booking outbox/recovery-review counts.
4. Fetches the hotel-scoped Firestore collections **from Source.SERVER only** in
   document-id pages of 200 (max 10,000 documents per collection for a phone-side diagnostic).
5. Checks property absence, room assignment, original room continuity, booking-room links,
   payment/financial/source/food parent references, local pending/failed sync, local-vs-server
   financial fields, and cross-hotel IDs. Returns issues and collection counts.
6. Missing permissions, missing backup, failed network, unknown schema, mismatches, or
   oversized collections result in a **blocked report**. Does not use cached data.

### Important limitations

- This is a diagnostic, **not** an authorization to migrate. A report with no issues
  is not proof of owner authorization, verified off-device immutable backup,
  server-side migration lock, cross-device write quiescence, rollback safety,
  account-wide reconciliation or issued-invoice correctness.
- Some legitimate edits and preexisting display or cloud schema discrepancies can
  produce blockers. Review them; never silently overwrite records to silence a check.
- The read is not a transaction across collections. Changes during the audit can
  result in an inconsistent view. Migration will require a separate server-side
  consistent snapshot and enforced write exclusion.
- The full collection inventory reads multiple documents per hotel, so it must
  run only on explicit developer/test requests, **not on every login**.
- No export of guest names, mobile numbers, dates or financial values. Issue identifiers
  may contain record IDs; treat diagnostic output as internal information.
- This pass does NOT inspect server booking-lock internals or server migration
  marker state. The subsequent migration implementation must do so before writing.

## Testing and installation

Run the bundled `Install-Stage2-Readiness.ps1` after extracting this archive.
It only adds the four approved new files, verifies Git state, runs
`:app:testDebugUnitTest` and `:app:assembleDebug`, commits and pushes if successful.
Do **not** install Code 21 on customer phones or use a real hotel as a live migration test.
