package com.opencontacts.feature.contacts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencontacts.core.crypto.AppLockRepository
import com.opencontacts.core.crypto.AppLockSettings
import com.opencontacts.core.model.ContactDraft
import com.opencontacts.core.model.ContactPhoneNumber
import com.opencontacts.core.model.ContactSocialLink
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.normalizedPhoneNumbers
import com.opencontacts.core.model.allPhoneNumbers
import com.opencontacts.core.model.FolderSummary
import com.opencontacts.core.model.TagSummary
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.domain.contacts.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultSessionManager: VaultSessionManager,
    private val contactRepository: ContactRepository,
    appLockRepository: AppLockRepository,
) : ViewModel() {
    val settings: StateFlow<AppLockSettings> = appLockRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLockSettings.DEFAULT)

    val contacts: StateFlow<List<ContactSummary>> = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeContacts(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val tags: StateFlow<List<TagSummary>> = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeTags(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folders: StateFlow<List<FolderSummary>> = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeFolders(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _editingContact = MutableStateFlow<ContactEditorState?>(null)
    val editingContact: StateFlow<ContactEditorState?> = _editingContact

    private val _callLogs = MutableStateFlow<List<CallLogItem>>(emptyList())
    val callLogs: StateFlow<List<CallLogItem>> = _callLogs

    val callLogGroups: StateFlow<List<CallLogGroup>> = combine(_callLogs, contacts) { logs, contactList -> logs to contactList }
        .mapLatest { (logs, contactList) ->
            withContext(Dispatchers.Default) {
                val contactsById = contactList.associateBy { it.id }
                val contactsByNumber = contactList
                    .flatMap { contact ->
                        contact.allPhoneNumbers().mapNotNull { phone ->
                            normalizePhoneForMatching(phone.value)
                                .takeIf { it.isNotBlank() }
                                ?.let { normalized -> normalized to contact }
                        }
                    }
                    .toMap()

                groupCallLogs(
                    logs.map { log ->
                        val matched = log.matchedContactId?.let(contactsById::get) ?: contactsByNumber[log.normalizedNumber]
                        if (matched != null) log.copy(matchedContactId = matched.id, matchedDisplayName = matched.displayName) else log
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            vaultSessionManager.activeVaultId
                .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
                .collect { (vaultId, isLocked) ->
                    if (vaultId != null && !isLocked) {
                        runCatching { contactRepository.warmUpVault(vaultId) }
                    }
                }
        }
    }

    private var callLogsLoaded = false
    private var callLogsRefreshing = false

    fun refreshCallLogs(force: Boolean = false) {
        if ((callLogsLoaded || callLogsRefreshing) && !force) return
        callLogsRefreshing = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = queryDeviceCallLogs(context, contacts.value)
            _callLogs.value = result
            callLogsLoaded = true
            callLogsRefreshing = false
        }
    }

    fun startCreate(prefilledPhone: String = "") {
        _editingContact.value = ContactEditorState(phone = prefilledPhone)
    }

    fun startEdit(contact: ContactSummary) {
        _editingContact.value = ContactEditorState(
            id = contact.id,
            displayName = contact.displayName,
            phone = contact.allPhoneNumbers().firstOrNull()?.value.orEmpty(),
            additionalPhoneEntries = contact.allPhoneNumbers().drop(1).map(::toEditablePhoneEntry),
            tags = contact.tags.joinToString(", "),
            isFavorite = contact.isFavorite,
            folderName = contact.folderName.orEmpty(),
            folderNames = contact.folderNames,
            photoUri = contact.photoUri.orEmpty(),
            isBlocked = contact.isBlocked,
            blockMode = contact.blockMode,
            socialLinks = contact.socialLinks,
        )
    }

    fun updateEditor(state: ContactEditorState) {
        _editingContact.value = state
    }

    fun dismissEditor() {
        _editingContact.value = null
    }

    fun saveEditor() {
        val editor = _editingContact.value ?: return
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            contactRepository.saveContactDraft(
                vaultId = vaultId,
                draft = ContactDraft(
                    id = editor.id,
                    displayName = editor.displayName.ifBlank { "Test" },
                    primaryPhone = editor.phone.ifBlank { null },
                    phoneNumbers = parseEditorPhoneNumbers(editor.phone, editor.additionalPhoneEntries),
                    tags = editor.tags.split(',').mapNotNull { it.trim().takeIf(String::isNotBlank) },
                    isFavorite = editor.isFavorite,
                    folderName = editor.folderName.ifBlank { null },
                    folderNames = editor.folderNames.ifEmpty { listOfNotNull(editor.folderName.ifBlank { null }) },
                    photoUri = editor.photoUri.ifBlank { null },
                    isBlocked = editor.isBlocked || !editor.blockMode.equals("NONE", ignoreCase = true),
                    blockMode = editor.blockMode,
                    socialLinks = editor.socialLinks,
                ),
            )
            _editingContact.value = null
        }
    }

    fun delete(contactId: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.deleteContact(vaultId, contactId) }
    }

    fun deleteMany(contactIds: Collection<String>) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactIds.forEach { contactRepository.deleteContact(vaultId, it) } }
    }

    fun assignFolderToMany(contactIds: Collection<String>, folderName: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val cleanFolder = folderName.trim().ifBlank { return }
        val currentContacts = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            contactRepository.upsertFolder(vaultId, FolderSummary(name = cleanFolder))
            contactIds.forEach { id ->
                val current = currentContacts[id] ?: return@forEach
                contactRepository.saveContactDraft(
                    vaultId,
                    ContactDraft(
                        id = current.id,
                        displayName = current.displayName,
                        primaryPhone = current.primaryPhone,
                        phoneNumbers = current.allPhoneNumbers(),
                        tags = current.tags,
                        isFavorite = current.isFavorite,
                        folderName = cleanFolder,
                        folderNames = listOf(cleanFolder),
                        photoUri = current.photoUri,
                        isBlocked = current.isBlocked,
                        blockMode = current.blockMode,
                        socialLinks = current.socialLinks,
                    ),
                )
            }
        }
    }

    fun assignTagToMany(contactIds: Collection<String>, tagName: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val cleanTag = tagName.trim().removePrefix("#").ifBlank { return }
        val currentContacts = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            val existingTag = tags.value.firstOrNull { it.name.equals(cleanTag, ignoreCase = true) }
            contactRepository.upsertTag(vaultId, TagSummary(name = cleanTag, colorToken = existingTag?.colorToken ?: "blue"))
            contactIds.forEach { id ->
                val current = currentContacts[id] ?: return@forEach
                contactRepository.saveContactDraft(
                    vaultId,
                    ContactDraft(
                        id = current.id,
                        displayName = current.displayName,
                        primaryPhone = current.primaryPhone,
                        phoneNumbers = current.allPhoneNumbers(),
                        tags = (current.tags.filterNot { it.equals(cleanTag, ignoreCase = true) } + cleanTag).distinct(),
                        isFavorite = current.isFavorite,
                        folderName = current.folderName,
                        folderNames = current.folderNames,
                        photoUri = current.photoUri,
                        isBlocked = current.isBlocked,
                        blockMode = current.blockMode,
                        socialLinks = current.socialLinks,
                    ),
                )
            }
        }
    }


    fun toggleFavoriteMany(contactIds: Collection<String>, forceValue: Boolean? = null) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val currentContacts = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            contactIds.forEach { id ->
                val current = currentContacts[id] ?: return@forEach
                val target = forceValue ?: !current.isFavorite
                contactRepository.saveContactDraft(
                    vaultId,
                    ContactDraft(
                        id = current.id,
                        displayName = current.displayName,
                        primaryPhone = current.primaryPhone,
                        phoneNumbers = current.allPhoneNumbers(),
                        tags = current.tags,
                        isFavorite = target,
                        folderName = current.folderName,
                        folderNames = current.folderNames,
                        photoUri = current.photoUri,
                        isBlocked = current.isBlocked,
                        blockMode = current.blockMode,
                        socialLinks = current.socialLinks,
                    ),
                )
            }
        }
    }

    fun removeFolderFromMany(contactIds: Collection<String>) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val currentContacts = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            contactIds.forEach { id ->
                val current = currentContacts[id] ?: return@forEach
                contactRepository.saveContactDraft(
                    vaultId,
                    ContactDraft(
                        id = current.id,
                        displayName = current.displayName,
                        primaryPhone = current.primaryPhone,
                        phoneNumbers = current.allPhoneNumbers(),
                        tags = current.tags,
                        isFavorite = current.isFavorite,
                        folderName = null,
                        folderNames = emptyList(),
                        photoUri = current.photoUri,
                        isBlocked = current.isBlocked,
                        blockMode = current.blockMode,
                        socialLinks = current.socialLinks,
                    ),
                )
            }
        }
    }
}

