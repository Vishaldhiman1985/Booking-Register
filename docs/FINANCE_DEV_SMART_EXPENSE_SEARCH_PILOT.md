# Booking Register Finance DEV — Smart Expense Search Pilot

## Safety and scope

- Source changes are confined to `app/src/financeDev` and `app/src/testFinanceDev` plus this document.
- The existing production app, production database, Firebase, Firestore rules, Cloud Functions and sync code are untouched.
- The Finance DEV Android manifest still removes `INTERNET` permission.
- The existing Finance DEV Room database remains schema version 2; no migration or clearing of data.
- This phase is a **search and classification preview only**. It cannot save an expense, post a journal, pay a vendor or update a draft. The existing fictional draft form remains unchanged.
- Category/type visibility is stored as local Android preferences by immutable codes. This is a DEV preview without admin role enforcement; it must not be deployed as production configuration management.

## Included

- 18 categories and 175 expense types from the proposed hospitality master, bundled offline.
- 53 types visible in the small-hotel default preset; individual categories/types can be toggled in the DEV preview.
- English, Hindi and Romanized aliases, including `coal`, `koyla`, `kolla`, `koal`, `कोयला`, `bijli`, `silendar`, `putai`, `DTH`.
- Bounded spelling similarity for words of 4+ characters; exact aliases rank above typo guesses.
- Data-driven clarification choices for `gas`, `recharge`, `rent`, `labour/mazdoori` and `coal/koyla`.
- Hidden types are shown as disabled suggestions and cannot be selected by staff; the DEV settings preview can enable them.
- Manual category/type selection remains available; no auto-posting or automatic reclassification.
- JVM unit tests for synonyms, ambiguity, unknown words, and asset integrity.

## How to test on the separate Finance DEV app

1. Open **Expenses → Smart Expense Search**.
2. Search `kolla` and `कोयला`: verify the app asks whether the coal is for heating or kitchen/tandoor. The kitchen option is hidden in the small-hotel preset and must be enabled in settings before selection.
3. Search `DTH`: verify **IT, Communications & TV → DTH / Cable TV Recharge**.
4. Search `gas`: verify it asks what the fuel was used for.
5. Search `zyqplxv`: verify there is no confident match and manual selection is offered.
6. Open the settings icon next to Smart Expense Search and toggle a category or type. Reopen search and verify hidden results are blocked.
7. Verify pre-existing fictional drafts are still present and the Add Expense floating button still works.

## Known limitations / next phase

- Android Gradle build and on-device UI testing must be completed on the user's Windows machine; this patch was not built with Android SDK in the generation environment.
- Manager authorization, custom expense types, approved alias learning, full expense persistence and CA-approved statutory mapping are **not implemented**.
- The existing fictional draft form still uses six legacy category labels. The 175-code master must not be attached to that schema until a versioned Room migration and compatibility tests are approved.
- Search uses an intentionally conservative local dictionary and edit-distance ranking, not AI inference; new real-world spellings need to be added to the dictionary after validation.
- The master is a proposed CA-review seed, not an official ICAI-prescribed chart of accounts.
