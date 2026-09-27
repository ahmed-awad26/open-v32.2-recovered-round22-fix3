package com.opencontacts.data.db.database

import android.content.Context
import androidx.room.Room
import com.opencontacts.data.db.dao.VaultRegistryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideVaultRegistryDatabase(@ApplicationContext context: Context): VaultRegistryDatabase =
        Room.databaseBuilder(context, VaultRegistryDatabase::class.java, "vault_registry.db")
            .addMigrations(VaultRegistryDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideVaultRegistryDao(db: VaultRegistryDatabase): VaultRegistryDao = db.vaultRegistryDao()
}
