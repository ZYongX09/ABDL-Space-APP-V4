package top.abdl_space.app

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import top.abdl_space.app.core.data.compose.ComposeRepository
import top.abdl_space.app.core.data.session.SessionCredential
import top.abdl_space.app.core.data.session.SessionRepository
import top.abdl_space.app.core.data.session.SessionState
import top.abdl_space.app.core.data.session.SessionStore
import top.abdl_space.app.core.data.session.StoredSessions
import top.abdl_space.app.core.network.HttpModule
import top.abdl_space.app.core.network.api.MastodonApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real loopback POSTs with a controlled Main scheduler; no private state access or fake success. */
class V4ComposeViewModelTest {
    private val server = MockWebServer()
    private val dispatcher = StandardTestDispatcher()
    private lateinit var sessions: SessionRepository
    private lateinit var repository: ComposeRepository
    private lateinit var viewModel: V4ComposeViewModel

    @Before
    fun setUp() = runBlocking<Unit> {
        Dispatchers.setMain(dispatcher)
        server.start()
        val store = object : SessionStore {
            var value = StoredSessions("1", listOf(
                SessionCredential("1", "offline-token", "name", "Name"),
                SessionCredential("2", "second-offline-token", "peer", "Peer"),
            ))
            override suspend fun load() = value
            override suspend fun save(sessions: StoredSessions) { value = sessions }
            override suspend fun quarantine() {}
            override suspend fun reset() { value = StoredSessions() }
        }
        sessions = SessionRepository(store)
        sessions.restore()
        val json = HttpModule.json()
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().followRedirects(false).retryOnConnectionFailure(false).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(MastodonApi::class.java)
        repository = ComposeRepository(api, sessions, json)
        // Deliberately do not start the session collector: UI can be called first on injection.
        viewModel = V4ComposeViewModel(repository, sessions)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        server.shutdown()
    }

    private fun status(id: String) =
        """{"id":"$id","uri":"https://example.test/$id","created_at":"2026-10-27T00:00:00Z","account":{"id":"1"},"visibility":"public","content":"text"}"""

