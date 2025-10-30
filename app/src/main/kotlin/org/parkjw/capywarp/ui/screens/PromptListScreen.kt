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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import org.burnoutcrew.reorderable.ReorderableItem
import androidx.compose.foundation.layout.size
import androidx.compose.animation.Crossfade


@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PromptListScreen(
    onNavigateToSettings: () -> Unit,
    onEditPrompt: (Int) -> Unit,
    viewModel: PromptListViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val prompts by viewModel.prompts.collectAsState(initial = emptyList())
    val query by viewModel.query.collectAsState()
    val filtered by viewModel.filteredPrompts.collectAsState(initial = emptyList())

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }
    var showConfirm by remember { mutableStateOf(false) }

    // Use SnapshotStateList for stable in-place reordering without recreating the list on every move (only for full list mode)
    val localList = remember { androidx.compose.runtime.mutableStateListOf<org.parkjw.capywarp.data.model.Prompt>() }
    // Seed/refresh contents only when backing data changes (preserve current order while dragging)
    LaunchedEffect(prompts) {
        val ordered = prompts.sortedBy { it.order }
        // Replace contents while minimizing churn
        localList.clear()
        localList.addAll(ordered)
    }

    // Clear selection and scroll to top on query change
    val listState = rememberLazyListState()
    LaunchedEffect(query) {
        selectionMode = false
        selectedIds = emptySet()
        listState.scrollToItem(0)
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
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    // Search mode toggle + focus requester for smooth UX
    var searchMode by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(searchMode) {
        if (searchMode) {
            try { searchFocusRequester.requestFocus() } catch (_: Exception) {}
            keyboardController?.show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    // No left navigation content to keep the app title flush to the left with no extra gap
                },
                title = {
                    val titleAlpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (searchMode) 0f else 1f,
                        label = "titleAlpha"
                    )
                    val searchAlpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (searchMode) 1f else 0f,
                        label = "searchAlpha"
                    )
                    Box(Modifier.fillMaxWidth()) {
                        // Static title text (fades out but stays in place to avoid vertical shift)
                        Text(
                            text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.app_name),
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .graphicsLayer { alpha = titleAlpha }
                        )

                        // Inline, borderless search field (fades in exactly over the title)
                        TextField(
                            value = query,
                            onValueChange = viewModel::updateQuery,
                            placeholder = { Text(text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.search_hint)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent
                            ),
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester)
                                .graphicsLayer { alpha = searchAlpha }
                        )
                    }
                },
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

                    // When in search mode, show a Close (X) on the right next to Settings.
                    if (searchMode) {
                        IconButton(onClick = {
                            // Close search and clear the query
                            viewModel.clearQuery()
                            searchMode = false
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }) {
                            Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Close, contentDescription = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.close))
                        }
                    } else {
                        // Search toggle when not in search mode
                        IconButton(onClick = { searchMode = true }) {
                            Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Search, contentDescription = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.search))
                        }
                    }

                    // Settings at top-right (always visible)
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
            val hapticsLocal = androidx.compose.ui.platform.LocalHapticFeedback.current
            val reorderState = rememberReorderableLazyListState(
                listState = listState,
                onMove = { from, to ->
                    if (query.isNotBlank()) return@rememberReorderableLazyListState
                    // In-place mutate the SnapshotStateList to avoid recomposition churn
                    if (from.index != to.index && from.index in 0 until localList.size && to.index in 0..localList.size) {
                        val moved = localList.removeAt(from.index)
                        val safeTo = to.index.coerceIn(0, localList.size)
                        localList.add(safeTo, moved)
                    }
                    try { hapticsLocal.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                },
                onDragEnd = { _, _ ->
                    if (query.isBlank()) viewModel.persistOrder(localList)
                }
            )
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .then(if (query.isBlank()) Modifier.reorderable(reorderState) else Modifier),
                state = listState
            ) {
                val data = if (query.isBlank()) localList else filtered
                if (data.isEmpty() && query.isNotBlank()) {
                    item {
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .height(200.dp)) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.no_results_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                } else {
                    items(data, key = { it.id }) { prompt ->
                        val checked = selectedIds.contains(prompt.id)
                        val itemModifierBase = Modifier
                            .fillMaxWidth()
                            .clickable { if (selectionMode) toggleSelect(prompt.id) else onEditPrompt(prompt.id) }
                        if (query.isBlank()) {
                            ReorderableItem(reorderState, key = prompt.id) { isDragging ->
                                // When dragging starts (after long‑press), immediately enter selection mode
                                LaunchedEffect(isDragging) {
                                    if (isDragging) {
                                        if (!selectionMode) selectionMode = true
                                        selectedIds = setOf(prompt.id)
                                        try { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) } catch (_: Exception) {}
                                    }
                                }
                                ListItem(
                                    headlineContent = { Text(prompt.title) },
                                    supportingContent = {
                                        val actionLabel = if (prompt.outputType == 0) {
                                            when (prompt.resultAction) {
                                                1 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_copy_to_clipboard)
                                                2 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_show_notification)
                                                4 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_show_popup)
                                                else -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_process_result)
                                            }
                                        } else {
                                            when (prompt.resultAction) {
                                                2 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_show_notification_image)
                                                3 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_save_gallery)
                                                4 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_show_popup)
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
                                    modifier = itemModifierBase
                                        .then(Modifier.animateItemPlacement())
                                        .graphicsLayer {
                                            if (isDragging) {
                                                shadowElevation = 12f
                                                scaleX = 1.01f
                                                scaleY = 1.01f
                                            } else {
                                                shadowElevation = 0f
                                                scaleX = 1f
                                                scaleY = 1f
                                            }
                                        }
                                        .detectReorderAfterLongPress(reorderState)
                                )
                                Divider()
                            }
                        } else {
                            // No reorder wrapper while searching
                            ListItem(
                                headlineContent = { Text(prompt.title) },
                                supportingContent = {
                                    val actionLabel = if (prompt.outputType == 0) {
                                        when (prompt.resultAction) {
                                            1 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_copy_to_clipboard)
                                            2 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_show_notification)
                                            4 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_show_popup)
                                            else -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_process_result)
                                        }
                                    } else {
                                        when (prompt.resultAction) {
                                            2 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_show_notification_image)
                                            3 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_save_gallery)
                                            4 -> androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_show_popup)
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
                                modifier = itemModifierBase
                            )
                            Divider()
                        }
                    }
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
