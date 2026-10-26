package top.abdl_space.app.core.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.abdl_space.app.core.model.ProtocolViolation
import top.abdl_space.app.core.network.api.MessagesApi

class MessagesApiHttpTest : ProtocolHttpTest() {
    private fun api(): MessagesApi = retrofit.create(MessagesApi::class.java)

    @Test fun conversationsUseSelfHostedRouterAndExactLargeStringIds() = runBlocking<Unit> {
        enqueue("""{"conversations":[{"user_id":18446744073709551616001,"username":"peer","avatar":null,
            "last_message":"hello","last_message_at":1790985600,"last_message_id":"90071992547409930001","unread_count":2}]}""")
        val conversation = api().conversations(authorization).body()!!.conversations.single()
        assertEquals("18446744073709551616001", conversation.userId)
        assertEquals("90071992547409930001", conversation.lastMessageId)
        assertEquals("1790985600", conversation.lastMessageAt)
        assertEquals(2, conversation.unreadCount)
        val request = takeRequest()
        assertOfficial(request)
        assertEquals("GET", request.method)
        assertEquals("/api/messages/conversations", request.path)
    }

    @Test fun messagesUseUidBeforeIdAndPreserveSqlUtcTimestamp() = runBlocking<Unit> {
        enqueue("""{"messages":[{"id":90071992547409931234,"sender_id":"42","receiver_id":18446744073709551616001,
            "content":"message","client_msg_id":"offline-client-nonce","created_at":"2026-10-03 01:02:03","read":true}]}""")
        val message = api().messages(authorization, "18446744073709551616001", "90071992547409939999", 30).body()!!.messages.single()
        assertEquals("90071992547409931234", message.id)
        assertEquals("18446744073709551616001", message.receiverId)
        assertEquals("2026-10-03 01:02:03", message.createdAt)
        assertEquals("offline-client-nonce", message.clientMsgId)
        assertTrue(message.read)
        val request = takeRequest()
        assertOfficial(request)
        assertEquals("/api/messages/18446744073709551616001?before_id=90071992547409939999&limit=30", request.path)
    }

    @Test fun syncUsesBoundaryAndServerCursorWithoutInventedConsecutiveIds() = runBlocking<Unit> {
        enqueue("""{"events":[
            {"event_id":90071992547409931234,"type":"message.new","message":{"id":"msg-opaque","sender_id":"42","receiver_id":"43","content":"hi","created_at":1790985600}},
            {"event_id":"90071992547409939888","type":"message.read","peer_id":43,"read_up_to_id":"msg-opaque","reader_id":"42"}
          ],"has_more":true,"next_event_id":90071992547409939999,"future":true}""")
        val result = api().sync(authorization, "90071992547409930000", "99999999999999999999", 100).body()!!
        assertEquals("90071992547409939999", result.continuationAfter("90071992547409930000"))
        assertEquals("90071992547409931234", result.events.first().eventId)
        assertEquals("1790985600", result.events.first().message!!.createdAt)
        val request = takeRequest()
        assertOfficial(request)
        assertEquals("/api/messages/sync?after_event_id=90071992547409930000&through_event_id=99999999999999999999&limit=100", request.path)
    }

    @Test fun syncMissingOrNonAdvancingContinuationFailsInsteadOfAfterPlusOne() = runBlocking<Unit> {
        for (tail in listOf("", ",\"next_event_id\":\"90071992547409930000\"", ",\"next_event_id\":\"9\"")) {
            enqueue("""{"events":[],"has_more":true$tail}""")
            val response = api().sync(authorization, "90071992547409930000").body()!!
            assertThrows(ProtocolViolation::class.java) { response.continuationAfter("90071992547409930000") }
            val request = takeRequest()
            assertNull(request.requestUrl!!.queryParameter("through_event_id"))
        }
        enqueue("""{"events":[],"has_more":false}""")
        assertNull(api().sync(authorization, "90071992547409930000").body()!!.continuationAfter("90071992547409930000"))
        takeRequest()
    }

    @Test fun unknownPersistentEventCannotSilentlyAdvanceCursor() = runBlocking<Unit> {
        enqueue("""{"events":[{"event_id":"102","type":"message.future","future":true}],"has_more":false,"next_event_id":"102"}""")
        val response = api().sync(authorization, "100").body()!!
        assertThrows(ProtocolViolation::class.java) { response.continuationAfter("100") }
        takeRequest()
    }

    @Test fun malformedEnvelopesAndNonIntegralIdsAreNotSuccessfulEmptyLists() = runBlocking<Unit> {
        for (document in listOf("{}", """{"messages":[{"id":1.5,"sender_id":42,"content":"x","created_at":1}]}""", """{"messages":null}""")) {
            enqueue(document)
            val result = NetworkCall.body(json) { api().messages(authorization, "peer") }
            assertEquals(NetworkFailureKind.PROTOCOL, (result as NetworkResult.Failure).error.kind)
            takeRequest()
        }
    }
}
