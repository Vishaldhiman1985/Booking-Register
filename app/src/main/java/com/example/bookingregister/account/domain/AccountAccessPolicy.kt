package com.example.bookingregister.account.domain

object AccountPermission {
    const val VIEW_BOOKINGS = "VIEW_BOOKINGS"
    const val EDIT_BOOKINGS = "EDIT_BOOKINGS"
    const val TAKE_FOOD_ORDERS = "TAKE_FOOD_ORDERS"
    const val ADD_SERVICES = "ADD_SERVICES"
    const val MANAGE_ROOMS = "MANAGE_ROOMS"
    const val MANAGE_CATEGORIES = "MANAGE_CATEGORIES"
    const val VIEW_REPORTS = "VIEW_REPORTS"
    const val TAKE_PAYMENTS = "TAKE_PAYMENTS"
    const val MANAGE_STAFF = "MANAGE_STAFF"
    const val MANAGE_SUBSCRIPTION = "MANAGE_SUBSCRIPTION"
}

data class AccessDecision(
    val allowed: Boolean,
    val readOnly: Boolean = false,
    val reason: String? = null
)

class AccountAccessPolicy {
    fun permissionsFor(role: String): Set<String> {
        return when (role) {
            MemberRole.OWNER -> setOf(
                AccountPermission.VIEW_BOOKINGS,
                AccountPermission.EDIT_BOOKINGS,
                AccountPermission.TAKE_FOOD_ORDERS,
                AccountPermission.ADD_SERVICES,
                AccountPermission.MANAGE_ROOMS,
                AccountPermission.MANAGE_CATEGORIES,
                AccountPermission.VIEW_REPORTS,
                AccountPermission.TAKE_PAYMENTS,
                AccountPermission.MANAGE_STAFF,
                AccountPermission.MANAGE_SUBSCRIPTION
            )
            MemberRole.MANAGER -> setOf(
                AccountPermission.VIEW_BOOKINGS,
                AccountPermission.EDIT_BOOKINGS,
                AccountPermission.TAKE_FOOD_ORDERS,
                AccountPermission.ADD_SERVICES,
                AccountPermission.MANAGE_ROOMS,
                AccountPermission.MANAGE_CATEGORIES,
                AccountPermission.VIEW_REPORTS,
                AccountPermission.TAKE_PAYMENTS
            )
            else -> setOf(
                AccountPermission.VIEW_BOOKINGS,
                AccountPermission.TAKE_FOOD_ORDERS,
                AccountPermission.ADD_SERVICES,
                AccountPermission.TAKE_PAYMENTS
            )
        }
    }

    fun decide(
        accessState: HotelAccessState,
        member: HotelMember,
        permission: String,
        now: Long = System.currentTimeMillis()
    ): AccessDecision {
        if (!member.active) {
            return AccessDecision(false, reason = "This user is inactive.")
        }

        val hasPermission = permission in permissionsFor(member.role)
        if (!hasPermission) {
            return AccessDecision(false, reason = "This user does not have permission.")
        }

        if (!accessState.canUseFullApp(now)) {
            return when (permission) {
                AccountPermission.VIEW_BOOKINGS,
                AccountPermission.VIEW_REPORTS -> AccessDecision(true, readOnly = true)
                else -> AccessDecision(false, readOnly = true, reason = "Subscription is not active.")
            }
        }

        return AccessDecision(true)
    }

    fun canAddRoom(accessState: HotelAccessState, currentRoomCount: Int, now: Long = System.currentTimeMillis()): AccessDecision {
        if (!accessState.canEditData(now)) {
            return AccessDecision(false, readOnly = true, reason = "Subscription is not active.")
        }
        if (currentRoomCount >= accessState.maxRooms) {
            return AccessDecision(false, reason = "Room limit reached for this plan.")
        }
        return AccessDecision(true)
    }

    fun canAddUser(accessState: HotelAccessState, currentUserCount: Int, now: Long = System.currentTimeMillis()): AccessDecision {
        if (!accessState.canEditData(now)) {
            return AccessDecision(false, readOnly = true, reason = "Subscription is not active.")
        }
        if (currentUserCount >= accessState.maxUsers) {
            return AccessDecision(false, reason = "User limit reached for this plan.")
        }
        return AccessDecision(true)
    }
}
