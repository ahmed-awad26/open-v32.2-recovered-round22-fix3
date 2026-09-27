package com.opencontacts.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["contact_id"],
            childColumns = ["contact_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("contact_id"), Index("due_at"), Index("is_done")],
)
data class ReminderEntity(
    @PrimaryKey @ColumnInfo(name = "reminder_id") val reminderId: String,
    @ColumnInfo(name = "contact_id") val contactId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "due_at") val dueAt: Long,
    @ColumnInfo(name = "is_done") val isDone: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
