package top.abdl_space.app.core.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class WireModelTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = false }

    @Test fun typedIdIsOpaqueStringAndDoesNotInventNumericOrOriginSemantics() {
        for (id in listOf("p_9", "c_parent", "nbw_9", "nbw_comment_9", "acc_nbw_9", "post_nbw_9", "opaque-uuid", "18446744073709551616001")) {
            val decoded = json.decodeFromString<TypedId>(json.encodeToString(id))
            assertEquals(id, decoded.raw)
            assertEquals(json.encodeToString(id), json.encodeToString(decoded))
        }
        assertThrows(IllegalArgumentException::class.java) { TypedId("") }
        assertThrows(IllegalArgumentException::class.java) { TypedId(" ") }
    }

    @Test fun stringOrIntegerCompatibilityNeverRoundsLargeIds() {
        for (document in listOf("""{"id":18446744073709551616001}""", """{"id":"18446744073709551616001"}""")) {
            assertEquals("18446744073709551616001", json.decodeFromString<Account>(document).id)
        }
        for (idToken in listOf("1.5", "1e30", "true", "null", "{}", "[]", "\"\"")) {
            assertThrows(SerializationException::class.java) { json.decodeFromString<Account>("{\"id\":$idToken}") }
        }
    }

    @Test fun accountDefaultsDoNotInventServerIdentityUrlsOrTimes() {
        val account = json.decodeFromString<Account>("""{"id":"opaque","username":"fallback","display_name":"","future":{"x":1}}""")
        assertEquals("fallback", account.preferredDisplayName)
        assertNull(account.avatar)
        assertNull(account.createdAt)
        assertNull(account.url)
        assertTrue(account.fields.isEmpty())
        assertThrows(SerializationException::class.java) { json.decodeFromString<Account>("{}") }
    }

    @Test fun mediaUnknownTypeAndProcessingUrlsRemainSafe() {
        val media = json.decodeFromString<MediaAttachment>("""{"id":"media-opaque","type":"future_type","url":null,"preview_url":null,"meta":{"original":{"width":1920,"height":1080},"future":true}}""")
        assertEquals(MediaType.UNKNOWN, media.type)
        assertNull(media.url)
        assertNull(media.previewUrl)
        assertEquals(1920, media.meta!!.original!!.width)
        assertEquals(1080, media.meta.original.height)
        for ((wire, expected) in listOf("image" to MediaType.IMAGE, "gifv" to MediaType.GIFV, "video" to MediaType.VIDEO, "audio" to MediaType.AUDIO)) {
            assertEquals(expected, json.decodeFromString<MediaAttachment>("""{"id":"m","type":"$wire"}""").type)
        }
    }

    @Test fun hiddenPollCountsRemainUnknownAndChoiceIndicesAreIntegers() {
        val poll = json.decodeFromString<Poll>("""{"id":"poll-opaque","expires_at":null,"multiple":true,"votes_count":5,"voters_count":null,"options":[{"title":"one","votes_count":null},{"title":"two","votes_count":3}],"own_votes":[1]}""")
        assertNull(poll.expiresAt)
        assertNull(poll.votersCount)
        assertNull(poll.options[0].votesCount)
        assertEquals(3L, poll.options[1].votesCount)
        assertEquals(listOf(1), poll.ownVotes)
        assertEquals(5L, poll.votesCount)
    }

    @Test fun requiredStatusFieldsStayRequiredDespiteOptionalExtensionTolerance() {
        val valid = """{"id":"opaque","uri":"https://offline.invalid/s","created_at":"2026-10-03T00:00:00Z","account":{"id":"a"},"visibility":"public","future":{"version":2}}"""
        val status = json.decodeFromString<Status>(valid)
        assertEquals("opaque", status.id)
        assertTrue(status.mediaAttachments.isEmpty())
        assertNull(status.poll)
        assertNull(status.nbwSync)
        assertThrows(SerializationException::class.java) { json.decodeFromString<Status>(valid.replace("\"public\"", "\"future\"")) }
        assertThrows(SerializationException::class.java) { json.decodeFromString<Status>(valid.replace("\"account\":{\"id\":\"a\"},", "")) }
    }
}
