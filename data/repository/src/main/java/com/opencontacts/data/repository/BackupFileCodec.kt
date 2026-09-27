package com.opencontacts.data.repository

import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val BACKUP_MAGIC = 0x4F434241 // OCBA
private const val BACKUP_VERSION = 1

@Singleton
class BackupFileCodec @Inject constructor() {
    fun wrap(encryptedPayload: ByteArray, createdAt: Long, itemCount: Int): ByteArray {
        val digest = sha256(encryptedPayload)
        val buffer = ByteBuffer.allocate(4 + 4 + 8 + 4 + 32 + encryptedPayload.size)
        buffer.putInt(BACKUP_MAGIC)
        buffer.putInt(BACKUP_VERSION)
        buffer.putLong(createdAt)
        buffer.putInt(itemCount)
        buffer.put(digest)
        buffer.put(encryptedPayload)
        return buffer.array()
    }

    fun unwrap(bytes: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(bytes)
        val magic = buffer.int
        val version = buffer.int
        require(magic == BACKUP_MAGIC) { "Invalid backup magic" }
        require(version == BACKUP_VERSION) { "Unsupported backup version: $version" }
        buffer.long // createdAt
        buffer.int // itemCount
        val storedDigest = ByteArray(32)
        buffer.get(storedDigest)
        val encryptedPayload = ByteArray(buffer.remaining())
        buffer.get(encryptedPayload)
        val actualDigest = sha256(encryptedPayload)
        require(storedDigest.contentEquals(actualDigest)) { "Backup integrity check failed" }
        return encryptedPayload
    }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
}
