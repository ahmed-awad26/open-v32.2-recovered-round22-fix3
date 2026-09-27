package com.opencontacts.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AboutRoute(
    onBack: () -> Unit,
    appViewModel: AppViewModel,
) {
    SettingsScaffold(title = "About", onBack = onBack) { modifier ->
        LazyColumn(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SettingsSection(title = "OpenContacts", subtitle = "Private contacts and call context") {
                    SettingsValueRow(
                        title = "Version",
                        subtitle = "Current production-style revision",
                        value = appViewModel.appVersion,
                    )
                    SettingsSpacer()
                    Text(
                        "This build focuses on structured settings, stronger lock handling, safer storage locations, inline search, call visibility, and clearer status styling.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingsSection(title = "Security posture") {
                    Text(
                        "Sensitive contact data stays in the private vault database. Backup and export folder selection now uses the Storage Access Framework instead of unsafe raw file paths.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
