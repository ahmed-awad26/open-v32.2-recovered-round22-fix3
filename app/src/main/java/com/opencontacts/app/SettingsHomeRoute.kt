package com.opencontacts.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private data class SettingsEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
)

@Composable
fun SettingsHomeRoute(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val entries = listOf(
        SettingsEntry("Vaults", "Create, edit, delete, lock, and choose the default vault", Icons.Default.Folder, "vaults"),
        SettingsEntry("Groups & Tags", "Manage folders and tags for the current vault from one place", Icons.Default.Groups, "workspace"),
        SettingsEntry(stringResource(R.string.security_title), "Biometric lock, lock timing, PIN, and lock-screen customization", Icons.Default.Password, "settings/security"),
        SettingsEntry("Import & Export", "Import CSV or VCF, export in multiple formats, and open backup & restore tools", Icons.Default.Storage, "settings/importexport"),
        SettingsEntry("Preferences", "Sorting, search, density, trash retention, and contact behavior", Icons.Default.Tune, "settings/preferences"),
        SettingsEntry(stringResource(R.string.notifications_calls_title), "Default phone role, overlay access, ongoing call return, and recording", Icons.Default.Notifications, "settings/notifications"),
        SettingsEntry("Blocked Contacts", "Review blocked contacts and unblock directly", Icons.Default.Block, "settings/blocked"),
        SettingsEntry("Trash", "Restore deleted contacts or remove them permanently", Icons.Default.Archive, "settings/trash"),
        SettingsEntry(stringResource(R.string.appearance_title), "Theme presets, accent colors, language, and visual presentation", Icons.Default.Palette, "settings/appearance"),
        SettingsEntry("About", "Version, privacy-first scope, and implementation notes", Icons.Default.Info, "settings/about"),
    )
    SettingsScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries) { entry ->
                SettingsNavigationItem(
                    title = entry.title,
                    subtitle = entry.subtitle,
                    icon = entry.icon,
                    onClick = { onNavigate(entry.route) },
                )
            }
        }
    }
}
