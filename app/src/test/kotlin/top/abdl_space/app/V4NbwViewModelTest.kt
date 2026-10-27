package top.abdl_space.app

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import top.abdl_space.app.core.data.nbw.NbwClock
import top.abdl_space.app.core.data.nbw.NbwForumOptionsRepository
import top.abdl_space.app.core.data.nbw.NbwRepository
import top.abdl_space.app.core.data.nbw.NbwSnapshot
import top.abdl_space.app.core.data.nbw.NbwSnapshotState
import top.abdl_space.app.core.data.session.SessionCredential
import top.abdl_space.app.core.data.session.SessionRepository
import top.abdl_space.app.core.data.session.SessionState
import top.abdl_space.app.core.data.session.SessionStore
import top.abdl_space.app.core.data.session.StoredSessions
import top.abdl_space.app.core.model.NbwRemoteMode
import top.abdl_space.app.core.network.HttpModule
import top.abdl_space.app.core.network.api.NbwApi
import java.util.concurrent.TimeUnit

/**
 * The discovery entry's ViewModel over real loopback HTTP and the real SessionRepository, using
 * the same lease-isolation harness as V4MessagesViewModelTest. Guests must never reach the wire;
 * the TTL recheck stays silent; the explicit re-check really re-verifies and failure stays closed.
 */
class V4NbwViewModelTest {
    private val server = MockWebServer()
    private val dispatcher = StandardTestDispatcher()
    private val refreshJob = SupervisorJob()
    private val client = OkHttpClient.Builder().followRedirects(false).build()
    private lateinit var sessions: SessionRepository
    private lateinit var repository: NbwRepository
    private lateinit var viewModel: V4NbwViewModel
    private lateinit var json: Json
    private val clock = FakeNbwClock()

    private class FakeNbwClock(@Volatile var nowMs: Long = 10_000L) : NbwClock {
        override fun now(): Long = nowMs
    }

    /** Advance the virtual scheduler and let real loopback HTTP finish, like the messages harness. */
    private fun settle() {
        repeat(60) {
            dispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(5)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server.start()
        json = HttpModule.json()
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        try {
            if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
            refreshJob.cancel()
            dispatcher.scheduler.advanceUntilIdle()
            withTimeout(5_000) { refreshJob.cancelAndJoin() }
        } finally {
            Dispatchers.resetMain()
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

    private fun enqueueJson(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun build(storeInitial: StoredSessions) = runBlocking<Unit> {
        val store = object : SessionStore {
            var value = storeInitial
            override suspend fun load() = value
            override suspend fun save(sessions: StoredSessions) { value = sessions }
            override suspend fun quarantine() {}
            override suspend fun reset() { value = StoredSessions() }
        }
        sessions = SessionRepository(store)
        sessions.restore()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NbwApi::class.java)
        repository = NbwRepository(
            api,
            sessions,
            NbwForumOptionsRepository(api, json, clock),
            json,
            clock,
            CoroutineScope(refreshJob + dispatcher),
        )
        viewModel = V4NbwViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        settle()
    }

    private suspend fun signIn(accountId: String, token: String) {
        val lease = (sessions.state.value as SessionState.Ready).lease
        assertTrue(sessions.signIn(SessionCredential(accountId, token, "name-$accountId", "Name $accountId"), lease))
    }

    @Test
    fun `entry exposes the verified snapshot and the ttl recheck stays silent`() = runBlocking<Unit> {
        build(StoredSessions())
        enqueueJson(CAPABILITIES_FULL)
        enqueueJson(OPTIONS_DOCUMENT)
        signIn("1", "token-1")
        settle()
        val snapshot = viewModel.snapshot.value
        assertEquals(NbwSnapshotState.READY, snapshot.state)
        assertEquals(NbwRemoteMode.FULL, snapshot.capabilities!!.remoteMode)
        assertEquals("catalog-1", snapshot.options!!.configRevision)
        assertEquals(2, server.requestCount)
        viewModel.refreshIfExpired()
        settle()
        // Well inside the 5-minute TTL: no retransmission.
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `explicit recheck bypasses the ttl and really re-verifies`() = runBlocking<Unit> {
        build(StoredSessions())
        enqueueJson(CAPABILITIES_FULL)
        enqueueJson(OPTIONS_DOCUMENT)
        signIn("1", "token-1")
        settle()
        assertEquals(2, server.requestCount)
        enqueueJson(CAPABILITIES_FULL)
        viewModel.refresh()
        settle()
        assertEquals(NbwSnapshotState.READY, viewModel.snapshot.value.state)
        assertEquals(3, server.requestCount)
        val third = server.takeRequest(5, TimeUnit.SECONDS)!!
        server.takeRequest(5, TimeUnit.SECONDS)!!
        val recheck = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/app/capabilities", third.path)
        assertEquals("/api/app/capabilities", recheck.path)
        assertEquals("Bearer token-1", recheck.getHeader("Authorization"))
    }

    @Test
    fun `failure collapses to local_only and the explicit retry recovers`() = runBlocking<Unit> {
        build(StoredSessions())
        enqueueJson("server exploded", 500)
        signIn("1", "token-1")
        settle()
        assertEquals(NbwSnapshotState.LOCAL_ONLY, viewModel.snapshot.value.state)
        assertNull(viewModel.snapshot.value.capabilities)
        enqueueJson(CAPABILITIES_FULL)
        enqueueJson(OPTIONS_DOCUMENT)
        viewModel.refresh()
        settle()
        val snapshot = viewModel.snapshot.value
        assertEquals(NbwSnapshotState.READY, snapshot.state)
        assertEquals("catalog-1", snapshot.options!!.configRevision)
    }

    @Test
    fun `guest entry never sends anything`() = runBlocking<Unit> {
        build(StoredSessions())
        viewModel.refreshIfExpired()
        viewModel.refresh()
        settle()
        assertEquals(NbwSnapshot(), viewModel.snapshot.value)
        assertEquals(0, server.requestCount)
    }
}

private const val CAPABILITIES_FULL = """{
  "schema_version":1,"remote_mode":"full","reason":"available","policy_revision":"r-1",
  "binding":{"state":"bound","uid":"remote-83","username":"remote"},
  "authorization":{"state":"ready","lifetime_policy":"non_expiring"},
  "fallback":{"fid":"18","policy_version":1,"consented":false}
}"""

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
