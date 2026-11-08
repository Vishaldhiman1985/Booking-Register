Booking Register Finance DEV — Phase 2F
=======================================

PATCH SCOPE
- Only app/src/financeDev and app/src/testFinanceDev.
- No production source, release signing, Firebase configuration, network access,
  server rules, or finance-domain changes.
- Created against the exact Finance-Phase2F-Source.zip uploaded by the user.

FEATURES
1. Smart Expense Search selection closes search and opens the fictional Add Expense draft.
   Category, type, and permanent codes are prefilled. Selecting from within the draft
   updates the classification in place.
2. New fictional drafts persist canonical category code, expense type code, and label
   snapshot. Editing existing six-category drafts preserves the legacy classification
   unless explicitly changed. No financial posting is enabled.
3. Room database version 2 -> 3 adds nullable classification fields and append-only
   local CA mapping/tax-note proposal history. Migration 1 -> 2 is retained.
4. Expenses top-right menu -> Accounting Standards & Tax Rules permits *pending*
   local proposals with source reference, effective date, reason, and history.
   No CA identity verification, approval, activation, tax rates, or statutory
   filing is implemented. All existing seed mappings remain unchanged.
5. The Add Expense draft requires deliberate selection of a fictional payee; it no
   longer silently defaults DTH recharge to the demo laundry vendor.

IMPORTANT SAFETY
- This is an offline Finance DEV preview only. No real expense or payment posting.
- Do not uninstall the Finance DEV app or clear app storage.
- Use adb install -r after BUILD SUCCESSFUL to preserve Finance DEV local drafts.
- Do not merge, push, or deploy to the 39 production Booking Register users.

APPLY (from the current Booking-Register project folder):
Expand-Archive -LiteralPath "$env:USERPROFILE\Downloads\Finance-Phase2F-Patch.zip" -DestinationPath . -Force

TEST:
.\gradlew.bat '-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false' :finance-domain:test :app:testFinanceDevUnitTest :app:assembleFinanceDev

After a successful build, check Finance DEV on the device before committing.

VERIFICATION PERFORMED IN PATCH ENVIRONMENT
- Kotlin policy sources compiled with actual Money.kt and synthetic Room contract stubs.
- SQLite migration SQL executed against a synthetic legacy draft; row preserved.
- Policy smoke tests passed for canonical IDs, legacy data, and rejecting fake CA approval.
- Full Android Gradle build and on-device migration cannot run in this environment;
  they must be tested on the user's Windows Android Studio installation.
