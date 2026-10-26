package top.abdl_space.app.core.data.session

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator

class SessionCipherTest {
    private fun cipher(): AesGcmSessionCipher {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return AesGcmSessionCipher { key }
    }

    @Test
    fun `cipher encrypts token and uses unique nonces`() {
        val cipher = cipher()
        val plaintext = "secret-account-token".toByteArray()
        val first = cipher.encrypt(plaintext)
        val second = cipher.encrypt(plaintext)
        assertFalse(first.contentEquals(second))
        assertFalse(first.toString(Charsets.UTF_8).contains("secret-account-token"))
        assertTrue(plaintext.contentEquals(cipher.decrypt(first)))
    }

    @Test(expected = AEADBadTagException::class)
    fun `tampered ciphertext fails authentication`() {
        val cipher = cipher()
        val envelope = cipher.encrypt("secret".toByteArray())
        envelope[envelope.lastIndex] = (envelope.last().toInt() xor 1).toByte()
        cipher.decrypt(envelope)
    }

    @Test(expected = AEADBadTagException::class)
    fun `another install key cannot decrypt saved sessions`() {
        val envelope = cipher().encrypt("secret".toByteArray())
        cipher().decrypt(envelope)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid envelope version is rejected`() {
        val cipher = cipher()
        val envelope = cipher.encrypt("secret".toByteArray())
        envelope[0] = 2
        cipher.decrypt(envelope)
    }

    @Test
    fun `encrypted serializer round trips complete active account`() = runBlocking {
        val serializer = EncryptedSessionSerializer(cipher())
        val sessions = StoredSessions("001", listOf(SessionCredential("001", "token123", "name", "显示名称")))
        val output = ByteArrayOutputStream()
        serializer.writeTo(sessions, output)
        assertFalse(output.toString(Charsets.UTF_8).contains("token123"))
        val restored = serializer.readFrom(ByteArrayInputStream(output.toByteArray()))
        assertEquals("001", restored.activeAccountId)
        assertEquals("token123", restored.activeCredential()?.accessToken)
        assertEquals("显示名称", restored.activeCredential()?.displayName)
        assertFalse(restored.toString().contains("token123"))
    }

    @Test(expected = java.io.IOException::class)
    fun `corruption does not silently replace account storage with guest`() = runBlocking {
        EncryptedSessionSerializer(cipher()).readFrom(ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        Unit
    }
}
