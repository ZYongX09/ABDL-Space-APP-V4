package top.abdl_space.app.core.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(private val store: SessionStore) {
    private val mutex = Mutex()
    private var generation = 0L
    private var pendingLogout: StoredSessions? = null
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    suspend fun restore() = mutex.withLock {
        if (mutableState.value is SessionState.Ready) return@withLock
        val logout = pendingLogout
        if (logout != null) {
            // Only finish the interrupted logout after its quarantine marker is writable again.
            if (!storeQuarantineOk()) return@withLock
            persist(logout, logoutAttempt = true)
            return@withLock
        }
        try {
            publish(store.load())
        } catch (exception: IOException) {
            failStorage(exception)
        }
    }

    private suspend fun storeQuarantineOk(): Boolean = try {
        store.quarantine()
        true
    } catch (_: IOException) {
        false
    }

    suspend fun resetAfterConfirmation(): Boolean = mutex.withLock {
        if (mutableState.value !is SessionState.StorageUnavailable) return@withLock false
        try {
            store.reset()
            pendingLogout = null
            publish(StoredSessions())
            true
        } catch (exception: IOException) {
            failStorage(exception)
            false
        }
    }

    fun isCurrent(lease: SessionLease): Boolean =
        (state.value as? SessionState.Ready)?.lease == lease

    fun activeCredential(lease: SessionLease): SessionCredential? =
        (state.value as? SessionState.Ready)?.takeIf { it.lease == lease }?.stored?.activeCredential()

    /** Reject delayed login completions after a logout or account switch. */
    suspend fun signIn(credential: SessionCredential, expectedLease: SessionLease): Boolean = mutex.withLock {
        val current = state.value as? SessionState.Ready ?: return@withLock false
        if (current.lease != expectedLease) return@withLock false
        persist(
            StoredSessions(
                activeAccountId = credential.accountId,
                accounts = current.stored.accounts.filterNot { it.accountId == credential.accountId } + credential,
            )
        )
    }

    suspend fun switchAccount(accountId: String): Boolean = mutex.withLock {
        val current = state.value as? SessionState.Ready ?: return@withLock false
        if (current.stored.accounts.none { it.accountId == accountId }) return@withLock false
        persist(current.stored.copy(activeAccountId = accountId))
    }

    suspend fun signOut(accountId: String): Boolean = mutex.withLock {
        val current = state.value as? SessionState.Ready ?: return@withLock false
        if (current.stored.accounts.none { it.accountId == accountId }) return@withLock false
        val remaining = current.stored.accounts.filterNot { it.accountId == accountId }
        val target = StoredSessions(
            activeAccountId = if (current.stored.activeAccountId == accountId) {
                remaining.firstOrNull()?.accountId
            } else {
                current.stored.activeAccountId
            },
            accounts = remaining,
        )
        pendingLogout = target
        generation += 1
        mutableState.value = SessionState.StorageUnavailable(SessionStorageFailure.LOGOUT_UNCONFIRMED)
        try {
            store.quarantine()
        } catch (_: IOException) {
            generation += 1
            mutableState.value = SessionState.StorageUnavailable(SessionStorageFailure.LOGOUT_UNCONFIRMED)
            return@withLock false
        }
        persist(target, logoutAttempt = true)
    }

    private suspend fun persist(sessions: StoredSessions, logoutAttempt: Boolean = false): Boolean {
        return try {
            store.save(sessions)
            if (logoutAttempt) pendingLogout = null
            publish(sessions)
            true
        } catch (exception: IOException) {
            failStorage(exception, logoutAttempt)
            false
        }
    }

    private fun failStorage(exception: IOException, logoutAttempt: Boolean = false) {
        generation += 1
        val reason = if (logoutAttempt) SessionStorageFailure.LOGOUT_UNCONFIRMED else {
            (exception as? SessionStorageException)?.reason ?: SessionStorageFailure.IO
        }
        mutableState.value = SessionState.StorageUnavailable(reason)
    }

    private fun publish(sessions: StoredSessions) {
        generation += 1
        mutableState.value = SessionState.Ready(
            stored = sessions,
            lease = SessionLease(sessions.activeAccountId, generation),
        )
    }
}
