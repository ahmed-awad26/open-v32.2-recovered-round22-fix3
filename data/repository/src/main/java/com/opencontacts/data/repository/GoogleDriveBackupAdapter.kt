package com.opencontacts.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveBackupAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun stageEncryptedBackup(sourceName: String, sourceBytes: ByteArray): File {
        val dir = File(context.filesDir, "cloud_staging/google_drive").apply { mkdirs() }
        val target = File(dir, sourceName)
        target.writeBytes(sourceBytes)
        return target
    }
}
