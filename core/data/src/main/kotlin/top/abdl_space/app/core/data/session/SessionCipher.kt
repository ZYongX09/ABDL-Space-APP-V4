package top.abdl_space.app.core.data.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

interface SessionCipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(envelope: ByteArray): ByteArray
}

internal class AesGcmSessionCipher(private val key: () -> SecretKey) : SessionCipher {
    private val associatedData = "ABDL-Space-V4/session/v1".toByteArray(Charsets.UTF_8)

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        require(iv.size == IV_SIZE)
        return ByteBuffer.allocate(HEADER_SIZE + iv.size + ciphertext.size)
            .put(VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
    }

    override fun decrypt(envelope: ByteArray): ByteArray {
        require(envelope.size in MIN_ENVELOPE_SIZE..MAX_ENVELOPE_SIZE) { "Invalid session envelope" }
        val buffer = ByteBuffer.wrap(envelope)
        require(buffer.get() == VERSION) { "Unsupported session envelope" }
        require(buffer.get().toInt() == IV_SIZE) { "Invalid session IV" }
        val iv = ByteArray(IV_SIZE).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext)
    }

    private companion object {
        const val VERSION: Byte = 1
        const val IV_SIZE = 12
        const val HEADER_SIZE = 2
        const val MIN_ENVELOPE_SIZE = HEADER_SIZE + IV_SIZE + 16
        const val MAX_ENVELOPE_SIZE = 1_048_576
    }
}

@Singleton
class AndroidSessionCipher @Inject constructor() : SessionCipher {
    private val cipher = AesGcmSessionCipher(::loadKey)

    override fun encrypt(plaintext: ByteArray): ByteArray = cipher.encrypt(plaintext)
    override fun decrypt(envelope: ByteArray): ByteArray = cipher.decrypt(envelope)

    @Synchronized
    fun resetKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS)
    }

    @Synchronized
    private fun loadKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "abdl_space_v4_sessions_v1"
    }
}
