package com.khetika.khetikasync.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    @SerialName("firebase_uid") val firebaseUid: String,
    @SerialName("email") val email: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("department") val department: String? = null,
    @SerialName("role") val role: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("fcm_token") val fcmToken: String? = null,
    @SerialName("pin_hash") val pinHash: String? = null,
)
