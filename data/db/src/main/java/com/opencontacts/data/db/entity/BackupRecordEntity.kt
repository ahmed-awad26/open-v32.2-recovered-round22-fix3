package com.opencontacts.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "backup_records",
    indices = [Index("created_at"), Index("status")],
)
data class BackupRecordEntity(
    @PrimaryKey @ColumnInfo(name = "backup_id") val backupId: String,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "vault_id") val vaultId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long,
)
