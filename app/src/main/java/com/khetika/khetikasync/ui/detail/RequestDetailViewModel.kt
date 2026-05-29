package com.khetika.khetikasync.ui.detail

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khetika.khetikasync.data.approval.ApprovalRepository
import com.khetika.khetikasync.data.approval.RequestWithSteps
import com.khetika.khetikasync.data.auth.AuthRepository
import com.khetika.khetikasync.data.file.FileRepository
import com.khetika.khetikasync.data.file.PickedFile
import com.khetika.khetikasync.data.model.UserDto
import com.khetika.khetikasync.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RequestDetailUiState(
    val isLoading: Boolean = true,
    val data: RequestWithSteps? = null,
    val usersById: Map<String, UserDto> = emptyMap(),
    val comment: String = "",
    val resubmitComment: String = "",
    val resubmitFiles: List<PickedFile> = emptyList(),
    val isActing: Boolean = false,
    val isResubmitting: Boolean = false,
    val isReminding: Boolean = false,
    val reminderSentFor: String? = null,
    val isDeleting: Boolean = false,
    val deleted: Boolean = false,
    val errorMessage: String? = null,
    val isCurrentUserActiveApprover: Boolean = false,
    val isRequester: Boolean = false,
)

@HiltViewModel
class RequestDetailViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val approvalRepository: ApprovalRepository,
    private val userRepository: UserRepository,
    private val fileRepository: FileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val requestId: String =
        requireNotNull(savedStateHandle[ARG_REQUEST_ID]) { "missing requestId" }

    private val _uiState = MutableStateFlow(RequestDetailUiState())
    val uiState: StateFlow<RequestDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        val me = authRepository.currentUser?.uid
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                val data = approvalRepository.getRequestWithSteps(requestId)
                    ?: error("Request not found")
                val approverUids = data.steps.map { it.approverUid }.toSet()
                val users = if (approverUids.isEmpty() || me == null) emptyMap()
                else userRepository.listVerifiedUsersExcept("___none___")
                    .filter { it.firebaseUid in approverUids || it.firebaseUid == data.request.requesterUid }
                    .associateBy { it.firebaseUid }
                data to users
            }.onSuccess { (data, users) ->
                val active = data.activeStep
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    data = data,
                    usersById = users,
                    isCurrentUserActiveApprover = me != null && active?.approverUid == me,
                    isRequester = me != null && data.request.requesterUid == me,
                )
            }.onFailure { e ->
                Log.e(TAG, "load FAILED", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    fun onCommentChange(value: String) {
        _uiState.value = _uiState.value.copy(comment = value)
    }

    fun onResubmitCommentChange(value: String) {
        _uiState.value = _uiState.value.copy(resubmitComment = value)
    }

    fun onResubmitFilesAdded(picked: List<PickedFile>) {
        val current = _uiState.value
        val merged = (current.resubmitFiles + picked).distinctBy { it.uri }
        _uiState.value = current.copy(resubmitFiles = merged)
    }

    fun onResubmitFileRemoved(uri: String) {
        val current = _uiState.value
        _uiState.value = current.copy(resubmitFiles = current.resubmitFiles.filterNot { it.uri == uri })
    }

    fun resubmit() {
        val state = _uiState.value
        if (state.isResubmitting) return
        val requestId = state.data?.request?.id ?: return
        _uiState.value = state.copy(isResubmitting = true, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                val uploaded = state.resubmitFiles.map { picked ->
                    val url = fileRepository.uploadApprovalFile(Uri.parse(picked.uri), picked.displayName)
                    picked.displayName to url
                }
                approvalRepository.resubmit(
                    requestId = requestId,
                    comment = state.resubmitComment.trim().ifBlank { null },
                    newFiles = uploaded,
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isResubmitting = false,
                    resubmitComment = "",
                    resubmitFiles = emptyList(),
                )
                load()
            }.onFailure { e ->
                Log.e(TAG, "resubmit FAILED", e)
                _uiState.value = _uiState.value.copy(
                    isResubmitting = false,
                    errorMessage = e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    fun approve() = act { stepId ->
        approvalRepository.approveStep(stepId, requestId, _uiState.value.comment.trim().ifBlank { null })
    }

    fun reject() = act { stepId ->
        approvalRepository.rejectStep(stepId, requestId, _uiState.value.comment.trim().ifBlank { null })
    }

    fun sendBack() = act { stepId ->
        approvalRepository.sendBackStep(stepId, requestId, _uiState.value.comment.trim().ifBlank { null })
    }

    private fun act(block: suspend (stepId: String) -> Unit) {
        val state = _uiState.value
        if (state.isActing) return
        val active = state.data?.activeStep
        val stepId = active?.id ?: run {
            _uiState.value = state.copy(errorMessage = "No active step")
            return
        }
        _uiState.value = state.copy(isActing = true, errorMessage = null)
        viewModelScope.launch {
            runCatching { block(stepId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isActing = false, comment = "")
                    load()
                }
                .onFailure { e ->
                    Log.e(TAG, "act FAILED", e)
                    _uiState.value = _uiState.value.copy(
                        isActing = false,
                        errorMessage = e.message ?: e.javaClass.simpleName,
                    )
                }
        }
    }

    fun remindApprover() {
        val state = _uiState.value
        if (state.isReminding) return
        val requestId = state.data?.request?.id ?: return
        _uiState.value = state.copy(isReminding = true, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                approvalRepository.remindApprover(requestId)
            }.onSuccess { approverUid ->
                _uiState.value = _uiState.value.copy(
                    isReminding = false,
                    reminderSentFor = approverUid,
                )
            }.onFailure { e ->
                Log.e(TAG, "remindApprover FAILED", e)
                _uiState.value = _uiState.value.copy(
                    isReminding = false,
                    errorMessage = e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    fun consumeReminderSent() {
        _uiState.value = _uiState.value.copy(reminderSentFor = null)
    }

    fun deleteRequest() {
        val state = _uiState.value
        if (state.isDeleting) return
        val data = state.data ?: return
        val requestId = data.request.id ?: return
        val anyStepApproved = data.steps.any { it.status == com.khetika.khetikasync.data.approval.STATUS_APPROVED }
        if (anyStepApproved) {
            _uiState.value = state.copy(errorMessage = "Cannot delete — at least one level has already approved.")
            return
        }
        _uiState.value = state.copy(isDeleting = true, errorMessage = null)
        viewModelScope.launch {
            runCatching { approvalRepository.deleteRequest(requestId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isDeleting = false, deleted = true)
                }
                .onFailure { e ->
                    Log.e(TAG, "deleteRequest FAILED", e)
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        errorMessage = e.message ?: e.javaClass.simpleName,
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    companion object {
        const val ARG_REQUEST_ID = "requestId"
        private const val TAG = "KhetikaDetail"
    }
}
