package org.parkjw.capywarp.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.parkjw.capywarp.ui.screens.PromptEditorScreen
import org.parkjw.capywarp.ui.screens.PromptListScreen
import org.parkjw.capywarp.ui.screens.SettingsScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import org.parkjw.capywarp.ui.theme.CapyWarpTheme

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ 알림 권한 요청 (앱 실행 시 1회)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    6001
                )
            }
        }

        setContent {
            val settingsVm: org.parkjw.capywarp.ui.viewmodels.SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val themeMode by settingsVm.theme.collectAsState(initial = "system")
            val languageCode by settingsVm.language.collectAsState(initial = "en")

            // Apply language at runtime (always use selected code; default English)
            androidx.compose.runtime.LaunchedEffect(languageCode) {
                val locales = androidx.core.os.LocaleListCompat.forLanguageTags(languageCode)
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
            }

            val isDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            CapyWarpTheme(darkTheme = isDark, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "prompts"
                    ) {
                        composable("prompts") { 
                            PromptListScreen(
                                onNavigateToSettings = { navController.navigate("settings") },
                                onEditPrompt = { promptId -> navController.navigate("prompt_editor/$promptId") }
                            )
                        }

                        composable(
                            route = "prompt_editor/{promptId}",
                            arguments = listOf(
                                navArgument("promptId") { type = NavType.IntType }
                            )
                        ) { backStackEntry ->
                            PromptEditorScreen(
                                promptId = backStackEntry.arguments?.getInt("promptId"),
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}