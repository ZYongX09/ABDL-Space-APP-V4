package top.abdl_space.app.core.data.timeline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
import org.junit.Assert.assertFalse
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
import top.abdl_space.app.core.network.NetworkFailureKind
import top.abdl_space.app.core.network.NetworkResult
import top.abdl_space.app.core.network.api.MastodonApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TimelineRepositoryTest {
    private val server = MockWebServer()
    private lateinit var sessions: SessionRepository
    private lateinit var repository: TimelineRepository

    @Before
    fun setUp() = runBlocking {
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
        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().followRedirects(false).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(MastodonApi::class.java)
        repository = TimelineRepository(api, sessions, json)
    }

    @After fun tearDown() { server.shutdown() }
    private fun lease() = (sessions.state.value as SessionState.Ready).lease
    private fun status(id: String) = """{"id":"$id","uri":"https://example.test/$id","created_at":"2026-10-26T00:00:00Z","account":{"id":"1"},"visibility":"public","content":"text"}"""

    @Test
    fun `cursor is read from Link while duplicate statuses are deduplicated`() = runBlocking {
        server.enqueue(MockResponse().setBody("[${status("p_1")},${status("p_1")}]")
            .setHeader("Link", "<${server.url("/api/v1/timelines/all?max_id=opaque%2Fcursor")}>; rel=\"next\""))
        val result = repository.page(TimelineKind.RECOMMENDED, lease()) as NetworkResult.Success
        assertEquals(listOf("p_1"), result.value.statuses.map { it.id })
        assertEquals("opaque/cursor", result.value.nextCursor)
        assertTrue(result.value.hasMore)
        val request = server.takeRequest()
        assertEquals("Bearer offline-token", request.getHeader("Authorization"))
        assertEquals("/api/v1/timelines/all?limit=20", request.path)
    }

    @Test
    fun `popular offset advances by server rows not deduplicated row count`() = runBlocking {
        server.enqueue(MockResponse().setBody(List(20) { status("p_1") }.joinToString(",", "[", "]")))
        val result = repository.page(TimelineKind.POPULAR, lease(), offset = 40) as NetworkResult.Success
        assertEquals(1, result.value.statuses.size)
        assertEquals(60, result.value.nextOffset)
        assertEquals("/api/v1/timelines/popular?offset=40&limit=20", server.takeRequest().path)
    }

    @Test
    fun `empty successful timeline terminates without manufacturing a cursor`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        val result = repository.page(TimelineKind.FOLLOWING, lease()) as NetworkResult.Success
        assertTrue(result.value.statuses.isEmpty())
        assertFalse(result.value.hasMore)
    }

    @Test
    fun `http failure is not converted into an empty success`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("failure"))
        val result = repository.page(TimelineKind.RECOMMENDED, lease()) as NetworkResult.Failure
        assertEquals(NetworkFailureKind.SERVER, result.error.kind)
    }

    @Test
    fun `response received after logout cannot enter a timeline`() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return MockResponse().setBody("[${status("private-old-account")}]")
            }
        }
        val original = lease()
        val pending = async(Dispatchers.Default) { repository.page(TimelineKind.FOLLOWING, original) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue(sessions.signOut("1"))
        } finally {
            release.countDown()
        }
        assertTrue(pending.await() is NetworkResult.Failure)
    }

    @Test
    fun `guest cannot initiate an authenticated request`() = runBlocking {
        sessions.signOut("1")
        val result = repository.page(TimelineKind.FOLLOWING, lease())
        assertTrue(result is NetworkResult.Failure)
        assertEquals(0, server.requestCount)
    }
}
