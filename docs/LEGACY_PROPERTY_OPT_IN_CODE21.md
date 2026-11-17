# Code 21 â€” Legacy 1.0.3 opt-in property conversion (development only)

Source: closed-test Code 20 branch `booking-register-legacy-recovery-closed-test`, pinned at
`6e7735c7808b56e0c2080e50199ec20e9ba0e2d5`. Do not alter that branch.

## This first commit

- Creates an independent Code 21 developer build identifier.
- Adds a **pure eligibility policy** and tests; it is NOT wired into login.
- NO Firebase writes, Room database changes, app pop-ups, property creation, migration, or booking changes.
- No production rollout. Do not install this development build over customer phones.

## Next implementation milestones (must pass in sequence)

1. Authoritative, server-only comparison of **every original booking/payment ID and value**
   from `legacy_v11_backup_*` with cloud records. Counts alone are not proof.
2. Read-only inventory of rooms, bookings, booking financial lines, OTA sources, room plans,
   food bills/orders, services, issued tax documents and all property-scoped references.
3. Owner-only consent notice only when **all** conditions pass. Never show after
   a previous migration. 'Later' must not block normal booking use.
4. Migration function must be atomic where possible, idempotent, audited, resumable,
   protected against concurrent writes, and must preserve all existing IDs,
   amounts, taxes, payment links, booking locks, reservations and issued bills.
5. Explicit success check on fresh Firestore and a second device before marking complete.
   On conflict, show an actionable warning and preserve all existing data.
6. Bhalo Hotels first, then the second 1.0.3 test account; only then wider closed testing.

Important: A legacy device that previously moved from 1.0.3 to Code 18 may lack the Code 20
v11 backup marker. Do not infer legacy origin solely from `propertyRemoteId = null`.

Do not use the former display-only patch or live Firebase admin migration package.