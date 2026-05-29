package com.khetika.khetikasync.ui.login

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.khetika.khetikasync.data.auth.AuthRepository
import com.khetika.khetikasync.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

enum class PostSignInDestination { HOME, REGISTRATION }

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val destination: PostSignInDestination? = null,
    val email: String = "",
    val pin: String = "",
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value)
    }

    fun onPinChange(value: String) {
        // Numeric, max 4.
        val cleaned = value.filter { it.isDigit() }.take(4)
        _uiState.value = _uiState.value.copy(pin = cleaned)
    }

    fun signInWithGoogle(activityContext: Context) {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            Log.d(TAG, "signInWithGoogle: starting")
            authRepository.signInWithGoogle(activityContext)
                .onSuccess { appUser -> finishLoginRouting(appUser.uid) }
                .onFailure { e ->
                    Log.e(TAG, "Firebase sign-in failed", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: e.javaClass.simpleName,
                    )
                }
        }
    }

    fun signInWithEmailAndPin() {
        if (_uiState.value.isLoading) return
        val state = _uiState.value
        if (state.email.isBlank() || state.pin.length != 4) {
            _uiState.value = state.copy(errorMessage = "Enter email and 4-digit PIN")
            return
        }
        _uiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            authRepository.signInWithEmailAndPin(state.email.trim(), state.pin)
                .onSuccess { appUser -> finishLoginRouting(appUser.uid) }
                .onFailure { e ->
                    Log.e(TAG, "PIN sign-in failed", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: e.javaClass.simpleName,
                    )
                }
        }
    }

    private suspend fun finishLoginRouting(uid: String) {
        val destination = runCatching {
            val existing = userRepository.getUserByFirebaseUid(uid)
            when {
                existing == null -> PostSignInDestination.REGISTRATION
                !existing.isVerified -> PostSignInDestination.REGISTRATION
                else -> {
                    if (existing.fcmToken.isNullOrBlank()) {
                        runCatching {
                            val token = FirebaseMessaging.getInstance().token.await()
                            userRepository.updateFcmToken(uid, token)
                            Log.d(TAG, "Backfilled FCM token")
                        }.onFailure { Log.w(TAG, "FCM backfill failed (non-fatal)", it) }
                    }
                    PostSignInDestination.HOME
                }
            }
        }.getOrElse { e ->
            Log.e(TAG, "Supabase lookup failed — signing out", e)
            authRepository.signOut()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = e.message ?: e.javaClass.simpleName,
            )
            return
        }
        Log.d(TAG, "Routing to $destination")
        _uiState.value = _uiState.value.copy(isLoading = false, destination = destination)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private companion object {
        const val TAG = "KhetikaLogin"
    }
}
