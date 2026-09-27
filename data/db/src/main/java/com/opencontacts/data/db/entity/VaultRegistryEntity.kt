package com.opencontacts.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vault_registry",
    indices = [Index(value = ["db_filename"], unique = true)]
)
data class VaultRegistryEntity(
    @PrimaryKey @ColumnInfo(name = "vault_id") val vaultId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "color_token") val colorToken: String,
    @ColumnInfo(name = "icon_token") val iconToken: String,
    @ColumnInfo(name = "db_filename") val dbFilename: String,
    @ColumnInfo(name = "key_alias") val keyAlias: String,
    @ColumnInfo(name = "is_locked") val isLocked: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
    @ColumnInfo(name = "requires_biometric") val requiresBiometric: Boolean,
    @ColumnInfo(name = "has_pin") val hasPin: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
