package com.neuralcast.radioplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.neuralcast.radioplayer.model.AppTheme
import com.neuralcast.radioplayer.ui.RadioPlayerViewModel
import com.neuralcast.radioplayer.ui.RadioScreen
import com.neuralcast.radioplayer.ui.SettingsScreen
import com.neuralcast.radioplayer.ui.theme.NeuralCastTheme

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        setContent {
            val viewModel: RadioPlayerViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val isDarkTheme = when (uiState.appPreferences.theme) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }

            NeuralCastTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "radio") {
                    composable("radio") {
                        RadioScreen(
                            uiState = uiState,
                            onPlayToggle = { station -> viewModel.onPlayToggle(station) },
                            onVolumeChange = viewModel::setVolume,
                            onSleepTimerSet = viewModel::setSleepTimer,
                            onErrorShown = viewModel::onErrorShown,
                            onSettingsClick = { navController.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            appPreferences = uiState.appPreferences,
                            onThemeChanged = viewModel::saveTheme,
                            onBufferSizeChanged = viewModel::saveBufferSize,
                            onDefaultVolumeChanged = viewModel::saveDefaultVolume,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        val permissionCheck = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        )
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            return
        }
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
