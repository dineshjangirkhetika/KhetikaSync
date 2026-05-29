package com.khetika.khetikasync.data.user

import android.util.Log
import com.khetika.khetikasync.data.model.UserDto
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val postgrest: Postgrest,
) {

    suspend fun upsertUser(user: UserDto) {
        Log.d(TAG, "upsertUser(firebase_uid=${user.firebaseUid}, email=${user.email})")
        try {
            postgrest.from(TABLE).upsert(user) {
                onConflict = "firebase_uid"
            }
            Log.d(TAG, "upsertUser ok")
        } catch (e: Throwable) {
            Log.e(TAG, "upsertUser FAILED for uid=${user.firebaseUid}", e)
            throw e
        }
    }

    suspend fun getUserByFirebaseUid(firebaseUid: String): UserDto? {
        Log.d(TAG, "getUserByFirebaseUid(uid=$firebaseUid)")
        return try {
            val result = postgrest.from(TABLE)
                .select(columns = Columns.ALL) {
                    filter { eq("firebase_uid", firebaseUid) }
                    limit(1)
                }
                .decodeSingleOrNull<UserDto>()
            Log.d(TAG, "getUserByFirebaseUid found=${result != null} verified=${result?.isVerified}")
            result
        } catch (e: Throwable) {
            Log.e(TAG, "getUserByFirebaseUid FAILED for uid=$firebaseUid", e)
            throw e
        }
    }

    suspend fun findVerifiedByEmail(email: String): UserDto? {
        val normalized = email.trim().lowercase()
        if (normalized.isBlank()) return null
        Log.d(TAG, "findVerifiedByEmail(email=$normalized)")
        return try {
            postgrest.from(TABLE)
                .select(columns = Columns.ALL) {
                    filter {
                        eq("email", normalized)
                        eq("is_verified", true)
                    }
                    limit(1)
                }
                .decodeSingleOrNull<UserDto>()
        } catch (e: Throwable) {
            Log.e(TAG, "findVerifiedByEmail FAILED", e)
            throw e
        }
    }

    suspend fun listVerifiedUsersExcept(firebaseUid: String): List<UserDto> {
        Log.d(TAG, "listVerifiedUsersExcept(uid=$firebaseUid)")
        return try {
            val result = postgrest.from(TABLE)
                .select(columns = Columns.ALL) {
                    filter {
                        eq("is_verified", true)
                        neq("firebase_uid", firebaseUid)
                    }
                }
                .decodeList<UserDto>()
            Log.d(TAG, "listVerifiedUsersExcept got ${result.size} users")
            result
        } catch (e: Throwable) {
            Log.e(TAG, "listVerifiedUsersExcept FAILED", e)
            throw e
        }
    }

    suspend fun searchByEmailPrefix(
        prefix: String,
        limit: Long = 6,
    ): List<UserDto> {
        val normalized = prefix.trim().lowercase()
        if (normalized.isBlank()) return emptyList()
        return try {
            postgrest.from(TABLE)
                .select(columns = Columns.ALL) {
                    filter {
                        eq("is_verified", true)
                        ilike("email", "$normalized%")
                    }
                    limit(limit)
                }
                .decodeList<UserDto>()
        } catch (e: Throwable) {
            Log.e(TAG, "searchByEmailPrefix FAILED", e)
            emptyList()
        }
    }

    suspend fun updatePinHash(firebaseUid: String, pinHash: String) {
        Log.d(TAG, "updatePinHash(uid=$firebaseUid)")
        try {
            postgrest.from(TABLE).update(
                buildJsonObject { put("pin_hash", pinHash) }
            ) {
                filter { eq("firebase_uid", firebaseUid) }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "updatePinHash FAILED", e)
            throw e
        }
    }

    suspend fun updateFcmToken(firebaseUid: String, fcmToken: String) {
        Log.d(TAG, "updateFcmToken(uid=$firebaseUid, tokenLen=${fcmToken.length})")
        try {
            postgrest.from(TABLE).update(
                buildJsonObject { put("fcm_token", fcmToken) }
            ) {
                filter { eq("firebase_uid", firebaseUid) }
            }
            Log.d(TAG, "updateFcmToken ok")
        } catch (e: Throwable) {
            Log.e(TAG, "updateFcmToken FAILED for uid=$firebaseUid", e)
            throw e
        }
    }

    private companion object {
        const val TAG = "KhetikaUserRepo"
        const val TABLE = "users"
    }
}
