package com.khetika.khetikasync.ui.registration

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.khetika.khetikasync.data.auth.AuthRepository
import com.khetika.khetikasync.data.auth.PinHasher
import com.khetika.khetikasync.data.model.UserDto
import com.khetika.khetikasync.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class RegistrationUiState(
    val department: String? = null,
    val pin: String = "",
    val confirmPin: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val submitted: Boolean = false,
) {
    val pinValid: Boolean get() = pin.length == 4 && pin.all { it.isDigit() }
    val pinsMatch: Boolean get() = pin == confirmPin

    val canSubmit: Boolean
        get() = !isSubmitting && !department.isNullOrBlank() && pinValid && pinsMatch
}

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    val departments: List<String> = listOf(
        "Admin",
        "Commercial",
        "Finance",
        "Human Resource",
        "Khetika Saathi",
        "Marketing",
        "Operations",
        "Operations Fresh",
        "Operations Grocery",
        "Quality",
        "Quality Assurance",
        "Sales",
        "Sales - DFM & Spices",
        "Sales - Fresh",
        "Sales - GT East",
        "Sales - NPD & Support",
        "Sales - Superzop",
        "Spices Division",
        "Supply Chain",
        "Technology",
    )

    fun onDepartmentSelected(value: String) {
        _uiState.value = _uiState.value.copy(department = value)
    }

    fun onPinChange(value: String) {
        val cleaned = value.filter { it.isDigit() }.take(4)
        _uiState.value = _uiState.value.copy(pin = cleaned)
    }

    fun onConfirmPinChange(value: String) {
        val cleaned = value.filter { it.isDigit() }.take(4)
        _uiState.value = _uiState.value.copy(confirmPin = cleaned)
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        val firebaseUser = authRepository.currentUser ?: run {
            _uiState.value = state.copy(errorMessage = "Not signed in")
            return
        }

        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val fcmToken = runCatching {
                FirebaseMessaging.getInstance().token.await()
            }.onFailure {
                Log.w(TAG, "Failed to fetch FCM token — proceeding without it", it)
            }.getOrNull()

            val pinHash = PinHasher.hash(state.pin, firebaseUser.email)
            val dto = UserDto(
                firebaseUid = firebaseUser.uid,
                email = firebaseUser.email,
                displayName = firebaseUser.displayName,
                photoUrl = firebaseUser.photoUrl,
                department = state.department,
                role = null,
                isVerified = true,
                fcmToken = fcmToken,
                pinHash = pinHash,
            )
            Log.d(TAG, "submit: uid=${dto.firebaseUid} dept=${state.department}")
            runCatching {
                userRepository.upsertUser(dto)
            }.onSuccess {
                Log.d(TAG, "submit ok — profile saved, login complete")
                _uiState.value = _uiState.value.copy(isSubmitting = false, submitted = true)
            }.onFailure { e ->
                Log.e(TAG, "submit FAILED", e)
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private companion object {
        const val TAG = "KhetikaRegister"
    }
}
