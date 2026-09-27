package com.opencontacts.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "contact_folder_cross_ref",
    primaryKeys = ["contact_id", "folder_name"],
    foreignKeys = [
        ForeignKey(entity = ContactEntity::class, parentColumns = ["contact_id"], childColumns = ["contact_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = FolderEntity::class, parentColumns = ["folder_name"], childColumns = ["folder_name"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("contact_id"), Index("folder_name")]
)
data class ContactFolderCrossRef(
    @ColumnInfo(name = "contact_id") val contactId: String,
    @ColumnInfo(name = "folder_name") val folderName: String,
)
