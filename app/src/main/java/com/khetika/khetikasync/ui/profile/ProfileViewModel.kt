package com.khetika.khetikasync.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khetika.khetikasync.data.auth.AuthRepository
import com.khetika.khetikasync.data.model.UserDto
import com.khetika.khetikasync.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val department: String? = null,
    val role: String? = null,
    val signedOut: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun initialState(): ProfileUiState {
        val user = authRepository.currentUser
        return ProfileUiState(
            isLoading = true,
            displayName = user?.displayName.orEmpty(),
            email = user?.email.orEmpty(),
            photoUrl = user?.photoUrl,
        )
    }

    fun load() {
        val uid = authRepository.currentUser?.uid ?: run {
            _uiState.value = _uiState.value.copy(isLoading = false, signedOut = true)
            return
        }
        viewModelScope.launch {
            runCatching { userRepository.getUserByFirebaseUid(uid) }
                .onSuccess { row: UserDto? ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        displayName = row?.displayName ?: _uiState.value.displayName,
                        email = row?.email ?: _uiState.value.email,
                        photoUrl = row?.photoUrl ?: _uiState.value.photoUrl,
                        department = row?.department,
                        role = row?.role,
                    )
                }
                .onFailure { e ->
                    Log.e(TAG, "load FAILED", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: e.javaClass.simpleName,
                    )
                }
        }
    }

    fun signOut() {
        authRepository.signOut()
        _uiState.value = _uiState.value.copy(signedOut = true)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private companion object {
        const val TAG = "KhetikaProfile"
    }
}
