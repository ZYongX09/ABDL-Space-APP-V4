package top.abdl_space.app.core.data.compose

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
import top.abdl_space.app.core.network.HttpModule
import top.abdl_space.app.core.network.NetworkFailureKind
import top.abdl_space.app.core.network.api.MastodonApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Real loopback HTTP against the production Retrofit/MastodonApi surface and the production
 * Json config. No fabricated transports, no reflection; the idempotency key is asserted from
 * what actually goes over the wire.
 */
class ComposeRepositoryTest {
    private val server = MockWebServer()
    private lateinit var sessions: SessionRepository
    private lateinit var repository: ComposeRepository
    private lateinit var json: Json

    @Before
    fun setUp() = runBlocking<Unit> {
        server.start()
        val store = object : SessionStore {
            var value = StoredSessions("1", listOf(SessionCredential("1", "offline-token", "name", "Name")))
            override suspend fun load() = value
            override suspend fun save(sessions: StoredSessions) { value = sessions }
            override suspend fun quarantine() {}
            override suspend fun reset() { value = StoredSessions() }
        }
        sessions = SessionRepository(store)
        sessions.restore()
        json = HttpModule.json()
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().followRedirects(false).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(MastodonApi::class.java)
        repository = ComposeRepository(api, sessions, json)
    }

    @After fun tearDown() { server.shutdown() }

