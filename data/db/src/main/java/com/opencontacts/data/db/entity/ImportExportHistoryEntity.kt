package com.opencontacts.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "import_export_history",
    indices = [Index("created_at"), Index("operation_type")],
)
data class ImportExportHistoryEntity(
    @PrimaryKey @ColumnInfo(name = "history_id") val historyId: String,
    @ColumnInfo(name = "operation_type") val operationType: String,
    @ColumnInfo(name = "vault_id") val vaultId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "item_count") val itemCount: Int,
)
