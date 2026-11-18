# Booking Register Code 21 — Stage 5: OFFLINE backup & recovery verification foundation

**Base:** `feature/legacy-property-opt-in-code21` @ `158c146176efd39a6ec2cce915f5eeb6f6720a35`.
**Protected:** `booking-register-legacy-recovery-closed-test` @ `6e7735c7808b56e0c2080e50199ec20e9ba0e2d5`.

## Scope — FOUR new files only

- `functions/src/legacyProperty/Stage5OfflineBackupEvidence.ts`
- `functions/src/legacyProperty/Stage5RecoveryDryRun.ts`
- `functions/test/legacyProperty.stage5.test.ts`
- `docs/LEGACY_103_STAGE5_BACKUP_RECOVERY.md`

**No existing source files, Firestore rules, Cloud Functions entry points, Android code, or database schemas are modified. No Firebase deployments, no account access, no device installs, and no live backups or restores are performed by this stage.**

## What Stage 5 actually implements

1. A pure offline-memory snapshot inventory checker for the **14 collections** enumerated in Stage 2, including explicit claims for empty collections.
2. SHA-256 comparison of provided serialized-document bytes against caller-provided digests; rejects corrupt bytes, duplicate IDs, missing enumerations, tenant/id invalidity and collection scan overflows.
3. An **offline dry-run** that compares a source representation with an independently labelled rehearsal representation, detects missing/extra/changed document IDs and content fingerprints (including bookings, payments, taxes and assignments), requires distinct project IDs and checks a separate sample artifact's byte hash.
4. A report with immutable `migrationAuthorized: false`, `recoveryWritesAuthorized: false`, `productionBackupVerified: false`, regardless of tests passing.
5. Mock/fixture-only regression tests. No Firestore SDK imports, no network requests, no write privileges.

**This is NOT an actual backup, a Firestore export reader, a trusted proof of source completeness, an encrypted/immutable off-device copy, or a restore tool.** The supplied `Uint8Array` representation may not preserve Firestore-specific types (timestamps, document references, GeoPoints, etc.), and no test fixture can prove native Firestore restorability. Caller-provided collection-enumeration claims and hashes are not independent evidence that production was completely read. This test harness cannot authorize migration.

## Required REAL backup + rollback procedure (future work; do not execute yet)

1. **Freeze the target scope safely.** Existing cloud booking/payment write paths do not enforce a migration lock; a migration marker alone is ineffective. Do not migrate a live hotel until concurrent writes are truly prevented or a concurrency-safe protocol is proven.
2. **Approved scope and consent.** Establish server-trusted legacy provenance, current owner authorization, hotel identity, target project and permitted data scope. Do not infer provenance solely from missing property IDs.
3. **Separate, verified destination.** Select a designated backup storage bucket and separate nonproduction restore project. Verify access, retention/immutability policies and least-privilege credentials; do not bundle any credentials in scripts or Git.
4. **Complete native Firestore export.** Use a reviewed and explicitly approved backup/export procedure. Native export includes collection groups; scope must include the hotel account/members, all hotel subcollections (not just Stage 2's 14), server booking locks, markers and financial dependents, and any relevant top-level collections. Record operation ID, timestamp, database, bucket, export prefix, job status, size and independent checksums where obtainable. Firebase data outside Firestore (Auth, Storage) needs separate analysis if applicable.
5. **Off-device immutable copy.** Independently verify a separate locked copy with controlled retention; do not treat a database export in the same project/bucket as automatically immutable or disaster-proof. Treat all snapshots as sensitive guest/financial information, encrypt and restrict access.
6. **Reconciliation.** Compare authoritative server counts and exact original IDs and monetary/tax values with the export, separately from phone cache and app reports; include bookings, payment links, room nights, booking locks, sources, invoices and outstanding unsynced local data.
7. **Rehearse restore in a SEPARATE Firebase project.** Restore the real native export and independently check referential integrity and amounts with pre-export baselines. Prove per-document repair without overwriting new booking/payment activity or other hotels' data. An entire-database restore into production is not an acceptable default rollback.
8. **Plan the emergency stop.** Have named operators, a stop condition, an auditable change log, an evidence-preserving failed-state workflow and documented restoration command runbooks **reviewed but not run**.
9. **After a narrowly approved pilot:** capture a new snapshot and post-change audit. No automatic replay over newer customer edits. Do not turn any green Stage 5 offline-test result into a migration authorization.

## Operational recovery facts

- Rolling back Cloud Functions **does not** restore already changed Firestore documents.
- Production data can be edited by **multiple active app versions**; a marker unknown to current writers does not stop them.
- A Firestore export is not automatically a point-in-time transactional multi-system backup for Firestore + Firebase Auth + client-local SQLite.
- Phone-side `legacy_v11_backup_*` tables protect part of a single device's old data; they are not an independent verified cloud backup.
- Code 20 stays frozen. Keep code 19/20 compatibility as a release gate.

## Installer / tests

The accompanying PowerShell installer is pinned to the exact Stage 4 Code 21 HEAD and Code 20 protected SHA. It checks file SHA-256, Git cleanliness and exact-four-new-files allowlist, uses an isolated official Node.js 22 (without uninstalling Node 24), runs `npm run build`, both Stage 4 & Stage 5 isolated Vitest suites, then `:app:testDebugUnitTest :app:assembleDebug`. Only if everything passes does it commit/push the four new files to `feature/legacy-property-opt-in-code21`.

**NEVER run `firebase deploy`, `firebase firestore:delete`, any production export/restore, or install Code 21 on a customer handset as part of Stage 5.**
