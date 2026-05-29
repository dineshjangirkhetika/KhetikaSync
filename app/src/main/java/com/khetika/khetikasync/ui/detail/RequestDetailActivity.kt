package com.khetika.khetikasync.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.khetika.khetikasync.data.file.PickedFile
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khetika.khetikasync.R
import com.khetika.khetikasync.data.approval.STATUS_APPROVED
import com.khetika.khetikasync.data.approval.STATUS_PENDING
import com.khetika.khetikasync.data.approval.STATUS_REJECTED
import com.khetika.khetikasync.data.approval.STATUS_SENT_BACK
import com.khetika.khetikasync.data.model.ApprovalFileDto
import com.khetika.khetikasync.data.model.ApprovalRequestDto
import com.khetika.khetikasync.data.model.ApprovalStepDto
import com.khetika.khetikasync.data.model.UserDto
import com.khetika.khetikasync.ui.theme.KhetikaSyncTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class RequestDetailActivity : ComponentActivity() {

    private val viewModel: RequestDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KhetikaSyncTheme {
                RequestDetailRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onOpenFile = ::openFile,
                )
            }
        }
    }

    private fun openFile(uri: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setData(Uri.parse(uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        }
    }

    companion object {
        fun intent(context: Context, requestId: String): Intent =
            Intent(context, RequestDetailActivity::class.java).apply {
                putExtra(RequestDetailViewModel.ARG_REQUEST_ID, requestId)
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestDetailRoute(
    viewModel: RequestDetailViewModel,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearError()
    }

    val reminderSentLabel = stringResource(R.string.detail_reminder_sent)
    LaunchedEffect(state.reminderSentFor) {
        if (state.reminderSentFor != null) {
            snackbarHostState.showSnackbar(reminderSentLabel)
            viewModel.consumeReminderSent()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    val data = state.data
                    val anyStepApproved = data?.steps?.any { it.status == STATUS_APPROVED } == true
                    val canDelete = state.isRequester && data != null && !anyStepApproved
                    if (canDelete) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.detail_delete),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                state.data == null -> Text(
                    text = state.errorMessage ?: stringResource(R.string.detail_not_found),
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> {
                    val data = state.data!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RequestSummaryCard(
                            request = data.request,
                            requester = state.usersById[data.request.requesterUid],
                            files = data.files,
                            onOpenFile = onOpenFile,
                        )
                        StepsTimeline(
                            steps = data.steps,
                            usersById = state.usersById,
                            activeStepId = data.activeStep?.id,
                            canRemind = state.isRequester && data.request.status == STATUS_PENDING,
                            isReminding = state.isReminding,
                            onRemind = viewModel::remindApprover,
                        )
                        if (state.isCurrentUserActiveApprover) {
                            ActionBar(
                                comment = state.comment,
                                onCommentChange = viewModel::onCommentChange,
                                isActing = state.isActing,
                                onApprove = viewModel::approve,
                                onReject = viewModel::reject,
                                onSendBack = viewModel::sendBack,
                            )
                        }
                        if (state.isRequester && data.request.status == STATUS_SENT_BACK) {
                            ResubmitCard(
                                comment = state.resubmitComment,
                                onCommentChange = viewModel::onResubmitCommentChange,
                                pickedFiles = state.resubmitFiles,
                                onFilesAdded = viewModel::onResubmitFilesAdded,
                                onFileRemoved = viewModel::onResubmitFileRemoved,
                                isResubmitting = state.isResubmitting,
                                onResubmit = viewModel::resubmit,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteRequest()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    enabled = !state.isDeleting,
                ) { Text(stringResource(R.string.detail_delete_confirm)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun RequestSummaryCard(
    request: ApprovalRequestDto,
    requester: UserDto?,
    files: List<ApprovalFileDto>,
    onOpenFile: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(request.status)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(request.department)
                    request.category?.let { append(" · "); append(it) }
                    append(" · ")
                    append(request.priority)
                },
                style = MaterialTheme.typography.bodySmall,
            )
            requester?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.detail_requested_by,
                        it.displayName?.takeIf { n -> n.isNotBlank() } ?: it.email,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            request.createdAt?.let { iso ->
                Text(
                    text = stringResource(R.string.detail_created_at, formatIso(iso)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            request.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(Modifier.height(12.dp))
                Text(text = desc, style = MaterialTheme.typography.bodyMedium)
            }

            // Legacy single-file (from older requests) + per-row files.
            val legacy = request.fileName?.takeIf { it.isNotBlank() }
            if (legacy != null || files.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.detail_attachments),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                if (legacy != null) {
                    OutlinedButton(onClick = { request.fileUri?.let(onOpenFile) }) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null)
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = legacy,
                        )
                    }
                    if (files.isNotEmpty()) Spacer(Modifier.height(6.dp))
                }
                for (f in files) {
                    OutlinedButton(
                        onClick = { onOpenFile(f.fileUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null)
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = f.fileName,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResubmitCard(
    comment: String,
    onCommentChange: (String) -> Unit,
    pickedFiles: List<PickedFile>,
    onFilesAdded: (List<PickedFile>) -> Unit,
    onFileRemoved: (String) -> Unit,
    isResubmitting: Boolean,
    onResubmit: () -> Unit,
) {
    val context = LocalContext.current
    val pickFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val picked = uris.mapNotNull { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val name = queryDisplayName(context, uri) ?: return@mapNotNull null
            PickedFile(uri = uri.toString(), displayName = name)
        }
        onFilesAdded(picked)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.detail_resubmit_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.detail_resubmit_subtitle),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { pickFilesLauncher.launch(arrayOf("*/*")) },
            ) {
                Icon(Icons.Filled.AttachFile, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.detail_resubmit_attach_files))
            }

            if (pickedFiles.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.detail_resubmit_no_new_files),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (file in pickedFiles) {
                        AssistChip(
                            onClick = { onFileRemoved(file.uri) },
                            label = { Text(file.displayName) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = comment,
                onValueChange = onCommentChange,
                label = { Text(stringResource(R.string.detail_comment_label)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (isResubmitting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Button(
                    onClick = onResubmit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.detail_resubmit))
                }
            }
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    cursor.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && it.moveToFirst()) {
            return it.getString(nameIndex)
        }
    }
    return null
}

@Composable
private fun StepsTimeline(
    steps: List<ApprovalStepDto>,
    usersById: Map<String, UserDto>,
    activeStepId: String?,
    canRemind: Boolean,
    isReminding: Boolean,
    onRemind: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.detail_steps_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            val sorted = steps.sortedBy { it.level }
            sorted.forEachIndexed { index, step ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color(0x22000000),
                    )
                }
                StepRow(
                    step = step,
                    approver = usersById[step.approverUid],
                    showRemind = canRemind && step.id != null && step.id == activeStepId,
                    isReminding = isReminding,
                    onRemind = onRemind,
                )
            }
        }
    }
}

@Composable
private fun StepRow(
    step: ApprovalStepDto,
    approver: UserDto?,
    showRemind: Boolean,
    isReminding: Boolean,
    onRemind: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            StepDot(status = step.status)
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.detail_level_n, step.level),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(step.status)
                if (showRemind) {
                    Spacer(Modifier.size(6.dp))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !isReminding, onClick = onRemind),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.NotificationsActive,
                            contentDescription = stringResource(R.string.detail_remind_button),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = approver?.displayName?.takeIf { it.isNotBlank() }
                    ?: approver?.email
                    ?: step.approverUid,
                style = MaterialTheme.typography.bodyMedium,
            )
            step.actedAt?.let {
                Text(
                    text = stringResource(R.string.detail_acted_at, formatIso(it)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            step.note?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "“$note”",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun StepDot(status: String) {
    val color = when (status) {
        STATUS_APPROVED -> Color(0xFF2E7D32)
        STATUS_REJECTED -> Color(0xFFC62828)
        STATUS_SENT_BACK -> Color(0xFFEF6C00)
        STATUS_PENDING -> Color(0xFF1565C0)
        else -> Color.Gray
    }
    Surface(
        color = color,
        shape = CircleShape,
        modifier = Modifier.size(14.dp),
    ) {}
}

@Composable
private fun ActionBar(
    comment: String,
    onCommentChange: (String) -> Unit,
    isActing: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onSendBack: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.detail_your_action),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = comment,
                onValueChange = onCommentChange,
                label = { Text(stringResource(R.string.detail_comment_label)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            if (isActing) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                // Primary action — large, full-width.
                Button(
                    onClick = onApprove,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.detail_approve),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(10.dp))
                // Secondary actions — side by side.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onSendBack,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF6C00)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF6C00)),
                    ) {
                        Icon(Icons.Filled.Undo, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.detail_send_back))
                    }
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC62828)),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.detail_reject))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (bg, fg, label) = when (status) {
        STATUS_APPROVED -> Triple(Color(0xFFD1F1D6), Color(0xFF0F5132), "APPROVED")
        STATUS_REJECTED -> Triple(Color(0xFFF8D7DA), Color(0xFF842029), "REJECTED")
        STATUS_SENT_BACK -> Triple(Color(0xFFFFE0B2), Color(0xFF7B3F00), "SENT BACK")
        STATUS_PENDING -> Triple(Color(0xFFCFE2FF), Color(0xFF084298), "PENDING")
        else -> Triple(Color(0xFFE0E0E0), Color(0xFF424242), status.uppercase())
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

private fun formatIso(iso: String): String = runCatching {
    OffsetDateTime.parse(iso)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a"))
}.getOrDefault(iso)
