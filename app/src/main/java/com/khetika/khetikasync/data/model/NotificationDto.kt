package com.khetika.khetikasync.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    @SerialName("id") val id: String? = null,
    @SerialName("recipient_uid") val recipientUid: String,
    @SerialName("type") val type: String,
    @SerialName("title") val title: String,
    @SerialName("body") val body: String? = null,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ApprovalFileDto(
    @SerialName("id") val id: String? = null,
    @SerialName("request_id") val requestId: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("file_url") val fileUrl: String,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
)
