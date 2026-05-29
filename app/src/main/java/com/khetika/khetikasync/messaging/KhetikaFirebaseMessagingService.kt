package com.khetika.khetikasync.messaging

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.khetika.khetikasync.KhetikaApplication.Companion.CHANNEL_APPROVALS
import com.khetika.khetikasync.R
import com.khetika.khetikasync.data.auth.AuthRepository
import com.khetika.khetikasync.data.user.UserRepository
import com.khetika.khetikasync.ui.detail.RequestDetailActivity
import com.khetika.khetikasync.ui.home.HomeActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class KhetikaFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var userRepository: UserRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "onNewToken len=${token.length}")
        val uid = authRepository.currentUser?.uid
        if (uid == null) {
            Log.d(TAG, "No Firebase user — token will be saved on next Registration submit")
            return
        }
        scope.launch {
            runCatching { userRepository.updateFcmToken(uid, token) }
                .onFailure { Log.e(TAG, "Failed to push token to Supabase", it) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "onMessageReceived from=${message.from} data=${message.data}")

        // Prefer data payload (works in both foreground and background).
        val data = message.data
        val title = data["title"]
            ?: message.notification?.title
            ?: "Khetika Sync"
        val body = data["body"]
            ?: message.notification?.body
            ?: ""
        val requestId = data["request_id"]

        postNotification(
            title = title,
            body = body,
            requestId = requestId,
        )
    }

    private fun postNotification(title: String, body: String, requestId: String?) {
        val tapIntent = if (requestId != null) {
            // Open the request detail deep-linked, with Home in the back stack
            // so Back returns to the list naturally.
            val parent = Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val child = RequestDetailActivity.intent(this, requestId).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            PendingIntent.getActivities(
                this,
                requestId.hashCode(),
                arrayOf(parent, child),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, HomeActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_APPROVALS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Use the request id (when present) as the notification id so we
        // collapse repeat alerts for the same request into a single line.
        val notifId = requestId?.hashCode() ?: System.currentTimeMillis().toInt()
        manager.notify(notifId, notification)
        Log.d(TAG, "Posted system notification id=$notifId requestId=$requestId")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "KhetikaFcm"
    }
}
