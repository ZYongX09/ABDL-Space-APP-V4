package top.abdl_space.app.core.data.auth

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
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
import top.abdl_space.app.core.network.api.AuthApi
import top.abdl_space.app.core.network.api.MastodonApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AuthRepositoryTest {
    private val server = MockWebServer()
    private val store = object : SessionStore {
        var current = StoredSessions()
        override suspend fun load() = current
        override suspend fun save(sessions: StoredSessions) { current = sessions }
        override suspend fun quarantine() {}
        override suspend fun reset() { current = StoredSessions() }
    }
    private lateinit var sessions: SessionRepository
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() = runBlocking {
        server.start()
        sessions = SessionRepository(store)
        sessions.restore()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().followRedirects(false).build())
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
        repository = AuthRepository(retrofit.create(AuthApi::class.java), retrofit.create(MastodonApi::class.java), sessions)
    }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun `password login verifies token before storing an account`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"token":"opaque-token"}"""))
        server.enqueue(MockResponse().setBody("""{"id":"001","username":"name","display_name":"昵称"}"""))
        assertEquals(AuthOutcome.SignedIn, repository.passwordSignIn(" name ", "password"))
        val login = server.takeRequest()
        assertEquals("/api/auth/login", login.path)
        assertEquals("{\"login\":\"name\",\"password\":\"password\"}", login.body.readUtf8())
        val verify = server.takeRequest()
        assertEquals("/api/v1/accounts/verify_credentials", verify.path)
        assertEquals("Bearer opaque-token", verify.getHeader("Authorization"))
        val saved = (sessions.state.value as SessionState.Ready).stored.activeCredential()
        assertEquals("001", saved?.accountId)
        assertEquals("昵称", saved?.displayName)
    }

    @Test
    fun `invalid token response does not save a fake account`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"token":"opaque-token"}"""))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid token"}"""))
        assertTrue(repository.passwordSignIn("name", "wrong") is AuthOutcome.Failure)
        assertTrue((sessions.state.value as SessionState.Ready).stored.accounts.isEmpty())
    }

    @Test
    fun `registration action never creates an authenticated session`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"action":"register","email":"person@example.test"}"""))
        assertEquals(AuthOutcome.RegistrationRequired("person@example.test"), repository.codeSignIn("person@example.test", "123456"))
        assertTrue((sessions.state.value as SessionState.Ready).stored.accounts.isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `delayed verification cannot override a later account change`() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = if (request.path == "/api/auth/login") {
                MockResponse().setBody("""{"token":"late-token"}""")
            } else {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                MockResponse().setBody("""{"id":"late","username":"late"}""")
            }
        }
        val pending = async(kotlinx.coroutines.Dispatchers.Default) { repository.passwordSignIn("name", "pw") }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val lease = (sessions.state.value as SessionState.Ready).lease
            assertTrue(sessions.signIn(SessionCredential("other", "other-token", "other", "Other"), lease))
        } finally {
            release.countDown()
        }
        assertTrue(pending.await() is AuthOutcome.Failure)
        assertEquals("other", (sessions.state.value as SessionState.Ready).stored.activeAccountId)
    }

    @Test
    fun `opaque error body is not echoed to screen or persisted`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("raw credential token-secret"))
        val outcome = repository.passwordSignIn("name", "pw") as AuthOutcome.Failure
        assertFalse(outcome.message.contains("token-secret"))
        assertTrue((sessions.state.value as SessionState.Ready).stored.accounts.isEmpty())
    }
}
