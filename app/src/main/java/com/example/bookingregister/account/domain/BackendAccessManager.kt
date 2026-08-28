package com.example.bookingregister.account.domain

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

class BackendAccessManager(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-south1"),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    suspend fun bootstrapOwner(): BackendAccess {
        val idToken = freshAuthToken()
        val result = functions
            .getHttpsCallable("bootstrapHotelOwner")
            .call(mapOf("idToken" to idToken))
            .await()

        return BackendAccess.from(result.data).copy(allowed = true)
    }

    suspend fun getMyAccess(forceRefreshToken: Boolean = false): BackendAccess {
        val idToken = authToken(forceRefreshToken)
        val result = functions
            .getHttpsCallable("getMyHotelAccess")
            .call(mapOf("idToken" to idToken))
            .await()

        return BackendAccess.from(result.data)
    }

    suspend fun claimMyDevice(
        deviceId: String,
        deviceName: String
    ): DeviceClaimResult {
        val result = functions
            .getHttpsCallable("claimMyDevice")
            .call(
                mapOf(
                    "deviceId" to deviceId.trim(),
                    "deviceName" to deviceName.trim()
                )
            )
            .await()

        return DeviceClaimResult.from(result.data)
    }

    suspend fun logoutMyDevice(
        hotelId: String,
        deviceId: String
    ): DeviceLogoutResult {
        val result = functions
            .getHttpsCallable("logoutMyDevice")
            .call(
                mapOf(
                    "hotelId" to hotelId.trim(),
                    "deviceId" to deviceId.trim()
                )
            )
            .await()

        return DeviceLogoutResult.from(result.data)
    }


    suspend fun createHotelUser(
        email: String,
        password: String,
        displayName: String,
        role: String
    ): CreatedHotelUser {
        val idToken = freshAuthToken()
        val result = functions
            .getHttpsCallable("createHotelUser")
            .call(
                mapOf(
                    "idToken" to idToken,
                    "email" to email.trim(),
                    "password" to password,
                    "displayName" to displayName.trim(),
                    "role" to role.trim().uppercase()
                )
            )
            .await()

        return CreatedHotelUser.from(result.data)
    }

    private suspend fun freshAuthToken(): String {
        return authToken(forceRefresh = true)
    }

    private suspend fun authToken(forceRefresh: Boolean): String {
        val user = auth.currentUser ?: error("Please login again.")
        return user.getIdToken(forceRefresh).await().token ?: error("Please login again.")
    }
}

data class CreatedHotelUser(
    val uid: String,
    val email: String,
    val role: String,
    val created: Boolean
) {
    companion object {
        fun from(data: Any?): CreatedHotelUser {
            val map = data as? Map<*, *> ?: emptyMap<String, Any?>()
            return CreatedHotelUser(
                uid = map["uid"] as? String ?: "",
                email = map["email"] as? String ?: "",
                role = map["role"] as? String ?: "",
                created = map["created"] as? Boolean ?: false
            )
        }
    }
}

data class DeviceClaimResult(
    val allowed: Boolean,
    val hotelId: String?,
    val deviceId: String?,
    val deviceStatus: String?,
    val decision: String?,
    val reason: String?
) {
    companion object {
        fun from(data: Any?): DeviceClaimResult {
            val map = data as? Map<*, *> ?: emptyMap<String, Any?>()

            return DeviceClaimResult(
                allowed = map["allowed"] as? Boolean ?: false,
                hotelId = map["hotelId"] as? String,
                deviceId = map["deviceId"] as? String,
                deviceStatus = map["deviceStatus"] as? String,
                decision = map["decision"] as? String,
                reason = map["reason"] as? String
            )
        }
    }
}

data class DeviceLogoutResult(
    val released: Boolean,
    val hotelId: String?,
    val deviceId: String?,
    val deviceStatus: String?,
    val reason: String?
) {
    companion object {
        fun from(data: Any?): DeviceLogoutResult {
            val map = data as? Map<*, *> ?: emptyMap<String, Any?>()

            return DeviceLogoutResult(
                released = map["released"] as? Boolean ?: false,
                hotelId = map["hotelId"] as? String,
                deviceId = map["deviceId"] as? String,
                deviceStatus = map["deviceStatus"] as? String,
                reason = map["reason"] as? String
            )
        }
    }
}
data class BackendAccess(
    val allowed: Boolean,
    val hotelId: String?,
    val role: String?,
    val permissions: Set<String>,
    val status: String?,
    val reason: String?,
    val accessUntilMillis: Long
) {
    val shouldBootstrapOwner: Boolean
        get() = reason == "NO_ACTIVE_MEMBERSHIP"

    fun blockedMessage(): String {
        return when (reason) {
            "NO_ACTIVE_MEMBERSHIP" -> "This login is not connected to an active hotel account."
            else -> when (status) {
                "PAST_DUE" -> "Subscription payment is pending. Please contact the owner."
                "SUSPENDED" -> "This hotel account is suspended. Please contact support."
                else -> "This account is not active. Please contact support."
            }
        }
    }

    companion object {
        fun from(data: Any?): BackendAccess {
            val map = data as? Map<*, *> ?: emptyMap<String, Any?>()
            val explicitPermissions = (map["permissions"] as? List<*>)
                ?.mapNotNull { it as? String }
                ?.map { it.trim().uppercase() }
                ?.filter { it.isNotBlank() }
                ?.toSet()
                .orEmpty()
            return BackendAccess(
                allowed = map["allowed"] as? Boolean ?: false,
                hotelId = map["hotelId"] as? String,
                role = map["role"] as? String,
                permissions = explicitPermissions.ifEmpty {
                    AccountAccessPolicy().permissionsFor((map["role"] as? String).orEmpty())
                },
                status = map["status"] as? String,
                reason = map["reason"] as? String,
                accessUntilMillis = (map["accessUntilMillis"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}
