package com.khetika.khetikasync.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khetika.khetikasync.R
import com.khetika.khetikasync.data.approval.RequestWithSteps
import com.khetika.khetikasync.data.approval.STATUS_APPROVED
import com.khetika.khetikasync.data.approval.STATUS_PENDING
import com.khetika.khetikasync.data.approval.STATUS_REJECTED
import com.khetika.khetikasync.data.approval.STATUS_SENT_BACK
import com.khetika.khetikasync.data.model.ApprovalRequestDto
import com.khetika.khetikasync.data.model.NotificationDto
import com.khetika.khetikasync.ui.create.CreateApprovalRequestActivity
import com.khetika.khetikasync.ui.detail.RequestDetailActivity
import com.khetika.khetikasync.ui.login.LoginActivity
import com.khetika.khetikasync.ui.notifications.NotificationsActivity
import com.khetika.khetikasync.ui.profile.ProfileActivity
import com.khetika.khetikasync.ui.theme.KhetikaSyncTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class HomeActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KhetikaSyncTheme {
                HomeRoute(
                    viewModel = viewModel,
                    onSignedOut = ::goToLogin,
                    onCreateRequest = ::goToCreate,
                    onOpenRequest = ::openDetail,
                    onOpenProfile = ::goToProfile,
                    onOpenNotifications = ::goToNotifications,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    private fun goToCreate() {
        startActivity(Intent(this, CreateApprovalRequestActivity::class.java))
    }

    private fun goToProfile() {
        startActivity(Intent(this, ProfileActivity::class.java))
    }

    private fun goToNotifications() {
        startActivity(Intent(this, NotificationsActivity::class.java))
    }

    private fun openDetail(requestId: String) {
        startActivity(RequestDetailActivity.intent(this, requestId))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeRoute(
    viewModel: HomeViewModel,
    onSignedOut: () -> Unit,
    onCreateRequest: () -> Unit,
    onOpenRequest: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* result intentionally ignored — declined is fine, we just won't show pushes */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(state.signedOut) {
        if (state.signedOut) onSignedOut()
    }

    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearError()
    }

    val reminderLabel = stringResource(R.string.detail_reminder_sent)
    LaunchedEffect(state.reminderSentForRequestId) {
        if (state.reminderSentForRequestId != null) {
            snackbarHostState.showSnackbar(reminderLabel)
            viewModel.consumeReminderSent()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.profile_title))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenNotifications) {
                        BadgedBox(badge = {
                            if (state.unreadCount > 0) {
                                Badge { Text(state.unreadCount.toString()) }
                            }
                        }) {
                            Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.home_notifications))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateRequest) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.create_request_title))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.home_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = viewModel::clearSearch) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            val isSearching = state.searchQuery.isNotBlank()

            if (isSearching) {
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        state.isSearching -> CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                        state.searchResults.isEmpty() -> Text(
                            text = stringResource(R.string.home_search_empty),
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        else -> LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                SectionHeader(
                                    stringResource(R.string.home_search_results, state.searchResults.size)
                                )
                            }
                            items(state.searchResults, key = { it.id ?: it.title }) { request ->
                                RequestCard(
                                    request = request,
                                    onClick = { request.id?.let(onOpenRequest) },
                                )
                            }
                        }
                    }
                }
                return@Column
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.DateRange, contentDescription = null)
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = (state.selectedDateMillis ?: todayUtcMillis()).let { millis ->
                            Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        },
                    )
                }
                DepartmentFilter(
                    selected = state.selectedDepartment,
                    options = viewModel.departments,
                    onSelected = viewModel::onDepartmentSelected,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))

            StatusChipRow(
                selected = state.selectedStatus,
                options = viewModel.statuses,
                onSelected = viewModel::onStatusSelected,
                approvedByMeSelected = state.onlyApprovedByMe,
                onToggleApprovedByMe = viewModel::onToggleApprovedByMe,
            )

            Spacer(Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                    state.pendingForMe.isEmpty() && state.myRequests.isEmpty() && state.approvedByMe.isEmpty() -> Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(bottom = 12.dp),
                            tint = Color(0x55000000),
                        )
                        Text(
                            text = stringResource(R.string.home_empty),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!state.onlyApprovedByMe && state.pendingForMe.isNotEmpty()) {
                            item {
                                SectionHeader(stringResource(R.string.home_section_pending_for_me, state.pendingForMe.size))
                            }
                            items(state.pendingForMe, key = { it.request.id ?: it.request.title }) { rws ->
                                PendingActionCard(
                                    item = rws,
                                    onClick = { rws.request.id?.let(onOpenRequest) },
                                )
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                        if (!state.onlyApprovedByMe && state.myRequests.isNotEmpty()) {
                            item {
                                SectionHeader(stringResource(R.string.home_section_my_requests, state.myRequests.size))
                            }
                            items(state.myRequests, key = { it.id ?: it.title }) { request ->
                                RequestCard(
                                    request = request,
                                    onClick = { request.id?.let(onOpenRequest) },
                                    onRemind = request.id?.let { id -> { viewModel.remindApprover(id) } },
                                    isReminding = state.isReminding,
                                )
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                        if (state.approvedByMe.isNotEmpty()) {
                            item {
                                SectionHeader(stringResource(R.string.home_section_approved_by_me, state.approvedByMe.size))
                            }
                            items(state.approvedByMe, key = { "abm_${it.id ?: it.title}" }) { request ->
                                RequestCard(
                                    request = request,
                                    onClick = { request.id?.let(onOpenRequest) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.selectedDateMillis ?: todayUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.onDateSelected(it) }
                    showDatePicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun StatusChipRow(
    selected: String?,
    options: List<String>,
    onSelected: (String?) -> Unit,
    approvedByMeSelected: Boolean,
    onToggleApprovedByMe: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selected == null && !approvedByMeSelected,
            onClick = { onSelected(null) },
            label = { Text(stringResource(R.string.home_all_statuses)) },
        )
        for (option in options) {
            FilterChip(
                selected = !approvedByMeSelected && selected == option,
                onClick = { onSelected(option) },
                label = {
                    Text(option.replace('_', ' ').replaceFirstChar { it.uppercase() })
                },
            )
        }
        FilterChip(
            selected = approvedByMeSelected,
            onClick = onToggleApprovedByMe,
            label = { Text(stringResource(R.string.home_filter_approved_by_me)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepartmentFilter(
    selected: String?,
    options: List<String>,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected ?: stringResource(R.string.home_all_departments),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.registration_department_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_all_departments)) },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestCard(
    request: ApprovalRequestDto,
    onClick: () -> Unit,
    onRemind: (() -> Unit)? = null,
    isReminding: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(request.status)
                if (onRemind != null && request.status == STATUS_PENDING) {
                    IconButton(
                        onClick = onRemind,
                        enabled = !isReminding,
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Icon(
                            Icons.Filled.NotificationsActive,
                            contentDescription = stringResource(R.string.detail_remind_button),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    val parts = listOfNotNull(
                        request.department,
                        request.category,
                        request.priority,
                        "${request.levels} level${if (request.levels > 1) "s" else ""}",
                    )
                    append(parts.joinToString(" · "))
                },
                style = MaterialTheme.typography.bodySmall,
            )
            request.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingActionCard(
    item: RequestWithSteps,
    onClick: () -> Unit,
) {
    val req = item.request
    val active = item.activeStep
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = req.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(req.priority.lowercase(), labelOverride = req.priority)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    val parts = listOfNotNull(
                        req.department,
                        req.category,
                        "Level ${active?.level ?: "?"}/${req.levels}",
                    )
                    append(parts.joinToString(" · "))
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatusBadge(status: String, labelOverride: String? = null) {
    val (bg, fg, label) = when (status) {
        STATUS_APPROVED -> Triple(Color(0xFFD1F1D6), Color(0xFF0F5132), "APPROVED")
        STATUS_REJECTED -> Triple(Color(0xFFF8D7DA), Color(0xFF842029), "REJECTED")
        STATUS_SENT_BACK -> Triple(Color(0xFFFFE0B2), Color(0xFF7B3F00), "SENT BACK")
        STATUS_PENDING -> Triple(Color(0xFFCFE2FF), Color(0xFF084298), "PENDING")
        "urgent" -> Triple(Color(0xFFFFE0B2), Color(0xFF7B3F00), labelOverride ?: "URGENT")
        "high" -> Triple(Color(0xFFF8D7DA), Color(0xFF842029), labelOverride ?: "HIGH")
        else -> Triple(Color(0xFFE0E0E0), Color(0xFF424242), labelOverride ?: status.uppercase())
    }
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
