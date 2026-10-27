package top.abdl_space.app.core.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.abdl_space.app.core.network.api.NotificationApi
import top.abdl_space.app.core.network.dto.NotificationKind

/**
 * Contract tests for /api/v1/notifications, mirroring the verified legacy V1 path: exact path,
 * explicit authorization, optional types[]/exclude_types[] filters, Link max_id continuation, and
 * legacy per-item degradation (unknown types stay inspectable; invalid items are dropped).
 */
class NotificationApiHttpTest : ProtocolHttpTest() {
    private fun api(): NotificationApi = retrofit.create(NotificationApi::class.java)

    /** Literal server document from the verified legacy field set, plus an unknown extension. */
    private fun notification(
        id: String,
        type: String? = "mention",
        withAccount: Boolean = true,
        withStatus: Boolean = true,
        createdAt: String = "2026-10-26T00:00:00.000Z",
    ): String {
        val typeField = type?.let { "\"type\":\"$it\"," } ?: ""
        val accountField = if (withAccount) {
            """"account":{"id":"18446744073709551616001","username":"offline","acct":"offline","display_name":"Offline Account"},"""
        } else ""
        val statusField = if (withStatus) {
            """"status":{"id":"c_789","uri":"https://abdl-space.top/statuses/c_789","created_at":"2026-10-26T00:00:00Z","account":{"id":"18446744073709551616001"},"visibility":"public","content":"<p>hello</p>"},"""
        } else ""
        return """{"id":"$id",$typeField"created_at":"$createdAt",$accountField$statusField"future_extension":{"kept":true}}"""
    }

    @Test fun usesExactPathAuthorizationAndExplicitFilters() = runBlocking<Unit> {
        enqueue("""[${notification("101")}]""")
        api().notifications(authorization, limit = 20, types = listOf("mention", "follow"), excludeTypes = listOf("poll")).body()!!
        val request = takeRequest()
        assertOfficial(request)
        assertEquals("GET", request.method)
        assertEquals("/api/v1/notifications", request.requestUrl!!.encodedPath)
        assertEquals("20", request.requestUrl!!.queryParameter("limit"))
        assertNull(request.requestUrl!!.queryParameter("max_id"))
        assertEquals(listOf("mention", "follow"), request.requestUrl!!.queryParameterValues("types[]"))
        assertEquals(listOf("poll"), request.requestUrl!!.queryParameterValues("exclude_types[]"))
    }

    @Test fun unfilteredPageOmitsFilterParameters() = runBlocking<Unit> {
        enqueue("[]")
        api().notifications(authorization, maxId = "18446744073709551616001", limit = 20).body()!!
        val request = takeRequest()
        assertEquals("/api/v1/notifications", request.requestUrl!!.encodedPath)
        assertEquals("18446744073709551616001", request.requestUrl!!.queryParameter("max_id"))
        assertEquals(0, request.requestUrl!!.queryParameterValues("types[]").size)
        assertEquals(0, request.requestUrl!!.queryParameterValues("exclude_types[]").size)
    }

    @Test fun continuationReadsLinkMaxIdWithoutFollowingTheLink() = runBlocking<Unit> {
        val next = "cursor:{\"p\":\"18446744073709551616001\"}+next/β"
        val link = server.url("/api/v1/notifications").newBuilder().host(ApiConfig.WEB_HOST).addQueryParameter("max_id", next).build()
        server.enqueue(
            okhttp3.mockwebserver.MockResponse().setBody("[${notification("101")}]")
                .addHeader("Link", "<$link>; rel=\"next\"")
                .setHeader("Content-Type", "application/json"),
        )
        val response = api().notifications(authorization, limit = 20)
        assertEquals(next, LinkPagination.from(response).nextMaxId)
        assertEquals("101", response.body()!!.single().id)
        val request = takeRequest()
        assertOfficial(request)
        assertTrue(request.requestUrl!!.encodedPath.endsWith("/api/v1/notifications"))
    }