    private fun takeRequest() = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))

    private fun settle(expectedRequests: Int, predicate: (V4ComposeUiState) -> Boolean = { true }) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        do {
            dispatcher.scheduler.advanceUntilIdle()
            val state = viewModel.compose.value
            if (server.requestCount >= expectedRequests && !state.sending && predicate(state)) return
            Thread.sleep(1)
        } while (System.nanoTime() < deadline)
        throw AssertionError("Publish did not settle: requests=${server.requestCount}, state=${viewModel.compose.value}")
    }

    @Test
    fun `queued and in flight clicks send only once and completion is separately acknowledged`() = runBlocking<Unit> {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return MockResponse().setBody(status("c_once"))
            }
        }
        try {
            viewModel.publishPost("同一份草稿")
            viewModel.publishPost("同一份草稿")
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            viewModel.onComposeEditorOpened()
            viewModel.publishPost("同一份草稿")
            assertTrue(viewModel.compose.value.sending)
            assertEquals(1, server.requestCount)
        } finally {
            release.countDown()
        }
        settle(1) { it.publishedId == "c_once" }
        assertNotNull(viewModel.compose.value.feedback)
        // A queued duplicate after the response but before the shell handles success is ignored.
        viewModel.publishPost("同一份草稿")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, server.requestCount)
        viewModel.consumePublishedPost("wrong-id")
        assertEquals("c_once", viewModel.compose.value.publishedId)
        viewModel.consumePublishedPost("c_once")
        assertNull(viewModel.compose.value.publishedId)
        assertNotNull(viewModel.compose.value.feedback)
        viewModel.onComposeEditorOpened()
        assertNull(viewModel.compose.value.publishedId)
        viewModel.consumeComposeFeedback()
        assertEquals(V4ComposeUiState(lease = (sessions.state.value as SessionState.Ready).lease), viewModel.compose.value)
    }

    @Test
    fun `an old success notice cannot complete a reopened editor or a failed new post`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody(status("c_old")))
        viewModel.publishPost("旧稿")
        settle(1) { it.publishedId == "c_old" }
        val previousKey = takeRequest().getHeader("Idempotency-Key")
        viewModel.onComposeEditorOpened()
        assertNull(viewModel.compose.value.publishedId)
        assertNotNull(viewModel.compose.value.feedback)
        server.enqueue(MockResponse().setResponseCode(422).setBody("{}"))
        viewModel.publishPost("新稿")
        assertNull(viewModel.compose.value.feedback)
        settle(2) { it.error != null }
        assertNull(viewModel.compose.value.feedback)
        assertNull(viewModel.compose.value.publishedId)
        assertNotEquals(previousKey, takeRequest().getHeader("Idempotency-Key"))
        // A delayed acknowledgment for the previous post must not consume a new success.
        server.enqueue(MockResponse().setBody(status("c_new")))
        viewModel.publishPost("新稿")
        settle(3) { it.publishedId == "c_new" }
        viewModel.consumePublishedPost("c_old")
        assertEquals("c_new", viewModel.compose.value.publishedId)
    }

    @Test
    fun `http and malformed status failures never emit completion and preserve retry identity`() = runBlocking<Unit> {
        val responses = listOf(
            MockResponse().setResponseCode(400).setBody("{}"),
            MockResponse().setResponseCode(422).setBody("{}"),
            MockResponse().setResponseCode(500).setBody("{}"),
            MockResponse().setBody("{}"),
            MockResponse().setBody(status("").replace("\"public\"", "\"unknown\"")),
        )
        val keys = mutableListOf<String?>()
        responses.forEachIndexed { index, response ->
            server.enqueue(response)
            viewModel.publishPost("不能误清的草稿")
            settle(index + 1) { it.error != null }
            val state = viewModel.compose.value
            assertNull(state.publishedId)
            assertNull(state.feedback)
            assertTrue(state.error!!.contains("草稿已保留"))
            keys += takeRequest().getHeader("Idempotency-Key")
        }
        assertTrue(keys.first() != null && keys.distinct().size == 1)
        server.enqueue(MockResponse().setBody(status("c_confirmed")))
        viewModel.publishPost("不能误清的草稿")
        settle(responses.size + 1) { it.publishedId == "c_confirmed" }
        assertEquals(keys.first(), takeRequest().getHeader("Idempotency-Key"))
    }

    @Test
    fun `a disconnected write is unknown not success and retry uses the same key`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        viewModel.publishPost("断线草稿")
        settle(1) { it.error != null }
        assertTrue(viewModel.compose.value.error!!.contains("结果未知"))
        assertNull(viewModel.compose.value.feedback)
        assertNull(viewModel.compose.value.publishedId)
        val key = takeRequest().getHeader("Idempotency-Key")
        server.enqueue(MockResponse().setBody(status("c_after_disconnect")))
        viewModel.publishPost("断线草稿")
        settle(2) { it.publishedId == "c_after_disconnect" }
        assertEquals(key, takeRequest().getHeader("Idempotency-Key"))
    }

    @Test
    fun `cancellation before dispatch releases sending without issuing or confirming a post`() = runBlocking<Unit> {
        viewModel.publishPost("尚未发送的草稿")
        assertTrue(viewModel.compose.value.sending)
        viewModel.viewModelScope.cancel()
        settle(0) { it.error != null }
        assertNull(viewModel.compose.value.feedback)
        assertNull(viewModel.compose.value.publishedId)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `in flight cancellation retains the key for a recreated viewmodel and never confirms the old one`() = runBlocking<Unit> {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return MockResponse().setBody(status("c_retry_cancelled"))
            }
        }
        val previous = viewModel
        try {
            viewModel.publishPost("在途取消的草稿")
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            viewModel.viewModelScope.cancel()
            settle(1) { it.error != null }
            assertFalse(previous.compose.value.sending)
            assertNull(previous.compose.value.publishedId)
            assertNull(previous.compose.value.feedback)
        } finally {
            release.countDown()
        }
        val key = takeRequest().getHeader("Idempotency-Key")
        viewModel = V4ComposeViewModel(repository, sessions)
        viewModel.publishPost("在途取消的草稿")
        settle(2) { it.publishedId == "c_retry_cancelled" }
        assertEquals(key, takeRequest().getHeader("Idempotency-Key"))
        assertNull(previous.compose.value.publishedId)
        assertNull(previous.compose.value.feedback)
    }

    @Test
    fun `account switch before the collector runs cannot inherit success or a failed retry key`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        viewModel.publishPost("相同账号文字")
        settle(1) { it.error != null }
        val oldKey = takeRequest().getHeader("Idempotency-Key")
        assertTrue(sessions.switchAccount("2"))
        server.enqueue(MockResponse().setBody(status("c_second")))
        viewModel.publishPost("相同账号文字")
        settle(2) { it.publishedId == "c_second" }
        val request = takeRequest()
        assertEquals("Bearer second-offline-token", request.getHeader("Authorization"))
        assertNotEquals(oldKey, request.getHeader("Idempotency-Key"))
        assertTrue(sessions.switchAccount("1"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(V4ComposeUiState(lease = (sessions.state.value as SessionState.Ready).lease), viewModel.compose.value)
    }
}
