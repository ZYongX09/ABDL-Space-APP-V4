package top.abdl_space.app.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Test
import top.abdl_space.app.core.model.ProtocolViolation
import top.abdl_space.app.core.network.api.MastodonApi

class PaginationHttpTest : ProtocolHttpTest() {
    @Test fun multipleHeadersAndRelationsPreserveEncodedOpaqueCursor() = runBlocking<Unit> {
        val cursor = "base64:+/=?&汉字"
        val next = retrofit.baseUrl().newBuilder().encodedPath("/api/v1/timelines/all").addQueryParameter("max_id", cursor).build()
        val previous = retrofit.baseUrl().newBuilder().encodedPath("/api/v1/timelines/all").addQueryParameter("min_id", "newer_opaque").build()
        server.enqueue(MockResponse().setBody("[]")
            .addHeader("Link", "<$previous>; title=\"contains, comma\"; rel=prev")
            .addHeader("Link", "<$next>; type=\"application/json\"; rel=\"next alternate\""))
        val response = retrofit.create(MastodonApi::class.java).allTimeline(authorization, "old-cursor")
        val cursors = LinkPagination.from(response)
        assertEquals(cursor, cursors.nextMaxId)
        assertEquals("newer_opaque", cursors.previousMinId)
        takeRequest()
    }

    @Test fun unrelatedOrMaliciousLinksCannotSupplyACredentialedContinuation() = runBlocking<Unit> {
        val trusted = retrofit.baseUrl().newBuilder().encodedPath("/api/v1/timelines/all").build()
        val malicious = trusted.newBuilder().host("abdl-space.top.attacker.invalid").addQueryParameter("max_id", "foreign").build()
        val wrongPath = trusted.newBuilder().encodedPath("/api/auth/login").addQueryParameter("max_id", "wrong-path").build()
        server.enqueue(MockResponse().setBody("[$STATUS_DOCUMENT]")
            .addHeader("Link", "<$malicious>; rel=\"next\", <$wrongPath>; rel=\"next\"")
            .addHeader("Link", "not-a-link; rel=next"))
        val response = retrofit.create(MastodonApi::class.java).allTimeline(authorization)
        assertNull(LinkPagination.from(response).nextMaxId)
        assertEquals("c_789", response.body()!!.single().id)
        takeRequest()
        assertEquals(1, server.requestCount)
    }

    @Test fun noLinkDoesNotInferCursorFromLastStatusOrStripOpaqueCharacters() = runBlocking<Unit> {
        enqueue("[$STATUS_DOCUMENT]")
        val response = retrofit.create(MastodonApi::class.java).allTimeline(authorization)
        assertEquals(PageCursors(), LinkPagination.from(response))
        takeRequest()
    }

    @Test fun duplicateConflictingOrNonAdvancingNextIsProtocolFailure() = runBlocking<Unit> {
        val path = "/api/v1/timelines/all"
        val base = retrofit.baseUrl().newBuilder().encodedPath(path).build()
        val first = base.newBuilder().addQueryParameter("max_id", "next-a").build()
        val second = base.newBuilder().addQueryParameter("max_id", "next-b").build()
        val duplicate = base.newBuilder().addQueryParameter("max_id", "next-a").addQueryParameter("max_id", "next-b").build()
        for (link in listOf("<$first>; rel=next, <$second>; rel=next", "<$duplicate>; rel=next", "<$first>; rel=next")) {
            server.enqueue(MockResponse().setBody("[]").setHeader("Link", link))
            val response = retrofit.create(MastodonApi::class.java).allTimeline(authorization, "next-a")
            assertThrows(ProtocolViolation::class.java) { LinkPagination.from(response) }
            takeRequest()
        }
    }

    @Test fun relativeSamePathLinkWorksWithoutFollowingIt() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody("[]").setHeader("Link", "<?max_id=opaque%2Bcursor%2Fnext>; rel=next"))
        val response = retrofit.create(MastodonApi::class.java).allTimeline(authorization)
        assertEquals("opaque+cursor/next", LinkPagination.from(response).nextMaxId)
        takeRequest()
        assertEquals(1, server.requestCount)
    }
}
