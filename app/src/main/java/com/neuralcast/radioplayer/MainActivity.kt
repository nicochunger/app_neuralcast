package com.neuralcast.radioplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.neuralcast.radioplayer.ui.RadioPlayerViewModel
import com.neuralcast.radioplayer.ui.RadioScreen
import com.neuralcast.radioplayer.ui.theme.NeuralCastTheme

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        setContent {
            NeuralCastTheme {
                val viewModel: RadioPlayerViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                RadioScreen(
                    uiState = uiState,
                    onPlayToggle = { station -> viewModel.onPlayToggle(station) },
                    onErrorShown = viewModel::onErrorShown
                )
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
