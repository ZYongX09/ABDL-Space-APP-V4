package top.abdl_space.app.core.data.nbw

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import top.abdl_space.app.core.data.session.SessionLease
import top.abdl_space.app.core.model.NbwForumOptions
import top.abdl_space.app.core.network.NetworkCall
import top.abdl_space.app.core.network.NetworkFailure
import top.abdl_space.app.core.network.NetworkFailureKind
import top.abdl_space.app.core.network.NetworkResult
import top.abdl_space.app.core.network.api.NbwApi
import javax.inject.Inject
import javax.inject.Singleton

/** Account-scoped catalog cache: five-minute TTL, one fetch per lease/generation, fail closed. */
@Singleton
class NbwForumOptionsRepository @Inject constructor(
    private val api: NbwApi,
    private val json: Json,
    private val clock: NbwClock,
) {
    private val mutex = Mutex()
    private var lease: SessionLease? = null
    private var generation = 0L
    private var options: NbwForumOptions? = null
    private var loadedAt: Long? = null
    private var inFlight: Flight? = null

    private class Flight(val lease: SessionLease, val generation: Long) {
        val result = CompletableDeferred<NetworkResult<NbwForumOptions>>()
    }

    /** Cached catalog, only ever for the lease it was loaded under. */
    suspend fun cached(lease: SessionLease?): NbwForumOptions? = mutex.withLock {
        options?.takeIf { lease != null && this.lease == lease }
    }

    /** Also invalidates same-lease requests, e.g. when the verified capability mode changes. */
    suspend fun clear() = mutex.withLock { clearLocked() }

    private fun clearLocked() {
        generation += 1
        lease = null
        options = null
        loadedAt = null
        inFlight?.result?.complete(unavailable())
        inFlight = null
    }

    /**
     * Choose cached/join/owner atomically. A cancelled runner releases its waiters even though its
     * coroutine is cancelled, and neither its cleanup nor a response after clear can touch a newer
     * flight. Joiners are not owners: cancelling one must not cancel the shared HTTP request.
     */
    suspend fun load(lease: SessionLease, authorization: String): NetworkResult<NbwForumOptions> {
        currentCoroutineContext().ensureActive()
        var owner = false
        val flight = mutex.withLock {
            if (this.lease != lease) {
                clearLocked()
                this.lease = lease
            }
            val cached = options
            val timestamp = loadedAt
            if (cached != null && timestamp != null && clock.now() - timestamp in 0 until NbwRepository.TTL_MS) {
                return NetworkResult.Success(cached)
            }
            inFlight ?: Flight(lease, generation).also {
                inFlight = it
                owner = true
            }
        }
        if (!owner) return flight.result.await()
        try {
            val result = NetworkCall.body(json) { api.options(authorization) }
            currentCoroutineContext().ensureActive()
            return mutex.withLock {
                if (inFlight !== flight || this.lease != flight.lease || generation != flight.generation) {
                    return@withLock unavailable()
                }
                inFlight = null
                when (result) {
                    is NetworkResult.Success -> {
                        options = result.value
                        loadedAt = clock.now()
                    }
                    is NetworkResult.Failure -> {
                        options = null
                        loadedAt = null
                    }
                }
                flight.result.complete(result)
                result
            }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (inFlight === flight) inFlight = null
                    flight.result.complete(unavailable())
                }
            }
        }
    }

    private fun unavailable(): NetworkResult.Failure =
        NetworkResult.Failure(NetworkFailure(NetworkFailureKind.NETWORK))
}
