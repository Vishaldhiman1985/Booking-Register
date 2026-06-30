# Booking Register Admin Panel

This is a small private web panel for controlling hotel accounts without opening Firebase manually.

## What it controls

- View hotel accounts
- See owner email and user count
- Change subscription status
- Change maximum allowed users
- Extend access for a selected number of days

## Before first use

1. Open Firebase Console.
2. Go to Project settings.
3. Add a Web app.
4. Copy the Firebase web config.
5. Paste it into `admin/app.js` and replace:
   - `YOUR_FIREBASE_WEB_API_KEY`
   - `YOUR_FIREBASE_WEB_APP_ID`

## Deploy

From the project folder:

```powershell
npx firebase-tools deploy --only functions,hosting
```

## Domain

After hosting is deployed, connect:

```text
admin.bookingregister.in
```

in Firebase Hosting.

Keep `www.bookingregister.in` for the public website later.
