package com.opencontacts.data.db.database

import android.content.Context
import androidx.room.Room
import com.opencontacts.core.crypto.VaultPassphraseManager
import com.opencontacts.data.db.dao.VaultRegistryDao
import com.opencontacts.data.db.entity.ContactEntity
import com.opencontacts.data.db.entity.VaultRegistryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Singleton
class VaultDatabaseFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultRegistryDao: VaultRegistryDao,
    private val vaultPassphraseManager: VaultPassphraseManager,
) {
    private val cache = ConcurrentHashMap<String, VaultDatabase>()
    private val mutex = Mutex()

    suspend fun getDatabase(vaultId: String): VaultDatabase {
        cache[vaultId]?.let { return it }
        return mutex.withLock {
            cache[vaultId]?.let { return@withLock it }

            SqlCipherLoader.ensureLoaded()

            val vault = requireNotNull(vaultRegistryDao.getById(vaultId)) {
                "Vault $vaultId is not registered"
            }
            val database = buildDatabase(vault)
            cache[vaultId] = database
            database
        }
    }

    suspend fun seedIfEmpty(vaultId: String, contacts: List<ContactEntity>) {
        if (contacts.isEmpty()) return
        val database = getDatabase(vaultId)
        if (database.contactsDao().count() == 0) {
            database.contactsDao().upsertAll(contacts)
        }
    }

    suspend fun deleteVaultArtifacts(vault: VaultRegistryEntity) {
        closeVault(vault.vaultId)
        context.getDatabasePath(vault.dbFilename).delete()
        context.getDatabasePath(vault.dbFilename + "-wal").delete()
        context.getDatabasePath(vault.dbFilename + "-shm").delete()
        vaultPassphraseManager.deletePassphrase(vault.vaultId)
    }

    suspend fun closeVault(vaultId: String) {
        mutex.withLock {
            cache.remove(vaultId)?.close()
        }
    }

    suspend fun closeAll() {
        mutex.withLock {
            cache.values.forEach { it.close() }
            cache.clear()
        }
    }

    private suspend fun buildDatabase(vault: VaultRegistryEntity): VaultDatabase {
        val file = context.getDatabasePath(vault.dbFilename)
        file.parentFile?.mkdirs()
        val passphrase = vaultPassphraseManager.getOrCreatePassphrase(vault.vaultId)
        val factory = SupportOpenHelperFactory(passphrase.copyOf())
        passphrase.fill(0.toByte())

        return Room.databaseBuilder(context, VaultDatabase::class.java, file.absolutePath)
            .openHelperFactory(factory)
            .addMigrations(VAULT_DB_MIGRATION_5_6)
            .addMigrations(VAULT_DB_MIGRATION_6_7)
            .addMigrations(VAULT_DB_MIGRATION_7_8)
            .addMigrations(VAULT_DB_MIGRATION_8_9)
            .fallbackToDestructiveMigration()
            .build()
    }
}

private object SqlCipherLoader {
    private val loaded = AtomicBoolean(false)

    fun ensureLoaded() {
        if (loaded.compareAndSet(false, true)) {
            System.loadLibrary("sqlcipher")
        }
    }
}
