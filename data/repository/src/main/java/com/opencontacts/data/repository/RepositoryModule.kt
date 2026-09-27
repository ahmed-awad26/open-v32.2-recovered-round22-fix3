package com.opencontacts.data.repository

import com.opencontacts.domain.contacts.ContactRepository
import com.opencontacts.domain.vaults.VaultRepository
import com.opencontacts.domain.vaults.VaultTransferRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindVaultRepository(impl: VaultRepositoryImpl): VaultRepository

    @Binds
    @Singleton
    abstract fun bindContactRepository(impl: ContactRepositoryImpl): ContactRepository

    @Binds
    @Singleton
    abstract fun bindVaultTransferRepository(impl: VaultTransferRepositoryImpl): VaultTransferRepository
}
