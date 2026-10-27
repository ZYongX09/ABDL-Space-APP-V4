package top.abdl_space.app.core.data.nbw

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.abdl_space.app.core.data.session.SessionLease
import top.abdl_space.app.core.data.session.SessionRepository
import top.abdl_space.app.core.data.session.SessionState
import top.abdl_space.app.core.model.NbwCapabilities
import top.abdl_space.app.core.model.NbwForumOptions
import top.abdl_space.app.core.network.AuthorizationHeader
import top.abdl_space.app.core.network.NetworkCall
import top.abdl_space.app.core.network.NetworkResult
import top.abdl_space.app.core.network.api.FallbackConsentRequest
import top.abdl_space.app.core.network.api.NbwApi
import javax.inject.Inject
import javax.inject.Singleton

/** Monotonic clock seam for TTL/single-flight timing; production binds elapsedRealtime. */
fun interface NbwClock {
    fun now(): Long
}

enum class NbwSnapshotState { LOADING, READY, STALE, UNAVAILABLE, LOCAL_ONLY }

/**
 * What the NBW entry may display for the current session lease. capabilities/options exist only
 * after a verified response; any unverifiable state collapses to LOCAL_ONLY (fail closed).
 */
data class NbwSnapshot(
    val state: NbwSnapshotState = NbwSnapshotState.UNAVAILABLE,
    val capabilities: NbwCapabilities? = null,
    val options: NbwForumOptions? = null,
    val lease: SessionLease? = null,
)

/**
 * Session-level NBW capability/catalog snapshot: 5-minute TTL, single-flight for the entire
 * capability + catalog refresh, and lease generation checks. Only a signed-in lease triggers
 * requests; guests fail closed without network traffic. No posting or UI consent path this round.
 */
