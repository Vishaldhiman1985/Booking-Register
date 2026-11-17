# Code 21 — Stage 3: opt-in upgrade notice component (isolated, not activated)

Source: `feature/legacy-property-opt-in-code21` at
`51ed262de8350fbea7cba2835ffdc367ed415451`.

Protected Code 20: `booking-register-legacy-recovery-closed-test` at
`6e7735c7808b56e0c2080e50199ec20e9ba0e2d5`.

## Allowlist: additions only

- `app/src/main/java/com/example/bookingregister/legacy/Legacy103UpgradeNoticePolicy.kt`
- `app/src/main/java/com/example/bookingregister/legacy/Legacy103UpgradeNoticeDialog.kt`
- `app/src/test/java/com/example/bookingregister/legacy/Legacy103UpgradeNoticePolicyTest.kt`
- `docs/LEGACY_103_STAGE3_NOTICE.md`

No existing source, Gradle, AndroidManifest, database migrations, sync,
recovery, booking, financial, Cloud Function, or Firebase rules files change.

## What is implemented

- One fail-closed eligibility policy combines the existing original-booking/payment
  audit, Stage 2 property readiness, the existing preflight decision, and explicit
  future server-proof requirements.
- Checks include verified 1.0.3 origin, authenticated OWNER role, fresh server
  state, verified immutable backup, enforced write exclusion, a ready migration
  service, no prior migration, and a one-time unhandled notice marker.
- A Material dialog with **Later** and **Continue**. The dialog itself does not
  read or write Firebase/Room. Buttons only invoke user-intent callbacks; there
  is no conversion implementation in this stage.
- 15 pure unit tests exercise eligible and ineligible cases.

## Critical non-features (deliberate safety)

- This code is **not wired into login, booking chart, or customer screens**.
- The notice is **not visible in any installed app** yet.
- There is no migration button connected to a server function.
- There is no server-side migration lock, owner-status token, immutable off-device
  backup proof, persisted notice-handled marker, or production conversion endpoint.
  Accordingly, no current production path can supply the full verified evidence.
- A passing policy test is NOT a test of a real hotel's data and NOT permission
  to migrate. The final server endpoint must revalidate authorization, record
  consistency and concurrent-write exclusion in a protected transaction.
- DO NOT install this development APK on customer phones.

## Tests and source security

The installer checks the exact two pinned branches, clean Git state, SHA-256
checksums for all four package files, and an additions-only file allowlist.
Tests run under a short Windows Robolectric HOME/TEMP path to avoid the
Windows native-loader issue observed in Stage 2.

It commits and pushes **only after the full app unit-test suite and debug APK
build pass**. On any failure, no commit/push occurs and existing app logic is
unchanged. This stage does not execute Firebase or network operations from code.
