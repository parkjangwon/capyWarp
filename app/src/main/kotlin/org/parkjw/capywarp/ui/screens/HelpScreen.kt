package org.parkjw.capywarp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.help_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Intro (app philosophy) — remove the Q-style title per request, keep body only
            Text(text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.help_intro_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

            // 1. Permissions
            Spacer(Modifier.height(8.dp))
            Text(text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.help_section_permissions), style = MaterialTheme.typography.titleMedium)
            Text(text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.help_permissions_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

            // 2. Prompt management Tips
            Spacer(Modifier.height(8.dp))
            Text(text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.help_section_prompt_tips), style = MaterialTheme.typography.titleMedium)
            Text(text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.help_prompt_tips_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

            // 3. How to run prompts
            Spacer(Modifier.height(8.dp))
            Text(text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.help_section_how_to_run), style = MaterialTheme.typography.titleMedium)
            Text(text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.help_how_to_run_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

            // 4. Response usage
            Spacer(Modifier.height(8.dp))
            Text(text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.help_section_response_usage), style = MaterialTheme.typography.titleMedium)
            Text(text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.help_response_usage_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

            // 5. Gemini API guide
            Spacer(Modifier.height(8.dp))
            Text(text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.help_section_gemini_api), style = MaterialTheme.typography.titleMedium)
            Text(text = androidx.compose.ui.res.stringResource(org.parkjw.capywarp.R.string.help_gemini_api_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