    @Test fun knownTypesMapAndLargeIntegerIdsStayLiteral() = runBlocking<Unit> {
        enqueue("""[${notification("90071992547409930001", "favourite")}]""")
        val model = api().notifications(authorization).body()!!.single().toModel()!!
        assertEquals("90071992547409930001", model.id)
        assertEquals(NotificationKind.Favourite, model.kind)
        assertEquals("favourite", model.rawType)
        assertEquals("Offline Account", model.account!!.preferredDisplayName)
        assertEquals("hello", model.status!!.content.removePrefix("<p>").removeSuffix("</p>"))
        val request = takeRequest()
        assertOfficial(request)
    }

    @Test fun unknownTypeDegradesToExplicitUnknownAndKeepsRawString() = runBlocking<Unit> {
        enqueue("[" + notification("101", "server.future.kind") + "," + notification("102") + "]")
        val models = api().notifications(authorization).body()!!.map { it.toModel() }
        val unknown = models[0]!!
        assertEquals(NotificationKind.ExplicitUnknown, unknown.kind)
        assertEquals("server.future.kind", unknown.rawType)
        assertEquals("101", unknown.id)
        // A known type beside the unknown one still maps; nothing crashes or is remapped.
        assertEquals(NotificationKind.Mention, models[1]!!.kind)
        takeRequest()
    }

    @Test fun missingTypeDegradesInsteadOfFailingThePage() = runBlocking<Unit> {
        enqueue("""[${notification("101", type = null)}]""")
        val model = api().notifications(authorization).body()!!.single().toModel()!!
        assertEquals(NotificationKind.ExplicitUnknown, model.kind)
        assertEquals("", model.rawType)
        takeRequest()
    }

    @Test fun invalidItemsAreDroppedAndEventBackedTypesMayLackAccounts() = runBlocking<Unit> {
        val documents = listOf(
            // Missing id and missing created_at are dropped individually (legacy removeUnsupportedItems).
            """{"type":"mention","created_at":"2026-10-26T00:00:00.000Z","account":{"id":"42"}}""",
            """{"id":"102","type":"mention","account":{"id":"42"}}""",
            // account is required for mention, but not for the two verified event-backed types.
            notification("103", "mention", withAccount = false),
            notification("104", "moderation_warning", withAccount = false, withStatus = false),
            notification("105", "severed_relationships", withAccount = false, withStatus = false),
            notification("106", "poll"),
        ).joinToString(",", "[", "]")
        enqueue(documents)
        val models = api().notifications(authorization).body()!!.mapNotNull { it.toModel() }
        assertEquals(listOf("104", "105", "106"), models.map { it.id })
        assertEquals(
            listOf(NotificationKind.ModerationWarning, NotificationKind.SeveredRelationships, NotificationKind.Poll),
            models.map { it.kind },
        )
        assertNull(models[0].account)
        takeRequest()
    }

    @Test fun malformedEnvelopesAreNotSuccessfulEmptyLists() = runBlocking<Unit> {
        val documents = listOf(
            """{"notifications":[]}""",
            """[{"id":1.5,"type":"mention","created_at":"2026-10-26T00:00:00.000Z","account":{"id":"42"}}]""",
            """[{"id":true,"type":"mention","created_at":"2026-10-26T00:00:00.000Z","account":{"id":"42"}}]""",
        )
        for (document in documents) {
            enqueue(document)
            val result = NetworkCall.body(json) { api().notifications(authorization) }
            assertEquals(NetworkFailureKind.PROTOCOL, (result as NetworkResult.Failure).error.kind)
            takeRequest()
        }
    }

    @Test fun unauthorizedIsClassifiedNotSwallowed() = runBlocking<Unit> {
        enqueue("""{"error":"The access token is invalid"}""", code = 401)
        val result = NetworkCall.body(json) { api().notifications("Bearer offline-revoked") }
        val failure = (result as NetworkResult.Failure).error
        assertEquals(NetworkFailureKind.UNAUTHORIZED, failure.kind)
        assertEquals(UnauthorizedReason.SESSION_EXPIRED, failure.unauthorizedReason)
        takeRequest()
    }
}
