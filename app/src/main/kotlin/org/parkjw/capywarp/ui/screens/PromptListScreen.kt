package org.parkjw.capywarp.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import org.parkjw.capywarp.ui.viewmodels.PromptListViewModel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete


@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PromptListScreen(
    onNavigateToSettings: () -> Unit,
    onEditPrompt: (Int) -> Unit,
    viewModel: PromptListViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val prompts by viewModel.prompts.collectAsState(initial = emptyList())
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }
    var showConfirm by remember { mutableStateOf(false) }

    // Local reorderable list state
    val density = androidx.compose.ui.platform.LocalDensity.current
    val itemHeightPx = with(density) { 72.dp.toPx() }
    var isDragging by remember { mutableStateOf(false) }
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var localList by remember(prompts) { mutableStateOf(prompts.sortedBy { it.order }) }
    if (!isDragging && localList.map { it.id } != prompts.map { it.id }) {
        localList = prompts.sortedBy { it.order }
    }

    fun toggleSelect(id: Int) {
        val ns = selectedIds.toMutableSet()
        if (!ns.add(id)) ns.remove(id)
        selectedIds = ns
        selectionMode = selectedIds.isNotEmpty()
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    var lastBackTime by remember { mutableStateOf(0L) }

    androidx.activity.compose.BackHandler {
        if (selectionMode) {
            selectionMode = false
            selectedIds = emptySet()
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackTime < 1800) {
                (context as? android.app.Activity)?.finish()
            } else {
                lastBackTime = now
                android.widget.Toast.makeText(context, context.getString(org.parkjw.capywarp.R.string.press_back_again_to_exit), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    var reorderMode by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("") },
                actions = {
                    // Multi-select actions (icons) appear to the left of Reorder when selection mode is active
                    if (selectionMode) {
                        // Duplicate (only when exactly one item selected)
                        if (selectedIds.size == 1) {
                            IconButton(onClick = {
                                val id = selectedIds.first()
                                viewModel.duplicatePrompt(id) {
                                    selectedIds = emptySet()
                                    selectionMode = false
                                }
                            }) {
                                Icon(imageVector = androidx.compose.material.icons.Icons.Filled.ContentCopy, contentDescription = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.duplicate))
                            }
                        }
                        // Delete (opens confirm dialog)
                        IconButton(onClick = { showConfirm = true }) {
                            Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Delete, contentDescription = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.delete))
                        }
                    }
                    // Reorder toggle (left of settings)
                    IconButton(onClick = { reorderMode = !reorderMode }) {
                        val icon = if (reorderMode) androidx.compose.material.icons.Icons.Filled.Done else androidx.compose.material.icons.Icons.Filled.Reorder
                        val desc = if (reorderMode) "Done" else "Reorder"
                        Icon(imageVector = icon, contentDescription = desc)
                    }
                    // Settings at top-right
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Settings, contentDescription = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(onClick = { onEditPrompt(-1) }) {
                    Icon(painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_input_add), contentDescription = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.new_prompt))
                }
            }
        },
        bottomBar = {
            // Multi-select actions moved to TopAppBar as icon buttons; no bottom bar actions.
        }
    ) { paddingValues ->
        if (prompts.isEmpty()) {
            // 빈 상태 표시
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)) {
                Text(
                    text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.no_prompts_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                items(localList, key = { it.id }) { prompt ->
                    val checked = selectedIds.contains(prompt.id)
                    ListItem(
                        headlineContent = { Text(prompt.title) },
                        supportingContent = {
                            val actionLabel = if (prompt.outputType == 0) {
                                when (prompt.resultAction) {
                                    1 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_copy_to_clipboard)
                                    2 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_show_notification)
                                    else -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_process_result)
                                }
                            } else {
                                when (prompt.resultAction) {
                                    2 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_show_notification_image)
                                    3 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_save_gallery)
                                    else -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_process_result)
                                }
                            }
                            Column {
                                if (prompt.template.isNotBlank()) {
                                    Text(
                                        text = prompt.template.take(60).replace("\n", " ") + if (prompt.template.length > 60) "…" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(actionLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        trailingContent = {
                            if (reorderMode) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        val idx = localList.indexOfFirst { it.id == prompt.id }
                                        if (idx > 0) {
                                            val mutable = localList.toMutableList()
                                            val item = mutable.removeAt(idx)
                                            mutable.add(idx - 1, item)
                                            localList = mutable
                                            viewModel.persistOrder(localList)
                                        }
                                    }) {
                                        Icon(painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.arrow_up_float), contentDescription = "Up")
                                    }
                                    IconButton(onClick = {
                                        val idx = localList.indexOfFirst { it.id == prompt.id }
                                        if (idx >= 0 && idx < localList.lastIndex) {
                                            val mutable = localList.toMutableList()
                                            val item = mutable.removeAt(idx)
                                            mutable.add(idx + 1, item)
                                            localList = mutable
                                            viewModel.persistOrder(localList)
                                        }
                                    }) {
                                        Icon(painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.arrow_down_float), contentDescription = "Down")
                                    }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (reorderMode) {
                                        onEditPrompt(prompt.id)
                                    } else if (selectionMode) toggleSelect(prompt.id) else onEditPrompt(prompt.id)
                                },
                                onLongClick = {
                                    if (!reorderMode) {
                                        if (!selectionMode) selectionMode = true
                                        toggleSelect(prompt.id)
                                    }
                                }
                            )
                    )
                    Divider()
                }
            }
        }

        if (showConfirm) {
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                title = { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.confirm_delete_title)) },
                text = { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.confirm_delete_message, selectedIds.size)) },
                confirmButton = {
                    TextButton(onClick = {
                        showConfirm = false
                        viewModel.deletePromptsByIds(selectedIds) {
                            selectionMode = false
                            selectedIds = emptySet()
                        }
                    }) { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirm = false }) { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.cancel)) }
                }
            )
        }
    }
}
