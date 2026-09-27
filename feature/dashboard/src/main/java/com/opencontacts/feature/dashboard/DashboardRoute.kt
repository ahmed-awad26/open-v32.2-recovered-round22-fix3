package com.opencontacts.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.opencontacts.core.ui.OpenContactsLogo

@Composable
fun DashboardRoute(
    activeVaultName: String,
    vaultCount: Int,
    contactCount: Int,
    onOpenContacts: () -> Unit,
    onOpenVaults: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenImportExport: () -> Unit,
) {
    val actions = listOf(
        DashboardAction("Contacts", "Add, edit, delete, call", Icons.Default.Contacts, onOpenContacts),
        DashboardAction("Search", "Fast lookup inside vault", Icons.Default.ManageSearch, onOpenSearch),
        DashboardAction("Tags & folders", "Organize modern workspaces", Icons.Default.Workspaces, onOpenWorkspace),
        DashboardAction("Vaults", "Switch and secure workspaces", Icons.Default.Security, onOpenVaults),
        DashboardAction("Security", "PIN, biometric, lock state", Icons.Default.AdminPanelSettings, onOpenSecurity),
        DashboardAction("Backup", "Encrypted backups and restore", Icons.Default.Backup, onOpenBackup),
        DashboardAction("Import/Export", "VCF, CSV, JSON workflows", Icons.Default.FolderCopy, onOpenImportExport),
    )

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = CardDefaults.elevatedShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        OpenContactsLogo(modifier = Modifier.size(56.dp), cornerRadius = 16.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("OpenContacts", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Private contacts workspace with modern vault-first architecture, security controls, and structured workflows.",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill("Active vault", activeVaultName)
                        StatPill("Vaults", vaultCount.toString())
                        StatPill("Contacts", contactCount.toString())
                    }
                }
            }

            Text("Workspace", style = MaterialTheme.typography.titleLarge)

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(actions) { action ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = action.onClick,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CardDefaults.shape,
                            ) {
                                Icon(
                                    action.icon,
                                    contentDescription = null,
                                    modifier = Modifier.padding(12.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(action.title, style = MaterialTheme.typography.titleMedium)
                            Text(action.subtitle, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = action.onClick, modifier = Modifier.padding(top = 4.dp)) {
                                Text("Open")
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class DashboardAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun StatPill(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = CardDefaults.shape,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
