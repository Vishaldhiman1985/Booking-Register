# Play 1.0.3 client data recovery test

This branch starts from internal-test commit `c4886b6`. It has not been merged or released.

## What the candidate does

- During the exact Room v11 upgrade, it copies the old hotel's rooms, sources, bookings and payments into local `legacy_v11_backup_*` tables before changing the working schema.
- On first sync, it checks each old booking and payment ID against **server** Firestore data. A cached empty result cannot start a replay.
- It queues a missing booking through the existing server-authorized booking change set path. It queues a missing payment only when its parent booking already exists remotely or is queued. Stable IDs and stage markers prevent repeated automatic creation.
- A legacy booking with room tax but no detailed room-night tax lines is kept locally and flagged for review. Its tax is not guessed.
- Existing cloud documents are never overwritten by this recovery path. Server room conflicts leave the local booking and backup in place.

## Test before any client rollout

1. Keep the Play 1.0.3 app installed on the test phone. Do not clear its app data or uninstall it.
2. Record the test hotel's booking count, names, dates, room assignments, payment amounts and balance on the old phone. Include a booking known to be missing from another device and one already present in the cloud.
3. Distribute a higher-version-code candidate only to this test account through a dedicated **closed testing** track. Do not use Internal App Sharing: it uses a separate signing key and cannot reliably test an in-place upgrade from a Play-installed app.
4. Upgrade in place. Verify all old entries remain visible on the phone. Verify the new-device chart and payments match, and that opening the app a second time creates no duplicates.
5. Repeat with network unavailable during upgrade, then restore network. Verify backup rows remain and sync resumes without duplicate money records.
6. If any tax-bearing booking or room conflict is flagged, compare the old phone, cloud and bills before deciding how to resolve it. Do not clear data or widen Firestore write rules.

Production Firestore rules, Cloud Functions, the existing internal-test release and the `production` branch are untouched.

## First device baseline (5 November 2026)

- Test handset: Android 15; Play-installed `com.bookingregister.app` version 1.0.3, code 4.
- Old app shows one test booking in room L1 for 8–9 November, with a ₹2,000 balance.
- Its Sync Status reports `PERMISSION_DENIED` for that booking. The booking remains visible locally.
- The public Play-installed APK uses Google's app-signing certificate, so the candidate must arrive through a Play testing track to verify an in-place update. A locally upload-key-signed APK is not the same install path.
