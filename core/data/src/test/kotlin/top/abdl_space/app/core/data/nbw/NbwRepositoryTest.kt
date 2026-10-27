package top.abdl_space.app.core.data.nbw

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import top.abdl_space.app.core.data.session.SessionCredential
import top.abdl_space.app.core.data.session.SessionRepository
import top.abdl_space.app.core.data.session.SessionState
import top.abdl_space.app.core.data.session.SessionStore
import top.abdl_space.app.core.data.session.StoredSessions
import top.abdl_space.app.core.model.NbwRemoteMode
import top.abdl_space.app.core.network.HttpModule
import top.abdl_space.app.core.network.NetworkResult
import top.abdl_space.app.core.network.api.NbwApi
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * Real HTTP loopback port of the legacy NBWForumOptionsRepository semantics: signed-in-only
 * prefetch, 5-minute TTL, single-flight, lease generation isolation, and the three-state
 * fail-closed mapping. Documents reuse the verified legacy schema (fid 18 闲聊 not required,
 * fid 36 必选分类 required, typeid 302 moderator-only). No fixtures, no reflection.
 */
class NbwRepositoryTest {
    private val server = MockWebServer()
    private val refreshJob = SupervisorJob()
    private val refreshScope = CoroutineScope(refreshJob + Dispatchers.IO)
    private val client = OkHttpClient.Builder().followRedirects(false).build()
    private lateinit var sessions: SessionRepository
    private lateinit var optionsRepository: NbwForumOptionsRepository
    private lateinit var repository: NbwRepository
    private lateinit var json: Json
    private val clock = FakeNbwClock()

    private class FakeNbwClock(@Volatile var nowMs: Long = 10_000L) : NbwClock {
        override fun now(): Long = nowMs
    }

    private class InMemoryStore(initial: StoredSessions) : SessionStore {
        var value = initial
        override suspend fun load() = value
        override suspend fun save(sessions: StoredSessions) { value = sessions }
        override suspend fun quarantine() {}
        override suspend fun reset() { value = StoredSessions() }
    }

