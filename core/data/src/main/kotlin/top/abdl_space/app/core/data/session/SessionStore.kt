package top.abdl_space.app.core.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

interface SessionStore {
    suspend fun load(): StoredSessions
    suspend fun save(sessions: StoredSessions)
    suspend fun quarantine()
    suspend fun reset()
}

internal class EncryptedSessionSerializer(private val cipher: SessionCipher) : Serializer<StoredSessions> {
    private val json = Json { ignoreUnknownKeys = true }
    override val defaultValue: StoredSessions = StoredSessions()

    override suspend fun readFrom(input: InputStream): StoredSessions {
        val envelope = input.readBytes()
        try {
            val plaintext = cipher.decrypt(envelope)
            return try {
                json.decodeFromString<StoredSessions>(plaintext.toString(Charsets.UTF_8))
            } finally {
                plaintext.fill(0)
            }
        } catch (exception: GeneralSecurityException) {
            throw SessionStorageException(SessionStorageFailure.DECRYPTION, exception)
        } catch (exception: java.security.ProviderException) {
            throw SessionStorageException(SessionStorageFailure.DECRYPTION, exception)
        } catch (exception: SerializationException) {
            throw SessionStorageException(SessionStorageFailure.INVALID_CONTENT, exception)
        } catch (exception: IllegalArgumentException) {
            throw SessionStorageException(SessionStorageFailure.INVALID_CONTENT, exception)
        }
    }

    override suspend fun writeTo(t: StoredSessions, output: OutputStream) {
        val plaintext = json.encodeToString(t).toByteArray(Charsets.UTF_8)
        try {
            output.write(cipher.encrypt(plaintext))
        } catch (exception: GeneralSecurityException) {
            throw IOException("Unable to secure V4 account storage", exception)
        } catch (exception: java.security.ProviderException) {
            throw SessionStorageException(SessionStorageFailure.DECRYPTION, exception)
        } finally {
            plaintext.fill(0)
        }
    }
}

@Singleton
class EncryptedSessionStore @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: AndroidSessionCipher,
) : SessionStore {
    private val file = File(context.noBackupFilesDir, "v4-sessions.enc")
    private val quarantineFile = File(context.noBackupFilesDir, "v4-sessions-quarantined")
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dataStore: DataStore<StoredSessions> = createStore()

    private fun createStore(): DataStore<StoredSessions> = DataStoreFactory.create(
        serializer = EncryptedSessionSerializer(cipher),
        scope = scope,
        produceFile = { file },
    )

    override suspend fun load(): StoredSessions = locked {
        if (quarantineFile.exists()) throw SessionStorageException(SessionStorageFailure.LOGOUT_UNCONFIRMED)
        dataStore.data.first()
    }

    override suspend fun save(sessions: StoredSessions) = locked {
        dataStore.updateData { sessions }
        clearQuarantine()
    }

    override suspend fun quarantine() = locked { writeQuarantine() }

    override suspend fun reset() = locked {
        // Explicit user-confirmed reset only. Cancel the old DataStore before removing its file.
        writeQuarantine()
        scope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
        try {
            if (file.exists() && !file.delete()) throw IOException("Unable to reset account storage")
            cipher.resetKey()
        } catch (exception: GeneralSecurityException) {
            throw SessionStorageException(SessionStorageFailure.DECRYPTION, exception)
        } finally {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            dataStore = createStore()
        }
        dataStore.updateData { StoredSessions() }
        clearQuarantine()
    }

    private fun writeQuarantine() {
        java.io.FileOutputStream(quarantineFile).use { stream ->
            stream.write(1)
            stream.fd.sync()
        }
    }

    private fun clearQuarantine() {
        if (quarantineFile.exists() && !quarantineFile.delete()) {
            throw IOException("Unable to finish account storage update")
        }
    }

    private suspend fun <T> locked(block: suspend () -> T): T = kotlinx.coroutines.withContext(Dispatchers.IO) {
        mutex.lock()
        try { block() } finally { mutex.unlock() }
    }
}
