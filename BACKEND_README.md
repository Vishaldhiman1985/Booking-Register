# Booking Register Backend

This backend is the first PMS-grade control layer for Booking Register.
It is intentionally separate from the Android single-module app.

## What this backend controls

- Hotel account creation
- Owner membership
- Staff/manager user creation
- Maximum active user limit per hotel
- Trial/subscription status
- Manual subscription control by platform admin

## Cloud Functions

- `bootstrapHotelOwner`
  Creates `/hotelAccounts/{hotelId}` for the signed-in owner and sets custom claims.

- `createHotelUser`
  Owner/manager creates a staff or manager login under the same hotel account.
  It blocks creation if active users are already at the plan limit.

- `setHotelUserActive`
  Owner/manager can activate or deactivate a hotel user.

- `setHotelSubscription`
  Platform admin updates subscription status, max users, plan, and access date.

- `getMyHotelAccess`
  App can call this after login to check whether the user is allowed to continue.

## Data model

```text
hotelAccounts/{hotelId}
  ownerUid
  ownerEmail
  planId
  status: TRIALING | ACTIVE | PAST_DUE | SUSPENDED
  maxUsers
  accessUntil
  trialStartedAt
  trialEndsAt

hotelAccounts/{hotelId}/members/{uid}
  email
  displayName
  role: OWNER | MANAGER | STAFF
  active
```

Existing operational data remains under:

```text
hotels/{hotelId}/rooms
hotels/{hotelId}/roomCategories
hotels/{hotelId}/bookings
hotels/{hotelId}/bookingSources
hotels/{hotelId}/bookingLocks
```

## Setup

1. Install Firebase CLI if needed.
2. Open terminal in the project root.
3. Run:

```powershell
cd functions
npm install
Copy-Item .env.example .env
```

4. Edit `functions/.env` and set your admin email:

```text
PLATFORM_ADMIN_EMAILS=your-real-admin-email@gmail.com
```

5. Build:

```powershell
npm run build
```

6. Run the emulator safety suite:

```powershell
npm test
```

7. Deploy the Functions and the matching Firestore Rules together:

```powershell
npm run deploy
```

## Important

The current Android app uses shared hotel IDs and active hotel membership documents.
Its critical booking, payment, accounting, billing, lock, audit, and room-lifecycle
writes are server-owned. Do not deploy Functions without the matching checked-in
Firestore Rules.

After every deployment, verify the production function inventory. Legacy callable
functions that are no longer exported by `functions/src/index.ts` must not remain
deployed, because an older APK could otherwise bypass the current server policies.