data class ContactEditorState(
    val id: String? = null,
    val displayName: String = "",
    val phone: String = "",
    val additionalPhoneEntries: List<EditablePhoneEntry> = emptyList(),
    val tags: String = "",
    val isFavorite: Boolean = false,
    val folderName: String = "",
    val folderNames: List<String> = emptyList(),
    val photoUri: String = "",
    val isBlocked: Boolean = false,
    val blockMode: String = "NONE",
    val socialLinks: List<ContactSocialLink> = emptyList(),
)


data class EditablePhoneEntry(
    val value: String = "",
    val label: String = "",
)

internal fun toEditablePhoneEntry(phone: ContactPhoneNumber): EditablePhoneEntry = EditablePhoneEntry(
    value = phone.value,
    label = phone.label ?: defaultLabelForPhoneType(phone.type),
)

internal fun defaultLabelForPhoneType(type: String): String = when (type.trim().lowercase()) {
    "home" -> "Home"
    "work" -> "Work"
    "mobile", "cell" -> "Mobile"
    else -> ""
}

private fun inferPhoneType(label: String): String = when (label.trim().lowercase()) {
    "home" -> "home"
    "work" -> "work"
    "mobile", "cell" -> "mobile"
    else -> "custom"
}

internal fun parseEditorPhoneNumbers(primaryPhone: String, additionalPhoneEntries: List<EditablePhoneEntry>): List<ContactPhoneNumber> =
    buildList {
        primaryPhone.trim().takeIf { it.isNotBlank() }?.let { add(ContactPhoneNumber(value = it)) }
        additionalPhoneEntries.forEach { entry ->
            val cleanValue = entry.value.trim()
            if (cleanValue.isNotBlank()) {
                val cleanLabel = entry.label.trim()
                add(
                    ContactPhoneNumber(
                        value = cleanValue,
                        type = inferPhoneType(cleanLabel),
                        label = cleanLabel.ifBlank { null },
                    )
                )
            }
        }
    }.normalizedPhoneNumbers()
