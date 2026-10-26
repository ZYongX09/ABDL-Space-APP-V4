package top.abdl_space.app.core.data.session

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SessionRepositoryTest {
    private class MemoryStore(var stored: StoredSessions = StoredSessions()) : SessionStore {
        var failSave = false
        var failLoad = false
        var failQuarantine = false
        var cancelAfterQuarantine = false
        var quarantined = false
        override suspend fun load(): StoredSessions {
            if (quarantined) throw SessionStorageException(SessionStorageFailure.LOGOUT_UNCONFIRMED)
            if (failLoad) throw IOException("unavailable")
            return stored
        }
        override suspend fun save(sessions: StoredSessions) {
            if (failSave) throw IOException("unavailable")
            stored = sessions
            quarantined = false
        }
        override suspend fun quarantine() {
            if (failQuarantine) throw IOException("marker unavailable")
            quarantined = true
            if (cancelAfterQuarantine) throw kotlinx.coroutines.CancellationException("Activity stopped")
        }
        override suspend fun reset() {
            stored = StoredSessions()
            quarantined = false
        }
    }

    private fun SessionRepository.ready() = state.value as SessionState.Ready
    private fun credential(id: String) = SessionCredential(id, "token-$id", "user$id", "User $id")

    @Test
    fun `restoration publishes persisted active session`() = runBlocking {
        val repository = SessionRepository(MemoryStore(StoredSessions("001", listOf(credential("001")))))
        assertEquals(SessionState.Loading, repository.state.value)
        repository.restore()
        assertEquals("001", repository.ready().lease.accountId)
        assertEquals("token-001", repository.activeCredential(repository.ready().lease)?.accessToken)
    }

    @Test
    fun `account switch rejects pending result from old generation`() = runBlocking {
        val repository = SessionRepository(MemoryStore(StoredSessions("1", listOf(credential("1"), credential("2")))))
        repository.restore()
        val oldLease = repository.ready().lease
        assertTrue(repository.switchAccount("2"))
        assertFalse(repository.isCurrent(oldLease))
        assertNull(repository.activeCredential(oldLease))
        assertFalse(repository.signIn(credential("3"), oldLease))
        assertEquals("2", repository.ready().stored.activeAccountId)
    }

    @Test
    fun `login and logout persist and invalidate previous leases`() = runBlocking {
        val store = MemoryStore()
        val repository = SessionRepository(store)
        repository.restore()
        val guestLease = repository.ready().lease
        assertTrue(repository.signIn(credential("1"), guestLease))
        assertFalse(repository.isCurrent(guestLease))
        val signedInLease = repository.ready().lease
        assertEquals("1", store.stored.activeAccountId)
        assertTrue(repository.signOut("1"))
        assertFalse(repository.isCurrent(signedInLease))
        assertTrue(store.stored.accounts.isEmpty())
        assertNull(store.stored.activeAccountId)
    }

    @Test
    fun `logout does not reuse generation even if same account logs in again`() = runBlocking {
        val repository = SessionRepository(MemoryStore(StoredSessions("1", listOf(credential("1")))))
        repository.restore()
        val oldLease = repository.ready().lease
        repository.signOut("1")
        repository.signIn(credential("1"), repository.ready().lease)
        assertEquals("1", repository.ready().lease.accountId)
        assertFalse(repository.isCurrent(oldLease))
    }

    @Test
    fun `failed storage never exposes a newly logged in token`() = runBlocking {
        val store = MemoryStore()
        val repository = SessionRepository(store)
        repository.restore()
        store.failSave = true
        assertFalse(repository.signIn(credential("1"), repository.ready().lease))
        assertEquals(SessionState.StorageUnavailable(SessionStorageFailure.IO), repository.state.value)
        assertNull(store.stored.activeAccountId)
    }

    @Test
    fun `failed restore is distinct from logged out`() = runBlocking {
        val repository = SessionRepository(MemoryStore().apply { failLoad = true })
        repository.restore()
        assertEquals(SessionState.StorageUnavailable(SessionStorageFailure.IO), repository.state.value)
    }

    @Test
    fun `failed logout quarantines stored token across repository restart`() = runBlocking {
        val store = MemoryStore(StoredSessions("1", listOf(credential("1"))))
        val repository = SessionRepository(store)
        repository.restore()
        store.failSave = true
        assertFalse(repository.signOut("1"))
        assertEquals(SessionState.StorageUnavailable(SessionStorageFailure.LOGOUT_UNCONFIRMED), repository.state.value)
        val restarted = SessionRepository(store)
        restarted.restore()
        assertEquals(SessionState.StorageUnavailable(SessionStorageFailure.LOGOUT_UNCONFIRMED), restarted.state.value)
        assertNull(restarted.activeCredential(SessionLease("1", 1)))
    }

    @Test
    fun `marker write failure still blocks immediate restore from re-enabling old account`() = runBlocking {
        val store = MemoryStore(StoredSessions("1", listOf(credential("1")))).apply { failQuarantine = true }
        val repository = SessionRepository(store)
        repository.restore()
        assertFalse(repository.signOut("1"))
        assertEquals(SessionState.StorageUnavailable(SessionStorageFailure.LOGOUT_UNCONFIRMED), repository.state.value)
        store.failQuarantine = false
        // The only recovery from an unconfirmed logout is finishing the logout, never the old session.
        repository.restore()
        assertTrue(repository.ready().stored.accounts.isEmpty())
        assertFalse(store.quarantined)
    }

    @Test
    fun `retrying a failed logout saves the logout rather than restoring old credentials`() = runBlocking {
        val store = MemoryStore(StoredSessions("1", listOf(credential("1"))))
        val repository = SessionRepository(store)
        repository.restore()
        store.failSave = true
        assertFalse(repository.signOut("1"))
        store.failSave = false
        repository.restore()
        assertTrue(repository.ready().stored.accounts.isEmpty())
        assertFalse(store.quarantined)
    }

    @Test
    fun `transient storage error can be retried without clearing accounts`() = runBlocking {
        val store = MemoryStore(StoredSessions("1", listOf(credential("1")))).apply { failLoad = true }
        val repository = SessionRepository(store)
        repository.restore()
        store.failLoad = false
        repository.restore()
        assertEquals("1", repository.ready().stored.activeAccountId)
    }

    @Test
    fun `confirmed reset recovers quarantined storage to a new guest generation`() = runBlocking {
        val store = MemoryStore(StoredSessions("1", listOf(credential("1")))).apply { quarantined = true }
        val repository = SessionRepository(store)
        repository.restore()
        assertTrue(repository.resetAfterConfirmation())
        assertNull(repository.ready().stored.activeAccountId)
        assertTrue(store.stored.accounts.isEmpty())
        assertFalse(store.quarantined)
    }

    @Test
    fun `healthy account storage cannot be reset through recovery entry`() = runBlocking {
        val repository = SessionRepository(MemoryStore(StoredSessions("1", listOf(credential("1")))))
        repository.restore()
        assertFalse(repository.resetAfterConfirmation())
        assertEquals("1", repository.ready().stored.activeAccountId)
    }

    @Test
    fun `unknown account cannot become active`() = runBlocking {
        val repository = SessionRepository(MemoryStore())
        repository.restore()
        val lease = repository.ready().lease
        assertFalse(repository.switchAccount("not-found"))
        assertTrue(repository.isCurrent(lease))
    }
}
