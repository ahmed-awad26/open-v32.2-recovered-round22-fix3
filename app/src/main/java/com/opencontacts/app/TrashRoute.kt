package com.opencontacts.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.domain.contacts.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun TrashRoute(
    onBack: () -> Unit,
    appViewModel: AppViewModel,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val settings by appViewModel.appLockSettings.collectAsStateWithLifecycle()
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    SettingsScaffold(title = "Trash", onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Trash overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (items.isEmpty()) "Trash is currently empty."
                                    else "${items.size} deleted contact(s) can still be restored before final removal.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Retention policy", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        "Deleted contacts are kept for ${settings.trashRetentionDays} day(s) unless you empty the trash manually.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        FilledTonalButton(
                            onClick = { confirmEmptyTrash = true },
                            enabled = items.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                            Text("Empty trash")
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                item {
                    SettingsSection(title = "Trash is empty") {
                        Text("Deleted contacts will appear here until they are restored or permanently removed.")
                    }
                }
            } else {
                items(items, key = { it.id }) { contact ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(contact.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(contact.primaryPhone ?: "No phone", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { viewModel.restore(contact.id) }) {
                                    Icon(Icons.Default.RestoreFromTrash, contentDescription = "Restore")
                                }
                                IconButton(onClick = { pendingDeleteId = contact.id }) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = "Delete forever")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmEmptyTrash) {
        AlertDialog(
            onDismissRequest = { confirmEmptyTrash = false },
            title = { Text("Empty trash?") },
            text = { Text("This will permanently delete every contact currently in trash. This action cannot be undone.") },
            confirmButton = {
                FilledTonalButton(onClick = {
                    confirmEmptyTrash = false
                    viewModel.emptyTrash()
                }) { Text("Empty now") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmEmptyTrash = false }) { Text("Cancel") }
            },
        )
    }

    pendingDeleteId?.let { contactId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete forever?") },
            text = { Text("This contact will be removed permanently from trash and cannot be restored.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteForever(contactId)
                    pendingDeleteId = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            },
        )
    }
}

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val sessionManager: VaultSessionManager,
    private val contactRepository: ContactRepository,
) : ViewModel() {
    val items: StateFlow<List<ContactSummary>> = sessionManager.activeVaultId
        .combine(sessionManager.isLocked) { vaultId, locked -> vaultId to locked }
        .flatMapLatest { (vaultId, locked) ->
            if (vaultId == null || locked) flowOf(emptyList()) else contactRepository.observeTrash(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(contactId: String) {
        val vaultId = sessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.restoreContact(vaultId, contactId) }
    }

    fun deleteForever(contactId: String) {
        val vaultId = sessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.permanentlyDeleteContact(vaultId, contactId) }
    }

    fun emptyTrash() {
        val vaultId = sessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.purgeDeletedOlderThan(vaultId, Long.MAX_VALUE) }
    }
}
