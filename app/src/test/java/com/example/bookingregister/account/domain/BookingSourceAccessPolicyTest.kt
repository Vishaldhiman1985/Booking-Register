package com.example.bookingregister.account.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingSourceAccessPolicyTest {

    private val policy = AccountAccessPolicy()

    @Test
    fun ownerCanManageBookingSources() {
        assertTrue(
            AccountPermission.MANAGE_SOURCES in policy.permissionsFor(MemberRole.OWNER)
        )
    }

    @Test
    fun managerCanManageBookingSources() {
        assertTrue(
            AccountPermission.MANAGE_SOURCES in policy.permissionsFor(MemberRole.MANAGER)
        )
    }

    @Test
    fun staffCannotManageBookingSources() {
        assertFalse(
            AccountPermission.MANAGE_SOURCES in policy.permissionsFor(MemberRole.STAFF)
        )
    }

    @Test
    fun unknownRoleCannotManageBookingSources() {
        assertFalse(
            AccountPermission.MANAGE_SOURCES in policy.permissionsFor("UNKNOWN")
        )
    }
}