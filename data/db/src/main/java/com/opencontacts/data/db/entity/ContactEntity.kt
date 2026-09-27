package com.opencontacts.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contacts",
    indices = [
        Index("display_name"),
        Index("sort_key"),
        Index("primary_phone"),
        Index("is_favorite"),
        Index("updated_at"),
        Index("folder_name"),
        Index("is_deleted"),
        Index("deleted_at"),
        Index("photo_uri"),
        Index("is_blocked"),
        Index("block_mode"),
    ]
)
data class ContactEntity(
    @PrimaryKey @ColumnInfo(name = "contact_id") val contactId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "sort_key") val sortKey: String,
    @ColumnInfo(name = "primary_phone") val primaryPhone: String?,
    @ColumnInfo(name = "tag_csv") val tagCsv: String,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "folder_name") val folderName: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "photo_uri") val photoUri: String? = null,
    @ColumnInfo(name = "is_blocked") val isBlocked: Boolean = false,
    @ColumnInfo(name = "block_mode") val blockMode: String = "NONE",
    @ColumnInfo(name = "external_links_json") val externalLinksJson: String = "[]",
    @ColumnInfo(name = "phone_numbers_json") val phoneNumbersJson: String = "[]",
)
