package com.example.bookingregister.account.domain

data class StaffInvitation(
    val email: String,
    val role: String,
    val invitedByUid: String,
    val createdAt: Long,
    val expiresAt: Long,
    val status: String
)

object StaffInvitationStatus {
    const val PENDING = "PENDING"
    const val ACCEPTED = "ACCEPTED"
    const val CANCELLED = "CANCELLED"
    const val EXPIRED = "EXPIRED"
}

class StaffInvitationPolicy {
    fun canCreateInvite(
        accessState: HotelAccessState,
        actor: HotelMember,
        currentActiveUsers: Int,
        now: Long = System.currentTimeMillis()
    ): AccessDecision {
        val accountPolicy = AccountAccessPolicy()
        val roleDecision = accountPolicy.decide(accessState, actor, AccountPermission.MANAGE_STAFF, now)
        if (!roleDecision.allowed) return roleDecision
        return accountPolicy.canAddUser(accessState, currentActiveUsers, now)
    }
}
