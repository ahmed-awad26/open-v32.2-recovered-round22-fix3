package com.opencontacts.feature.contacts

import com.opencontacts.core.ui.ReliableOutlinedTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AdditionalPhoneEntriesEditor(
    entries: List<EditablePhoneEntry>,
    onEntriesChange: (List<EditablePhoneEntry>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Additional phones",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Add each extra number separately and give it its own label.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entries.isEmpty()) {
            Text(
                text = "No extra numbers yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        entries.forEachIndexed { index, entry ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Extra number ${index + 1}", style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = {
                            onEntriesChange(entries.toMutableList().apply { removeAt(index) })
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove number")
                        }
                    }
                    ReliableOutlinedTextField(
                        value = entry.value,
                        onValueChange = { value ->
                            onEntriesChange(entries.toMutableList().apply { this[index] = entry.copy(value = value) })
                        },
                        label = { Text("Phone number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    ReliableOutlinedTextField(
                        value = entry.label,
                        onValueChange = { label ->
                            onEntriesChange(entries.toMutableList().apply { this[index] = entry.copy(label = label) })
                        },
                        label = { Text("Label") },
                        placeholder = { Text("WhatsApp / Work / Home / Telegram...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("WhatsApp", "Telegram", "Home", "Work").forEach { suggestion ->
                            SuggestionChip(
                                onClick = {
                                    onEntriesChange(entries.toMutableList().apply { this[index] = entry.copy(label = suggestion) })
                                },
                                label = { Text(suggestion) },
                            )
                        }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { onEntriesChange(entries + EditablePhoneEntry()) },
            colors = ButtonDefaults.outlinedButtonColors(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Add another number")
        }
    }
}
