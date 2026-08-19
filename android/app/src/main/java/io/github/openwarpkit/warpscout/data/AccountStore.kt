package io.github.openwarpkit.warpscout.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val accountFile = File(context.filesDir, "secrets/account.bin")

    suspend fun hasAccount(): Boolean = withContext(Dispatchers.IO) {
        accountFile.isFile
    }

    suspend fun read(): String? = withContext(Dispatchers.IO) {
        if (!accountFile.isFile) return@withContext null
        val bytes = accountFile.readBytes()
        if (bytes.size < 13) return@withContext null
        val buffer = ByteBuffer.wrap(bytes)
        val nonceLength = buffer.get().toInt() and 0xff
        if (nonceLength !in 12..16 || bytes.size <= nonceLength + 1) return@withContext null
        val nonce = ByteArray(nonceLength)
        buffer.get(nonce)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, nonce))
        cipher.doFinal(ciphertext).decodeToString()
    }

    suspend fun write(accountJson: String) = withContext(Dispatchers.IO) {
        requireValidAccount(accountJson)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(accountJson.encodeToByteArray())
        val output = ByteBuffer.allocate(1 + cipher.iv.size + ciphertext.size)
            .put(cipher.iv.size.toByte())
            .put(cipher.iv)
            .put(ciphertext)
            .array()
        accountFile.parentFile?.mkdirs()
        val temporaryFile = File(accountFile.parentFile, "account.tmp")
        temporaryFile.writeBytes(output)
        if (!temporaryFile.renameTo(accountFile)) {
            temporaryFile.copyTo(accountFile, overwrite = true)
            temporaryFile.delete()
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        check(!accountFile.exists() || accountFile.delete())
    }

    fun requireValidAccount(accountJson: String) {
        val value = JSONObject(accountJson)
        val required = listOf("id", "token", "private_key", "peer_public_key")
        require(required.all { value.optString(it).isNotBlank() })
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "warpscout-account-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
