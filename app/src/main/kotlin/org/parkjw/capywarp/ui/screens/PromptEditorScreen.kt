package org.parkjw.capywarp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
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
    val selectedTextColor = androidx.compose.ui.graphics.Color.White
    val unselectedTextColor = MaterialTheme.colorScheme.onSurface
    val radioColors = androidx.compose.material3.RadioButtonDefaults.colors(
        selectedColor = androidx.compose.ui.graphics.Color.White,
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
                RadioButton(selected = selected == 1, onClick = { onSelected(1) }, colors = radioColors)
                Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_action_clipboard), color = if (selected == 1) selectedTextColor else unselectedTextColor)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == 2, onClick = { onSelected(2) }, colors = radioColors)
                Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.editor_action_notify_image), color = if (selected == 2) selectedTextColor else unselectedTextColor)
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
    val selectedTextColor = androidx.compose.ui.graphics.Color.White
    val unselectedTextColor = MaterialTheme.colorScheme.onSurface
    val radioColors = androidx.compose.material3.RadioButtonDefaults.colors(
        selectedColor = androidx.compose.ui.graphics.Color.White,
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
