package top.abdl_space.app.core.network

import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Before
import retrofit2.Retrofit
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** Real TLS/HTTP loopback harness. No live DNS, private-state reflection, or fabricated transports. */
abstract class ProtocolHttpTest {
    protected lateinit var server: MockWebServer
    protected lateinit var json: Json
    protected lateinit var client: OkHttpClient
    protected lateinit var retrofit: Retrofit
    protected val authorization = "Bearer offline-test-session"

    @Before
    fun startServer() {
        val certificate = HeldCertificate.Builder()
            .commonName("offline protocol tests")
            .addSubjectAlternativeName(ApiConfig.WEB_HOST)
            .addSubjectAlternativeName(ApiConfig.API_HOST)
            .build()
        val serverTrust = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTrust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer()
        server.protocols = listOf(okhttp3.Protocol.HTTP_1_1)
        server.useHttps(serverTrust.sslSocketFactory(), false)
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        json = HttpModule.json()
        client = HttpModule.okHttp(NativeClientIdentity("4.0.0-test", 41)).newBuilder()
            .proxy(Proxy.NO_PROXY)
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .sslSocketFactory(clientTrust.sslSocketFactory(), clientTrust.trustManager)
            .dns(Dns { host ->
                if (host !in setOf(ApiConfig.WEB_HOST, ApiConfig.API_HOST)) throw UnknownHostException("Live DNS disabled in tests")
                listOf(InetAddress.getByName("127.0.0.1"))
            })
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
        // Preserve the REAL configured hostname through DNS, SNI, TLS validation, and Host.
        retrofit = createRetrofit(client, json, server.url("/").newBuilder().host(ApiConfig.WEB_HOST).build())
    }

    @After
    fun stopServer() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        server.shutdown()
    }

    protected fun enqueue(body: String, code: Int = 200): MockResponse =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body).also(server::enqueue)

    protected fun takeRequest(): RecordedRequest =
        checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "Expected an actual loopback HTTP request" }

    protected fun assertOfficial(request: RecordedRequest, authenticated: Boolean = true) {
        org.junit.Assert.assertEquals("${ApiConfig.WEB_HOST}:${server.port}", request.getHeader("Host"))
        org.junit.Assert.assertEquals(if (authenticated) authorization else null, request.getHeader("Authorization"))
        org.junit.Assert.assertEquals("2", request.getHeader("X-ABDL-NBW-Protocol"))
        org.junit.Assert.assertEquals("MastodonAndroid/4.0.0-test", request.getHeader("User-Agent"))
        org.junit.Assert.assertEquals("41", request.getHeader("X-App-Version-Code"))
        org.junit.Assert.assertTrue(request.getHeader("Cache-Control").orEmpty().contains("no-store"))
    }
}

// Literal server documents derived from legacy Account/Status/NBW fields. Assertions below
// inspect real Retrofit deserialization, not pre-created models or self-comparing fixtures.
internal const val ACCOUNT_DOCUMENT = """{
  "id":"18446744073709551616001","username":"offline","acct":"offline",
  "display_name":"Offline Account","note":"<p>bio</p>","avatar":"https://assets.invalid/a.png",
  "followers_count":12,"following_count":3,"statuses_count":7,
  "source":{"privacy":"private","note":"bio"},"fields":[],"emojis":[]
}"""

internal const val STATUS_DOCUMENT = """{
  "id":"c_789","uri":"https://abdl-space.top/statuses/c_789","created_at":"2026-10-03T00:00:00Z",
  "account":{"id":"42","username":"offline","acct":"offline","display_name":"Offline"},
  "visibility":"public","content":"<p>offline &amp; typed</p>","in_reply_to_id":"p_parent-opaque",
  "media_attachments":[],"mentions":[],"tags":[],"emojis":[],
  "favourites_count":6,"bookmarks_count":2,"favourited":true,"bookmarked":false,
  "nbw_sync":{"state":"pending","reason":"queued"}
}"""

internal const val CAPABILITIES_DOCUMENT = """{
  "schema_version":1,"remote_mode":"full","reason":"available","policy_revision":"r-1",
  "binding":{"state":"bound","uid":"remote-83","username":"remote"},
  "authorization":{"state":"ready","lifetime_policy":"non_expiring"},
  "fallback":{"fid":18,"policy_version":1,"consented":false}
}"""

internal const val OPTIONS_DOCUMENT = """{
  "schema_version":1,"config_revision":"catalog-1",
  "forums":[
    {"fid":18,"name":"闲聊","required":false,"types":[{"typeid":37,"name":"日常","moderator_only":false}]},
    {"fid":36,"name":"必选分类","required":true,"types":[
      {"typeid":301,"name":"分享","moderator_only":false},
      {"typeid":302,"name":"管理","moderator_only":true}
    ]}
  ],"fallback":{"fid":18,"policy_version":1}
}"""
