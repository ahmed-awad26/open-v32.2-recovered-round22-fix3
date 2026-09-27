package com.opencontacts.domain.vaults

import com.opencontacts.core.model.VaultSummary
import kotlinx.coroutines.flow.Flow

interface VaultRepository {
    fun observeVaults(): Flow<List<VaultSummary>>
    suspend fun ensureDefaultVault(): VaultSummary
    suspend fun createVault(displayName: String): VaultSummary
    suspend fun renameVault(vaultId: String, newName: String)
    suspend fun deleteVault(vaultId: String)
    suspend fun setLocked(vaultId: String, locked: Boolean)
    suspend fun setDefaultVault(vaultId: String)
}