    @Before
    fun setUp() {
        server.start()
        json = HttpModule.json()
        sessions = SessionRepository(InMemoryStore(StoredSessions()))
        runBlocking<Unit> { sessions.restore() }
        val api = retrofit().create(NbwApi::class.java)
        optionsRepository = NbwForumOptionsRepository(api, json, clock)
        // Guest start: the session collector activates only after a test signs in.
        repository = NbwRepository(
            api,
            sessions,
            optionsRepository,
            json,
            clock,
            refreshScope,
        )
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        try {
            withTimeout(5_000) { refreshJob.cancelAndJoin() }
        } finally {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
            try {
                server.shutdown()
            } finally {
                assertTrue("OkHttp executor did not stop", client.dispatcher.executorService.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }

    private fun retrofit(): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun dispatch(body: String, code: Int = 200): MockResponse =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun awaitSnapshot(timeoutMs: Long = 5_000, predicate: (NbwSnapshot) -> Boolean): NbwSnapshot {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        var last = repository.snapshot.value
        while (System.nanoTime() < deadline) {
            last = repository.snapshot.value
            if (predicate(last)) return last
            Thread.sleep(10)
        }
        throw AssertionError("Timed out waiting for snapshot; state=${last.state}, mode=${last.capabilities?.remoteMode}, hasCatalog=${last.options != null}")
    }

    private suspend fun signIn(accountId: String, token: String) {
        val lease = (sessions.state.value as SessionState.Ready).lease
        assertTrue(sessions.signIn(SessionCredential(accountId, token, "name-$accountId", "Name $accountId"), lease))
    }

    private suspend fun activeLease() = (sessions.state.value as SessionState.Ready).lease

    private suspend fun awaitNoRefreshChildren() = withTimeout(5_000) {
        while (refreshJob.children.count() != 1) delay(1)
    }

    @Test
    fun `guest session never issues a request and stays unverified`() = runBlocking<Unit> {
        repository.refreshIfExpired()
        repository.refreshNow()
        assertEquals(NbwSnapshot(), repository.snapshot.value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `signed in prefetch maps full mode and the legacy catalog semantics`() = runBlocking<Unit> {
        enqueue(CAPABILITIES_FULL)
        enqueue(OPTIONS_DOCUMENT)
        signIn("1", "token-1")
        val snapshot = awaitSnapshot { it.state == NbwSnapshotState.READY }
        val capabilities = snapshot.capabilities!!
        assertEquals(NbwRemoteMode.FULL, capabilities.remoteMode)
        assertTrue(capabilities.canRead)
        assertTrue(capabilities.canWrite)
        val options = snapshot.options!!
        // fid 18 闲聊: not required, fallback target. fid 36 必选分类: required, 302 is moderator-only.
        assertEquals(listOf("18", "36"), options.forums.map { it.fid })
        assertFalse(options.forums[0].required)
        assertTrue(options.forums[1].required)
        assertTrue(options.isLegal("18", "0"))
        assertTrue(options.isLegal("18", "37"))
        assertTrue(options.isLegal("36", "301"))
        assertFalse(options.isLegal("36", "0"))
        assertFalse(options.isLegal("36", "302"))
        assertFalse(options.isLegal("999", "301"))
        assertTrue(options.canFallback)
        val first = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/app/capabilities", first.path)
        assertEquals("Bearer token-1", first.getHeader("Authorization"))
        assertEquals("2", first.getHeader("X-ABDL-NBW-Protocol"))
        val second = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/app/nbw/options", second.path)
        assertEquals("Bearer token-1", second.getHeader("Authorization"))
    }

    @Test
    fun `three remote modes map to snapshot states and local_only stops at capabilities`() = runBlocking<Unit> {
        enqueue(CAPABILITIES_LOCAL_ONLY)
        signIn("1", "token-1")
        var snapshot = awaitSnapshot { it.state == NbwSnapshotState.LOCAL_ONLY }
        assertEquals(NbwRemoteMode.LOCAL_ONLY, snapshot.capabilities!!.remoteMode)
        assertFalse(snapshot.capabilities!!.canRead)
        assertEquals(1, server.requestCount)
        assertTrue(sessions.signOut("1"))
        awaitSnapshot { it.state == NbwSnapshotState.LOCAL_ONLY && it.lease == null }

        enqueue(CAPABILITIES_READ_ONLY)
        enqueue(OPTIONS_DOCUMENT)
        signIn("1", "token-1")
        snapshot = awaitSnapshot { it.state == NbwSnapshotState.READY }
        assertEquals(NbwRemoteMode.READ_ONLY, snapshot.capabilities!!.remoteMode)
        assertTrue(snapshot.capabilities!!.canRead)
        assertFalse(snapshot.capabilities!!.canWrite)
        assertTrue(sessions.signOut("1"))
        awaitSnapshot { it.state == NbwSnapshotState.LOCAL_ONLY && it.lease == null }

        enqueue(CAPABILITIES_FULL)
        enqueue(OPTIONS_DOCUMENT)
        signIn("1", "token-1")
        snapshot = awaitSnapshot { it.state == NbwSnapshotState.READY && it.capabilities?.remoteMode == NbwRemoteMode.FULL }
        assertTrue(snapshot.capabilities!!.canWrite)
    }

    @Test
    fun `unverifiable capability response collapses to local_only`() = runBlocking<Unit> {
        enqueue("server exploded", 500)
        signIn("1", "token-1")
        var snapshot = awaitSnapshot { it.state == NbwSnapshotState.LOCAL_ONLY }
        assertNull(snapshot.capabilities)
        assertNull(snapshot.options)
        assertEquals(1, server.requestCount)
        assertTrue(sessions.signOut("1"))
        awaitSnapshot { it.lease == null }
        // A future schema is a protocol failure, never a silently accepted grant.
        enqueue(CAPABILITIES_FULL.replace("\"schema_version\":1", "\"schema_version\":2"))
        signIn("1", "token-1")
        snapshot = awaitSnapshot { it.state == NbwSnapshotState.LOCAL_ONLY && it.capabilities == null }
        assertNull(snapshot.options)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `catalog failure fails closed instead of keeping partial state`() = runBlocking<Unit> {
        enqueue(CAPABILITIES_FULL)
        enqueue("catalog exploded", 500)
        signIn("1", "token-1")
        val snapshot = awaitSnapshot { it.state == NbwSnapshotState.LOCAL_ONLY }
        assertNull(snapshot.capabilities)
        assertNull(snapshot.options)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `ttl guards the entry recheck and expiry retransmits both requests`() = runBlocking<Unit> {
        enqueue(CAPABILITIES_FULL)
        enqueue(OPTIONS_DOCUMENT)
        signIn("1", "token-1")
        awaitSnapshot { it.state == NbwSnapshotState.READY }
        assertEquals(2, server.requestCount)
        clock.nowMs += 60_000
        repository.refreshIfExpired()
        assertEquals(2, server.requestCount)
        clock.nowMs += NbwRepository.TTL_MS + 1
        enqueue(CAPABILITIES_FULL)
        enqueue(OPTIONS_DOCUMENT)
        repository.refreshIfExpired()
        awaitSnapshot { it.state == NbwSnapshotState.READY }
        // Both the capability and the catalog are older than the TTL now.
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `stale state retains the last trusted catalog while reverifying`() = runBlocking<Unit> {
        enqueue(CAPABILITIES_FULL)
        enqueue(OPTIONS_DOCUMENT)
        signIn("1", "token-1")
        awaitSnapshot { it.state == NbwSnapshotState.READY }
        clock.nowMs += NbwRepository.TTL_MS + 1
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/api/app/capabilities") {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return dispatch(CAPABILITIES_FULL)
                }
                return dispatch(OPTIONS_DOCUMENT)
            }
        }
        repository.refreshIfExpired()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val stale = repository.snapshot.value
        assertEquals(NbwSnapshotState.STALE, stale.state)
        assertEquals("catalog-1", stale.options!!.configRevision)
        release.countDown()
        awaitSnapshot { it.state == NbwSnapshotState.READY }
    }

    @Test
    fun `capability refresh is single flight across concurrent callers`() = runBlocking<Unit> {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/api/app/capabilities") {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return dispatch(CAPABILITIES_FULL)
                }
                return dispatch(OPTIONS_DOCUMENT)
            }
        }
        signIn("1", "token-1")
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        repository.refreshNow()
        repository.refreshIfExpired()
        assertEquals(1, server.requestCount)
        release.countDown()
        awaitSnapshot { it.state == NbwSnapshotState.READY }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `concurrent catalog loads share one http request`() = runBlocking<Unit> {
        enqueue(CAPABILITIES_FULL)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/api/app/nbw/options") {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return dispatch(OPTIONS_DOCUMENT)
                }
                return dispatch(CAPABILITIES_FULL)
            }
        }
        signIn("1", "token-1")
        // The capability flow itself blocks on the catalog request.
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val joined = async(start = CoroutineStart.UNDISPATCHED) { optionsRepository.load(activeLease(), "Bearer token-1") }
        release.countDown()
        val result = joined.await()
        assertTrue(result is NetworkResult.Success)
        val snapshot = awaitSnapshot { it.state == NbwSnapshotState.READY }
        assertEquals("catalog-1", snapshot.options!!.configRevision)
        assertEquals("catalog-1", (result as NetworkResult.Success).value.configRevision)
        // Exactly one options request: the joiner reused the in-flight fetch instead of re-sending.
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `switch account refetches under the new lease and never serves the old catalog`() = runBlocking<Unit> {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.getHeader("Authorization")) {
                "Bearer token-1" -> if (request.path == "/api/app/capabilities") dispatch(CAPABILITIES_FULL) else dispatch(OPTIONS_DOCUMENT)
                else -> if (request.path == "/api/app/capabilities") dispatch(CAPABILITIES_ACCOUNT_2) else dispatch(OPTIONS_DOCUMENT_ACCOUNT_2)
            }
        }
        signIn("1", "token-1")
        var snapshot = awaitSnapshot { it.state == NbwSnapshotState.READY && it.lease?.accountId == "1" }
        assertEquals("catalog-1", snapshot.options!!.configRevision)
        assertEquals("remote-83", snapshot.capabilities!!.binding.uid)
        signIn("2", "token-2")
        snapshot = awaitSnapshot { it.state == NbwSnapshotState.READY && it.lease?.accountId == "2" }
        assertEquals("catalog-2", snapshot.options!!.configRevision)
        assertEquals("remote-84", snapshot.capabilities!!.binding.uid)
        assertEquals(4, server.requestCount)
        val requests = List(4) { server.takeRequest(5, TimeUnit.SECONDS)!! }
        assertEquals(
            listOf("Bearer token-1", "Bearer token-1", "Bearer token-2", "Bearer token-2"),
            requests.map { it.getHeader("Authorization") },
        )
        assertEquals(
            listOf("/api/app/capabilities", "/api/app/nbw/options", "/api/app/capabilities", "/api/app/nbw/options"),
            requests.map { it.path },
        )
    }

    @Test
    fun `late response from the previous account is dropped`() = runBlocking<Unit> {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.getHeader("Authorization")) {
                "Bearer token-1" -> {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    dispatch(CAPABILITIES_FULL)
                }
                else -> dispatch(CAPABILITIES_LOCAL_ONLY)
            }
        }
        signIn("1", "token-1")
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        // Switch accounts while account 1's capability response is still blocked on the wire.
        signIn("2", "token-2")
        release.countDown()
        val snapshot = awaitSnapshot { it.state == NbwSnapshotState.LOCAL_ONLY && it.lease?.accountId == "2" }
        // Account 2 returned a verified local_only document; preserve that mode, never account 1's grant.
        assertEquals(NbwRemoteMode.LOCAL_ONLY, snapshot.capabilities!!.remoteMode)
        assertNull(snapshot.options)
        // Two capability requests only: account 1's flow died before any catalog fetch, and its
        // late response could not enter the account 2 snapshot.
        assertEquals(2, server.requestCount)
        val first = server.takeRequest(5, TimeUnit.SECONDS)!!
        val second = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("Bearer token-1", first.getHeader("Authorization"))
        assertEquals("Bearer token-2", second.getHeader("Authorization"))
    }

    @Test
    fun `sign out collapses to fail closed local_only without requests`() = runBlocking<Unit> {
        enqueue(CAPABILITIES_FULL)
        enqueue(OPTIONS_DOCUMENT)
        signIn("1", "token-1")
        awaitSnapshot { it.state == NbwSnapshotState.READY }
        assertTrue(sessions.signOut("1"))
        val snapshot = awaitSnapshot { it.state == NbwSnapshotState.LOCAL_ONLY && it.lease == null }
        assertNull(snapshot.capabilities)
        assertNull(snapshot.options)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `capability mode change invalidates the retained catalog`() = runBlocking<Unit> {
        enqueue(CAPABILITIES_FULL)
        enqueue(OPTIONS_DOCUMENT)
        signIn("1", "token-1")
        awaitSnapshot { it.state == NbwSnapshotState.READY }
        clock.nowMs += NbwRepository.TTL_MS + 1
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/app/capabilities" -> dispatch(CAPABILITIES_READ_ONLY)
                else -> dispatch(OPTIONS_DOCUMENT_ACCOUNT_2)
            }
        }
        repository.refreshIfExpired()
        val snapshot = awaitSnapshot { it.state == NbwSnapshotState.READY && it.capabilities?.remoteMode == NbwRemoteMode.READ_ONLY }
        // The full-mode catalog was dropped and reloaded, not carried across the mode change.
        assertEquals("catalog-2", snapshot.options!!.configRevision)
    }

    @Test
    fun `fallback consent requires a full grant then reverifies from the server`() = runBlocking<Unit> {
        enqueue(CAPABILITIES_READ_ONLY)
        enqueue(OPTIONS_DOCUMENT)
        signIn("1", "token-1")
        awaitSnapshot { it.state == NbwSnapshotState.READY }
        assertFalse(repository.setFallbackConsent(true))
        assertEquals(2, server.requestCount)
        assertTrue(sessions.signOut("1"))
        awaitSnapshot { it.lease == null }

        val capabilityGets = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.method == "PUT" -> MockResponse().setResponseCode(204)
                request.path == "/api/app/capabilities" ->
                    if (capabilityGets.incrementAndGet() == 1) dispatch(CAPABILITIES_FULL) else dispatch(CAPABILITIES_FULL_CONSENTED)
                else -> dispatch(OPTIONS_DOCUMENT)
            }
        }
        signIn("1", "token-1")
        awaitSnapshot { it.state == NbwSnapshotState.READY && it.capabilities!!.fallback.consented == false }
        assertTrue(repository.setFallbackConsent(true))
        val snapshot = awaitSnapshot { it.state == NbwSnapshotState.READY && it.capabilities?.fallback?.consented == true }
        assertEquals(NbwSnapshotState.READY, snapshot.state)
        // read_only phase (2) + full activation (2) + PUT + re-verify GET = 6.
        assertEquals(6, server.requestCount)
        val put = List(6) { server.takeRequest(5, TimeUnit.SECONDS)!! }.first { it.method == "PUT" }
        assertEquals("/api/app/nbw/fallback-consent", put.path)
        assertEquals("2", put.getHeader("X-ABDL-NBW-Protocol"))
        assertEquals("""{"accepted":true,"policy_version":1}""", put.body.readUtf8())
    }

    @Test
    fun `concurrent consent writes collapse to one put`() = runBlocking<Unit> {
        enqueue(CAPABILITIES_FULL)
        enqueue(OPTIONS_DOCUMENT)
        signIn("1", "token-1")
        awaitSnapshot { it.state == NbwSnapshotState.READY }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.method == "PUT" -> {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    MockResponse().setResponseCode(204)
                }
                else -> dispatch(CAPABILITIES_FULL)
            }
        }
        val first = async(Dispatchers.IO) { repository.setFallbackConsent(true) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertFalse(repository.setFallbackConsent(false))
        assertEquals(3, server.requestCount)
        release.countDown()
        assertTrue(first.await())
    }

    @Test
    fun `entry refresh before init collector runs still activates the latest session once`() = runBlocking<Unit> {
        refreshJob.children.toList().forEach { it.cancelAndJoin() }
        val dispatcher = StandardTestDispatcher()
        val api = retrofit().create(NbwApi::class.java)
        repository = NbwRepository(api, sessions, optionsRepository, json, clock, CoroutineScope(refreshJob + dispatcher))
        enqueue(CAPABILITIES_FULL)
        enqueue(OPTIONS_DOCUMENT)
        signIn("1", "token-1")
        // Deliberately do not run the init collector first.
        repository.refreshIfExpired()
        assertEquals("1", repository.snapshot.value.lease?.accountId)
        assertEquals(NbwSnapshotState.LOADING, repository.snapshot.value.state)
        try {
            withTimeout(5_000) {
                while (repository.snapshot.value.state != NbwSnapshotState.READY) {
                    dispatcher.scheduler.runCurrent()
                    delay(5)
                }
            }
            assertEquals(2, server.requestCount)
            repository.refreshIfExpired()
            dispatcher.scheduler.runCurrent()
            assertEquals(2, server.requestCount)
        } finally {
            refreshJob.cancel()
            dispatcher.scheduler.runCurrent()
        }
    }

    @Test
    fun `single flight includes catalog loading and releases after completion`() = runBlocking<Unit> {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val capabilities = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/api/app/capabilities") {
                    capabilities.incrementAndGet()
                    return dispatch(CAPABILITIES_FULL)
                }
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return dispatch(OPTIONS_DOCUMENT)
            }
        }
        signIn("1", "token-1")
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            repository.refreshNow()
            repository.refreshIfExpired()
            assertEquals(1, capabilities.get())
            assertEquals(2, server.requestCount)
        } finally {
            release.countDown()
        }
        awaitSnapshot { it.state == NbwSnapshotState.READY }
        awaitNoRefreshChildren()
        repository.refreshNow()
        awaitSnapshot { it.state == NbwSnapshotState.READY && capabilities.get() == 2 }
        assertEquals(3, server.requestCount)
        assertEquals(2, capabilities.get())
    }

    @Test
    fun `cancelled old capability cleanup cannot clear a replacement job`() = runBlocking<Unit> {
        refreshJob.children.toList().forEach { it.cancelAndJoin() }
        class PausingDispatcher : CoroutineDispatcher() {
            val held = ConcurrentLinkedQueue<Runnable>()
            @Volatile var pausedJob: Job? = null
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                if (context[Job] === pausedJob) held.add(block)
                else Dispatchers.IO.dispatch(context, block)
            }
        }
        val dispatcher = PausingDispatcher()
        val api = retrofit().create(NbwApi::class.java)
        repository = NbwRepository(api, sessions, optionsRepository, json, clock, CoroutineScope(refreshJob + dispatcher))
        val collector = refreshJob.children.single()
        val oldEntered = CountDownLatch(1)
        val oldRelease = CountDownLatch(1)
        val newEntered = CountDownLatch(1)
        val newRelease = CountDownLatch(1)
        val newCapabilities = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Authorization") == "Bearer token-1") {
                    oldEntered.countDown()
                    check(oldRelease.await(5, TimeUnit.SECONDS))
                    return dispatch(CAPABILITIES_FULL)
                }
                if (request.path == "/api/app/capabilities") {
                    newCapabilities.incrementAndGet()
                    newEntered.countDown()
                    check(newRelease.await(5, TimeUnit.SECONDS))
                    return dispatch(CAPABILITIES_ACCOUNT_2)
                }
                return dispatch(OPTIONS_DOCUMENT_ACCOUNT_2)
            }
        }
        signIn("1", "token-1")
        try {
            assertTrue(oldEntered.await(5, TimeUnit.SECONDS))
            val oldJob = refreshJob.children.single { it !== collector }
            dispatcher.pausedJob = oldJob
            signIn("2", "token-2")
            assertTrue(newEntered.await(5, TimeUnit.SECONDS))
            withTimeout(5_000) { while (dispatcher.held.isEmpty()) delay(1) }
            // Finish the old cancelled coroutine only after account 2 owns the slot.
            dispatcher.pausedJob = null
            while (true) (dispatcher.held.poll() ?: break).run()
            withTimeout(5_000) { oldJob.join() }
            repository.refreshNow()
            repository.refreshIfExpired()
            assertEquals(1, newCapabilities.get())
            assertEquals(2, server.requestCount)
            newRelease.countDown()
            val snapshot = awaitSnapshot { it.state == NbwSnapshotState.READY && it.lease?.accountId == "2" }
            assertEquals("catalog-2", snapshot.options!!.configRevision)
            assertEquals(1, newCapabilities.get())
            assertEquals(3, server.requestCount)
        } finally {
            dispatcher.pausedJob = null
            while (true) (dispatcher.held.poll() ?: break).run()
            oldRelease.countDown()
            newRelease.countDown()
        }
    }

    @Test
    fun `elapsed realtime zero is a valid catalog load time and exact ttl expires`() = runBlocking<Unit> {
        clock.nowMs = 0
        enqueue(CAPABILITIES_FULL)
        enqueue(OPTIONS_DOCUMENT)
        signIn("1", "token-1")
        awaitSnapshot { it.state == NbwSnapshotState.READY }
        awaitNoRefreshChildren()
        clock.nowMs = NbwRepository.TTL_MS - 1
        repository.refreshIfExpired()
        val cached = optionsRepository.load(activeLease(), "Bearer token-1")
        assertTrue(cached is NetworkResult.Success)
        assertEquals(2, server.requestCount)
        clock.nowMs = NbwRepository.TTL_MS
        enqueue(CAPABILITIES_FULL)
        enqueue(OPTIONS_DOCUMENT_ACCOUNT_2)
        repository.refreshIfExpired()
        val snapshot = awaitSnapshot { it.state == NbwSnapshotState.READY && it.options?.configRevision == "catalog-2" }
        assertEquals("catalog-2", snapshot.options!!.configRevision)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `mode change inside ttl drops full catalog before read_only catalog returns`() = runBlocking<Unit> {
        enqueue(CAPABILITIES_FULL)
        enqueue(OPTIONS_DOCUMENT)
        signIn("1", "token-1")
        awaitSnapshot { it.state == NbwSnapshotState.READY }
        awaitNoRefreshChildren()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/api/app/capabilities") return dispatch(CAPABILITIES_READ_ONLY)
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return dispatch(OPTIONS_DOCUMENT_ACCOUNT_2)
            }
        }
        repository.refreshNow()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val loading = repository.snapshot.value
            assertEquals(NbwSnapshotState.LOADING, loading.state)
            assertEquals(NbwRemoteMode.READ_ONLY, loading.capabilities!!.remoteMode)
            assertNull(loading.options)
            assertNull(optionsRepository.cached(activeLease()))
        } finally {
            release.countDown()
        }
        val snapshot = awaitSnapshot { it.state == NbwSnapshotState.READY && it.options?.configRevision == "catalog-2" }
        assertEquals(NbwRemoteMode.READ_ONLY, snapshot.capabilities!!.remoteMode)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `full and read_only refresh failures discard previously trusted state`() = runBlocking<Unit> {
        for (mode in listOf(CAPABILITIES_FULL, CAPABILITIES_READ_ONLY)) {
            for (catalogFailure in listOf(false, true)) {
                enqueue(mode)
                enqueue(OPTIONS_DOCUMENT)
                signIn("1", "token-1")
                awaitSnapshot { it.state == NbwSnapshotState.READY }
                awaitNoRefreshChildren()
                clock.nowMs += NbwRepository.TTL_MS
                if (catalogFailure) {
                    enqueue(mode)
                    enqueue("catalog unavailable", 500)
                } else {
                    enqueue("capabilities unavailable", 500)
                }
                repository.refreshIfExpired()
                val failed = awaitSnapshot { it.state == NbwSnapshotState.LOCAL_ONLY && it.lease != null }
                assertNull(failed.capabilities)
                assertNull(failed.options)
                assertNull(optionsRepository.cached(activeLease()))
                assertFalse(repository.setFallbackConsent(true))
                assertTrue(sessions.signOut("1"))
                awaitSnapshot { it.lease == null }
            }
        }
        // Each mode: initial 2 + failed capability 1, then initial 2 + failed catalog 2.
        assertEquals(14, server.requestCount)
    }

    @Test
    fun `same lease clear rejects old catalog completion without replacing newer cache`() = runBlocking<Unit> {
        enqueue(CAPABILITIES_LOCAL_ONLY)
        signIn("1", "token-1")
        awaitSnapshot { it.state == NbwSnapshotState.LOCAL_ONLY && it.capabilities != null }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (calls.incrementAndGet() == 1) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return dispatch(OPTIONS_DOCUMENT)
                }
                return dispatch(OPTIONS_DOCUMENT_ACCOUNT_2)
            }
        }
        val lease = activeLease()
        val old = async(Dispatchers.IO) { optionsRepository.load(lease, "Bearer token-1") }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val waiter = async(start = CoroutineStart.UNDISPATCHED) { optionsRepository.load(lease, "Bearer token-1") }
            optionsRepository.clear()
            assertTrue(withTimeout(5_000) { waiter.await() } is NetworkResult.Failure)
            assertTrue(optionsRepository.load(lease, "Bearer token-1") is NetworkResult.Success)
            assertEquals("catalog-2", optionsRepository.cached(lease)!!.configRevision)
            release.countDown()
            assertTrue(withTimeout(5_000) { old.await() } is NetworkResult.Failure)
            assertEquals("catalog-2", optionsRepository.cached(lease)!!.configRevision)
            assertEquals(3, server.requestCount)
        } finally {
            release.countDown()
            old.cancelAndJoin()
        }
    }

    @Test
    fun `cancelled catalog owner releases joiners and allows a replacement fetch`() = runBlocking<Unit> {
        enqueue(CAPABILITIES_LOCAL_ONLY)
        signIn("1", "token-1")
        awaitSnapshot { it.state == NbwSnapshotState.LOCAL_ONLY && it.capabilities != null }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (calls.incrementAndGet() == 1) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return dispatch(OPTIONS_DOCUMENT)
                }
                return dispatch(OPTIONS_DOCUMENT_ACCOUNT_2)
            }
        }
        val lease = activeLease()
        val owner = async(Dispatchers.IO) { optionsRepository.load(lease, "Bearer token-1") }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val waiter = async(start = CoroutineStart.UNDISPATCHED) { optionsRepository.load(lease, "Bearer token-1") }
            withTimeout(5_000) { owner.cancelAndJoin() }
            assertTrue(withTimeout(5_000) { waiter.await() } is NetworkResult.Failure)
            assertNull(optionsRepository.cached(lease))
            val replacement = optionsRepository.load(lease, "Bearer token-1")
            assertTrue(replacement is NetworkResult.Success)
            assertEquals("catalog-2", optionsRepository.cached(lease)!!.configRevision)
            assertEquals(3, server.requestCount)
        } finally {
            release.countDown()
            owner.cancelAndJoin()
        }
    }

    @Test
    fun `cancelled consent releases its slot for the next put`() = runBlocking<Unit> {
        enqueue(CAPABILITIES_FULL)
        enqueue(OPTIONS_DOCUMENT)
        signIn("1", "token-1")
        awaitSnapshot { it.state == NbwSnapshotState.READY }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val puts = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method != "PUT") return dispatch(CAPABILITIES_FULL_CONSENTED)
                if (puts.incrementAndGet() == 1) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                return MockResponse().setResponseCode(204)
            }
        }
        val first = async(Dispatchers.IO) { repository.setFallbackConsent(true) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            withTimeout(5_000) { first.cancelAndJoin() }
            assertTrue(repository.setFallbackConsent(true))
            awaitSnapshot { it.state == NbwSnapshotState.READY && it.capabilities?.fallback?.consented == true }
            assertEquals(2, puts.get())
            assertEquals(5, server.requestCount)
        } finally {
            release.countDown()
            first.cancelAndJoin()
        }
    }

    @Test
    fun `old account consent completion cannot release new account consent slot`() = runBlocking<Unit> {
        enqueue(CAPABILITIES_FULL)
        enqueue(OPTIONS_DOCUMENT)
        signIn("1", "token-1")
        awaitSnapshot { it.state == NbwSnapshotState.READY }
        val oldEntered = CountDownLatch(1)
        val oldRelease = CountDownLatch(1)
        val newEntered = CountDownLatch(1)
        val newRelease = CountDownLatch(1)
        val puts = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "PUT") {
                    puts.incrementAndGet()
                    val old = request.getHeader("Authorization") == "Bearer token-1"
                    (if (old) oldEntered else newEntered).countDown()
                    check((if (old) oldRelease else newRelease).await(5, TimeUnit.SECONDS))
                    return MockResponse().setResponseCode(204)
                }
                return if (request.path == "/api/app/capabilities") dispatch(CAPABILITIES_ACCOUNT_2) else dispatch(OPTIONS_DOCUMENT_ACCOUNT_2)
            }
        }
        val old = async(Dispatchers.IO) { repository.setFallbackConsent(true) }
        var next: kotlinx.coroutines.Deferred<Boolean>? = null
        try {
            assertTrue(oldEntered.await(5, TimeUnit.SECONDS))
            signIn("2", "token-2")
            awaitSnapshot { it.state == NbwSnapshotState.READY && it.lease?.accountId == "2" }
            val newConsent = async(Dispatchers.IO) { repository.setFallbackConsent(true) }
            next = newConsent
            assertTrue(newEntered.await(5, TimeUnit.SECONDS))
            oldRelease.countDown()
            assertFalse(withTimeout(5_000) { old.await() })
            assertFalse(repository.setFallbackConsent(false))
            assertEquals(2, puts.get())
            newRelease.countDown()
            assertTrue(withTimeout(5_000) { newConsent.await() })
        } finally {
            oldRelease.countDown()
            newRelease.countDown()
            old.cancelAndJoin()
            next?.cancelAndJoin()
        }
    }

    @Test
    fun `consent during an active refresh schedules a post put recheck`() = runBlocking<Unit> {
        enqueue(CAPABILITIES_FULL)
        enqueue(OPTIONS_DOCUMENT)
        signIn("1", "token-1")
        awaitSnapshot { it.state == NbwSnapshotState.READY }
        awaitNoRefreshChildren()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val gets = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "PUT") return MockResponse().setResponseCode(204)
                if (gets.incrementAndGet() == 1) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return dispatch(CAPABILITIES_FULL)
                }
                return dispatch(CAPABILITIES_FULL_CONSENTED)
            }
        }
        repository.refreshNow()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue(repository.setFallbackConsent(true))
        } finally {
            release.countDown()
        }
        val snapshot = awaitSnapshot { it.state == NbwSnapshotState.READY && it.capabilities?.fallback?.consented == true }
        assertTrue(snapshot.capabilities!!.fallback.consented)
        assertEquals(2, gets.get())
        assertEquals(5, server.requestCount)
    }
}

