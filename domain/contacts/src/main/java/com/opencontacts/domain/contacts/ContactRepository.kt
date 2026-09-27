package com.opencontacts.domain.contacts

import com.opencontacts.core.model.ContactDetails
import com.opencontacts.core.model.ContactDraft
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.FolderSummary
import com.opencontacts.core.model.TagSummary
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun observeContacts(vaultId: String): Flow<List<ContactSummary>>
    fun observeContactDetails(vaultId: String, contactId: String): Flow<ContactDetails?>
    fun observeTags(vaultId: String): Flow<List<TagSummary>>
    fun observeFolders(vaultId: String): Flow<List<FolderSummary>>
    fun observeBlockedContacts(vaultId: String): Flow<List<ContactSummary>>
    fun observeTrash(vaultId: String): Flow<List<ContactSummary>>
    suspend fun upsertContact(vaultId: String, contact: ContactSummary)
    suspend fun saveContactDraft(vaultId: String, draft: ContactDraft): String
    suspend fun deleteContact(vaultId: String, contactId: String)
    suspend fun restoreContact(vaultId: String, contactId: String)
    suspend fun permanentlyDeleteContact(vaultId: String, contactId: String)
    suspend fun purgeDeletedOlderThan(vaultId: String, cutoffMillis: Long)
    suspend fun addNote(vaultId: String, contactId: String, body: String)
    suspend fun deleteNote(vaultId: String, noteId: String)
    suspend fun addReminder(vaultId: String, contactId: String, title: String, dueAt: Long)
    suspend fun setReminderDone(vaultId: String, reminderId: String, done: Boolean)
    suspend fun deleteReminder(vaultId: String, reminderId: String)
    suspend fun upsertTag(vaultId: String, tag: TagSummary)
    suspend fun deleteTag(vaultId: String, tagName: String)
    suspend fun upsertFolder(vaultId: String, folder: FolderSummary)
    suspend fun deleteFolder(vaultId: String, folderName: String)
    suspend fun setContactBlocked(vaultId: String, contactId: String, blocked: Boolean)
    suspend fun setContactBlockMode(vaultId: String, contactId: String, mode: String)
    suspend fun warmUpVault(vaultId: String)
}
