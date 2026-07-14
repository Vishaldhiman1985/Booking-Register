#!/usr/bin/env python3
"""Static guardrails for BookingRegister P0 hardening patch.
Run from the project root: python tools/verify_hardening_patch.py
"""
from pathlib import Path
import sys

ROOT = Path.cwd()

checks = []

def contains(path: str, text: str, label: str) -> None:
    p = ROOT / path
    ok = p.exists() and text in p.read_text(encoding="utf-8-sig")
    checks.append((ok, label, path))

def absent(path: str, text: str, label: str) -> None:
    p = ROOT / path
    ok = p.exists() and text not in p.read_text(encoding="utf-8-sig")
    checks.append((ok, label, path))

def missing(path: str, label: str) -> None:
    checks.append((not (ROOT / path).exists(), label, path))

contains("app/src/main/java/com/example/bookingregister/data/AppDatabase.kt", "version = 35", "Room database upgraded to v35")
contains("app/src/main/java/com/example/bookingregister/data/AppDatabase.kt", "MIGRATION_34_35", "Migration 34→35 exists")
contains("app/src/main/java/com/example/bookingregister/data/entities/BookingPaymentEntity.kt", "originalPaymentRemoteId", "Refunds link to original payment")
contains("app/src/main/java/com/example/bookingregister/data/entities/BookingSyncOutboxEntity.kt", "BookingSyncOperationType", "Outbox distinguishes SAVE and DELETE")
contains("app/src/main/java/com/example/bookingregister/data/repository/BookingRepository.kt", "markBookingPending = false", "Remote child records do not create outgoing booking edits")
absent("app/src/main/java/com/example/bookingregister/data/repository/BookingRepository.kt", "backfillLocalPaymentsToCloud", "Synced payments are not blindly re-uploaded")
contains("app/src/main/java/com/example/bookingregister/data/repository/BookingRepository.kt", "pushBookingDeleteAndMark", "Cancellation uses dedicated delete path")
contains("app/src/main/java/com/example/bookingregister/data/repository/BookingRepository.kt", "A payment must never be created in cloud before its parent booking is confirmed", "Parent booking is confirmed before payment upload")
contains("app/src/main/java/com/example/bookingregister/data/sync/CloudSyncManager.kt", "saveBookingAggregateServer", "Booking aggregate uses server callable")
contains("app/src/main/java/com/example/bookingregister/data/sync/CloudSyncManager.kt", "cancelBookingServer", "Cancellation uses server callable")
contains("app/src/main/java/com/example/bookingregister/data/sync/CloudSyncManager.kt", "saveBookingPaymentServer", "Payments use server callable")
contains("app/src/main/java/com/example/bookingregister/data/sync/CloudSyncManager.kt", "saveFoodOrderAggregateServer", "Food order + items use one server transaction")
contains("app/src/main/java/com/example/bookingregister/data/sync/CloudSyncManager.kt", "saveFoodBillAggregateServer", "Food bill aggregate uses one server transaction")
contains("app/src/main/java/com/example/bookingregister/data/sync/FoodRealtimeSyncService.kt", "shouldAcceptRemoteFoodEntity", "Remote food bills protect pending local changes")
contains("functions/index.js", "exports.saveBookingAggregateServer", "Backend booking aggregate function exists")
contains("functions/index.js", "exports.cancelBookingServer", "Backend cancellation function exists")
contains("functions/index.js", "exports.saveFoodOrderAggregateServer", "Backend atomic food order function exists")
contains("functions/index.js", "exports.saveFoodBillAggregateServer", "Backend atomic food bill function exists")
contains("functions/index.js", "originalPaymentRemoteId", "Backend validates linked refunds")
contains("functions/index.js", "updateBookingPaymentSummaryInTransaction", "Backend updates paid/balance/status atomically")
contains("firestore.rules", "match /roomGstSlabs/{slabId}", "Room GST slabs are cloud-readable and server-written")
missing("functions/src/index.ts", "Stale TypeScript backend source removed")
missing("functions/lib/index.js", "Stale compiled backend removed")

failed = False
for ok, label, path in checks:
    print(("PASS" if ok else "FAIL") + f"  {label}  [{path}]")
    failed |= not ok

if failed:
    print("\nPatch verification FAILED.")
    sys.exit(1)
print(f"\nAll {len(checks)} static hardening checks passed.")
