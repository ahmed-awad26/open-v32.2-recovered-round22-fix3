package com.opencontacts.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.opencontacts.core.crypto.AppLockRepository
import com.opencontacts.core.crypto.AppLockSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private const val DEFAULT_BACKUP_FOLDER = "vault_backups"
private const val DEFAULT_EXPORT_FOLDER = "vault_exports"
private const val DEFAULT_RECORDINGS_FOLDER = "call_recordings"

enum class TransferDestinationKind {
    BACKUP,
    EXPORT,
    RECORDINGS,
}

data class FolderSelectionSummary(
    val uriString: String,
    val displayName: String,
)

data class SavedTransferDocument(
    val path: String,
    val sizeBytes: Long,
)

data class RecordingOutputTarget(
    val absolutePath: String? = null,
    val parcelFileDescriptor: android.os.ParcelFileDescriptor? = null,
    val displayPath: String,
)

data class ReadableTransferDocument(
    val path: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    private val byteReader: () -> ByteArray,
    private val inputStreamProvider: (() -> InputStream)? = null,
) {
    fun readBytes(): ByteArray = byteReader()
    fun readText(): String = readBytes().decodeToString()
    fun openInputStream(): InputStream = inputStreamProvider?.invoke() ?: ByteArrayInputStream(byteReader())
}

class TransferDestinationException(message: String, cause: Throwable? = null) : IOException(message, cause)

