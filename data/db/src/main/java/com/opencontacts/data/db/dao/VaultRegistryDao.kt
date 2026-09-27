package com.opencontacts.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.opencontacts.data.db.entity.VaultRegistryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultRegistryDao {
    @Query("SELECT * FROM vault_registry ORDER BY is_default DESC, created_at ASC")
    fun observeAll(): Flow<List<VaultRegistryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VaultRegistryEntity)

    @Query("SELECT * FROM vault_registry ORDER BY is_default DESC, created_at ASC LIMIT 1")
    suspend fun firstOrNull(): VaultRegistryEntity?

    @Query("SELECT * FROM vault_registry WHERE is_default = 1 LIMIT 1")
    suspend fun getDefaultOrNull(): VaultRegistryEntity?

    @Query("SELECT * FROM vault_registry ORDER BY created_at ASC LIMIT 1")
    suspend fun getOldestOrNull(): VaultRegistryEntity?

    @Query("SELECT * FROM vault_registry WHERE vault_id = :vaultId LIMIT 1")
    suspend fun getById(vaultId: String): VaultRegistryEntity?

    @Query("SELECT COUNT(*) FROM vault_registry")
    suspend fun count(): Int

    @Query("UPDATE vault_registry SET is_default = 0, updated_at = :updatedAt WHERE is_default = 1")
    suspend fun clearDefaultFlags(updatedAt: Long)

    @Query("UPDATE vault_registry SET is_default = 1, updated_at = :updatedAt WHERE vault_id = :vaultId")
    suspend fun markAsDefault(vaultId: String, updatedAt: Long)

    @Query("DELETE FROM vault_registry WHERE vault_id = :vaultId")
    suspend fun deleteById(vaultId: String)
}
