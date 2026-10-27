package top.abdl_space.app

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import top.abdl_space.app.core.data.session.SessionCredential
import top.abdl_space.app.core.data.session.SessionRepository
import top.abdl_space.app.core.data.session.SessionState
import top.abdl_space.app.core.data.session.SessionStore
import top.abdl_space.app.core.data.session.StoredSessions
import top.abdl_space.app.core.network.HttpModule
import top.abdl_space.app.core.network.api.MessagesApi
import top.abdl_space.app.core.network.api.NotificationApi
import top.abdl_space.app.core.network.dto.ConversationsResponse
import top.abdl_space.app.core.network.dto.NotificationDto
import top.abdl_space.app.core.network.dto.NotificationKind
import top.abdl_space.app.ui.V4MessageTab
import top.abdl_space.app.ui.V4MessagesUiState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Real HTTP loopback against the real SessionRepository with an in-memory store: the same
 * lease-isolation harness as TimelineRepositoryTest, driving the app-owned messages ViewModel.
 * No fabricated fixtures beyond literal legacy-shaped documents, no reflection, no tautologies.
 */
class V4MessagesViewModelTest {
    private val server = MockWebServer()
    private val dispatcher = StandardTestDispatcher()
    private lateinit var sessions: SessionRepository
    private lateinit var viewModel: V4MessagesViewModel
    private lateinit var notificationApi: NotificationApi
    private lateinit var messagesApi: MessagesApi

    /** Bounded condition wait: virtual Main and real loopback callbacks must both complete. */
    private fun settle(expectedRequests: Int = server.requestCount, predicate: (V4MessagesUiState) -> Boolean = { true }) {
        awaitCondition("Messages did not settle") {
            val state = viewModel.state.value
            val busy = state.notifications.isLoading || state.notifications.isLoadingMore || state.conversations.isLoading
            server.requestCount >= expectedRequests && !busy && predicate(state)
        }
    }

    private fun awaitCondition(description: String, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        do {
            dispatcher.scheduler.advanceUntilIdle()
            if (predicate()) return
            Thread.sleep(1)
        } while (System.nanoTime() < deadline)
        throw AssertionError("$description: requests=${server.requestCount}, state=${viewModel.state.value}")
    }

    @Before
    fun setUp() = runBlocking<Unit> {
        Dispatchers.setMain(dispatcher)
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
        val json = HttpModule.json()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().followRedirects(false).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        notificationApi = retrofit.create(NotificationApi::class.java)
        messagesApi = retrofit.create(MessagesApi::class.java)
        viewModel = V4MessagesViewModel(sessions, notificationApi, messagesApi, json)
        // Leave the initial session collector queued, just like first-time injection can do.
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        server.shutdown()
    }

    private fun notification(id: String, type: String = "mention"): String = """{"id":"$id","type":"$type",""" +
        """"created_at":"2026-10-26T00:00:00.000Z",""" +
        """"account":{"id":"18446744073709551616001","username":"offline","acct":"offline","display_name":"Offline Account"},""" +
        """"status":{"id":"c_$id","uri":"https://abdl-space.top/statuses/c_$id","created_at":"2026-10-26T00:00:00Z",""" +
        """"account":{"id":"18446744073709551616001"},"visibility":"public","content":"<p>summary</p>"},""" +
        """"future_extension":true}"""

    private fun page(vararg ids: String, type: String = "mention", nextCursor: String? = null): MockResponse {
        val body = ids.joinToString(",", "[", "]") { notification(it, type) }
        val response = MockResponse().setBody(body)
        if (nextCursor != null) {
            response.addHeader("Link", "<${server.url("/api/v1/notifications?max_id=$nextCursor")}>; rel=\"next\"")
        }
        return response
    }

    @Test
    fun `first notification page loads typed rows with link continuation`() = runBlocking<Unit> {
        server.enqueue(page("101", "102", nextCursor = "cursor-opaque-1"))
        viewModel.tabSelected(V4MessageTab.Notifications)
        dispatcher.scheduler.advanceUntilIdle(); settle()
        val state = viewModel.state.value.notifications
        assertEquals(listOf("101", "102"), state.notifications.map { it.id })
        assertEquals(listOf(NotificationKind.Mention, NotificationKind.Mention), state.notifications.map { it.kind })
        assertTrue(state.hasLoaded)
        assertTrue(state.hasMore)
        assertFalse(state.isLoading)
        assertNull(state.error)
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/notifications?limit=20", request.path)
        assertEquals("Bearer offline-token", request.getHeader("Authorization"))
    }