@Singleton
class NbwRepository @Inject constructor(
    private val api: NbwApi,
    private val sessions: SessionRepository,
    private val optionsRepository: NbwForumOptionsRepository,
    private val json: kotlinx.serialization.json.Json,
    private val clock: NbwClock,
    @NbwRefreshScope private val refreshScope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var generation = 0L
    private var lease: SessionLease? = null
    private var capabilities: NbwCapabilities? = null
    private var lastAttempt: Long? = null
    private var capabilityJob: Job? = null
    private var refreshAfterFlight = false
    private var consentFlight: ConsentFlight? = null

    private class ConsentFlight(val lease: SessionLease, val generation: Long)

    private val mutableSnapshot = MutableStateFlow(NbwSnapshot())
    val snapshot: StateFlow<NbwSnapshot> = mutableSnapshot.asStateFlow()

    init {
        refreshScope.launch {
            // Also runs on initial injection: no discovery screen is needed for signed-in prefetch.
            sessions.state.collect { refreshIfExpired() }
        }
    }

    /**
     * Read the latest state under the same lock used by entry refreshes. An entry can race the
     * initial collector, and an older collector emission must never reactivate a previous lease.
     */
    private suspend fun syncSessionLocked(): SessionLease? {
        val next = (sessions.state.value as? SessionState.Ready)?.lease?.takeIf { it.accountId != null }
        if (next == lease) return next
        generation += 1
        capabilityJob?.cancel()
        capabilityJob = null
        refreshAfterFlight = false
        consentFlight = null
        val wasActive = lease?.accountId != null
        lease = next
        capabilities = null
        optionsRepository.clear()
        lastAttempt = null
        mutableSnapshot.value = when (next) {
            null -> NbwSnapshot(
                state = if (wasActive) NbwSnapshotState.LOCAL_ONLY else NbwSnapshotState.UNAVAILABLE,
            )
            else -> NbwSnapshot(state = NbwSnapshotState.LOADING, lease = next)
        }
        return next
    }

    /** Entry visible again: re-verify only when the TTL has elapsed; a no-op for guests. */
    suspend fun refreshIfExpired() = mutex.withLock {
        val current = syncSessionLocked() ?: return@withLock
        val attemptedAt = lastAttempt
        if (attemptedAt != null && clock.now() - attemptedAt in 0 until TTL_MS) return@withLock
        startRefreshLocked(current)
    }

    /** Explicit user action (retry/re-check): bypasses the TTL, never bypasses single-flight. */
    suspend fun refreshNow() = mutex.withLock {
        val current = syncSessionLocked() ?: return@withLock
        startRefreshLocked(current)
    }

    /**
     * Requires a signed-in lease, an idle consent write and a verified full grant. A successful
     * PUT always schedules a subsequent capability read, even if an earlier read is still running;
     * a local flag is never authoritative consent. No UI triggers this method this round.
     */
    suspend fun setFallbackConsent(accepted: Boolean): Boolean {
        val flight = mutex.withLock {
            val current = syncSessionLocked() ?: return@withLock null
            if (consentFlight != null || capabilities?.canWrite != true) return@withLock null
            ConsentFlight(current, generation).also { consentFlight = it }
        } ?: return false
        try {
            val credential = sessions.activeCredential(flight.lease) ?: return false
            val result = NetworkCall.completion(json) {
                api.fallbackConsent(
                    AuthorizationHeader.bearer(credential.accessToken),
                    FallbackConsentRequest(accepted),
                )
            }
            currentCoroutineContext().ensureActive()
            return mutex.withLock {
                if (consentFlight !== flight || !isCurrentLocked(flight.lease, flight.generation)) {
                    return@withLock false
                }
                val succeeded = result is NetworkResult.Success
                if (succeeded) {
                    if (capabilityJob?.isCompleted == false) {
                        refreshAfterFlight = true
                    } else {
                        startRefreshLocked(flight.lease)
                    }
                }
                succeeded
            }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    // A cancelled/old-account PUT cannot release another account's consent slot.
                    if (consentFlight === flight) consentFlight = null
                }
            }
        }
    }

    private suspend fun startRefreshLocked(current: SessionLease) {
        if (capabilityJob?.isCompleted == false || !refreshScope.isActive) return
        val ticket = generation
        lastAttempt = clock.now()
        val cached = optionsRepository.cached(current)
        mutableSnapshot.value = NbwSnapshot(
            state = if (cached != null) NbwSnapshotState.STALE else NbwSnapshotState.LOADING,
            capabilities = capabilities,
            options = cached,
            lease = current,
        )
        // Register before dispatch, including with an immediate/unconfined scope dispatcher.
        val job = refreshScope.launch(start = CoroutineStart.LAZY) { capabilityFlow(current, ticket) }
        capabilityJob = job
        if (!job.start()) capabilityJob = null
    }

    private suspend fun capabilityFlow(current: SessionLease, ticket: Long) {
        val job = currentCoroutineContext()[Job]!!
        try {
            val credential = sessions.activeCredential(current)
            if (credential == null) {
                mutex.withLock {
                    if (isCurrentLocked(current, ticket)) failClosedLocked(current)
                    finishRefreshLocked(current, ticket, job)
                }
                return
            }
            val authorization = AuthorizationHeader.bearer(credential.accessToken)
            val result = NetworkCall.body(json) { api.capabilities(authorization) }
            currentCoroutineContext().ensureActive()
            val wantOptions = mutex.withLock {
                if (!isCurrentLocked(current, ticket)) return@withLock false
                val loadOptions = when (result) {
                    is NetworkResult.Failure -> {
                        failClosedLocked(current)
                        false
                    }
                    is NetworkResult.Success -> {
                        val value = result.value
                        val previousMode = capabilities?.remoteMode
                        capabilities = value
                        if (!value.canRead) {
                            optionsRepository.clear()
                            publishLocked(current, NbwSnapshotState.LOCAL_ONLY)
                            false
                        } else {
                            if (previousMode != null && previousMode != value.remoteMode) {
                                optionsRepository.clear()
                            }
                            val cached = optionsRepository.cached(current)
                            publishLocked(current, if (cached == null) NbwSnapshotState.LOADING else NbwSnapshotState.STALE)
                            true
                        }
                    }
                }
                if (!loadOptions) finishRefreshLocked(current, ticket, job)
                loadOptions
            }
            if (!wantOptions) return
            currentCoroutineContext().ensureActive()
            // This store owns catalog freshness (including a valid loadedAt == 0).
            val optionsResult = optionsRepository.load(current, authorization)
            currentCoroutineContext().ensureActive()
            mutex.withLock {
                if (!isCurrentLocked(current, ticket)) return@withLock
                when (optionsResult) {
                    is NetworkResult.Failure -> failClosedLocked(current)
                    is NetworkResult.Success -> publishLocked(current, NbwSnapshotState.READY)
                }
                // Publish + release atomically: an observer of READY may immediately retry.
                finishRefreshLocked(current, ticket, job)
            }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock { finishRefreshLocked(current, ticket, job) }
            }
        }
    }

    private suspend fun finishRefreshLocked(current: SessionLease, ticket: Long, job: Job) {
        // Own the slot through catalog completion, and never clear a replacement job.
        if (capabilityJob !== job) return
        capabilityJob = null
        if (refreshAfterFlight && isCurrentLocked(current, ticket)) {
            refreshAfterFlight = false
            startRefreshLocked(current)
        }
    }

    private suspend fun publishLocked(current: SessionLease, state: NbwSnapshotState) {
        mutableSnapshot.value = NbwSnapshot(
            state = state,
            capabilities = capabilities,
            options = optionsRepository.cached(current),
            lease = current,
        )
    }

    private suspend fun failClosedLocked(current: SessionLease) {
        capabilities = null
        optionsRepository.clear()
        publishLocked(current, NbwSnapshotState.LOCAL_ONLY)
    }

    private fun isCurrentLocked(current: SessionLease, ticket: Long): Boolean =
        lease == current && generation == ticket && sessions.isCurrent(current)

    companion object {
        const val TTL_MS = 5 * 60 * 1000L
    }
}
