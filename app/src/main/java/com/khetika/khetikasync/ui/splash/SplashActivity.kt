package com.khetika.khetikasync.ui.splash

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.khetika.khetikasync.data.auth.AuthRepository
import com.khetika.khetikasync.data.user.UserRepository
import com.khetika.khetikasync.ui.home.HomeActivity
import com.khetika.khetikasync.ui.login.LoginActivity
import com.khetika.khetikasync.ui.registration.RegistrationActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var userRepository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        var keep = true
        splashScreen.setKeepOnScreenCondition { keep }

        lifecycleScope.launch {
            val target = resolveTarget()
            Log.d(TAG, "Resolved target → ${target.simpleName}")
            keep = false
            startActivity(
                Intent(this@SplashActivity, target).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
        }
    }

    private suspend fun resolveTarget(): Class<*> {
        val firebaseUser = authRepository.currentUser
        if (firebaseUser == null) {
            Log.d(TAG, "No Firebase user → Login")
            return LoginActivity::class.java
        }
        Log.d(TAG, "Firebase user present uid=${firebaseUser.uid} email=${firebaseUser.email}")

        val supabaseUser = runCatching {
            userRepository.getUserByFirebaseUid(firebaseUser.uid)
        }.onFailure {
            Log.e(TAG, "Supabase lookup threw — treating as not-logged-in", it)
        }.getOrNull()

        return if (supabaseUser != null && supabaseUser.isVerified) {
            Log.d(TAG, "Verified user → Home")
            if (supabaseUser.fcmToken.isNullOrBlank()) {
                runCatching {
                    val token = FirebaseMessaging.getInstance().token.await()
                    userRepository.updateFcmToken(firebaseUser.uid, token)
                    Log.d(TAG, "Backfilled FCM token on cold start")
                }.onFailure { Log.w(TAG, "FCM backfill failed (non-fatal)", it) }
            }
            HomeActivity::class.java
        } else {
            // Login isn't "complete" until the profile exists + is_verified=true.
            // Sign out so the user re-authenticates instead of silently jumping to Registration.
            Log.d(TAG, "Not verified (row=${supabaseUser != null}, verified=${supabaseUser?.isVerified == true}) → signOut → Login")
            authRepository.signOut()
            LoginActivity::class.java
        }
    }

    private companion object {
        const val TAG = "KhetikaSplash"
    }
}
