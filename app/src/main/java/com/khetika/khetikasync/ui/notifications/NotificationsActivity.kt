package com.khetika.khetikasync.ui.notifications

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khetika.khetikasync.R
import com.khetika.khetikasync.data.model.NotificationDto
import com.khetika.khetikasync.ui.detail.RequestDetailActivity
import com.khetika.khetikasync.ui.theme.KhetikaSyncTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NotificationsActivity : ComponentActivity() {

    private val viewModel: NotificationsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KhetikaSyncTheme {
                NotificationsRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onOpenRequest = ::openRequest,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun openRequest(requestId: String) {
        startActivity(RequestDetailActivity.intent(this, requestId))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsRoute(
    viewModel: NotificationsViewModel,
    onBack: () -> Unit,
    onOpenRequest: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.openRequestId) {
        state.openRequestId?.let {
            onOpenRequest(it)
            viewModel.consumeOpenRequest()
        }
    }

    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearError()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (state.notifications.any { !it.isRead }) {
                        TextButton(onClick = viewModel::onMarkAllRead) {
                            Icon(Icons.Filled.DoneAll, contentDescription = null)
                            Spacer(Modifier.height(0.dp))
                            Text(
                                modifier = Modifier.padding(start = 6.dp),
                                text = stringResource(R.string.home_mark_all_read),
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
                state.notifications.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.NotificationsNone,
                        contentDescription = null,
                        tint = Color(0x55000000),
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Text(
                        text = stringResource(R.string.home_no_notifications),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                ) {
                    items(state.notifications, key = { it.id ?: it.title }) { notif ->
                        NotificationCard(notif = notif, onTap = { viewModel.onTapNotification(notif) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationCard(notif: NotificationDto, onTap: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onTap,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (notif.isRead) 1.dp else 3.dp,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Unread dot indicator
            Surface(
                color = if (notif.isRead) Color.Transparent else MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp),
            ) {}
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = notif.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (notif.isRead) FontWeight.Normal else FontWeight.SemiBold,
                )
                notif.body?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
