# Code 21 Stage 4 — SERVER BASELINE GUARDS (READ ONLY; NOT DEPLOYABLE)

Base: `feature/legacy-property-opt-in-code21` at `75af8c64680b792a6f04d41fe51ee004ea7011b3`.
Protected backup: `booking-register-legacy-recovery-closed-test` at `6e7735c7808b56e0c2080e50199ec20e9ba0e2d5`.

## File allowlist: only NEW files

- `functions/src/legacyProperty/Stage4ServerSafetyPolicy.ts`
- `functions/src/legacyProperty/Stage4ServerReadOnlyPreflight.ts`
- `functions/test/legacyProperty.stage4.test.ts`
- `docs/LEGACY_103_STAGE4_SERVER_GUARDS.md`

**No pre-existing source files are modified.** `functions/src/index.ts`, Firebase Rules, Android, CloudSyncManager, BookingRepository, booking functions, database schemas, and migrations are unchanged.

## What the server module does

- Takes a Firestore Admin read interface, a server-authenticated UID and hotel ID; the eventual wrapper must obtain UID from `request.auth.uid`, NEVER from request data.
- Reads owner-account and active OWNER membership before accessing the hotel's inventory.
- Reads hotel existence, first managed-property document (any existing property blocks), one existing migration marker and up to 501 room documents; over 500 blocks conservatively.
- Checks no assigned property, tenant matching, and presence of at least one active room. A missing room data record, missing owner record, failed read or oversized inventory fails closed.
- Always returns `migrationAuthorized: false`. Its list of blockers explicitly includes legacy provenance, off-device immutable backup, exhaustive financial reconciliation, dependent records/locks, cross-device write exclusion and a rehearsed recovery plan. **No boolean supplied by the Android client can bypass these blockers.**
- Uses only Firestore reads; does not import a mutator interface, export a callable, create properties, update records or read/write production Firebase during installation.

## Why no migration writes yet

The existing server write paths for bookings and payments do not currently consult a legacy migration lock. Introducing a new marker would not stop other active devices from modifying bookings. Therefore an independent new migration function alone CANNOT guarantee consistency. Also, an Android-local original 1.0.3 backup is not independent server-side evidence without a verified off-device backup.

Before any production callable or conversion writes are enabled, we MUST independently design and test:

1. Off-device verified complete backup and per-document recovery, including original IDs, amounts, GST, invoices and dependent records.
2. Server-side, trusted proof that an account genuinely originated from 1.0.3; do not infer it from null `propertyRemoteId`.
3. Atomic/idempotent and resumable migration that preserves all IDs, all numbers and booking locks.
4. **Actual enforced cross-device write exclusion** across *all* existing write paths, or a validated safe migration while concurrent writes occur. This cannot be assumed just because the migration function holds a marker.
5. Transactional revalidation and post-migration verification across multiple device sessions, with rehearsed recovery.
6. First test in local Firebase emulators with copied/synthetic data; only then a separate controlled pilot, after backups and rollback checks.

The present module is not registered in `index.ts` and is therefore not exported as a callable. **Do NOT deploy this package to Firebase or install a Code 21 APK on customer devices.**

## Testing

- `npm run build` in `functions` — verifies existing functions + new TypeScript.
- `./node_modules/.bin/vitest run test/legacyProperty.stage4.test.ts --reporter=verbose` — pure mock-based tests; no Firebase emulators or network writes.
- `:app:testDebugUnitTest :app:assembleDebug` — Android regression, with short-path Windows Robolectric HOME/TEMP.

The bundle installer enforces Git commit pins, SHA256 payload checks, only the above four untracked additions, full test/build success, before allowing Git commit/push to the isolated Code 21 branch. It does NOT deploy backend functions, alter Firebase rules, or run any real account audit.
