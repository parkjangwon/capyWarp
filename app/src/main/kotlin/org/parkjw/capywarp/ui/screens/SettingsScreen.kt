package org.parkjw.capywarp.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.parkjw.capywarp.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHelp: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    val apiKey by viewModel.apiKey.collectAsState()
    val model by viewModel.model.collectAsState()
    val imageModel by viewModel.imageModel.collectAsState()
    val theme by viewModel.theme.collectAsState()

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            viewModel.exportPrompts(context.contentResolver, uri) { ok, msg ->
                scope.launch { snackbarHost.showSnackbar(msg) }
            }
        }
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importPrompts(context.contentResolver, uri) { ok, msg ->
                scope.launch { snackbarHost.showSnackbar(msg) }
            }
        }
    }

    Scaffold(
        topBar = {},
        snackbarHost = { SnackbarHost(hostState = snackbarHost) },
        bottomBar = { org.parkjw.capywarp.ui.components.BannerAd(modifier = Modifier.fillMaxWidth()) }
    ) { paddingValues ->
        // 스크롤 지원을 위해 verticalScroll 적용
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1) 테마
            Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_theme_section), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.fillMaxWidth()) {
                // Removed duplicate inline label; keep section header and dropdown label only
                ThemeDropdown(current = theme, onSelected = viewModel::setTheme)
            }

            // Language selection just below Theme
            val language by viewModel.language.collectAsState()
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(8.dp))
                Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_language), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                LanguageDropdown(current = language, onSelected = viewModel::setLanguage)
            }

            Divider()

            // 2) Gemini API 설정
            Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_gemini_section), style = MaterialTheme.typography.titleMedium)
            Text(
                text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_api_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = viewModel::setApiKey,
                label = { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_api_key_label)) },
                supportingText = {
                    Text(
                        androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_api_get_key),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Column(Modifier.fillMaxWidth()) {
                Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_model_label), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                ModelDropdown(current = model, onSelected = viewModel::setModel)
                Spacer(Modifier.height(4.dp))
                Text(
                    androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_model_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(Modifier.fillMaxWidth()) {
                Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_image_model_label), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                ImageModelDropdown(current = imageModel, onSelected = viewModel::setImageModel)
            }

            // Tip card
            androidx.compose.material3.ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("💡 " + androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_tip_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_tip_free_plan),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider()

            // 3) User prompt
            Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_user_prompt_section), style = MaterialTheme.typography.titleMedium)
            val userPrompt by viewModel.userPrompt.collectAsState()
            // Use local buffered state to avoid IME glitches caused by immediate DataStore writes
            var localUserPrompt by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(userPrompt) }
            // Keep local state in sync when settings change externally (e.g., restore)
            LaunchedEffect(userPrompt) {
                if (userPrompt != localUserPrompt) localUserPrompt = userPrompt
            }
            var saveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
            OutlinedTextField(
                value = localUserPrompt,
                onValueChange = { v ->
                    localUserPrompt = v
                    saveJob?.cancel()
                    saveJob = scope.launch {
                        kotlinx.coroutines.delay(200)
                        viewModel.setUserPrompt(v)
                    }
                },
                label = { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_user_prompt_label)) },
                supportingText = { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_user_prompt_support)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            // Auto-attach selected text settings
            val autoAttach by viewModel.autoAttachSelectedText.collectAsState()
            val attachPos by viewModel.autoAttachPosition.collectAsState()
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_auto_attach),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.Switch(checked = autoAttach, onCheckedChange = { viewModel.setAutoAttachSelectedText(it) })
            }
            if (autoAttach) {
                Spacer(Modifier.height(8.dp))
                Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_auto_attach_position), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                AttachPositionDropdown(current = attachPos, onSelected = viewModel::setAutoAttachPosition)
            }

            Divider()

            // 4) Backup & Restore
            Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_backup_section), style = MaterialTheme.typography.titleMedium)
            Text(
                text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_backup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    val now = java.time.LocalDateTime.now()
                    val fileName = "capyWarp-backup-%04d%02d%02d%02d%02d.json".format(
                        now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute
                    )
                    createDoc.launch(fileName)
                }) { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_backup_create)) }
                Button(onClick = { openDoc.launch(arrayOf("application/json")) }) { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_backup_restore)) }
            }

            // Single compact help button just above version info (no extra section)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Button(onClick = onNavigateToHelp) {
                    Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.help_title))
                }
            }

            Spacer(Modifier.height(24.dp))
            // App version information
            Text(
                text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_app_version) + "\n" +
                        androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_version_detail, org.parkjw.capywarp.BuildConfig.VERSION_NAME, org.parkjw.capywarp.BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeDropdown(current: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // 요구사항: 순서 = 시스템 -> 라이트 -> 다크, 기본값 = 시스템
    val options = listOf(
        "system" to androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_theme_system),
        "light" to androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_theme_light),
        "dark" to androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_theme_dark)
    )
    val currentLabel = options.firstOrNull { it.first == current }?.second ?: options.first().second
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_theme_choose)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    onSelected(id)
                    expanded = false
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(current: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = SettingsViewModel.AVAILABLE_MODELS
    val currentLabel = options.firstOrNull { it.first == current }?.second ?: current
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_model_choose)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    onSelected(id)
                    expanded = false
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageModelDropdown(current: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = SettingsViewModel.AVAILABLE_IMAGE_MODELS
    val currentLabel = options.firstOrNull { it.first == current }?.second ?: current
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_image_model_choose)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    onSelected(id)
                    expanded = false
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(current: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = SettingsViewModel.AVAILABLE_LANGUAGES
    val currentLabel = options.firstOrNull { it.first == current }?.second ?: options.first().second
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_language_choose)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (code, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    onSelected(code)
                    expanded = false
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachPositionDropdown(current: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        "top" to androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_attach_top),
        "bottom" to androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_attach_bottom)
    )
    val currentLabel = options.firstOrNull { it.first == current }?.second ?: options.first().second
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.settings_auto_attach_position)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    onSelected(id)
                    expanded = false
                })
            }
        }
    }
}
