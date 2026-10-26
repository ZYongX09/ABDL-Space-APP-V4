package top.abdl_space.app.core.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.abdl_space.app.core.model.StatusVisibility
import top.abdl_space.app.core.network.api.MastodonApi
import top.abdl_space.app.core.network.dto.*

class MastodonApiHttpTest : ProtocolHttpTest() {
    private fun api(): MastodonApi = retrofit.create(MastodonApi::class.java)

    @Test fun verifyCredentialsUsesWebSessionNotBusinessHost() = runBlocking<Unit> {
        enqueue(ACCOUNT_DOCUMENT)
        val result = api().verifyCredentials(authorization).body()!!
        assertEquals("18446744073709551616001", result.id)
        assertEquals(StatusVisibility.PRIVATE, result.source!!.privacy)
        val request = takeRequest()
        assertOfficial(request)
        assertEquals("GET", request.method)
        assertEquals("/api/v1/accounts/verify_credentials", request.path)
        assertEquals(0L, request.bodySize)
    }

    @Test fun allTimelineUsesEncodedOpaqueMaxIdAndServerLinkCursor() = runBlocking<Unit> {
        val next = "cursor:{\"p\":\"18446744073709551616001\"}+next/β"
        val link = server.url("/api/v1/timelines/all").newBuilder().host(ApiConfig.WEB_HOST).addQueryParameter("max_id", next).build()
        server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("[$STATUS_DOCUMENT]").addHeader("Link", "<$link>; rel=\"next\""))
        val response = api().allTimeline(authorization, "cursor:+/汉", 25)
        assertEquals("c_789", response.body()!!.single().id)
        assertEquals(next, LinkPagination.from(response).nextMaxId)
        val request = takeRequest()
        assertOfficial(request)
        assertEquals("/api/v1/timelines/all", request.requestUrl!!.encodedPath)
        assertEquals("cursor:+/汉", request.requestUrl!!.queryParameter("max_id"))
        assertEquals("25", request.requestUrl!!.queryParameter("limit"))
        assertNull(request.requestUrl!!.queryParameter("offset"))
    }

    @Test fun homeAndPopularHaveDifferentPaginationContracts() = runBlocking<Unit> {
        enqueue("[]")
        api().homeTimeline(authorization, maxId = "opaque-max", minId = "opaque-min", sinceId = "opaque-since", limit = 20)
        val home = takeRequest()
        assertOfficial(home)
        assertEquals("/api/v1/timelines/home", home.requestUrl!!.encodedPath)
        assertEquals("opaque-max", home.requestUrl!!.queryParameter("max_id"))
        assertEquals("opaque-min", home.requestUrl!!.queryParameter("min_id"))
        assertEquals("opaque-since", home.requestUrl!!.queryParameter("since_id"))
        enqueue("[]")
        api().popularTimeline(authorization, offset = 40, limit = 20)
        val popular = takeRequest()
        assertOfficial(popular)
        assertEquals("/api/v1/timelines/popular?offset=40&limit=20", popular.path)
        assertNull(popular.requestUrl!!.queryParameter("max_id"))
        enqueue("[]")
        api().allTimeline(authorization)
        assertEquals("/api/v1/timelines/all", takeRequest().path)
    }

    @Test fun createStatusSerializesTypedParentMediaAndPollAndIdempotencyHeader() = runBlocking<Unit> {
        enqueue(STATUS_DOCUMENT)
        val response = api().createStatus(
            authorization, "offline-idempotency-key",
            CreateStatusRequest(
                status = "评论 & \"text\"", inReplyToId = "nbw_comment_18446744073709551616001",
                mediaIds = listOf("media_opaque"), mediaAttributes = listOf(MediaAttributeRequest("media_opaque", "caption")),
                poll = CreatePollRequest(listOf("一", "二"), 3600, multiple = true, hideTotals = true),
                visibility = StatusVisibility.UNLISTED, sensitive = true, mentalCrisis = true,
                spoilerText = "CW", language = "zh",
            ),
        )
        assertEquals("pending", response.body()!!.nbwSync!!.state)
        val request = takeRequest()
        assertOfficial(request)
        assertEquals("POST", request.method)
        assertEquals("/api/v1/statuses", request.path)
        assertEquals("offline-idempotency-key", request.getHeader("Idempotency-Key"))
        assertEquals(json.parseToJsonElement("""{
            "status":"评论 & \"text\"","in_reply_to_id":"nbw_comment_18446744073709551616001",
            "media_ids":["media_opaque"],"media_attributes":[{"id":"media_opaque","description":"caption"}],
            "poll":{"options":["一","二"],"expires_in":3600,"multiple":true,"hide_totals":true},
            "visibility":"unlisted","sensitive":true,"mental_crisis":true,"spoiler_text":"CW","language":"zh"
        }"""), json.parseToJsonElement(request.body.readUtf8()))
    }

    @Test fun defaultCreateDoesNotInventNbwOrGeoOrSchedulingFields() = runBlocking<Unit> {
        enqueue(STATUS_DOCUMENT)
        api().createStatus(authorization, "offline-key", CreateStatusRequest(status = "ordinary", visibility = StatusVisibility.PUBLIC))
        val body = json.parseToJsonElement(takeRequest().body.readUtf8())
        assertEquals(json.parseToJsonElement("""{"status":"ordinary","visibility":"public","sensitive":false,"mental_crisis":false,"spoiler_text":""}"""), body)
    }

    @Test fun statusDetailEscapesPathIdInsteadOfNumericConversionOrUrlInjection() = runBlocking<Unit> {
        enqueue(STATUS_DOCUMENT)
        val status = api().status(authorization, "opaque/part ?#+").body()!!
        assertEquals("<p>offline &amp; typed</p>", status.content)
        val request = takeRequest()
        assertOfficial(request)
        assertEquals("/api/v1/statuses/opaque%2Fpart%20%3F%23+", request.path)
    }

    @Test fun actionEndpointsSendJsonObjectsAndDecodeCompleteLocalStatuses() = runBlocking<Unit> {
        for (action in listOf("favourite", "unfavourite", "bookmark", "unbookmark", "reblog", "unreblog")) {
            enqueue(STATUS_DOCUMENT)
            val response = when (action) {
                "favourite" -> api().favourite(authorization, "p_opaque")
                "unfavourite" -> api().unfavourite(authorization, "p_opaque")
                "bookmark" -> api().bookmark(authorization, "p_opaque")
                "unbookmark" -> api().unbookmark(authorization, "p_opaque")
                "reblog" -> api().reblog(authorization, "p_opaque", ReblogRequest(StatusVisibility.PRIVATE))
                else -> api().unreblog(authorization, "p_opaque")
            }
            assertEquals("c_789", response.body()!!.id)
            val request = takeRequest()
            assertOfficial(request)
            assertEquals("POST", request.method)
            assertEquals("/api/v1/statuses/p_opaque/$action", request.path)
            assertEquals(json.parseToJsonElement(if (action == "reblog") """{"visibility":"private"}""" else "{}"), json.parseToJsonElement(request.body.readUtf8()))
        }
    }

    @Test fun deleteAcceptsEmptyOrPartialSuccessfulResponseOnly() = runBlocking<Unit> {
        for ((code, document) in listOf(204 to "", 200 to "{}", 200 to """{"id":"p_deleted"}""")) {
            enqueue(document, code)
            val result = NetworkCall.completion(json) { api().deleteStatus(authorization, "p_deleted") }
            assertEquals(NetworkResult.Success(Unit), result)
            val request = takeRequest()
            assertOfficial(request)
            assertEquals("DELETE", request.method)
            assertEquals("/api/v1/statuses/p_deleted", request.path)
        }
        enqueue("""{"error":"forbidden"}""", 403)
        assertEquals(NetworkFailureKind.FORBIDDEN, (NetworkCall.completion(json) { api().deleteStatus(authorization, "p_deleted") } as NetworkResult.Failure).error.kind)
        takeRequest()
    }

    @Test fun searchUsesV2AndLowercaseTypedFilter() = runBlocking<Unit> {
        enqueue("""{"accounts":[$ACCOUNT_DOCUMENT],"statuses":[$STATUS_DOCUMENT],"hashtags":[{"name":"测试","url":"https://abdl-space.top/tags/test"}]}""")
        val result = api().search(authorization, "汉字 & +?", SearchType.STATUSES, true, "opaque-max", 20, 10).body()!!
        assertEquals("c_789", result.statuses.single().id)
        assertEquals("测试", result.hashtags.single().name)
        val request = takeRequest()
        assertOfficial(request)
        assertEquals("GET", request.method)
        assertEquals("/api/v2/search", request.requestUrl!!.encodedPath)
        assertEquals("汉字 & +?", request.requestUrl!!.queryParameter("q"))
        assertEquals("statuses", request.requestUrl!!.queryParameter("type"))
        assertEquals("true", request.requestUrl!!.queryParameter("resolve"))
        assertEquals("opaque-max", request.requestUrl!!.queryParameter("max_id"))
        assertEquals("20", request.requestUrl!!.queryParameter("offset"))
        assertEquals("10", request.requestUrl!!.queryParameter("limit"))
    }

    @Test fun contextDegradationUsesBodyOrHeaderButDoesNotHideHttpErrors() = runBlocking<Unit> {
        for ((bodyFlag, header) in listOf(false to "NBW", true to "other", false to "other")) {
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("""{"ancestors":[],"descendants":[],"nbw_unavailable":$bodyFlag}""").setHeader("X-ABDL-Context-Degraded", header))
            val response = api().statusContext(authorization, "p_parent")
            assertEquals(bodyFlag || header.equals("nbw", true), response.contextWithDegradation().nbwUnavailable)
            assertEquals("/api/v1/statuses/p_parent/context", takeRequest().path)
        }
        enqueue("""{"error":"upstream unavailable"}""", 503)
        val result = NetworkCall.body(json) { api().statusContext(authorization, "p_parent") }
        assertEquals(NetworkFailureKind.SERVER, (result as NetworkResult.Failure).error.kind)
        takeRequest()
    }

    @Test fun unknownOptionalExtensionsDoNotDiscardValidStatusOrHiddenPollCounts() = runBlocking<Unit> {
        val document = STATUS_DOCUMENT.trim().removeSuffix("}") + """,
            "friend_card":{"schema_version":999,"mode":false},
            "advertisement":{"future":true},"unknown_extension":{"x":[1,2]},
            "poll":{"id":"poll_future","options":[{"title":"hidden","votes_count":null}],"voters_count":null},
            "reblog":null
        }"""
        enqueue(document)
        val result = api().status(authorization, "c_789").body()!!
        assertEquals("c_789", result.id)
        val poll = checkNotNull(result.poll)
        assertNull(poll.options.single().votesCount)
        assertNull(poll.votersCount)
        takeRequest()
    }

    @Test fun missingRequiredStatusNeverBecomesAnEmptyRenderableObject() = runBlocking<Unit> {
        for (document in listOf("{}", """{"id":"p_missing","visibility":"public"}""", STATUS_DOCUMENT.replace("\"public\"", "\"future_visibility\""))) {
            enqueue(document)
            val result = NetworkCall.body(json) { api().status(authorization, "opaque") }
            assertEquals(NetworkFailureKind.PROTOCOL, (result as NetworkResult.Failure).error.kind)
            takeRequest()
        }
    }
}
