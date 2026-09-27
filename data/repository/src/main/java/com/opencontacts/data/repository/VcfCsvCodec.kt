package com.opencontacts.data.repository

import com.opencontacts.core.model.ContactPhoneNumber
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.allPhoneNumbers
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val explicitFolderHeaders = setOf("folder", "foldername", "group", "groupname", "container")
private val explicitTagHeaders = setOf("tags", "tag", "categories", "labels")
private val explicitPhoneListHeaders = setOf("phonenumbers", "phones", "allphones", "secondaryphones", "additionalphones")

@Singleton
class VcfHandler @Inject constructor() {
    fun parse(stream: InputStream): List<ContactSummary> {
        val contacts = mutableListOf<ContactSummary>()
        val unfolded = mutableListOf<String>()
        var previous: String? = null

        stream.bufferedReader().forEachLine { raw ->
            val line = raw.trimEnd()
            if ((line.startsWith(" ") || line.startsWith("\t")) && previous != null) {
                previous += line.trimStart()
                unfolded[unfolded.lastIndex] = previous!!
            } else {
                previous = line
                unfolded += line
            }
        }

        var name: String? = null
        val phoneNumbers = mutableListOf<ContactPhoneNumber>()
        var tags: List<String> = emptyList()
        val folders = mutableListOf<String>()

        fun commit() {
            val displayName = decodeVcf(name?.trim().orEmpty())
            if (displayName.isBlank()) return
            contacts += ContactSummary(
                id = UUID.randomUUID().toString(),
                displayName = displayName,
                primaryPhone = phoneNumbers.firstOrNull()?.value,
                phoneNumbers = phoneNumbers.toList(),
                tags = tags,
                isFavorite = false,
                folderName = folders.firstOrNull(),
                folderNames = folders.distinctBy { it.lowercase() },
            )
        }

        unfolded.forEach { raw ->
            val line = raw.trim()
            when {
                line.equals("BEGIN:VCARD", ignoreCase = true) -> {
                    name = null
                    phoneNumbers.clear()
                    tags = emptyList()
                    folders.clear()
                }
                line.startsWith("FN:", ignoreCase = true) -> name = line.substringAfter(':')
                line.startsWith("TEL", ignoreCase = true) -> {
                    val rawValue = line.substringAfter(':').trim()
                    val cleanValue = rawValue.replace("[\\s\\-()]".toRegex(), "")
                    if (cleanValue.isNotBlank()) {
                        val header = line.substringBefore(':')
                        val typeHint = header.substringAfter("TYPE=", "mobile").substringBefore(';').substringBefore(',').trim().lowercase()
                        phoneNumbers += ContactPhoneNumber(value = cleanValue, type = typeHint.ifBlank { "mobile" })
                    }
                }
                line.startsWith("CATEGORIES:", ignoreCase = true) -> {
                    tags = line.substringAfter(':')
                        .split(',')
                        .map { decodeVcf(it).trim().removePrefix("#") }
                        .filter { it.isNotBlank() }
                        .distinctBy { it.lowercase() }
                }
                line.startsWith("X-OPENCONTACTS-FOLDER:", ignoreCase = true) ||
                    line.startsWith("X-FOLDER:", ignoreCase = true) ||
                    line.startsWith("X-GROUP:", ignoreCase = true) ||
                    line.startsWith("X-GROUP-NAME:", ignoreCase = true) -> {
                    decodeVcf(line.substringAfter(':'))
                        .split('|', ',', ';')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .forEach { folders += it }
                }
                line.equals("END:VCARD", ignoreCase = true) -> commit()
            }
        }
        return contacts
    }

    fun write(contacts: List<ContactSummary>, stream: OutputStream) {
        val writer = stream.bufferedWriter()
        contacts.forEach { contact ->
            writer.write("BEGIN:VCARD\r\n")
            writer.write("VERSION:3.0\r\n")
            writer.write("FN:${encodeVcf(contact.displayName)}\r\n")
            writer.write("N:${encodeVcf(contact.displayName)};;;;\r\n")
            contact.allPhoneNumbers().forEach { phone ->
                val type = when (phone.type.trim().lowercase()) {
                    "home" -> "HOME"
                    "work" -> "WORK"
                    else -> "CELL"
                }
                writer.write("TEL;TYPE=$type:${encodeVcf(phone.value)}\r\n")
            }
            if (contact.tags.isNotEmpty()) {
                writer.write(
                    "CATEGORIES:${contact.tags.joinToString(",") { tag -> encodeVcf(tag) }}\r\n"
                )
            }
            contact.folderNames.ifEmpty { listOfNotNull(contact.folderName) }
                .filter { it.isNotBlank() }
                .forEach { folderName ->
                    writer.write("X-OPENCONTACTS-FOLDER:${encodeVcf(folderName)}\r\n")
                }
            writer.write("END:VCARD\r\n")
        }
        writer.flush()
    }

