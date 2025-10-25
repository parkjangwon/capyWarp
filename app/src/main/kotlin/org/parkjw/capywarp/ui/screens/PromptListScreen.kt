package org.parkjw.capywarp.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.onSizeChanged


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
    var dragAccumulated by remember { mutableStateOf(0f) }
    var localList by remember(prompts) { mutableStateOf(prompts.sortedBy { it.order }) }
    // Track a measured row height (px) for reliable swap thresholds across devices
    var measuredRowHeightPx by remember { mutableStateOf(itemHeightPx) }
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

    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("") },
                actions = {
                    // Multi-select actions (icons) appear at the left of Settings when selection mode is active
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
        val scope = rememberCoroutineScope()
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
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(modifier = Modifier.padding(paddingValues), state = listState) {
                items(localList, key = { it.id }) { prompt ->
                    val checked = selectedIds.contains(prompt.id)
                    // Track last drag direction to stabilize accumulator and prevent overshoot
                    var lastDragDir by remember { mutableStateOf(0) }
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
                        colors = ListItemDefaults.colors(
                            containerColor = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItemPlacement()
                            .onSizeChanged { measuredRowHeightPx = it.height.toFloat().coerceAtLeast(1f) }
                            .pointerInput(Unit) {
                                // Long-press to start drag; normal scroll works before long-press.
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { _: Offset ->
                                        if (!selectionMode) selectionMode = true
                                        selectedIds = setOf(prompt.id)
                                        draggingId = prompt.id
                                        isDragging = true
                                        dragAccumulated = 0f
                                        lastDragDir = 0
                                        try { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) } catch (_: Exception) {}
                                    },
                                    onDragEnd = {
                                        if (isDragging && draggingId == prompt.id) {
                                            isDragging = false
                                            draggingId = null
                                            dragAccumulated = 0f
                                            lastDragDir = 0
                                            viewModel.persistOrder(localList)
                                        }
                                    },
                                    onDragCancel = {
                                        if (isDragging && draggingId == prompt.id) {
                                            isDragging = false
                                            draggingId = null
                                            dragAccumulated = 0f
                                            lastDragDir = 0
                                        }
                                    },
                                    onDrag = { change: PointerInputChange, dragAmount: Offset ->
                                        if (!isDragging || draggingId != prompt.id) return@detectDragGesturesAfterLongPress
                                        // Consume drag and translate into reordering primarily
                                        change.consume()
                                        val dy = dragAmount.y
                                        val dir = when {
                                            dy > 1f -> 1
                                            dy < -1f -> -1
                                            else -> 0
                                        }
                                        // Reset accumulator when direction flips to avoid overshoot
                                        if (dir != 0 && lastDragDir != 0 && dir != lastDragDir) {
                                            dragAccumulated = 0f
                                        }
                                        if (dir != 0) lastDragDir = dir

                                        val perItem = measuredRowHeightPx.takeIf { it > 1f } ?: itemHeightPx
                                        dragAccumulated += dy

                                        var currentIndex = localList.indexOfFirst { it.id == draggingId }
                                        if (currentIndex == -1) return@detectDragGesturesAfterLongPress

                                        var swaps = 0
                                        var swappedThisFrame = false
                                        while (kotlin.math.abs(dragAccumulated) > perItem * 0.5f && swaps < 6) {
                                            if (dragAccumulated > 0 && currentIndex < localList.lastIndex) {
                                                val mutable = localList.toMutableList()
                                                val item = mutable.removeAt(currentIndex)
                                                mutable.add(currentIndex + 1, item)
                                                localList = mutable
                                                currentIndex += 1
                                                dragAccumulated -= perItem
                                                swaps++
                                                swappedThisFrame = true
                                                try { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                                            } else if (dragAccumulated < 0 && currentIndex > 0) {
                                                val mutable = localList.toMutableList()
                                                val item = mutable.removeAt(currentIndex)
                                                mutable.add(currentIndex - 1, item)
                                                localList = mutable
                                                currentIndex -= 1
                                                dragAccumulated += perItem
                                                swaps++
                                                swappedThisFrame = true
                                                try { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                                            } else {
                                                break
                                            }
                                        }

                                        // Gentle edge auto-scroll when dragging without swap and close to list edges
                                        if (!swappedThisFrame && dir != 0) {
                                            val firstIndex = listState.firstVisibleItemIndex
                                            val lastIndex = (firstIndex + listState.layoutInfo.visibleItemsInfo.size - 1).coerceAtLeast(firstIndex)
                                            // If moving up and not at very top, or moving down and not at very bottom, scroll a bit
                                            if ((dir < 0 && (firstIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) ||
                                                (dir > 0 && lastIndex < localList.lastIndex)) {
                                                val delta = if (dir > 0) 24f else -24f
                                                try {
                                                    // scrollBy must be called from a coroutine
                                                    scope.launch { listState.scrollBy(delta) }
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    }
                                )
                            }
                            .clickable {
                                if (selectionMode) toggleSelect(prompt.id) else onEditPrompt(prompt.id)
                            }
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
