package com.khetika.khetikasync.data.notification

import android.util.Log
import com.khetika.khetikasync.data.model.NotificationDto
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val postgrest: Postgrest,
) {

    suspend fun listForRecipient(uid: String, limit: Long = 50): List<NotificationDto> {
        Log.d(TAG, "listForRecipient(uid=$uid)")
        return try {
            postgrest.from(TABLE)
                .select(columns = Columns.ALL) {
                    filter { eq("recipient_uid", uid) }
                    order("created_at", Order.DESCENDING)
                    limit(limit)
                }
                .decodeList<NotificationDto>()
        } catch (e: Throwable) {
            Log.e(TAG, "listForRecipient FAILED", e)
            throw e
        }
    }

    suspend fun unreadCount(uid: String): Long {
        return try {
            postgrest.from(TABLE)
                .select(columns = Columns.list("id")) {
                    filter {
                        eq("recipient_uid", uid)
                        eq("is_read", false)
                    }
                    count(Count.EXACT)
                }
                .countOrNull() ?: 0L
        } catch (e: Throwable) {
            Log.e(TAG, "unreadCount FAILED", e)
            0L
        }
    }

    suspend fun markRead(id: String) {
        try {
            postgrest.from(TABLE).update(
                buildJsonObject { put("is_read", true) }
            ) {
                filter { eq("id", id) }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "markRead FAILED", e)
            throw e
        }
    }

    suspend fun markAllRead(uid: String) {
        try {
            postgrest.from(TABLE).update(
                buildJsonObject { put("is_read", true) }
            ) {
                filter {
                    eq("recipient_uid", uid)
                    eq("is_read", false)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "markAllRead FAILED", e)
            throw e
        }
    }

    suspend fun insert(notification: NotificationDto) {
        try {
            postgrest.from(TABLE).insert(notification)
        } catch (e: Throwable) {
            Log.e(TAG, "insert FAILED", e)
            // Notifications are best-effort — do not bubble.
        }
    }

    private companion object {
        const val TAG = "KhetikaNotifRepo"
        const val TABLE = "notifications"
    }
}