    @Test
    fun `unknown notification type stays inspectable without crashing`() = runBlocking<Unit> {
        server.enqueue(page("101", type = "server.future.kind"))
        viewModel.tabSelected(V4MessageTab.Notifications)
        dispatcher.scheduler.advanceUntilIdle(); settle()
        val row = viewModel.state.value.notifications.notifications.single()
        assertEquals(NotificationKind.ExplicitUnknown, row.kind)
        assertEquals("server.future.kind", row.rawType)
        assertEquals("18446744073709551616001", row.account!!.id)
    }

    @Test
    fun `append sends link max_id and a failed append keeps old rows`() = runBlocking<Unit> {
        server.enqueue(page("101", nextCursor = "cursor-opaque-1"))
        viewModel.tabSelected(V4MessageTab.Notifications)
        dispatcher.scheduler.advanceUntilIdle(); settle()
        server.enqueue(MockResponse().setResponseCode(503).setBody("busy"))
        viewModel.loadMoreNotifications()
        dispatcher.scheduler.advanceUntilIdle(); settle()
        var state = viewModel.state.value.notifications
        assertEquals(listOf("101"), state.notifications.map { it.id })
        // The first captured request is the initial page: no max_id on it.
        assertNull(server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!.queryParameter("max_id"))
        assertTrue(state.loadMoreError != null)
        assertTrue(state.hasMore)
        assertFalse(state.isLoadingMore)
        // A failed append retries the same cursor only through the explicit retry.
        server.enqueue(page("102", "101", nextCursor = null))
        viewModel.loadMoreNotifications()
        dispatcher.scheduler.advanceUntilIdle(); settle()
        state = viewModel.state.value.notifications
        assertEquals("cursor-opaque-1", server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!.queryParameter("max_id"))
        assertEquals(listOf("101", "102"), state.notifications.map { it.id })
        assertNull(state.loadMoreError)
        assertFalse(state.hasMore)
    }

    @Test
    fun `refresh failure keeps existing notifications and retry replaces them`() = runBlocking<Unit> {
        server.enqueue(page("101", nextCursor = "cursor-opaque-1"))
        viewModel.tabSelected(V4MessageTab.Notifications)
        dispatcher.scheduler.advanceUntilIdle(); settle()
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        viewModel.refresh(V4MessageTab.Notifications)
        dispatcher.scheduler.advanceUntilIdle(); settle()
        var state = viewModel.state.value.notifications
        assertEquals(listOf("101"), state.notifications.map { it.id })
        assertTrue(state.error != null)
        assertFalse(state.isLoading)
        server.enqueue(page("201"))
        viewModel.refresh(V4MessageTab.Notifications)
        dispatcher.scheduler.advanceUntilIdle(); settle()
        state = viewModel.state.value.notifications
        assertEquals(listOf("201"), state.notifications.map { it.id })
        assertNull(state.error)
        assertFalse(state.hasMore)
        assertTrue(state.hasLoaded)
    }

