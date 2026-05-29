package com.khetika.khetikasync.ui.notifications

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khetika.khetikasync.data.auth.AuthRepository
import com.khetika.khetikasync.data.model.NotificationDto
import com.khetika.khetikasync.data.notification.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val notifications: List<NotificationDto> = emptyList(),
    val errorMessage: String? = null,
    /** When non-null, the Activity should open RequestDetail for this id. */
    val openRequestId: String? = null,
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val uid = authRepository.currentUser?.uid ?: run {
            _uiState.value = _uiState.value.copy(isLoading = false)
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            runCatching { notificationRepository.listForRecipient(uid) }
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(isLoading = false, notifications = list)
                }
                .onFailure { e ->
                    Log.e(TAG, "refresh FAILED", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: e.javaClass.simpleName,
                    )
                }
        }
    }

    fun onTapNotification(notification: NotificationDto) {
        val id = notification.id
        viewModelScope.launch {
            if (id != null && !notification.isRead) {
                runCatching { notificationRepository.markRead(id) }
            }
            _uiState.value = _uiState.value.copy(
                openRequestId = notification.requestId,
                notifications = _uiState.value.notifications.map {
                    if (it.id == id) it.copy(isRead = true) else it
                }
            )
        }
    }

    fun consumeOpenRequest() {
        _uiState.value = _uiState.value.copy(openRequestId = null)
    }

    fun onMarkAllRead() {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching { notificationRepository.markAllRead(uid) }
            refresh()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private companion object {
        const val TAG = "KhetikaNotif"
    }
}
