package com.khetika.khetikasync.data.file

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.storage.Storage
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor(
    private val storage: Storage,
    @ApplicationContext private val context: Context,
) {

    /**
     * Reads bytes behind [uri] and uploads them to the public bucket.
     * Returns the public URL that anyone with the link can open.
     */
    suspend fun uploadApprovalFile(uri: Uri, displayName: String?): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read file URI: $uri")
        val safeName = sanitize(displayName ?: "file")
        val path = "${UUID.randomUUID()}_$safeName"
        Log.d(TAG, "uploadApprovalFile path=$path size=${bytes.size}")
        try {
            val bucket = storage.from(BUCKET)
            bucket.upload(path, bytes) { upsert = false }
            val publicUrl = bucket.publicUrl(path)
            Log.d(TAG, "Uploaded → $publicUrl")
            return publicUrl
        } catch (e: Throwable) {
            Log.e(TAG, "uploadApprovalFile FAILED", e)
            throw e
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "file" }

    private companion object {
        const val TAG = "KhetikaFileRepo"
        const val BUCKET = "approval_files"
    }
}
