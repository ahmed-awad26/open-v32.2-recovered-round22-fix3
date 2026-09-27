package com.opencontacts.core.crypto

object KeyAliasFactory {
    fun vaultAlias(vaultId: String): String = "opencontacts_vault_$vaultId"
}
