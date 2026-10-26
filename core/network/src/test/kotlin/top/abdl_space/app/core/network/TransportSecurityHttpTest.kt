package top.abdl_space.app.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import top.abdl_space.app.core.network.api.AuthApi
import top.abdl_space.app.core.network.api.MastodonApi
import top.abdl_space.app.core.network.dto.PasswordLoginRequest

/** @Url exists ONLY in the test probe; production interfaces have no arbitrary destination. */
interface OriginProbeApi {
    @GET suspend fun get(@Url url: HttpUrl, @Header("Authorization") authorization: String): Response<Unit>
}

class TransportSecurityHttpTest : ProtocolHttpTest() {
    @Test fun hiltFactoriesUseVerifiedExactProductionOriginsWithoutSendingLiveRequests() {
        val web = HttpModule.webRetrofit(client, json)
        val api = HttpModule.apiRetrofit(client, json)
        assertEquals("https://abdl-space.top/", web.baseUrl().toString())
        assertEquals("https://api.abdl-space.top/", api.baseUrl().toString())
        val transport = web.callFactory() as okhttp3.OkHttpClient
        assertFalse(transport.followRedirects)
        assertFalse(transport.followSslRedirects)
        assertNull(transport.cache)
    }

    @Test fun redirectNeverForwardsBearerEvenToOtherOfficialDomain() = runBlocking<Unit> {
        val original = retrofit.baseUrl()
        for (code in listOf(302, 307, 308)) {
            val location = original.newBuilder().host(ApiConfig.API_HOST).encodedPath("/redirect-leak-probe").build()
            server.enqueue(MockResponse().setResponseCode(code).setHeader("Location", location))
            val response = retrofit.create(MastodonApi::class.java).verifyCredentials(authorization)
            assertEquals(code, response.code())
            assertOfficial(takeRequest())
        }
        // If redirects were followed this would be six requests, or the call would time out.
        assertEquals(3, server.requestCount)
    }

    @Test fun loginRedirectDoesNotReplayPasswordBody() = runBlocking<Unit> {
        val location = retrofit.baseUrl().newBuilder().host("credentials.invalid").encodedPath("/steal").build()
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", location))
        val response = retrofit.create(AuthApi::class.java).loginWithPassword(PasswordLoginRequest("offline", "Offline-only-password"))
        assertEquals(307, response.code())
        val request = takeRequest()
        assertOfficial(request, authenticated = false)
        assertEquals("/api/auth/login", request.path)
        assertEquals(1, server.requestCount)
    }

    @Test fun foreignHostSuffixUserinfoPortOrSchemeIsRejectedBeforeHttp() = runBlocking<Unit> {
        val probe = retrofit.create(OriginProbeApi::class.java)
        val trusted = retrofit.baseUrl()
        val urls = listOf(
            trusted.newBuilder().host("abdl-space.top.attacker.invalid").build(),
            trusted.newBuilder().host("unofficial.invalid").build(),
            trusted.newBuilder().host(ApiConfig.API_HOST).build(),
            trusted.newBuilder().username("lookalike").build(),
            trusted.newBuilder().port(if (trusted.port == 65535) 65534 else trusted.port + 1).build(),
            trusted.newBuilder().scheme("http").build(),
        )
        for (url in urls) {
            val result = NetworkCall.completion(json) { probe.get(url, authorization) }
            assertEquals(NetworkFailureKind.UNTRUSTED_ORIGIN, (result as NetworkResult.Failure).error.kind)
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun urlRewriteCannotBypassTheNetworkOriginCheck() = runBlocking<Unit> {
        val trusted = retrofit.baseUrl()
        val rewritingClient = client.newBuilder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url(trusted.newBuilder().host(ApiConfig.API_HOST).build()).build())
        }.build()
        val api = createRetrofit(rewritingClient, json, trusted).create(MastodonApi::class.java)
        val result = NetworkCall.body(json) { api.verifyCredentials(authorization) }
        assertEquals(NetworkFailureKind.UNTRUSTED_ORIGIN, (result as NetworkResult.Failure).error.kind)
        assertEquals(0, server.requestCount)
    }

    @Test fun separateRequestTokensDoNotBleedAcrossCallsAndNativeHeadersArePresent() = runBlocking<Unit> {
        val api = retrofit.create(MastodonApi::class.java)
        for (token in listOf("Bearer offline-A", "Bearer offline-B")) {
            enqueue(ACCOUNT_DOCUMENT)
            api.verifyCredentials(token)
            val request = takeRequest()
            assertEquals(listOf(token), request.headers.values("Authorization"))
            assertEquals("MastodonAndroid/4.0.0-test", request.getHeader("User-Agent"))
            assertEquals("41", request.getHeader("X-App-Version-Code"))
        }
        // The HTTP stack itself must not contain a logging interceptor.
        val wired = retrofit.callFactory() as okhttp3.OkHttpClient
        assertFalse(wired.interceptors.any { it.javaClass.name.contains("logging", ignoreCase = true) })
        assertFalse(wired.networkInterceptors.any { it.javaClass.name.contains("logging", ignoreCase = true) })
        assertFalse(wired.followRedirects)
        assertFalse(wired.followSslRedirects)
        assertNull(wired.cache)
    }
}
