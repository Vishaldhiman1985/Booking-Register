# Code 21 — Stage 1: strictly read-only booking/payment verification

Base: `feature/legacy-property-opt-in-code21` at `87a9ed396b0dc7a6ae627c7b3ff70683ae59e40f`.
Protected recovery: `booking-register-legacy-recovery-closed-test` at `6e7735c7808b56e0c2080e50199ec20e9ba0e2d5`.

## Allowlisted changes for this stage

1. `app/src/main/java/com/example/bookingregister/legacy/Legacy103ReadOnlyAudit.kt` — NEW.
2. `app/src/test/java/com/example/bookingregister/legacy/Legacy103ReadOnlyAuditTest.kt` — NEW.
3. `docs/LEGACY_103_READ_ONLY_AUDIT.md` — NEW.

No existing files change. No database schema changes. No Firebase writes. No changes to
`BookingRepository.kt`, `CloudSyncManager.kt`, `LegacyPlay103Recovery.kt`, or
`AppDatabase.kt`, or to existing Cloud Functions.

## Behavior

The new class is *not wired into login or synchronization*. It is only a reusable,
read-only verifier for the next stage. It reads the `legacy_v11_backup_bookings`
and `legacy_v11_backup_booking_payments` tables, then asks Firestore **Source.SERVER**
for all corresponding document IDs. It checks every original business field
(stored in the backup), normalizing SQLite booleans and comma-delimited room IDs.
Only local/sync metadata fields are ignored. The comparator reports missing,
changed, duplicate and orphaned original records as blockers.

Any unavailable cloud read or schema inconsistency fails closed; it never retries
using cached Firebase data. No guest names, phone numbers or financial amounts
are logged in the report; issues identify records by remote ID.

**Crucial limitation:** A mismatch can reflect a legitimate edit made after
version 1.0.3. The verifier must never overwrite that edit or treat a matching
count alone as sufficient. Someone must investigate before permitting conversion.

Even a clean report DOES NOT make the hotel eligible to migrate. Rooms, source
catalogs, financial/tax lines, room-night plans, invoices, unpushed edits,
owner permission, cross-device concurrency, and full backups still require
independent verification. The Code 21 gate remains unwired, and the actual
migration, notice and backend function remain unimplemented.

## Developer testing

Run `:app:testDebugUnitTest` and `:app:assembleDebug` on the PC. Do NOT install
this development APK over any Bhalo or other customer phone.

Only after both checks pass should this source be committed and pushed. Protect
Code 20 and keep real Firebase records unchanged throughout this stage.
