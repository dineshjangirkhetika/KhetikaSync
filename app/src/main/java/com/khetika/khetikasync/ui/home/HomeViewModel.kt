package com.khetika.khetikasync.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khetika.khetikasync.data.approval.ApprovalRepository
import com.khetika.khetikasync.data.approval.RequestWithSteps
import com.khetika.khetikasync.data.auth.AuthRepository
import com.khetika.khetikasync.data.model.ApprovalRequestDto
import com.khetika.khetikasync.data.notification.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

data class HomeUiState(
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val signedOut: Boolean = false,
    val selectedDateMillis: Long? = todayUtcMillis(),
    val selectedDepartment: String? = null,
    val selectedStatus: String? = null,
    val onlyApprovedByMe: Boolean = false,
    val pendingForMe: List<RequestWithSteps> = emptyList(),
    val myRequests: List<ApprovalRequestDto> = emptyList(),
    val approvedByMe: List<ApprovalRequestDto> = emptyList(),
    val isReminding: Boolean = false,
    val reminderSentForRequestId: String? = null,
    val searchQuery: String = "",
    val searchResults: List<ApprovalRequestDto> = emptyList(),
    val isSearching: Boolean = false,
    val unreadCount: Long = 0L,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

fun todayUtcMillis(): Long =
    LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val approvalRepository: ApprovalRepository,
    private val notificationRepository: NotificationRepository,
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
    val statuses: List<String> = listOf("pending", "approved", "rejected", "sent_back")

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    private fun initialState(): HomeUiState {
        val user = authRepository.currentUser
        return HomeUiState(
            displayName = user?.displayName.orEmpty(),
            email = user?.email.orEmpty(),
            photoUrl = user?.photoUrl,
        )
    }

    fun onDateSelected(millis: Long?) {
        Log.d(TAG, "onDateSelected(millis=$millis tz=${ZoneId.systemDefault()})")
        _uiState.value = _uiState.value.copy(selectedDateMillis = millis)
        refresh()
    }

    fun onDepartmentSelected(department: String?) {
        _uiState.value = _uiState.value.copy(selectedDepartment = department)
        refresh()
    }

    fun onStatusSelected(status: String?) {
        // Status filter and "Approved by me" view are mutually exclusive.
        _uiState.value = _uiState.value.copy(
            selectedStatus = status,
            onlyApprovedByMe = false,
        )
        refresh()
    }

    fun onToggleApprovedByMe() {
        val current = _uiState.value.onlyApprovedByMe
        _uiState.value = _uiState.value.copy(
            onlyApprovedByMe = !current,
            selectedStatus = null,
        )
        refresh()
    }

    private var searchJob: Job? = null

    fun onSearchChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }
        val uid = authRepository.currentUser?.uid ?: return
        _uiState.value = _uiState.value.copy(isSearching = true)
        searchJob = viewModelScope.launch {
            delay(250)
            runCatching { approvalRepository.searchRelatedRequests(uid, query.trim()) }
                .onSuccess { hits ->
                    _uiState.value = _uiState.value.copy(searchResults = hits, isSearching = false)
                }
                .onFailure { e ->
                    Log.e(TAG, "search FAILED", e)
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        errorMessage = e.message ?: e.javaClass.simpleName,
                    )
                }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(searchQuery = "", searchResults = emptyList(), isSearching = false)
    }

    fun refresh() {
        val uid = authRepository.currentUser?.uid
        if (uid == null) {
            _uiState.value = _uiState.value.copy(signedOut = true)
            return
        }
        val state = _uiState.value
        // The Material DatePicker stores the picked day as UTC midnight, but
        // `created_at` is real wall-clock UTC. Translate the picked day into
        // start/end *in the user's local timezone*, then express those as UTC
        // instants. That's what "today" means to the user.
        val (dateStartIso, dateEndIso) = state.selectedDateMillis?.let { millis ->
            val localDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            val zone = ZoneId.systemDefault()
            val start = localDate.atStartOfDay(zone).toInstant().toString()
            val end = localDate.plusDays(1).atStartOfDay(zone).toInstant().toString()
            Log.d(TAG, "refresh: picked=$localDate tz=$zone → gte=$start lt=$end")
            start to end
        } ?: run {
            Log.d(TAG, "refresh: no date filter")
            null to null
        }
        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                val pending = approvalRepository.listActionableForMe(
                    approverUid = uid,
                    department = state.selectedDepartment,
                    dateStartIso = dateStartIso,
                    dateEndIso = dateEndIso,
                )
                val mineAll = approvalRepository.listMyRequests(
                    requesterUid = uid,
                    department = state.selectedDepartment,
                    dateStartIso = dateStartIso,
                    dateEndIso = dateEndIso,
                )
                val mine = state.selectedStatus
                    ?.let { st -> mineAll.filter { it.status == st } }
                    ?: mineAll
                val approvedByMeAll = approvalRepository.listApprovedByMe(
                    approverUid = uid,
                    department = state.selectedDepartment,
                    dateStartIso = dateStartIso,
                    dateEndIso = dateEndIso,
                )
                val approvedByMe = state.selectedStatus
                    ?.let { st -> approvedByMeAll.filter { it.status == st } }
                    ?: approvedByMeAll
                val unread = notificationRepository.unreadCount(uid)
                HomeFetch(pending, mine, approvedByMe, unread)
            }.onSuccess { data ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    pendingForMe = data.pending,
                    myRequests = data.mine,
                    approvedByMe = data.approvedByMe,
                    unreadCount = data.unread,
                )
            }.onFailure { e ->
                Log.e(TAG, "refresh FAILED", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    fun remindApprover(requestId: String) {
        if (_uiState.value.isReminding) return
        _uiState.value = _uiState.value.copy(isReminding = true)
        viewModelScope.launch {
            runCatching { approvalRepository.remindApprover(requestId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isReminding = false,
                        reminderSentForRequestId = requestId,
                    )
                }
                .onFailure { e ->
                    Log.e(TAG, "remindApprover FAILED", e)
                    _uiState.value = _uiState.value.copy(
                        isReminding = false,
                        errorMessage = e.message ?: e.javaClass.simpleName,
                    )
                }
        }
    }

    fun consumeReminderSent() {
        _uiState.value = _uiState.value.copy(reminderSentForRequestId = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private companion object {
        const val TAG = "KhetikaHome"
    }
}

private data class HomeFetch(
    val pending: List<RequestWithSteps>,
    val mine: List<ApprovalRequestDto>,
    val approvedByMe: List<ApprovalRequestDto>,
    val unread: Long,
)

