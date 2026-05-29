package com.khetika.khetikasync.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.khetika.khetikasync.BuildConfig
import com.khetika.khetikasync.data.user.UserRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    private val userRepository: UserRepository,
    private val prefs: SharedPreferences,
) {

    /**
     * Current identity. Firebase wins (most recent OAuth sign-in); otherwise
     * fall back to the local email+PIN session saved in [prefs].
     */
    val currentUser: AppUser?
        get() {
            val fb = firebaseAuth.currentUser
            if (fb != null) {
                return AppUser(
                    uid = fb.uid,
                    email = fb.email.orEmpty(),
                    displayName = fb.displayName,
                    photoUrl = fb.photoUrl?.toString(),
                )
            }
            val uid = prefs.getString(KEY_UID, null) ?: return null
            return AppUser(
                uid = uid,
                email = prefs.getString(KEY_EMAIL, "").orEmpty(),
                displayName = prefs.getString(KEY_DISPLAY_NAME, null),
                photoUrl = prefs.getString(KEY_PHOTO_URL, null),
            )
        }

    suspend fun signInWithGoogle(activityContext: Context): Result<AppUser> = runCatching {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        Log.d(TAG, "signInWithGoogle: webClientId blank=${webClientId.isBlank()} len=${webClientId.length}")
        check(webClientId.isNotBlank()) {
            "GOOGLE_WEB_CLIENT_ID is missing. Add it to local.properties."
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        Log.d(TAG, "Requesting credential from Credential Manager")
        val response = credentialManager.getCredential(
            context = activityContext,
            request = request,
        )

        val credential = response.credential
        Log.d(TAG, "Credential received type=${credential::class.java.simpleName}")
        check(credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type: ${credential::class.java.name}"
        }

        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        Log.d(TAG, "Got Google ID token, exchanging with Firebase")
        val firebaseCredential = GoogleAuthProvider.getCredential(
            googleIdTokenCredential.idToken,
            null,
        )

        val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
        val fb = requireNotNull(authResult.user) { "Firebase returned null user" }
        val appUser = AppUser(
            uid = fb.uid,
            email = fb.email.orEmpty(),
            displayName = fb.displayName,
            photoUrl = fb.photoUrl?.toString(),
        )
        persistSession(appUser)
        Log.d(TAG, "Firebase sign-in ok uid=${appUser.uid}")
        appUser
    }.onFailure {
        Log.e(TAG, "signInWithGoogle FAILED", it)
    }

    /**
     * Local email+PIN sign-in. Looks up the user row in Supabase, verifies the
     * PIN hash, and stores a session in SharedPreferences. Does NOT touch
     * Firebase — useful when Firebase session has expired or device is shared.
     */
    suspend fun signInWithEmailAndPin(email: String, pin: String): Result<AppUser> = runCatching {
        require(email.isNotBlank()) { "Email is required" }
        require(pin.length == 4 && pin.all { it.isDigit() }) { "PIN must be 4 digits" }

        Log.d(TAG, "signInWithEmailAndPin(email=$email)")
        val user = userRepository.findVerifiedByEmail(email)
            ?: error("No verified account for $email")

        val expected = user.pinHash
            ?: error("No PIN set for this account. Sign in with Google once and set a PIN.")

        val provided = PinHasher.hash(pin, user.email)
        check(provided == expected) { "Incorrect PIN" }

        val appUser = AppUser(
            uid = user.firebaseUid,
            email = user.email,
            displayName = user.displayName,
            photoUrl = user.photoUrl,
        )
        // We can't restore Firebase session without Admin SDK / Edge Function,
        // so we keep Firebase signed out and use the local-session fallback.
        firebaseAuth.signOut()
        persistSession(appUser)
        Log.d(TAG, "Local PIN sign-in ok uid=${appUser.uid}")
        appUser
    }.onFailure {
        Log.e(TAG, "signInWithEmailAndPin FAILED", it)
    }

    fun signOut() {
        Log.d(TAG, "signOut")
        firebaseAuth.signOut()
        prefs.edit { clear() }
    }

    private fun persistSession(user: AppUser) {
        prefs.edit {
            putString(KEY_UID, user.uid)
            putString(KEY_EMAIL, user.email)
            putString(KEY_DISPLAY_NAME, user.displayName)
            putString(KEY_PHOTO_URL, user.photoUrl)
        }
    }

    private companion object {
        const val TAG = "KhetikaAuth"
        const val KEY_UID = "uid"
        const val KEY_EMAIL = "email"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_PHOTO_URL = "photoUrl"
    }
}
