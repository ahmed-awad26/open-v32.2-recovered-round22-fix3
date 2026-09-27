package com.opencontacts.data.db.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class ContactWithRelations(
    @Embedded val contact: ContactEntity,
    @Relation(
        parentColumn = "folder_name",
        entityColumn = "folder_name",
    )
    val folder: FolderEntity?,
    @Relation(
        parentColumn = "contact_id",
        entityColumn = "folder_name",
        associateBy = Junction(
            value = ContactFolderCrossRef::class,
            parentColumn = "contact_id",
            entityColumn = "folder_name",
        )
    )
    val folders: List<FolderEntity>,
    @Relation(
        parentColumn = "contact_id",
        entityColumn = "tag_name",
        associateBy = Junction(
            value = ContactTagCrossRef::class,
            parentColumn = "contact_id",
            entityColumn = "tag_name",
        )
    )
    val tags: List<TagEntity>,
)
