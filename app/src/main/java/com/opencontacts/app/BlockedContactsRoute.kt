package com.opencontacts.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun BlockedContactsRoute(
    onBack: () -> Unit,
    appViewModel: AppViewModel,
) {
    val blocked by appViewModel.blockedContacts.collectAsStateWithLifecycle()
    SettingsScaffold(title = "Blocked Contacts", onBack = onBack) { modifier ->
        LazyColumn(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (blocked.isEmpty()) {
                item {
                    SettingsSection(title = "No blocked contacts") {
                        Text("Blocked contacts appear here for direct review and one-tap unblock.")
                    }
                }
            } else {
                items(blocked, key = { it.id }) { contact ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.34f)),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            androidx.compose.material3.Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(contact.displayName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(contact.primaryPhone ?: "No phone number", color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f))
                            TextButton(onClick = { appViewModel.unblockContact(contact.id) }) {
                                Text("Unblock")
                            }
                        }
                    }
                }
            }
        }
    }
}