    @Test
    fun `conversations load maps legacy document and failure waits for manual refresh`() = runBlocking<Unit> {
        server.enqueue(
            MockResponse().setBody(
                """{"conversations":[{"user_id":18446744073709551616001,"username":"peer","avatar":null,
                    "last_message":"hello","last_message_at":1790985600,"last_message_id":"90071992547409930001","unread_count":2}]}""",
            ),
        )
        viewModel.tabSelected(V4MessageTab.Direct)
        dispatcher.scheduler.advanceUntilIdle(); settle()
        val state = viewModel.state.value.conversations
        assertEquals(listOf("18446744073709551616001"), state.conversations.map { it.userId })
        assertEquals("hello", state.conversations.single().lastMessage)
        assertEquals(2, state.conversations.single().unreadCount)
        assertTrue(state.hasLoaded)
        assertEquals("/api/messages/conversations", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
        // A failed tab never auto-retries on reselect; only the manual refresh does.
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        viewModel.refresh(V4MessageTab.Direct)
        dispatcher.scheduler.advanceUntilIdle(); settle()
        assertTrue(viewModel.state.value.conversations.error != null)
        viewModel.tabSelected(V4MessageTab.Direct)
        dispatcher.scheduler.advanceUntilIdle(); settle()
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `empty successful conversations are real emptiness not an error`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody("""{"conversations":[]}"""))
        viewModel.tabSelected(V4MessageTab.Direct)
        dispatcher.scheduler.advanceUntilIdle(); settle()
        val state = viewModel.state.value.conversations
        assertTrue(state.conversations.isEmpty())
        assertTrue(state.hasLoaded)
        assertNull(state.error)
        server.takeRequest(5, TimeUnit.SECONDS)!!
    }

