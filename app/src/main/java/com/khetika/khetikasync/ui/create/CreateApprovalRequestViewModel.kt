package com.khetika.khetikasync.ui.create

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khetika.khetikasync.data.approval.ApprovalRepository
import com.khetika.khetikasync.data.auth.AuthRepository
import com.khetika.khetikasync.data.file.FileRepository
import com.khetika.khetikasync.data.file.PickedFile
import com.khetika.khetikasync.data.model.ApprovalRequestDto
import com.khetika.khetikasync.data.model.UserDto
import com.khetika.khetikasync.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateRequestUiState(
    val title: String = "",
    val description: String = "",
    val department: String? = null,
    val category: String? = null,
    val priority: String = "Normal",
    val files: List<PickedFile> = emptyList(),
    val levels: Int = 1,
    val approverEmailByLevel: Map<Int, String> = emptyMap(),
    val suggestionsByLevel: Map<Int, List<UserDto>> = emptyMap(),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val submitted: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isSubmitting &&
            title.isNotBlank() &&
            !department.isNullOrBlank() &&
            !category.isNullOrBlank() &&
            (1..levels).all { lvl -> !approverEmailByLevel[lvl].isNullOrBlank() }
}

@HiltViewModel
class CreateApprovalRequestViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val approvalRepository: ApprovalRepository,
    private val fileRepository: FileRepository,
) : ViewModel() {

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
    val categories: List<String> = listOf(
        "Vendor", "Legal", "Expense", "Procurement", "HR", "Other"
    )
    val priorities: List<String> = listOf("Normal", "Urgent", "High")
    val levelOptions: List<Int> = listOf(1, 2, 3)

    private val _uiState = MutableStateFlow(CreateRequestUiState())
    val uiState: StateFlow<CreateRequestUiState> = _uiState.asStateFlow()

    fun onTitleChange(value: String) {
        _uiState.value = _uiState.value.copy(title = value)
    }

    fun onDescriptionChange(value: String) {
        _uiState.value = _uiState.value.copy(description = value)
    }

    fun onDepartmentSelected(value: String) {
        // Department change invalidates any cached approver suggestions.
        searchJobs.values.forEach { it.cancel() }
        searchJobs.clear()
        _uiState.value = _uiState.value.copy(
            department = value,
            suggestionsByLevel = emptyMap(),
        )
    }

    fun onCategorySelected(value: String) {
        _uiState.value = _uiState.value.copy(category = value)
    }

    fun onPrioritySelected(value: String) {
        _uiState.value = _uiState.value.copy(priority = value)
    }

    fun onFilesAdded(picked: List<PickedFile>) {
        val current = _uiState.value
        // De-dup by URI.
        val merged = (current.files + picked).distinctBy { it.uri }
        _uiState.value = current.copy(files = merged)
    }

    fun onFileRemoved(uri: String) {
        val current = _uiState.value
        _uiState.value = current.copy(files = current.files.filterNot { it.uri == uri })
    }

    fun onLevelsChanged(levels: Int) {
        val current = _uiState.value
        val pruned = current.approverEmailByLevel.filterKeys { it <= levels }
        _uiState.value = current.copy(levels = levels, approverEmailByLevel = pruned)
    }

    private val searchJobs = mutableMapOf<Int, Job>()

    fun onApproverEmailChanged(level: Int, email: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            approverEmailByLevel = current.approverEmailByLevel + (level to email)
        )
        // Debounced live search.
        searchJobs[level]?.cancel()
        if (email.length < 2) {
            _uiState.value = _uiState.value.copy(
                suggestionsByLevel = _uiState.value.suggestionsByLevel - level,
            )
            return
        }
        searchJobs[level] = viewModelScope.launch {
            delay(200)
            val me = authRepository.currentUser?.uid
            // Only the request's selected department can be approvers.
            val dept = _uiState.value.department
            val results = userRepository.searchByEmailPrefix(email, department = dept)
                .filter { it.firebaseUid != me }
            _uiState.value = _uiState.value.copy(
                suggestionsByLevel = _uiState.value.suggestionsByLevel + (level to results),
            )
        }
    }

    fun onApproverSelected(level: Int, user: UserDto) {
        val current = _uiState.value
        _uiState.value = current.copy(
            approverEmailByLevel = current.approverEmailByLevel + (level to user.email),
            suggestionsByLevel = current.suggestionsByLevel - level,
        )
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
            runCatching {
                // 1) Resolve each email → firebase_uid.
                val approverUids = (1..state.levels).map { lvl ->
                    val email = state.approverEmailByLevel[lvl]!!.trim()
                    val user = userRepository.findVerifiedByEmail(email)
                        ?: error("No verified user found for level $lvl: $email")
                    if (user.firebaseUid == firebaseUser.uid) {
                        error("You cannot be your own approver (level $lvl)")
                    }
                    user.firebaseUid
                }

                // 2) Upload all files (parallel-ish; sequential for simplicity).
                val uploadedFiles: List<Pair<String, String>> = state.files.map { picked ->
                    val url = fileRepository.uploadApprovalFile(Uri.parse(picked.uri), picked.displayName)
                    picked.displayName to url
                }

                // 3) Create the request + step rows + file rows.
                val request = ApprovalRequestDto(
                    requesterUid = firebaseUser.uid,
                    title = state.title.trim(),
                    description = state.description.trim().ifBlank { null },
                    department = state.department!!,
                    category = state.category,
                    priority = state.priority,
                    fileName = null,
                    fileUri = null,
                    levels = state.levels,
                    // requesterUid is the only field that needs to come from auth;
                    // the rest are user-supplied.
                )
                approvalRepository.createRequest(request, approverUids, uploadedFiles)
            }.onSuccess {
                Log.d(TAG, "createRequest ok")
                _uiState.value = _uiState.value.copy(isSubmitting = false, submitted = true)
            }.onFailure { e ->
                Log.e(TAG, "createRequest FAILED", e)
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
        const val TAG = "KhetikaCreateReq"
    }
}
