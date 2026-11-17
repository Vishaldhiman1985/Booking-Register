package com.example.bookingregister.legacy

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Presentation only. This component is deliberately NOT connected to any
 * Activity or migration service in Stage 3. It makes no Firebase, Room, or
 * sync calls and it cannot perform a property conversion.
 *
 * Callbacks only express user intent. A later, separately reviewed server
 * implementation must recheck eligibility and enforce transaction safety.
 */
internal object Legacy103UpgradeNoticeDialog {
    fun showIfEligible(
        activity: Activity,
        evidence: Legacy103UpgradeNoticeEvidence,
        onLater: () -> Unit,
        onOwnerInterested: () -> Unit
    ): Boolean {
        val decision = Legacy103UpgradeNoticePolicy.evaluate(evidence)
        if (!decision.mayOfferNotice || activity.isFinishing || activity.isDestroyed) {
            return false
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Upgrade to multi-property management?")
            .setMessage(
                "Booking Register can now manage multiple properties within one account. " +
                    "Your existing bookings, room IDs, payments and financial records " +
                    "must remain intact.\n\n" +
                    "You can choose Later and continue using Booking Register normally. " +
                    "Choosing Continue only signals your interest; this notice does " +
                    "not change or migrate any records."
            )
            .setNegativeButton("Later") { _, _ -> onLater() }
            .setPositiveButton("Continue") { _, _ -> onOwnerInterested() }
            .setOnCancelListener { onLater() }
            .show()
        return true
    }
}
