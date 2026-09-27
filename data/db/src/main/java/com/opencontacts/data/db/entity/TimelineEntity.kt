package com.opencontacts.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "timeline_items",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["contact_id"],
            childColumns = ["contact_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("contact_id"), Index("created_at"), Index("type")],
)
data class TimelineEntity(
    @PrimaryKey @ColumnInfo(name = "timeline_id") val timelineId: String,
    @ColumnInfo(name = "contact_id") val contactId: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "subtitle") val subtitle: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