// Literal legacy-shape documents (same verified schema as the core/network protocol tests).
private const val CAPABILITIES_FULL = """{
  "schema_version":1,"remote_mode":"full","reason":"available","policy_revision":"r-1",
  "binding":{"state":"bound","uid":"remote-83","username":"remote"},
  "authorization":{"state":"ready","lifetime_policy":"non_expiring"},
  "fallback":{"fid":"18","policy_version":1,"consented":false}
}"""

private val CAPABILITIES_FULL_CONSENTED = CAPABILITIES_FULL.replace("\"consented\":false", "\"consented\":true")

private val CAPABILITIES_READ_ONLY = CAPABILITIES_FULL.replace("\"full\"", "\"read_only\"")

private val CAPABILITIES_LOCAL_ONLY = CAPABILITIES_FULL.replace("\"full\"", "\"local_only\"")

private val CAPABILITIES_ACCOUNT_2 = CAPABILITIES_FULL.replace("remote-83", "remote-84")

private const val OPTIONS_DOCUMENT = """{
  "schema_version":1,"config_revision":"catalog-1",
  "forums":[
    {"fid":"18","name":"闲聊","required":false,"types":[{"typeid":"37","name":"日常","moderator_only":false}]},
    {"fid":"36","name":"必选分类","required":true,"types":[
      {"typeid":"301","name":"分享","moderator_only":false},
      {"typeid":"302","name":"管理","moderator_only":true}
    ]}
  ],"fallback":{"fid":"18","policy_version":1}
}"""

private const val OPTIONS_DOCUMENT_ACCOUNT_2 = """{
  "schema_version":1,"config_revision":"catalog-2",
  "forums":[
    {"fid":"18","name":"闲聊","required":false,"types":[{"typeid":"37","name":"日常","moderator_only":false}]}
  ],"fallback":{"fid":"18","policy_version":1}
}"""
