package com.opencontacts.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "contact_tag_cross_ref",
    primaryKeys = ["contact_id", "tag_name"],
    foreignKeys = [
        ForeignKey(entity = ContactEntity::class, parentColumns = ["contact_id"], childColumns = ["contact_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["tag_name"], childColumns = ["tag_name"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("contact_id"), Index("tag_name")]
)
data class ContactTagCrossRef(
    @ColumnInfo(name = "contact_id") val contactId: String,
    @ColumnInfo(name = "tag_name") val tagName: String,
)