    private fun lease() = (sessions.state.value as SessionState.Ready).lease
    private fun status(id: String) =
        """{"id":"$id","uri":"https://example.test/$id","created_at":"2026-10-27T00:00:00Z","account":{"id":"1"},"visibility":"public","content":"text"}"""
    private fun enqueue(body: String, code: Int = 200) =
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))
    private fun takeRequest() =
        checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "Expected a real loopback request" }

    @Test
    fun `text post hits the verified contract with a typed body and idempotency key`() = runBlocking<Unit> {
        enqueue(status("c_new_1"))
        val result = repository.publish(lease(), "第一条真实文字帖")
        val published = result as ComposePublishResult.Published
        assertEquals("c_new_1", published.status.id)
        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/statuses", request.path)
        assertEquals("Bearer offline-token", request.getHeader("Authorization"))
        val key = request.getHeader("Idempotency-Key")
        assertTrue("Idempotency-Key must be a non-blank UUID-like token", !key.isNullOrBlank())
        assertEquals(
            json.parseToJsonElement("""{"status":"第一条真实文字帖","visibility":"public","sensitive":false,"mental_crisis":false,"spoiler_text":""}"""),
            json.parseToJsonElement(request.body.readUtf8()),
        )
    }

    @Test
    fun `crisis keywords flip only the typed mental_crisis field`() = runBlocking<Unit> {
        enqueue(status("c_new_2"))
        repository.publish(lease(), "最近有些抑郁，想聊聊")
        assertEquals(
            json.parseToJsonElement("""{"status":"最近有些抑郁，想聊聊","visibility":"public","sensitive":false,"mental_crisis":true,"spoiler_text":""}"""),
            json.parseToJsonElement(takeRequest().body.readUtf8()),
        )
    }

    @Test
    fun `retrying the identical payload reuses the same in-process idempotency key`() = runBlocking<Unit> {
        enqueue("{}", 500)
        enqueue(status("c_new_3"))
        assertTrue(repository.publish(lease(), "同一条草稿") is ComposePublishResult.Failed)
        assertTrue(repository.publish(lease(), "同一条草稿") is ComposePublishResult.Published)
        val first = takeRequest()
        val second = takeRequest()
        assertEquals("/api/v1/statuses", first.path)
        assertEquals(first.getHeader("Idempotency-Key"), second.getHeader("Idempotency-Key"))
    }

    @Test
    fun `editing the draft between attempts mints a fresh key like the legacy client`() = runBlocking<Unit> {
        enqueue("{}", 500)
        enqueue(status("c_new_4"))
        assertTrue(repository.publish(lease(), "改动前的文字") is ComposePublishResult.Failed)
        assertTrue(repository.publish(lease(), "改动后的文字") is ComposePublishResult.Published)
        val first = takeRequest()
        val second = takeRequest()
        assertNotEquals(first.getHeader("Idempotency-Key"), second.getHeader("Idempotency-Key"))
    }

    @Test
    fun `a confirmed publish clears the pending key even for identical text`() = runBlocking<Unit> {
        enqueue(status("c_new_5"))
        enqueue(status("c_new_6"))
        assertTrue(repository.publish(lease(), "独立的两条帖子") is ComposePublishResult.Published)
        assertTrue(repository.publish(lease(), "独立的两条帖子") is ComposePublishResult.Published)
        val first = takeRequest()
        val second = takeRequest()
        assertNotEquals(first.getHeader("Idempotency-Key"), second.getHeader("Idempotency-Key"))
    }

    @Test
    fun `failures keep the key for retry and are classified without raw server text`() = runBlocking<Unit> {
        for ((code, kind) in listOf(400 to NetworkFailureKind.HTTP, 422 to NetworkFailureKind.VALIDATION, 401 to NetworkFailureKind.UNAUTHORIZED, 429 to NetworkFailureKind.RATE_LIMITED)) {
            enqueue("""{"error":"server原文不应回显给用户"}""", code)
            val result = repository.publish(lease(), "分类失败的草稿") as ComposePublishResult.Failed
            assertEquals(kind, result.error.kind)
        }
        // All HTTP failures keep the same payload/key, including generic 400 responses.
        val keys = List(4) { takeRequest().getHeader("Idempotency-Key") }
        assertTrue(keys.first() != null && keys.distinct().size == 1)
        enqueue(status("c_new_7"))
        assertTrue(repository.publish(lease(), "分类失败的草稿") is ComposePublishResult.Published)
        assertEquals(keys.last(), takeRequest().getHeader("Idempotency-Key"))
    }

    @Test
    fun `invalid successful status responses retain the retry key until confirmed success`() = runBlocking<Unit> {
        val bodies = listOf("{}", "not-json", status(""), status("c_unknown").replace("\"public\"", "\"future\""))
        for (body in bodies) {
            enqueue(body)
            val result = repository.publish(lease(), "响应未知的草稿") as ComposePublishResult.Failed
            assertEquals(NetworkFailureKind.PROTOCOL, result.error.kind)
        }
        val keys = List(bodies.size) { takeRequest().getHeader("Idempotency-Key") }
        assertTrue(keys.first() != null && keys.distinct().size == 1)
        enqueue(status("c_resolved"))
        assertTrue(repository.publish(lease(), "响应未知的草稿") is ComposePublishResult.Published)
        assertEquals(keys.first(), takeRequest().getHeader("Idempotency-Key"))
    }

    @Test
    fun `account changes and returning to the same account never inherit an old retry key`() = runBlocking<Unit> {
        enqueue("{}", 500)
        assertTrue(repository.publish(lease(), "各账号相同文字") is ComposePublishResult.Failed)
        val first = takeRequest()
        assertTrue(sessions.signIn(SessionCredential("2", "second-offline-token", "peer", "Peer"), lease()))
        enqueue("{}", 500)
        assertTrue(repository.publish(lease(), "各账号相同文字") is ComposePublishResult.Failed)
        val second = takeRequest()
        assertEquals("Bearer second-offline-token", second.getHeader("Authorization"))
        assertNotEquals(first.getHeader("Idempotency-Key"), second.getHeader("Idempotency-Key"))
        assertTrue(sessions.switchAccount("1"))
        enqueue(status("c_back"))
        assertTrue(repository.publish(lease(), "各账号相同文字") is ComposePublishResult.Published)
        val third = takeRequest()
        assertEquals("Bearer offline-token", third.getHeader("Authorization"))
        assertNotEquals(first.getHeader("Idempotency-Key"), third.getHeader("Idempotency-Key"))
        assertNotEquals(second.getHeader("Idempotency-Key"), third.getHeader("Idempotency-Key"))
    }

    @Test
    fun `a new generation for the same account does not reuse a pending key`() = runBlocking<Unit> {
        enqueue("{}", 500)
        repository.publish(lease(), "重新登录的草稿")
        val previous = takeRequest().getHeader("Idempotency-Key")
        assertTrue(sessions.signOut("1"))
        assertTrue(sessions.signIn(SessionCredential("1", "replacement-offline-token", "name", "Name"), lease()))
        enqueue(status("c_signed_in_again"))
        assertTrue(repository.publish(lease(), "重新登录的草稿") is ComposePublishResult.Published)
        assertNotEquals(previous, takeRequest().getHeader("Idempotency-Key"))
    }

    @Test
    fun `cancelling an in flight post propagates cancellation and retains its key for retry`() = runBlocking<Unit> {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.sequenceNumber == 0) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                return MockResponse().setBody(status("c_after_cancel"))
            }
        }
        val pending = async(Dispatchers.Default) { repository.publish(lease(), "取消后仍需核实的草稿") }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            pending.cancelAndJoin()
            assertTrue(pending.isCancelled)
        } finally {
            release.countDown()
        }
        val cancelledKey = takeRequest().getHeader("Idempotency-Key")
        assertTrue(repository.publish(lease(), "取消后仍需核实的草稿") is ComposePublishResult.Published)
        assertEquals(cancelledKey, takeRequest().getHeader("Idempotency-Key"))
    }

    @Test
    fun `a response that arrives after logout is dropped and cannot publish`() = runBlocking<Unit> {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json").setBody(status("c_stale_account"))
            }
        }
        val original = lease()
        val pending = async(Dispatchers.Default) { repository.publish(original, "退出期间在途的帖子") }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue(sessions.signOut("1"))
        } finally {
            release.countDown()
        }
        assertTrue(pending.await() is ComposePublishResult.SessionChanged)
    }

    @Test
    fun `a guest lease cannot initiate a publish request`() = runBlocking<Unit> {
        sessions.signOut("1")
        val result = repository.publish(lease(), "访客的草稿")
        assertTrue(result is ComposePublishResult.Failed)
        assertEquals(NetworkFailureKind.UNAUTHORIZED, (result as ComposePublishResult.Failed).error.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the text request factory sends the verified subset with no invented extensions`() {
        val request = ComposeRepository.textStatusRequest("纯文字")
        assertNull(request.inReplyToId)
        assertNull(request.language)
        assertNull(request.mediaIds)
        assertNull(request.poll)
        assertTrue(!request.sensitive && request.spoilerText.isEmpty())
    }
}
