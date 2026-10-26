package top.abdl_space.app.core.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.abdl_space.app.core.model.NbwRemoteMode
import top.abdl_space.app.core.model.ProtocolViolation
import top.abdl_space.app.core.network.api.FallbackConsentRequest
import top.abdl_space.app.core.network.api.NbwApi
import top.abdl_space.app.core.network.api.NbwInteractionsApi

class NbwApiHttpTest : ProtocolHttpTest() {
    private fun api(): NbwApi = retrofit.create(NbwApi::class.java)

    @Test fun capabilityThreeModesAreSeparateFromIdentityAndGrant() = runBlocking<Unit> {
        for (mode in NbwRemoteMode.entries) {
            val wire = when (mode) { NbwRemoteMode.LOCAL_ONLY -> "local_only"; NbwRemoteMode.READ_ONLY -> "read_only"; NbwRemoteMode.FULL -> "full" }
            enqueue(CAPABILITIES_DOCUMENT.replace("\"full\"", "\"$wire\""))
            val capabilities = api().capabilities(authorization).body()!!
            assertEquals(mode, capabilities.remoteMode)
            assertEquals(mode != NbwRemoteMode.LOCAL_ONLY, capabilities.canRead)
            assertEquals(mode == NbwRemoteMode.FULL, capabilities.canWrite)
            assertTrue(capabilities.hasGrant)
            assertFalse(capabilities.fallback.consented)
            val request = takeRequest()
            assertOfficial(request)
            assertEquals("/api/app/capabilities", request.path)
            assertEquals("GET", request.method)
        }
    }

    @Test fun optionsUseAppRouterAndStableFullIdsNotMenuPositions() = runBlocking<Unit> {
        enqueue(OPTIONS_DOCUMENT)
        val options = api().options(authorization).body()!!
        assertTrue(options.isLegal("18", "0"))
        assertTrue(options.isLegal("18", "37"))
        assertTrue(options.isLegal("36", "301"))
        assertFalse(options.isLegal("36", "0"))
        assertFalse(options.isLegal("36", "302"))
        assertFalse(options.isLegal("36", "1"))
        assertFalse(options.isLegal("999", "301"))
        assertTrue(options.canFallback)
        val request = takeRequest()
        assertOfficial(request)
        assertEquals("GET", request.method)
        assertEquals("/api/app/nbw/options", request.path)
    }

    @Test fun fallbackConsentIsExplicitPutWithPolicyVersionNotSyncSwitch() = runBlocking<Unit> {
        for (accepted in listOf(true, false)) {
            enqueue("", 204)
            val result = api().fallbackConsent(authorization, FallbackConsentRequest(accepted))
            assertTrue(result.isSuccessful)
            val request = takeRequest()
            assertOfficial(request)
            assertEquals("PUT", request.method)
            assertEquals("/api/app/nbw/fallback-consent", request.path)
            assertEquals(json.parseToJsonElement("""{"accepted":$accepted,"policy_version":1}"""), json.parseToJsonElement(request.body.readUtf8()))
        }
    }

    @Test fun futureGrantModeOrSchemaAndMalformedFallbackFailClosed() = runBlocking<Unit> {
        val documents = listOf(
            CAPABILITIES_DOCUMENT.replace("\"full\"", "\"future_mode\""),
            CAPABILITIES_DOCUMENT.replace("\"ready\"", "\"future_grant\""),
            CAPABILITIES_DOCUMENT.replace("\"schema_version\":1", "\"schema_version\":2"),
            CAPABILITIES_DOCUMENT.replace("\"uid\":\"remote-83\",", ""),
            CAPABILITIES_DOCUMENT.replace("\"consented\":false", "\"consented\":null"),
            CAPABILITIES_DOCUMENT.replace("non_expiring", "unknown_lifetime"),
            CAPABILITIES_DOCUMENT.replace("\"fid\":18", "\"fid\":19"),
        )
        for (document in documents) {
            enqueue(document)
            val result = NetworkCall.body(json) { api().capabilities(authorization) }
            assertEquals(NetworkFailureKind.PROTOCOL, (result as NetworkResult.Failure).error.kind)
            takeRequest()
        }
    }

    @Test fun invalidOptionsDoNotSilentlyBecomeEmptyOrPermitFallback() = runBlocking<Unit> {
        for (document in listOf(
            OPTIONS_DOCUMENT.replace("\"fid\":36", "\"fid\":18"),
            OPTIONS_DOCUMENT.replace("\"typeid\":302", "\"typeid\":301"),
            OPTIONS_DOCUMENT.replace("\"typeid\":301", "\"typeid\":0"),
            OPTIONS_DOCUMENT.replace("\"moderator_only\":false", "\"moderator_only\":null"),
            "{}",
        )) {
            enqueue(document)
            val result = NetworkCall.body(json) { api().options(authorization) }
            assertEquals(NetworkFailureKind.PROTOCOL, (result as NetworkResult.Failure).error.kind)
            takeRequest()
        }
    }

    @Test fun remoteActionUsesReceiptRatherThanFabricatedStatus() = runBlocking<Unit> {
        val interactions = retrofit.create(NbwInteractionsApi::class.java)
        for (action in listOf("favourite", "unfavourite", "bookmark", "unbookmark")) {
            val favourite = action.contains("favourite")
            val field = if (favourite) "favourited" else "bookmarked"
            val enabled = !action.startsWith("un")
            enqueue("""{"id":"nbw_123","$field":$enabled}""")
            val receipt = when (action) {
                "favourite" -> interactions.favourite(authorization, "nbw_123")
                "unfavourite" -> interactions.unfavourite(authorization, "nbw_123")
                "bookmark" -> interactions.bookmark(authorization, "nbw_123")
                else -> interactions.unbookmark(authorization, "nbw_123")
            }.body()!!
            receipt.validateFor("nbw_123", favourite)
            assertNull(receipt.favouritesCount)
            assertNull(receipt.bookmarksCount)
            assertThrows(ProtocolViolation::class.java) { receipt.validateFor("nbw_other", favourite) }
            assertThrows(ProtocolViolation::class.java) { receipt.validateFor("nbw_123", !favourite) }
            val request = takeRequest()
            assertOfficial(request)
            assertEquals("POST", request.method)
            assertEquals("/api/v1/statuses/nbw_123/$action", request.path)
            assertEquals("{}", request.body.readUtf8())
        }
    }

    @Test fun negativeRemoteCountsAreProtocolErrorsNotInventedZeros() = runBlocking<Unit> {
        enqueue("""{"id":"nbw_123","favourited":true,"favourites_count":-1}""")
        val result = NetworkCall.body(json) { retrofit.create(NbwInteractionsApi::class.java).favourite(authorization, "nbw_123") }
        assertEquals(NetworkFailureKind.PROTOCOL, (result as NetworkResult.Failure).error.kind)
        takeRequest()
    }
}
