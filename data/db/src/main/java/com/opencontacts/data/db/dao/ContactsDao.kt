package com.opencontacts.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.opencontacts.data.db.entity.BackupRecordEntity
import com.opencontacts.data.db.entity.ContactEntity
import com.opencontacts.data.db.entity.ContactFolderCrossRef
import com.opencontacts.data.db.entity.ContactTagCrossRef
import com.opencontacts.data.db.entity.ContactWithRelations
import com.opencontacts.data.db.entity.FolderEntity
import com.opencontacts.data.db.entity.ImportExportHistoryEntity
import com.opencontacts.data.db.entity.NoteEntity
import com.opencontacts.data.db.entity.ReminderEntity
import com.opencontacts.data.db.entity.TagEntity
import com.opencontacts.data.db.entity.TimelineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactsDao {
    @Transaction
    @Query("SELECT * FROM contacts WHERE is_deleted = 0 ORDER BY sort_key ASC")
    fun observeAllDetailed(): Flow<List<ContactWithRelations>>

    @Transaction
    @Query("SELECT * FROM contacts WHERE is_deleted = 0 ORDER BY sort_key ASC")
    suspend fun getAllDetailed(): List<ContactWithRelations>

    @Query("SELECT * FROM contacts WHERE is_deleted = 0 ORDER BY sort_key ASC")
    suspend fun getAll(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE contact_id = :contactId LIMIT 1")
    suspend fun getAnyById(contactId: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE contact_id = :contactId AND is_deleted = 0 LIMIT 1")
    suspend fun getById(contactId: String): ContactEntity?

    @Transaction
    @Query("SELECT * FROM contacts WHERE contact_id = :contactId AND is_deleted = 0 LIMIT 1")
    fun observeByIdDetailed(contactId: String): Flow<ContactWithRelations?>

    @Transaction
    @Query("SELECT * FROM contacts WHERE is_deleted = 1 ORDER BY deleted_at DESC, updated_at DESC")
    fun observeTrashDetailed(): Flow<List<ContactWithRelations>>

    @Transaction
    @Query("SELECT * FROM contacts WHERE is_deleted = 1 ORDER BY deleted_at DESC, updated_at DESC")
    suspend fun getTrashDetailed(): List<ContactWithRelations>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ContactEntity>)

    @Query("DELETE FROM contacts WHERE contact_id = :contactId")
    suspend fun hardDeleteById(contactId: String)

    @Query("SELECT COUNT(*) FROM contacts WHERE is_deleted = 0")
    suspend fun count(): Int

    @Query("DELETE FROM contacts")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTag(entity: TagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTags(entities: List<TagEntity>)

    @Query("SELECT * FROM tags ORDER BY display_name ASC")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY display_name ASC")
    suspend fun getTags(): List<TagEntity>

    @Query("DELETE FROM tags WHERE tag_name = :tagName")
    suspend fun deleteTag(tagName: String)

    @Query("DELETE FROM tags")
    suspend fun clearTags()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(entity: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolders(entities: List<FolderEntity>)

    @Query("SELECT * FROM folders ORDER BY display_name ASC")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY display_name ASC")
    suspend fun getFolders(): List<FolderEntity>

    @Query("DELETE FROM folders WHERE folder_name = :folderName")
    suspend fun deleteFolder(folderName: String)


    @Query("DELETE FROM folders")
    suspend fun clearFolders()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContactFolderCrossRefs(entities: List<ContactFolderCrossRef>)

    @Query("DELETE FROM contact_folder_cross_ref WHERE contact_id = :contactId")
    suspend fun deleteFolderCrossRefsForContact(contactId: String)

    @Query("DELETE FROM contact_folder_cross_ref WHERE contact_id IN (:contactIds)")
    suspend fun deleteFolderCrossRefsForContacts(contactIds: List<String>)

    @Query("DELETE FROM contact_folder_cross_ref")
    suspend fun clearAllFolderCrossRefs()

    @Query("SELECT * FROM contact_folder_cross_ref")
    suspend fun getAllFolderCrossRefs(): List<ContactFolderCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContactTagCrossRefs(entities: List<ContactTagCrossRef>)

    @Query("DELETE FROM contact_tag_cross_ref WHERE contact_id = :contactId")
    suspend fun deleteCrossRefsForContact(contactId: String)

    @Query("DELETE FROM contact_tag_cross_ref WHERE contact_id IN (:contactIds)")
    suspend fun deleteCrossRefsForContacts(contactIds: List<String>)

    @Query("DELETE FROM contact_tag_cross_ref")
    suspend fun clearAllCrossRefs()

    @Query("SELECT * FROM contact_tag_cross_ref")
    suspend fun getAllCrossRefs(): List<ContactTagCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(entity: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotes(entities: List<NoteEntity>)

    @Query("SELECT * FROM notes WHERE contact_id = :contactId ORDER BY created_at DESC")
    fun observeNotes(contactId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes")
    suspend fun getAllNotes(): List<NoteEntity>

    @Query("DELETE FROM notes WHERE note_id = :noteId")
    suspend fun deleteNoteById(noteId: String)

    @Query("DELETE FROM notes WHERE contact_id = :contactId")
    suspend fun deleteNotesForContact(contactId: String)

    @Query("DELETE FROM notes WHERE contact_id IN (:contactIds)")
    suspend fun deleteNotesForContacts(contactIds: List<String>)

    @Query("DELETE FROM notes")
    suspend fun clearNotes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminder(entity: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminders(entities: List<ReminderEntity>)

    @Update
    suspend fun updateReminder(entity: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE reminder_id = :reminderId LIMIT 1")
    suspend fun getReminderById(reminderId: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE contact_id = :contactId ORDER BY is_done ASC, due_at ASC")
    fun observeReminders(contactId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun getAllReminders(): List<ReminderEntity>

    @Query("DELETE FROM reminders WHERE reminder_id = :reminderId")
    suspend fun deleteReminderById(reminderId: String)

    @Query("DELETE FROM reminders WHERE contact_id = :contactId")
    suspend fun deleteRemindersForContact(contactId: String)

    @Query("DELETE FROM reminders WHERE contact_id IN (:contactIds)")
    suspend fun deleteRemindersForContacts(contactIds: List<String>)

    @Query("DELETE FROM reminders")
    suspend fun clearReminders()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeline(entity: TimelineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimelineItems(entities: List<TimelineEntity>)

    @Query("SELECT * FROM timeline_items WHERE contact_id = :contactId ORDER BY created_at DESC")
    fun observeTimeline(contactId: String): Flow<List<TimelineEntity>>

    @Query("SELECT * FROM timeline_items")
    suspend fun getAllTimelineItems(): List<TimelineEntity>

    @Query("DELETE FROM timeline_items WHERE contact_id = :contactId")
    suspend fun deleteTimelineForContact(contactId: String)

    @Query("DELETE FROM timeline_items WHERE contact_id IN (:contactIds)")
    suspend fun deleteTimelineForContacts(contactIds: List<String>)

    @Query("DELETE FROM timeline_items")
    suspend fun clearTimeline()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBackupRecord(entity: BackupRecordEntity)

    @Query("SELECT * FROM backup_records ORDER BY created_at DESC")
    fun observeBackupRecords(): Flow<List<BackupRecordEntity>>

    @Query("SELECT * FROM backup_records ORDER BY created_at DESC")
    suspend fun getBackupRecords(): List<BackupRecordEntity>

    @Query("DELETE FROM backup_records")
    suspend fun clearBackupRecords()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImportExportHistory(entity: ImportExportHistoryEntity)

    @Query("SELECT * FROM import_export_history ORDER BY created_at DESC")
    fun observeImportExportHistory(): Flow<List<ImportExportHistoryEntity>>

    @Query("SELECT * FROM import_export_history ORDER BY created_at DESC")
    suspend fun getImportExportHistory(): List<ImportExportHistoryEntity>

    @Query("DELETE FROM import_export_history")
    suspend fun clearImportExportHistory()
}
