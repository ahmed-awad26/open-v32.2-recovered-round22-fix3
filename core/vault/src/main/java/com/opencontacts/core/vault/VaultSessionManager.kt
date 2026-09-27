package com.opencontacts.core.vault

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class VaultSessionManager @Inject constructor() {
    private val _activeVaultId = MutableStateFlow<String?>(null)
    private val _isLocked = MutableStateFlow(true)

    val activeVaultId: StateFlow<String?> = _activeVaultId.asStateFlow()
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    fun setVault(vaultId: String, locked: Boolean) {
        _activeVaultId.value = vaultId
        _isLocked.value = locked
    }

    fun switchTo(vaultId: String, unlock: Boolean = true) {
        _activeVaultId.value = vaultId
        _isLocked.value = !unlock
    }

    fun lock() {
        _isLocked.value = true
    }

    fun unlock() {
        if (_activeVaultId.value != null) {
            _isLocked.value = false
        }
    }

    fun clear() {
        _activeVaultId.value = null
        _isLocked.value = true
    }
}
