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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.opencontacts.core.common.startInternalCallOrPrompt
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.domain.contacts.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchRoute(
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, shape = CardDefaults.elevatedShape) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text("Search & discovery", style = MaterialTheme.typography.headlineMedium)
                        Text("Instant local search scoped to the active vault.")
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search contacts, numbers, tags, folders", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = favoritesOnly,
                            onClick = { viewModel.setFavoritesOnly(!favoritesOnly) },
                            label = { Text("Favorites only") },
                        )
                        FilterChip(
                            selected = query.any(Char::isDigit),
                            onClick = {},
                            enabled = false,
                            label = { Text("Number aware") },
                        )
                    }
                }
            }

            if (query.isBlank()) {
                // Keep the search surface intentionally empty before the user starts typing.
            } else if (results.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No results", style = MaterialTheme.typography.titleMedium)
                        Text("Try a different name, number, tag, or folder.")
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(results, key = { it.id }) { contact ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(contact.displayName, style = MaterialTheme.typography.titleMedium)
                                        if (contact.isFavorite) Icon(Icons.Default.Star, contentDescription = null)
                                    }
                                    Text(contact.primaryPhone ?: "No phone")
                                    if (contact.tags.isNotEmpty()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            contact.tags.take(3).forEach { tag -> AssistChip(onClick = {}, label = { Text(tag) }) }
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        contact.primaryPhone?.takeIf { it.isNotBlank() }?.let { phone ->
                                            startInternalCallOrPrompt(context, phone)
                                        }
                                    },
                                    enabled = !contact.primaryPhone.isNullOrBlank(),
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Call")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val vaultSessionManager: VaultSessionManager,
    private val contactRepository: ContactRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query = _query.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly = _favoritesOnly.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val results = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeContacts(vaultId)
        }
        .combine(_query) { contacts, query ->
            val normalized = query.trim().lowercase()
            if (normalized.isBlank()) contacts else contacts.filter { contact ->
                contact.displayName.lowercase().contains(normalized) ||
                    contact.primaryPhone.orEmpty().contains(normalized) ||
                    contact.tags.any { it.lowercase().contains(normalized) } ||
                    contact.folderName.orEmpty().lowercase().contains(normalized)
            }
        }
        .combine(_favoritesOnly) { contacts, favoritesOnly ->
            if (favoritesOnly) contacts.filter { it.isFavorite } else contacts
        }
        .map { it.sortedBy { contact -> contact.displayName.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setFavoritesOnly(value: Boolean) {
        _favoritesOnly.value = value
    }
}
