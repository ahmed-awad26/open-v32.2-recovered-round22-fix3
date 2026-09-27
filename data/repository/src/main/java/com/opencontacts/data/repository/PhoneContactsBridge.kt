package com.opencontacts.data.repository

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import com.opencontacts.core.model.ContactSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneContactsBridge @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun importContacts(limit: Int = Int.MAX_VALUE): List<ContactSummary> {
        val results = mutableListOf<ContactSummary>()
        val resolver = context.contentResolver
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        ) ?: return emptyList()
        cursor.use {
            val nameIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val idIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            while (it.moveToNext() && results.size < limit) {
                val displayName = it.getString(nameIndex).orEmpty().ifBlank { "Phone contact" }
                val number = it.getString(numberIndex)
                val id = "phone-" + it.getString(idIndex)
                results += ContactSummary(
                    id = id,
                    displayName = displayName,
                    primaryPhone = number,
                    tags = listOf("Imported", "Phone"),
                )
            }
        }
        return results.distinctBy { it.displayName + "|" + (it.primaryPhone ?: "") }
    }

    fun exportContacts(contacts: List<ContactSummary>): Int {
        if (contacts.isEmpty()) return 0
        val resolver = context.contentResolver
        var inserted = 0
        contacts.forEach { contact ->
            val ops = arrayListOf<ContentProviderOperation>()
            ops += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
            ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.displayName)
                .build()
            contact.primaryPhone?.takeIf { it.isNotBlank() }?.let { phone ->
                ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            }
            runCatching { resolver.applyBatch(ContactsContract.AUTHORITY, ops) }.onSuccess { inserted++ }
        }
        return inserted
    }
}