    private fun decodeVcf(value: String): String = value
        .replace("\\n", "\n")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")

    private fun encodeVcf(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")
}

@Singleton
class CsvHandler @Inject constructor() {
    private val defaultHeader = listOf("displayName", "primaryPhone", "phoneNumbers", "tags", "folderName", "isFavorite")

    fun parse(stream: InputStream): List<ContactSummary> {
        val reader = stream.bufferedReader()
        val firstLine = reader.readLine()?.trimStart('﻿') ?: return emptyList()
        val parsedFirstLine = parseCells(firstLine)
        val hasHeader = parsedFirstLine.any { headerCell ->
            val normalized = headerCell.trim().lowercase()
            normalized in setOf("displayname", "name", "fullname", "primaryphone", "phone", "mobile", "folder", "foldername", "group", "tags", "tag")
        }

        val headerMap = if (hasHeader) {
            parsedFirstLine.mapIndexed { index, raw -> raw.trim().lowercase() to index }.toMap()
        } else {
            defaultHeader.mapIndexed { index, raw -> raw.trim().lowercase() to index }.toMap()
        }

        val contacts = ArrayList<ContactSummary>()
        if (!hasHeader) {
            parseLine(firstLine, headerMap)?.let(contacts::add)
        }
        reader.lineSequence()
            .map { it.trimStart('﻿') }
            .mapNotNull { parseLine(it, headerMap) }
            .forEach(contacts::add)
        return contacts
    }

    fun write(contacts: List<ContactSummary>, stream: OutputStream) {
        val writer = stream.bufferedWriter()
        writer.write(defaultHeader.joinToString(","))
        writer.newLine()
        contacts.forEach { c ->
            val row = listOf(
                c.displayName,
                c.allPhoneNumbers().firstOrNull()?.value.orEmpty(),
                c.allPhoneNumbers().drop(1).joinToString("|") { it.value },
                c.tags.joinToString("|"),
                c.folderName.orEmpty(),
                c.isFavorite.toString(),
            ).joinToString(",") { cell -> quote(cell) }
            writer.write(row)
            writer.newLine()
        }
        writer.flush()
    }

    private fun parseLine(line: String, headerMap: Map<String, Int>): ContactSummary? {
        if (line.isBlank()) return null
        val cells = parseCells(line)
        fun value(vararg aliases: String): String {
            val index = aliases.firstNotNullOfOrNull { alias -> headerMap[alias.lowercase()] } ?: return ""
            return cells.getOrNull(index).orEmpty().trim()
        }

        val displayName = value("displayname", "name", "fullname")
        if (displayName.isBlank()) return null

        val folderValues = value(*explicitFolderHeaders.toTypedArray())
            .split('|', ',', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        val tags = value(*explicitTagHeaders.toTypedArray())
            .split('|', ',', ';')
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        val phone = value("primaryphone", "phone", "mobile", "number").ifBlank { null }
        val extraPhones = value(*explicitPhoneListHeaders.toTypedArray())
            .split('|', ',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { ContactPhoneNumber(value = it) }
        val phoneNumbers = listOfNotNull(phone?.let { ContactPhoneNumber(value = it) }) + extraPhones
        val favoriteRaw = value("isfavorite", "favorite", "starred")

        return ContactSummary(
            id = UUID.randomUUID().toString(),
            displayName = displayName,
            primaryPhone = phoneNumbers.firstOrNull()?.value,
            phoneNumbers = phoneNumbers,
            tags = tags,
            isFavorite = favoriteRaw.equals("true", ignoreCase = true) || favoriteRaw == "1",
            folderName = folderValues.firstOrNull(),
            folderNames = folderValues,
        )
    }

    private fun parseCells(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        val delimiter = if (line.count { it == ';' } > line.count { it == ',' }) ';' else ','
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> {
                    cells += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        cells += current.toString()
        return cells
    }

    private fun quote(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
