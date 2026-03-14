package com.neuralcast.radioplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.neuralcast.radioplayer.model.AppPreferences
import com.neuralcast.radioplayer.model.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appPreferences: AppPreferences,
    onThemeChanged: (AppTheme) -> Unit,
    isAdminModeEnabled: Boolean,
    isAdminModeAuthenticating: Boolean,
    hostAdminBaseUrl: String,
    hostAdminToken: String,
    onEnableAdminMode: (String) -> Unit,
    onSaveHostAdminConfig: (String, String) -> Unit,
    onDisableAdminMode: () -> Unit,
    onOpenAdminConsole: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var adminApiKey by remember { mutableStateOf("") }
    var hostAdminUrl by remember { mutableStateOf(hostAdminBaseUrl) }
    var hostAdminTokenValue by remember { mutableStateOf(hostAdminToken) }
    var isAdminSectionExpanded by rememberSaveable { mutableStateOf(false) }

    val isHostAdminConfigured = hostAdminBaseUrl.isNotBlank() && hostAdminToken.isNotBlank()
    val adminSummary = buildString {
        append(if (isAdminModeEnabled) "AzuraCast active" else "AzuraCast off")
        append(" · ")
        append(if (isHostAdminConfigured) "Host configured" else "Host not configured")
    }

    LaunchedEffect(isAdminModeEnabled) {
        if (isAdminModeEnabled) {
            adminApiKey = ""
        }
    }
    LaunchedEffect(hostAdminBaseUrl, hostAdminToken) {
        hostAdminUrl = hostAdminBaseUrl
        hostAdminTokenValue = hostAdminToken
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    AppTheme.values().forEach { theme ->
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = theme == appPreferences.theme,
                                onClick = { onThemeChanged(theme) }
                            )
                            Text(
                                text = theme.name.toDisplayLabel(),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isAdminSectionExpanded = !isAdminSectionExpanded }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Admin",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = adminSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (isAdminSectionExpanded) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                            contentDescription = if (isAdminSectionExpanded) {
                                "Collapse admin settings"
                            } else {
                                "Expand admin settings"
                            }
                        )
                    }

                    AnimatedVisibility(visible = isAdminSectionExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "Manage both AzuraCast skip access and the host-orchestrator endpoint from here.",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            HorizontalDivider()

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "AzuraCast Admin",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isAdminModeEnabled) {
                                        "Skip controls are active. Enter a new API key only if you want to replace the stored one."
                                    } else {
                                        "Enter your AzuraCast admin API key to enable skip-track controls."
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                OutlinedTextField(
                                    value = adminApiKey,
                                    onValueChange = { adminApiKey = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = {
                                        Text(
                                            if (isAdminModeEnabled) {
                                                "Replace AzuraCast API key"
                                            } else {
                                                "AzuraCast admin API key"
                                            }
                                        )
                                    },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                                )
                                Button(
                                    onClick = { onEnableAdminMode(adminApiKey) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = adminApiKey.isNotBlank() && !isAdminModeAuthenticating
                                ) {
                                    if (isAdminModeAuthenticating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(
                                            if (isAdminModeEnabled) {
                                                "Update AzuraCast Key"
                                            } else {
                                                "Enable Admin Mode"
                                            }
                                        )
                                    }
                                }
                                if (isAdminModeEnabled) {
                                    TextButton(
                                        onClick = onDisableAdminMode,
                                        enabled = !isAdminModeAuthenticating
                                    ) {
                                        Text("Disable Admin Mode")
                                    }
                                }
                            }

                            HorizontalDivider()

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Host Orchestrator",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isHostAdminConfigured) {
                                        "The VPS endpoint is configured. Save new values if you changed the domain or token."
                                    } else {
                                        "Save the VPS admin API URL and token to unlock the Admin Console."
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                OutlinedTextField(
                                    value = hostAdminUrl,
                                    onValueChange = { hostAdminUrl = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("Host admin API URL") }
                                )
                                OutlinedTextField(
                                    value = hostAdminTokenValue,
                                    onValueChange = { hostAdminTokenValue = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("Host admin API token") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                                )
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        onSaveHostAdminConfig(
                                            hostAdminUrl,
                                            hostAdminTokenValue
                                        )
                                    }
                                ) {
                                    Text("Save Host API")
                                }
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = onOpenAdminConsole,
                                    enabled = isHostAdminConfigured
                                ) {
                                    Text("Open Admin Console")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private fun String.toDisplayLabel(): String {
    return lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
