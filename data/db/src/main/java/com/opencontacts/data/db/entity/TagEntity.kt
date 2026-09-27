package com.opencontacts.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index("display_name")]
)
data class TagEntity(
    @PrimaryKey @ColumnInfo(name = "tag_name") val tagName: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "color_token") val colorToken: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
