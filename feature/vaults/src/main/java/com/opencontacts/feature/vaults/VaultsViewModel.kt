package com.opencontacts.feature.vaults

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencontacts.core.model.ContactDraft
import com.opencontacts.core.model.ContactPhoneNumber
import com.opencontacts.core.model.VaultSummary
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.domain.contacts.ContactRepository
import com.opencontacts.domain.vaults.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VaultsViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val contactRepository: ContactRepository,
    private val sessionManager: VaultSessionManager,
) : ViewModel() {
    val vaults: StateFlow<List<VaultSummary>> = repository.observeVaults()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeVaultId: StateFlow<String?> = sessionManager.activeVaultId

    private val _editingVault = MutableStateFlow<VaultEditorState?>(null)
    val editingVault: StateFlow<VaultEditorState?> = _editingVault.asStateFlow()

    private val _quickAddContact = MutableStateFlow<QuickAddContactState?>(null)
    val quickAddContact: StateFlow<QuickAddContactState?> = _quickAddContact.asStateFlow()

    init {
        viewModelScope.launch {
            val default = repository.ensureDefaultVault()
            if (sessionManager.activeVaultId.value == null) {
                sessionManager.setVault(default.id, locked = default.isLocked)
            }
        }
    }

    fun activate(vaultId: String) {
        viewModelScope.launch {
            val target = vaults.value.firstOrNull { it.id == vaultId } ?: return@launch
            sessionManager.setVault(vaultId, locked = target.isLocked)
        }
    }

    fun setDefaultVault(vaultId: String) {
        viewModelScope.launch {
            repository.setDefaultVault(vaultId)
        }
    }

    fun startCreate() {
        _editingVault.value = VaultEditorState(
            displayName = "",
            makeDefault = vaults.value.isEmpty(),
            useAfterSave = false,
        )
    }

    fun startRename(vault: VaultSummary) {
        _editingVault.value = VaultEditorState(
            id = vault.id,
            displayName = vault.displayName,
            makeDefault = vault.isDefault,
            useAfterSave = sessionManager.activeVaultId.value == vault.id,
        )
    }

    fun updateEditor(state: VaultEditorState) {
        _editingVault.value = state
    }

    fun dismissEditor() {
        _editingVault.value = null
    }

    fun saveEditor() {
        val editor = _editingVault.value ?: return
        viewModelScope.launch {
            if (editor.id == null) {
                val created = repository.createVault(editor.displayName)
                if (editor.makeDefault) {
                    repository.setDefaultVault(created.id)
                }
                if (editor.useAfterSave) {
                    val refreshed = vaults.value.firstOrNull { it.id == created.id } ?: created
                    sessionManager.setVault(created.id, locked = refreshed.isLocked)
                }
            } else {
                repository.renameVault(editor.id, editor.displayName)
                if (editor.makeDefault) {
                    repository.setDefaultVault(editor.id)
                }
                if (editor.useAfterSave) {
                    val refreshed = vaults.value.firstOrNull { it.id == editor.id }
                    sessionManager.setVault(editor.id, locked = refreshed?.isLocked ?: false)
                }
            }
            _editingVault.value = null
        }
    }

    fun startQuickAdd(vault: VaultSummary) {
        _quickAddContact.value = QuickAddContactState(vaultId = vault.id, vaultName = vault.displayName)
    }

    fun updateQuickAdd(state: QuickAddContactState) {
        _quickAddContact.value = state
    }

    fun dismissQuickAdd() {
        _quickAddContact.value = null
    }

    fun saveQuickAdd() {
        val draft = _quickAddContact.value ?: return
        viewModelScope.launch {
            contactRepository.saveContactDraft(
                vaultId = draft.vaultId,
                draft = ContactDraft(
                    displayName = draft.displayName.ifBlank { "Test" },
                    primaryPhone = draft.primaryPhone.ifBlank { null },
                    phoneNumbers = buildList {
                        draft.primaryPhone.trim().takeIf { it.isNotBlank() }?.let { add(ContactPhoneNumber(value = it)) }
                        draft.additionalPhones
                            .lineSequence()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .forEach { add(ContactPhoneNumber(value = it)) }
                    },
                    isFavorite = draft.isFavorite,
                ),
            )
            _quickAddContact.value = null
        }
    }

    fun deleteVault(vaultId: String) {
        viewModelScope.launch {
            val deletingActive = sessionManager.activeVaultId.value == vaultId
            repository.deleteVault(vaultId)
            val fallback = repository.ensureDefaultVault()
            if (deletingActive || sessionManager.activeVaultId.value == null) {
                sessionManager.setVault(fallback.id, locked = fallback.isLocked)
            }
        }
    }

    fun lockVault(vaultId: String) {
        viewModelScope.launch {
            repository.setLocked(vaultId, true)
            if (sessionManager.activeVaultId.value == vaultId) {
                sessionManager.lock()
            }
        }
    }
}

data class VaultEditorState(
    val id: String? = null,
    val displayName: String = "",
    val makeDefault: Boolean = false,
    val useAfterSave: Boolean = false,
)

data class QuickAddContactState(
    val vaultId: String,
    val vaultName: String,
    val displayName: String = "",
    val primaryPhone: String = "",
    val additionalPhones: String = "",
    val isFavorite: Boolean = false,
)
