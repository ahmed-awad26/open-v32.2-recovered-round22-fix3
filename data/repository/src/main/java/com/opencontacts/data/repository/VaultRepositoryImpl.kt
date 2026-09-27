package com.opencontacts.data.repository

import com.opencontacts.core.crypto.KeyAliasFactory
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.VaultSummary
import com.opencontacts.data.db.dao.VaultRegistryDao
import com.opencontacts.data.db.database.VaultDatabaseFactory
import com.opencontacts.data.db.entity.VaultRegistryEntity
import com.opencontacts.data.db.mapper.toEntity
import com.opencontacts.data.db.mapper.toModel
import com.opencontacts.domain.vaults.VaultRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class VaultRepositoryImpl @Inject constructor(
    private val dao: VaultRegistryDao,
    private val vaultDatabaseFactory: VaultDatabaseFactory,
) : VaultRepository {
    override fun observeVaults(): Flow<List<VaultSummary>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun ensureDefaultVault(): VaultSummary {
        dao.getDefaultOrNull()?.let { return it.toModel() }
        dao.getOldestOrNull()?.let { existing ->
            val now = System.currentTimeMillis()
            dao.clearDefaultFlags(now)
            dao.markAsDefault(existing.vaultId, now)
            return (dao.getById(existing.vaultId) ?: existing).toModel()
        }
        return createVault(displayName = "Personal")
    }

    override suspend fun createVault(displayName: String): VaultSummary {
        val now = System.currentTimeMillis()
        val vaultId = UUID.randomUUID().toString()
        val shouldBeDefault = dao.count() == 0
        val created = VaultRegistryEntity(
            vaultId = vaultId,
            displayName = displayName.ifBlank { "Vault" },
            colorToken = "blue",
            iconToken = "lock",
            dbFilename = "vault_${vaultId}.db",
            keyAlias = KeyAliasFactory.vaultAlias(vaultId),
            isLocked = false,
            isArchived = false,
            isDefault = shouldBeDefault,
            requiresBiometric = false,
            hasPin = false,
            createdAt = now,
            updatedAt = now,
        )
        if (shouldBeDefault) {
            dao.clearDefaultFlags(now)
        }
        dao.upsert(created)
        vaultDatabaseFactory.seedIfEmpty(
            vaultId = vaultId,
            contacts = listOf(
                ContactSummary(
                    id = "seed-${vaultId.take(8)}-1",
                    displayName = "Test",
                    primaryPhone = "+20 100 000 0001",
                    tags = listOf("Starter"),
                    isFavorite = true,
                ).toEntity(now),
            )
        )
        return created.toModel()
    }

    override suspend fun renameVault(vaultId: String, newName: String) {
        val current = dao.getById(vaultId) ?: return
        dao.upsert(current.copy(displayName = newName.ifBlank { current.displayName }, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteVault(vaultId: String) {
        val current = dao.getById(vaultId) ?: return
        vaultDatabaseFactory.deleteVaultArtifacts(current)
        dao.deleteById(vaultId)
        if (current.isDefault) {
            dao.getOldestOrNull()?.let { remaining ->
                val now = System.currentTimeMillis()
                dao.clearDefaultFlags(now)
                dao.markAsDefault(remaining.vaultId, now)
            }
        }
    }

    override suspend fun setLocked(vaultId: String, locked: Boolean) {
        val current = dao.getById(vaultId) ?: return
        dao.upsert(current.copy(isLocked = locked, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun setDefaultVault(vaultId: String) {
        if (dao.getById(vaultId) == null) return
        val now = System.currentTimeMillis()
        dao.clearDefaultFlags(now)
        dao.markAsDefault(vaultId, now)
    }
}
