package com.example.bookingregister.account.domain

object MemberRole {
    const val OWNER = "OWNER"
    const val MANAGER = "MANAGER"
    const val STAFF = "STAFF"
}

object SubscriptionStatus {
    const val TRIALING = "TRIALING"
    const val ACTIVE = "ACTIVE"
    const val GRACE = "GRACE"
    const val ACCOUNT_HOLD = "ACCOUNT_HOLD"
    const val EXPIRED = "EXPIRED"
    const val CANCELLED = "CANCELLED"
}

object PlanCode {
    const val BASIC = "BASIC"
}

data class PlanLimits(
    val planCode: String,
    val maxRooms: Int,
    val maxUsers: Int,
    val maxDevices: Int
)

object Plans {
    val basic = PlanLimits(
        planCode = PlanCode.BASIC,
        maxRooms = 20,
        maxUsers = 2,
        maxDevices = 2
    )
}

data class HotelAccessState(
    val hotelId: String,
    val planCode: String,
    val subscriptionStatus: String,
    val trialEndsAt: Long?,
    val subscriptionEndsAt: Long?,
    val maxRooms: Int,
    val maxUsers: Int,
    val maxDevices: Int
) {
    fun canUseFullApp(now: Long = System.currentTimeMillis()): Boolean {
        return when (subscriptionStatus) {
            SubscriptionStatus.TRIALING -> trialEndsAt == null || now <= trialEndsAt
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.GRACE -> true
            else -> false
        }
    }

    fun canEditData(now: Long = System.currentTimeMillis()): Boolean {
        return canUseFullApp(now)
    }
}

data class HotelMember(
    val uid: String,
    val email: String,
    val role: String,
    val active: Boolean
)
