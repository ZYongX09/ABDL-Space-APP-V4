package top.abdl_space.app.core.data.session

import kotlinx.serialization.Serializable

@Serializable
class SessionCredential(
    val accountId: String,
    val accessToken: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
) {
    init {
        require(accountId.isNotBlank())
        require(accessToken.isNotBlank())
    }

    override fun toString(): String = "SessionCredential(accountId=$accountId, accessToken=<redacted>)"
}

@Serializable
data class StoredSessions(
    val activeAccountId: String? = null,
    val accounts: List<SessionCredential> = emptyList(),
) {
    init {
        require(accounts.map { it.accountId }.distinct().size == accounts.size)
        require(activeAccountId == null || accounts.any { it.accountId == activeAccountId })
    }

    fun activeCredential(): SessionCredential? = accounts.firstOrNull { it.accountId == activeAccountId }
}

/** A response can update the UI only while its account and generation are still active. */
data class SessionLease(val accountId: String?, val generation: Long)

enum class SessionStorageFailure { IO, DECRYPTION, INVALID_CONTENT, LOGOUT_UNCONFIRMED }

class SessionStorageException(
    val reason: SessionStorageFailure,
    cause: Throwable? = null,
) : java.io.IOException("V4 account storage is unavailable: $reason", cause)

sealed interface SessionState {
    data object Loading : SessionState
    data class Ready(val stored: StoredSessions, val lease: SessionLease) : SessionState
    data class StorageUnavailable(val reason: SessionStorageFailure) : SessionState
}
