package com.opencontacts.core.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class VaultPassphraseManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    suspend fun getOrCreatePassphrase(vaultId: String): ByteArray = withContext(Dispatchers.IO) {
        val storageKey = passphraseStorageKey(vaultId)
        val encoded = prefs.getString(storageKey, null)
        if (encoded != null) {
            decrypt(vaultId, encoded)
        } else {
            val generated = ByteArray(PASSPHRASE_LENGTH).also(SecureRandom()::nextBytes)
            prefs.edit().putString(storageKey, encrypt(vaultId, generated)).apply()
            generated
        }
    }

    suspend fun deletePassphrase(vaultId: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(passphraseStorageKey(vaultId)).apply()
        runCatching { keyStore.deleteEntry(KeyAliasFactory.vaultAlias(vaultId)) }
    }

    private fun encrypt(vaultId: String, passphrase: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey(vaultId))
        val encrypted = cipher.doFinal(passphrase)
        val iv = cipher.iv
        return "${encode(iv)}:${encode(encrypted)}"
    }

    private fun decrypt(vaultId: String, encoded: String): ByteArray {
        val parts = encoded.split(':')
        require(parts.size == 2) { "Invalid wrapped passphrase format" }
        val iv = decode(parts[0])
        val encrypted = decode(parts[1])
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(vaultId), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    private fun getOrCreateSecretKey(vaultId: String): SecretKey {
        val alias = KeyAliasFactory.vaultAlias(vaultId)
        val existing = keyStore.getKey(alias, null)
        if (existing is SecretKey) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private val keyStore: KeyStore
        get() = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
    private fun passphraseStorageKey(vaultId: String): String = "vault_passphrase_$vaultId"

    private companion object {
        const val PREFS_NAME = "vault_crypto_store"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PASSPHRASE_LENGTH = 32
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