    @Test
    fun `response arriving after logout cannot enter any messages state`() = runBlocking<Unit> {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return page("private-old-account")
            }
        }
        viewModel.tabSelected(V4MessageTab.Notifications)
        dispatcher.scheduler.advanceUntilIdle()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue(sessions.signOut("1"))
        } finally {
            release.countDown()
        }
        dispatcher.scheduler.advanceUntilIdle(); settle()
        assertEquals(V4MessagesUiState(lease = (sessions.state.value as SessionState.Ready).lease), viewModel.state.value)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `collector first and load first both retain a ready sessions initial results`() = runBlocking<Unit> {
        // Load-first ordering is used by every ordinary test in this class.
        server.enqueue(page("load-first"))
        viewModel.tabSelected(V4MessageTab.Notifications)
        settle(1) { it.notifications.hasLoaded }
        assertEquals(listOf("load-first"), viewModel.state.value.notifications.notifications.map { it.id })
        takeRequest()
        viewModel.viewModelScope.cancel()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel = V4MessagesViewModel(sessions, notificationApi, messagesApi, HttpModule.json())
        dispatcher.scheduler.advanceUntilIdle()
        server.enqueue(page("collector-first"))
        server.enqueue(MockResponse().setBody("""{"conversations":[]}"""))
        viewModel.tabSelected(V4MessageTab.Notifications)
        settle(2) { it.notifications.hasLoaded }
        viewModel.tabSelected(V4MessageTab.Direct)
        settle(3) { it.conversations.hasLoaded }
        assertEquals(listOf("collector-first"), viewModel.state.value.notifications.notifications.map { it.id })
        assertTrue(viewModel.state.value.conversations.hasLoaded)
    }

    @Test
    fun `duplicate tab selection refresh and append calls never duplicate an in flight load`() = runBlocking<Unit> {
        server.enqueue(page("101", nextCursor = "cursor-one"))
        viewModel.tabSelected(V4MessageTab.Notifications)
        viewModel.tabSelected(V4MessageTab.Notifications)
        viewModel.refresh(V4MessageTab.Notifications)
        settle(1) { it.notifications.hasLoaded }
        assertEquals(1, server.requestCount)
        takeRequest()
        server.enqueue(page("102"))
        viewModel.loadMoreNotifications()
        viewModel.loadMoreNotifications()
        viewModel.refresh(V4MessageTab.Notifications)
        settle(2) { !it.notifications.hasMore }
        assertEquals(2, server.requestCount)
        assertEquals("cursor-one", takeRequest().requestUrl!!.queryParameter("max_id"))
        assertEquals(listOf("101", "102"), viewModel.state.value.notifications.notifications.map { it.id })
        server.enqueue(MockResponse().setBody("""{"conversations":[]}"""))
        viewModel.tabSelected(V4MessageTab.Direct)
        viewModel.tabSelected(V4MessageTab.Direct)
        viewModel.refresh(V4MessageTab.Direct)
        settle(3) { it.conversations.hasLoaded }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `link parse failures during refresh and append retain the last committed cursor`() = runBlocking<Unit> {
        server.enqueue(page("101", nextCursor = "retained-cursor"))
        viewModel.tabSelected(V4MessageTab.Notifications)
        settle(1) { it.notifications.hasLoaded }
        takeRequest()
        var retainedCursor = "retained-cursor"
        for ((index, append) in listOf(false, true).withIndex()) {
            val conflicting = page("must-not-apply", nextCursor = "uncommitted-one")
                .addHeader("Link", "<${server.url("/api/v1/notifications?max_id=uncommitted-two")}>; rel=next")
            server.enqueue(conflicting)
            if (append) viewModel.loadMoreNotifications() else viewModel.refresh(V4MessageTab.Notifications)
            settle(2 + index * 2) {
                if (append) it.notifications.loadMoreError != null else it.notifications.error != null
            }
            val failed = viewModel.state.value.notifications
            assertEquals(listOf("101"), failed.notifications.map { it.id })
            assertTrue(failed.hasLoaded && failed.hasMore)
            assertEquals(if (append) retainedCursor else null, takeRequest().requestUrl!!.queryParameter("max_id"))
            // An append after either failed operation must still send the previous real cursor.
            server.enqueue(page("101", nextCursor = if (append) null else "advanced-cursor"))
            viewModel.loadMoreNotifications()
            settle(3 + index * 2) { it.notifications.loadMoreError == null }
            assertEquals(retainedCursor, takeRequest().requestUrl!!.queryParameter("max_id"))
            retainedCursor = "advanced-cursor"
        }
        assertFalse(viewModel.state.value.notifications.hasMore)
    }

    @Test
    fun `http refresh and malformed body append failures both preserve the prior cursor`() = runBlocking<Unit> {
        server.enqueue(page("101", nextCursor = "retry-cursor"))
        viewModel.tabSelected(V4MessageTab.Notifications)
        settle(1) { it.notifications.hasLoaded }
        takeRequest()
        server.enqueue(MockResponse().setResponseCode(503).setBody("busy"))
        viewModel.refresh(V4MessageTab.Notifications)
        settle(2) { it.notifications.error != null }
        assertNull(takeRequest().requestUrl!!.queryParameter("max_id"))
        server.enqueue(MockResponse().setBody("not-json"))
        viewModel.loadMoreNotifications()
        settle(3) { it.notifications.loadMoreError != null }
        assertEquals("retry-cursor", takeRequest().requestUrl!!.queryParameter("max_id"))
        assertEquals(listOf("101"), viewModel.state.value.notifications.notifications.map { it.id })
        assertTrue(viewModel.state.value.notifications.hasMore)
        server.enqueue(page("102"))
        viewModel.loadMoreNotifications()
        settle(4) { !it.notifications.hasMore }
        assertEquals("retry-cursor", takeRequest().requestUrl!!.queryParameter("max_id"))
        assertEquals(listOf("101", "102"), viewModel.state.value.notifications.notifications.map { it.id })
    }

    @Test
    fun `a late cancelled job cannot clear the new accounts notification cursor or busy state`() = runBlocking<Unit> {
        val oldEntered = CompletableDeferred<Unit>()
        val oldRelease = CompletableDeferred<Unit>()
        val oldReturned = CompletableDeferred<Unit>()
        val newEntered = CompletableDeferred<Unit>()
        val newRelease = CompletableDeferred<Unit>()
        val controlled = object : NotificationApi by notificationApi {
            override suspend fun notifications(
                authorization: String,
                maxId: String?,
                limit: Int?,
                types: List<String>?,
                excludeTypes: List<String>?,
            ): Response<List<NotificationDto>> {
                if (authorization == "Bearer offline-token") {
                    oldEntered.complete(Unit)
                    // Deterministic non-cooperative completion: simulates a callback delivered
                    // after cancellation without changing any private production state.
                    return withContext(NonCancellable) {
                        oldRelease.await()
                        notificationApi.notifications(authorization, maxId, limit, types, excludeTypes)
                            .also { oldReturned.complete(Unit) }
                    }
                } else if (maxId == null) {
                    newEntered.complete(Unit)
                    newRelease.await()
                }
                return notificationApi.notifications(authorization, maxId, limit, types, excludeTypes)
            }
        }
        viewModel.viewModelScope.cancel()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel = V4MessagesViewModel(sessions, controlled, messagesApi, HttpModule.json())
        viewModel.tabSelected(V4MessageTab.Notifications)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(oldEntered.isCompleted)
        val original = (sessions.state.value as SessionState.Ready).lease
        assertTrue(sessions.signIn(SessionCredential("2", "second-offline-token", "peer", "Peer"), original))
        // Intentionally load before the queued collector processes the account change.
        viewModel.tabSelected(V4MessageTab.Notifications)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(newEntered.isCompleted)
        server.enqueue(page("old-private", nextCursor = "old-cursor"))
        oldRelease.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        takeRequest()
        // The new request is still gated, so a stale cleanup must not release its busy state.
        awaitCondition("Old notification callback did not return") { oldReturned.isCompleted }
        assertTrue(viewModel.state.value.notifications.isLoading)
        assertTrue(viewModel.state.value.notifications.notifications.isEmpty())
        server.enqueue(page("new-private", nextCursor = "new-cursor"))
        newRelease.complete(Unit)
        settle(2) { it.notifications.hasLoaded }
        assertEquals("Bearer second-offline-token", takeRequest().getHeader("Authorization"))
        assertEquals(listOf("new-private"), viewModel.state.value.notifications.notifications.map { it.id })
        server.enqueue(page("new-more"))
        viewModel.loadMoreNotifications()
        settle(3) { !it.notifications.hasMore }
        val append = takeRequest()
        assertEquals("new-cursor", append.requestUrl!!.queryParameter("max_id"))
        assertEquals("Bearer second-offline-token", append.getHeader("Authorization"))
        assertEquals(listOf("new-private", "new-more"), viewModel.state.value.notifications.notifications.map { it.id })
    }

    @Test
    fun `old conversation completion cannot replace the new account after load before collector`() = runBlocking<Unit> {
        val oldEntered = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val oldReturned = CompletableDeferred<Unit>()
        val controlled = object : MessagesApi by messagesApi {
            override suspend fun conversations(authorization: String): Response<ConversationsResponse> {
                if (authorization != "Bearer offline-token") return messagesApi.conversations(authorization)
                oldEntered.complete(Unit)
                return withContext(NonCancellable) {
                    releaseOld.await()
                    messagesApi.conversations(authorization).also { oldReturned.complete(Unit) }
                }
            }
        }
        viewModel.viewModelScope.cancel()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel = V4MessagesViewModel(sessions, notificationApi, controlled, HttpModule.json())
        viewModel.tabSelected(V4MessageTab.Direct)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(oldEntered.isCompleted)
        val original = (sessions.state.value as SessionState.Ready).lease
        assertTrue(sessions.signIn(SessionCredential("2", "second-offline-token", "peer", "Peer"), original))
        server.enqueue(MockResponse().setBody("""{"conversations":[]}"""))
        viewModel.tabSelected(V4MessageTab.Direct)
        settle(1) { it.conversations.hasLoaded }
        assertEquals("Bearer second-offline-token", takeRequest().getHeader("Authorization"))
        server.enqueue(MockResponse().setBody("""{"conversations":[{"user_id":"old-private","last_message":"stale"}]}"""))
        releaseOld.complete(Unit)
        settle(2) { oldReturned.isCompleted }
        assertEquals("Bearer offline-token", takeRequest().getHeader("Authorization"))
        assertTrue(viewModel.state.value.conversations.hasLoaded)
        assertTrue(viewModel.state.value.conversations.conversations.isEmpty())
        assertNull(viewModel.state.value.conversations.error)
    }

    private fun takeRequest() = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))

    @Test
    fun `guest lease never issues a request`() = runBlocking<Unit> {
        assertTrue(sessions.signOut("1"))
        dispatcher.scheduler.advanceUntilIdle(); settle()
        viewModel.tabSelected(V4MessageTab.Notifications)
        viewModel.tabSelected(V4MessageTab.Direct)
        viewModel.refresh(V4MessageTab.Notifications)
        viewModel.loadMoreNotifications()
        dispatcher.scheduler.advanceUntilIdle(); settle()
        assertEquals(0, server.requestCount)
        assertEquals(V4MessagesUiState(lease = (sessions.state.value as SessionState.Ready).lease), viewModel.state.value)
    }
}
