package com.khetika.khetikasync.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApprovalRequestDto(
    @SerialName("id") val id: String? = null,
    @SerialName("requester_uid") val requesterUid: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("department") val department: String,
    @SerialName("category") val category: String? = null,
    @SerialName("priority") val priority: String = "Normal",
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("file_uri") val fileUri: String? = null,
    @SerialName("levels") val levels: Int = 1,
    @SerialName("status") val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class ApprovalStepDto(
    @SerialName("id") val id: String? = null,
    @SerialName("request_id") val requestId: String,
    @SerialName("level") val level: Int,
    @SerialName("approver_uid") val approverUid: String,
    @SerialName("status") val status: String = "pending",
    @SerialName("note") val note: String? = null,
    @SerialName("acted_at") val actedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)
