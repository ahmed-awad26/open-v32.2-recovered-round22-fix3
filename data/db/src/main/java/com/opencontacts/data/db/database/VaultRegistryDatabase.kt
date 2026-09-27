package com.opencontacts.data.db.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.opencontacts.data.db.dao.VaultRegistryDao
import com.opencontacts.data.db.entity.VaultRegistryEntity

@Database(entities = [VaultRegistryEntity::class], version = 3, exportSchema = true)
abstract class VaultRegistryDatabase : RoomDatabase() {
    abstract fun vaultRegistryDao(): VaultRegistryDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vault_registry ADD COLUMN is_default INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE vault_registry
                    SET is_default = CASE
                        WHEN vault_id = (SELECT vault_id FROM vault_registry ORDER BY created_at ASC LIMIT 1) THEN 1
                        ELSE 0
                    END
                    """.trimIndent()
                )
            }
        }
    }
}
