package org.parkjw.capywarp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.parkjw.capywarp.ui.viewmodels.PromptEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptEditorScreen(
    promptId: Int?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PromptEditorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val ctx = LocalContext.current

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            androidx.compose.material3.ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::updateTitle,
                        label = { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_prompt_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Template field with cursor-aware insertion support
                    val templateFieldState = androidx.compose.runtime.saveable.rememberSaveable(stateSaver = androidx.compose.ui.text.input.TextFieldValue.Saver) {
                        androidx.compose.runtime.mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(state.template))
                    }
                    // Sync local field when VM state changes from elsewhere (e.g., load)
                    androidx.compose.runtime.LaunchedEffect(state.template) {
                        val current = templateFieldState.value
                        if (current.text != state.template) {
                            templateFieldState.value = current.copy(
                                text = state.template,
                                selection = androidx.compose.ui.text.TextRange(state.template.length)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = templateFieldState.value,
                        onValueChange = { v: androidx.compose.ui.text.input.TextFieldValue ->
                            templateFieldState.value = v
                            viewModel.updateTemplate(v.text)
                        },
                        label = { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_prompt_template)) },
                        supportingText = { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_prompt_template_help)) },
                        minLines = 5,
                        maxLines = 10,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 240.dp)
                    )
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    var showGenDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    var genInput by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
                    var genLoading by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    var genError by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
                    var genResult by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
                    var showConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        // Left: Generate Prompt button
                        // API key pre-check via SettingsViewModel
                        val settingsVm: org.parkjw.capywarp.ui.viewmodels.SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                        val apiKey by settingsVm.apiKey.collectAsState()
                        var showKeyDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                        if (showKeyDialog) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { showKeyDialog = false },
                                title = { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.gemini_key_required_title)) },
                                text = { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.gemini_key_required_message)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showKeyDialog = false
                                        val intent = android.content.Intent(ctx, org.parkjw.capywarp.ui.MainActivity::class.java).apply {
                                            putExtra("OPEN_SETTINGS", true)
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        ctx.startActivity(intent)
                                    }) { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.go_to_settings)) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showKeyDialog = false }) { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.cancel)) }
                                }
                            )
                        }

                        Button(onClick = {
                            if (apiKey.isBlank()) {
                                showKeyDialog = true
                                return@Button
                            }
                            genInput = ""
                            genError = null
                            genResult = null
                            showGenDialog = true
                        }) {
                            Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_generate_prompt))
                        }

                        // Right: $TEXT insert button (apply primary button design)
                        Button(onClick = {
                            val token = "\$TEXT"
                            val current = templateFieldState.value
                            val sel = current.selection
                            val start = sel.start.coerceIn(0, current.text.length)
                            val end = sel.end.coerceIn(0, current.text.length)
                            val newText = buildString {
                                append(current.text.substring(0, minOf(start, end)))
                                append(token)
                                append(current.text.substring(maxOf(start, end)))
                            }
                            val newCursor = minOf(start, end) + token.length
                            templateFieldState.value = androidx.compose.ui.text.input.TextFieldValue(
                                text = newText,
                                selection = androidx.compose.ui.text.TextRange(newCursor)
                            )
                            viewModel.updateTemplate(newText)
                        }) {
                            Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_insert_text_token))
                        }
                    }

                    // Generate Prompt input dialog
                    if (showGenDialog) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { if (!genLoading) showGenDialog = false },
                            title = { Text(text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_generate_prompt)) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = genInput,
                                        onValueChange = { genInput = it; genError = null },
                                        label = { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_generate_prompt_hint)) },
                                        minLines = 3,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (genError != null) {
                                        Text(genError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (genLoading) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.height(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_generating))
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(enabled = !genLoading, onClick = {
                                    if (genInput.isBlank()) {
                                        genError = ctx.getString(org.parkjw.capywarp.R.string.editor_input_required)
                                        return@TextButton
                                    }
                                    genLoading = true
                                    genError = null
                                    scope.launch {
                                        val res = viewModel.improvePromptFromIntent(genInput)
                                        genLoading = false
                                        res.onSuccess { text ->
                                            genResult = text
                                            showGenDialog = false
                                            showConfirm = true
                                        }.onFailure { e ->
                                            genError = e.message ?: "Error"
                                        }
                                    }
                                }) { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_request)) }
                            },
                            dismissButton = {
                                TextButton(enabled = !genLoading, onClick = { showGenDialog = false }) { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.close)) }
                            }
                        )
                    }

                    // Confirmation dialog with preview
                    if (showConfirm && genResult != null) {
                        val willReplace = state.template.isNotBlank()
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showConfirm = false },
                            title = { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_apply_generated_question)) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (willReplace) {
                                        Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_replace_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                    val previewScroll = rememberScrollState()
                                    androidx.compose.material3.ElevatedCard(Modifier.fillMaxWidth()) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 80.dp, max = 220.dp)
                                                .verticalScroll(previewScroll)
                                        ) {
                                            Text(
                                                text = genResult!!,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val newText = genResult!!
                                    templateFieldState.value = androidx.compose.ui.text.input.TextFieldValue(
                                        text = newText,
                                        selection = androidx.compose.ui.text.TextRange(newText.length)
                                    )
                                    viewModel.updateTemplate(newText)
                                    showConfirm = false
                                }) { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_use_this_prompt)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showConfirm = false }) { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.cancel)) }
                            }
                        )
                    }
                }
            }

            androidx.compose.material3.ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_section_response_format), style = MaterialTheme.typography.titleMedium)
                    OutputTypeSelector(
                        selected = state.outputType,
                        onSelected = viewModel::updateOutputType
                    )
                }
            }

            androidx.compose.material3.ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_section_result_action), style = MaterialTheme.typography.titleMedium)
                    ResultActionSelector(
                        outputType = state.outputType,
                        selected = state.resultAction,
                        onSelected = viewModel::updateResultAction
                    )
                }
            }

            if (state.error != null) {
                Text(text = state.error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ResultActionSelector(outputType: Int, selected: Int, onSelected: (Int) -> Unit) {
    val selectedTextColor = MaterialTheme.colorScheme.primary
    val unselectedTextColor = MaterialTheme.colorScheme.onSurface
    val radioColors = androidx.compose.material3.RadioButtonDefaults.colors(
        selectedColor = MaterialTheme.colorScheme.primary,
        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledSelectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledUnselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        if (outputType == 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == 2, onClick = { onSelected(2) }, colors = radioColors)
                Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_action_notify), color = if (selected == 2) selectedTextColor else unselectedTextColor)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == 4, onClick = { onSelected(4) }, colors = radioColors)
                Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_show_popup), color = if (selected == 4) selectedTextColor else unselectedTextColor)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == 1, onClick = { onSelected(1) }, colors = radioColors)
                Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_action_clipboard), color = if (selected == 1) selectedTextColor else unselectedTextColor)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == 2, onClick = { onSelected(2) }, colors = radioColors)
                Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_action_notify_image), color = if (selected == 2) selectedTextColor else unselectedTextColor)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == 4, onClick = { onSelected(4) }, colors = radioColors)
                Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.action_show_popup), color = if (selected == 4) selectedTextColor else unselectedTextColor)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == 3, onClick = { onSelected(3) }, colors = radioColors)
                Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_action_save_gallery), color = if (selected == 3) selectedTextColor else unselectedTextColor)
            }
        }
    }
}

@Composable
private fun OutputTypeSelector(selected: Int, onSelected: (Int) -> Unit) {
    val selectedTextColor = MaterialTheme.colorScheme.primary
    val unselectedTextColor = MaterialTheme.colorScheme.onSurface
    val radioColors = androidx.compose.material3.RadioButtonDefaults.colors(
        selectedColor = MaterialTheme.colorScheme.primary,
        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledSelectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledUnselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected == 0, onClick = { onSelected(0) }, colors = radioColors)
            Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_output_text), color = if (selected == 0) selectedTextColor else unselectedTextColor)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected == 1, onClick = { onSelected(1) }, colors = radioColors)
            Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_output_image), color = if (selected == 1) selectedTextColor else unselectedTextColor)
        }
    }
}