@Singleton
class TransferDestinationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLockRepository: AppLockRepository,
) {
    suspend fun setFolder(kind: TransferDestinationKind, uri: Uri): FolderSelectionSummary {
        val normalized = uri.normalizeScheme()
        try {
            context.contentResolver.takePersistableUriPermission(
                normalized,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Some providers already persist permission or do not expose it back; continue and validate the tree below.
        }

        val tree = requireTree(normalized)
        val displayName = tree.name?.takeIf(String::isNotBlank) ?: inferTreeName(normalized)
        when (kind) {
            TransferDestinationKind.BACKUP -> appLockRepository.setBackupFolder(normalized.toString(), displayName)
            TransferDestinationKind.EXPORT -> appLockRepository.setExportFolder(normalized.toString(), displayName)
            TransferDestinationKind.RECORDINGS -> appLockRepository.setRecordingsFolder(normalized.toString(), displayName)
        }
        return FolderSelectionSummary(normalized.toString(), displayName)
    }

    suspend fun resetFolder(kind: TransferDestinationKind) {
        val settings = appLockRepository.settings.first()
        val uriString = when (kind) {
            TransferDestinationKind.BACKUP -> settings.backupFolderUri
            TransferDestinationKind.EXPORT -> settings.exportFolderUri
            TransferDestinationKind.RECORDINGS -> settings.recordingsFolderUri
        }
        uriString?.let(::releasePersistedPermission)
        when (kind) {
            TransferDestinationKind.BACKUP -> appLockRepository.setBackupFolder(null, null)
            TransferDestinationKind.EXPORT -> appLockRepository.setExportFolder(null, null)
            TransferDestinationKind.RECORDINGS -> appLockRepository.setRecordingsFolder(null, null)
        }
    }

    suspend fun writeBackupDocument(
        fileName: String,
        mimeType: String,
        writeBlock: (OutputStream) -> Unit,
    ): SavedTransferDocument {
        val settings = appLockRepository.settings.first()
        return writeDocument(
            selectedFolderUri = settings.backupFolderUri,
            fallbackRelativePath = settings.backupPath.ifBlank { DEFAULT_BACKUP_FOLDER },
            fileName = fileName,
            mimeType = mimeType,
            writeBlock = writeBlock,
        )
    }

    suspend fun writeExportDocument(
        fileName: String,
        mimeType: String,
        subdirectory: String? = null,
        writeBlock: (OutputStream) -> Unit,
    ): SavedTransferDocument {
        val settings = appLockRepository.settings.first()
        return writeDocument(
            selectedFolderUri = settings.exportFolderUri,
            fallbackRelativePath = settings.exportPath.ifBlank { DEFAULT_EXPORT_FOLDER },
            fileName = fileName,
            mimeType = mimeType,
            subdirectory = subdirectory,
            writeBlock = writeBlock,
        )
    }

    suspend fun findLatestBackupDocument(prefix: String, extension: String): ReadableTransferDocument? {
        val settings = appLockRepository.settings.first()
        return findLatestDocument(
            selectedFolderUri = settings.backupFolderUri,
            fallbackRelativePath = settings.backupPath.ifBlank { DEFAULT_BACKUP_FOLDER },
            prefix = prefix,
            extension = extension,
        )
    }

    suspend fun findLatestExportDocument(prefix: String, extension: String, subdirectory: String? = null): ReadableTransferDocument? {
        val settings = appLockRepository.settings.first()
        return findLatestDocument(
            selectedFolderUri = settings.exportFolderUri,
            fallbackRelativePath = settings.exportPath.ifBlank { DEFAULT_EXPORT_FOLDER },
            prefix = prefix,
            extension = extension,
            subdirectory = subdirectory,
        )
    }

    fun describeBackupLocation(settings: AppLockSettings): String = describeFolder(
        selectedName = settings.backupFolderName,
        selectedUri = settings.backupFolderUri,
        fallbackRelativePath = settings.backupPath,
        fallbackLabel = "App storage",
    )

    fun describeExportLocation(settings: AppLockSettings): String = describeFolder(
        selectedName = settings.exportFolderName,
        selectedUri = settings.exportFolderUri,
        fallbackRelativePath = settings.exportPath,
        fallbackLabel = "App storage",
    )

    fun describeRecordingsLocation(settings: AppLockSettings): String = describeFolder(
        selectedName = settings.recordingsFolderName,
        selectedUri = settings.recordingsFolderUri,
        fallbackRelativePath = settings.recordingsPath,
        fallbackLabel = "App storage",
    )

    suspend fun prepareRecordingOutput(fileName: String): RecordingOutputTarget {
        val settings = appLockRepository.settings.first()
        val selectedFolderUri = settings.recordingsFolderUri
        return if (selectedFolderUri.isNullOrBlank()) {
            val directory = File(context.filesDir, settings.recordingsPath.ifBlank { DEFAULT_RECORDINGS_FOLDER }).apply { mkdirs() }
            val file = File(directory, fileName)
            RecordingOutputTarget(absolutePath = file.absolutePath, displayPath = "${directory.name}/$fileName")
        } else {
            val treeUri = Uri.parse(selectedFolderUri)
            val tree = requireTree(treeUri)
            tree.findFile(fileName)?.delete()
            val document = tree.createFile("audio/mp4", fileName)
                ?: throw TransferDestinationException("Could not create $fileName in the selected recordings folder.")
            val pfd = context.contentResolver.openFileDescriptor(document.uri, "w")
                ?: throw TransferDestinationException("Could not open the selected recordings folder for writing.")
            RecordingOutputTarget(parcelFileDescriptor = pfd, displayPath = "${tree.name ?: inferTreeName(treeUri)}/$fileName")
        }
    }

    private fun writeDocument(
        selectedFolderUri: String?,
        fallbackRelativePath: String,
        fileName: String,
        mimeType: String,
        subdirectory: String? = null,
        writeBlock: (OutputStream) -> Unit,
    ): SavedTransferDocument {
        return if (selectedFolderUri.isNullOrBlank()) {
            val directory = resolveFallbackDirectory(fallbackRelativePath, subdirectory)
            val file = File(directory, fileName)
            file.outputStream().use(writeBlock)
            SavedTransferDocument(path = "${directory.name}/$fileName", sizeBytes = file.length())
        } else {
            val treeUri = Uri.parse(selectedFolderUri)
            val tree = requireTree(treeUri)
            val targetDirectory = requireSubdirectory(tree, subdirectory)
            val existing = targetDirectory.findFile(fileName)
            existing?.delete()
            val document = targetDirectory.createFile(mimeType, fileName)
                ?: throw TransferDestinationException("Could not create $fileName in the selected folder.")
            context.contentResolver.openOutputStream(document.uri, "w")?.use(writeBlock)
                ?: throw TransferDestinationException("Could not write $fileName to the selected folder.")
            SavedTransferDocument(
                path = buildDisplayPath(tree.name ?: inferTreeName(treeUri), subdirectory, fileName),
                sizeBytes = document.length(),
            )
        }
    }

    private fun findLatestDocument(
        selectedFolderUri: String?,
        fallbackRelativePath: String,
        prefix: String,
        extension: String,
        subdirectory: String? = null,
    ): ReadableTransferDocument? {
        return if (selectedFolderUri.isNullOrBlank()) {
            val directory = resolveFallbackDirectory(fallbackRelativePath, subdirectory)
            val latest = directory.listFiles()
                ?.filter { it.name.startsWith(prefix) && it.extension.equals(extension, ignoreCase = true) }
                ?.maxByOrNull(File::lastModified)
                ?: return null
            ReadableTransferDocument(
                path = buildDisplayPath("AW", joinRelativePath(fallbackRelativePath, subdirectory), latest.name),
                sizeBytes = latest.length(),
                modifiedAt = latest.lastModified(),
                byteReader = latest::readBytes,
                inputStreamProvider = latest::inputStream,
            )
        } else {
            val treeUri = Uri.parse(selectedFolderUri)
            val tree = requireTree(treeUri)
            val directory = requireSubdirectory(tree, subdirectory)
            val latest = directory.listFiles()
                .filter { document ->
                    val name = document.name.orEmpty()
                    document.isFile && name.startsWith(prefix) && name.endsWith(".$extension", ignoreCase = true)
                }
                .maxByOrNull { it.lastModified() }
                ?: return null
            ReadableTransferDocument(
                path = buildDisplayPath(tree.name ?: inferTreeName(treeUri), subdirectory, latest.name.orEmpty()),
                sizeBytes = latest.length(),
                modifiedAt = latest.lastModified(),
                byteReader = {
                    context.contentResolver.openInputStream(latest.uri)?.use { it.readBytes() }
                        ?: throw TransferDestinationException("Could not read ${latest.name.orEmpty()} from the selected folder.")
                },
                inputStreamProvider = {
                    context.contentResolver.openInputStream(latest.uri)
                        ?: throw TransferDestinationException("Could not open ${latest.name.orEmpty()} from the selected folder.")
                },
            )
        }
    }

    private fun requireTree(uri: Uri): DocumentFile {
        val tree = DocumentFile.fromTreeUri(context, uri)
            ?: throw TransferDestinationException("The selected folder is no longer available.")
        if (!tree.exists() || !tree.isDirectory) {
            throw TransferDestinationException("The selected folder is no longer available.")
        }
        return tree
    }

    private fun releasePersistedPermission(uriString: String) {
        val uri = Uri.parse(uriString)
        val permission = context.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return
        val flags =
            (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        if (flags == 0) return
        try {
            context.contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // Ignore stale permission entries.
        }
    }

    private fun inferTreeName(uri: Uri): String {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull().orEmpty()
        return documentId.substringAfterLast(':').ifBlank { "Selected folder" }
    }

    private fun resolveFallbackDirectory(fallbackRelativePath: String, subdirectory: String?): File {
        val downloadsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val awRoot = File(downloadsRoot, "AW")
        val relative = joinRelativePath(fallbackRelativePath, subdirectory)
        return File(awRoot, relative.ifBlank { DEFAULT_EXPORT_FOLDER }).apply { mkdirs() }
    }

    private fun joinRelativePath(base: String, subdirectory: String?): String {
        val normalizedBase = base.trim().trim('/').removePrefix("AW/").removePrefix("AW").trim('/')
        val basePart = normalizedBase.takeUnless { it.equals("exports", ignoreCase = true) || it.equals("vault_exports", ignoreCase = true) }
        val subPart = subdirectory?.trim()?.trim('/')
        return listOfNotNull(basePart.takeIf { !it.isNullOrBlank() }, subPart.takeIf { !it.isNullOrBlank() }).joinToString("/")
    }

    private fun requireSubdirectory(tree: DocumentFile, subdirectory: String?): DocumentFile {
        if (subdirectory.isNullOrBlank()) return tree
        var current = tree
        subdirectory.split('/').filter { it.isNotBlank() }.forEach { segment ->
            current = current.findFile(segment)?.takeIf { it.isDirectory } ?: current.createDirectory(segment)
                ?: throw TransferDestinationException("Could not create $segment in the selected folder.")
        }
        return current
    }

    private fun buildDisplayPath(root: String, subdirectory: String?, fileName: String): String {
        val middle = subdirectory?.trim('/').orEmpty()
        return listOfNotNull(root, middle.takeIf { it.isNotBlank() }, fileName).joinToString("/")
    }

    private fun describeFolder(
        selectedName: String?,
        selectedUri: String?,
        fallbackRelativePath: String,
        fallbackLabel: String,
    ): String {
        return if (!selectedUri.isNullOrBlank()) {
            selectedName?.takeIf(String::isNotBlank) ?: "Selected folder"
        } else {
            listOfNotNull(fallbackLabel, "AW", joinRelativePath(fallbackRelativePath, null).takeIf { it.isNotBlank() }).joinToString("/")
        }
    }
}
