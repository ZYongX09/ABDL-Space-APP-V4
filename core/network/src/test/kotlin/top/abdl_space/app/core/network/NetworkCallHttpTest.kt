package top.abdl_space.app.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Test
import top.abdl_space.app.core.network.api.AuthApi
import top.abdl_space.app.core.network.api.MastodonApi
import top.abdl_space.app.core.network.dto.PasswordLoginRequest
import java.io.IOException
import java.net.SocketTimeoutException

class NetworkCallHttpTest : ProtocolHttpTest() {
    private fun api(): MastodonApi = retrofit.create(MastodonApi::class.java)

    @Test fun httpErrorsRetainStatusAndTypedClassification() = runBlocking<Unit> {
        val cases = listOf(
            400 to NetworkFailureKind.HTTP, 403 to NetworkFailureKind.FORBIDDEN,
            404 to NetworkFailureKind.NOT_FOUND, 409 to NetworkFailureKind.CONFLICT,
            422 to NetworkFailureKind.VALIDATION, 429 to NetworkFailureKind.RATE_LIMITED,
            500 to NetworkFailureKind.SERVER, 503 to NetworkFailureKind.SERVER,
        )
        for ((status, kind) in cases) {
            enqueue("""{"error":"offline-error-$status","code":"test_code","future":true}""", status)
            val failure = (NetworkCall.body(json) { api().verifyCredentials(authorization) } as NetworkResult.Failure).error
            assertEquals(kind, failure.kind)
            assertEquals(status, failure.httpStatus)
            assertEquals("test_code", failure.serverCode)
            assertEquals("offline-error-$status", failure.serverMessage)
            assertNull(failure.unauthorizedReason)
            takeRequest()
        }
    }

    @Test fun unauthorizedHasThreeEvidenceBasedBranchesNotBlanketLogout() = runBlocking<Unit> {
        enqueue("""{"error":"invalid password"}""", 401)
        val login = NetworkCall.body(json, AuthenticationContext.LOGIN) {
            retrofit.create(AuthApi::class.java).loginWithPassword(PasswordLoginRequest("offline", "wrong-offline"))
        }
        assertEquals(UnauthorizedReason.CREDENTIALS_REJECTED, (login as NetworkResult.Failure).error.unauthorizedReason)
        takeRequest()
        for (message in listOf("The access token is invalid", "Authentication required", "SESSION EXPIRED", "Invalid token")) {
            enqueue("""{"error":"$message"}""", 401)
            val result = NetworkCall.body(json) { api().verifyCredentials(authorization) }
            assertEquals(UnauthorizedReason.SESSION_EXPIRED, (result as NetworkResult.Failure).error.unauthorizedReason)
            takeRequest()
        }
        for (document in listOf("""{"error":"remote permission denied"}""", "not-json", "")) {
            enqueue(document, 401)
            val result = NetworkCall.body(json) { api().verifyCredentials(authorization) }
            val failure = (result as NetworkResult.Failure).error
            assertEquals(NetworkFailureKind.UNAUTHORIZED, failure.kind)
            assertEquals(401, failure.httpStatus)
            assertEquals(UnauthorizedReason.UNCONFIRMED, failure.unauthorizedReason)
            takeRequest()
        }
    }

    @Test fun onlyExact403BindingCodeIsBindingRequired() = runBlocking<Unit> {
        for ((status, document, expected) in listOf(
            Triple(403, """{"error":"binding needed","code":"NBW_BINDING_REQUIRED"}""", NetworkFailureKind.NBW_BINDING_REQUIRED),
            Triple(403, """{"error":"NBW_BINDING_REQUIRED"}""", NetworkFailureKind.FORBIDDEN),
            Triple(403, """{"code":"nbw_binding_required"}""", NetworkFailureKind.FORBIDDEN),
            Triple(409, """{"code":"NBW_BINDING_REQUIRED"}""", NetworkFailureKind.CONFLICT),
            Triple(401, """{"code":"NBW_BINDING_REQUIRED"}""", NetworkFailureKind.UNAUTHORIZED),
        )) {
            enqueue(document, status)
            val failure = (NetworkCall.body(json) { api().verifyCredentials(authorization) } as NetworkResult.Failure).error
            assertEquals(expected, failure.kind)
            takeRequest()
        }
    }

    @Test fun malformedOrOversizedErrorBodyCannotEraseHttpStatus() = runBlocking<Unit> {
        for (document in listOf("<html>gateway failed</html>", """{"error":{"unexpected":true}}""", "x".repeat(70 * 1024))) {
            enqueue(document, 503)
            val failure = (NetworkCall.body(json) { api().verifyCredentials(authorization) } as NetworkResult.Failure).error
            assertEquals(NetworkFailureKind.SERVER, failure.kind)
            assertEquals(503, failure.httpStatus)
            assertNull(failure.serverMessage)
            takeRequest()
        }
    }

    @Test fun rateLimitPreservesBothRetryAfterFormatsWithoutRetrying() = runBlocking<Unit> {
        for (retry in listOf("120", "Wed, 28 Oct 2026 01:02:03 GMT")) {
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", retry).setBody("""{"error":"slow down"}"""))
            val result = NetworkCall.body(json) { api().verifyCredentials(authorization) }
            assertEquals(retry, (result as NetworkResult.Failure).error.retryAfter)
            takeRequest()
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun emptyOrInvalidSuccessBodiesAreNotSuccessfulAccountResults() = runBlocking<Unit> {
        for ((status, document) in listOf(204 to "", 200 to "", 200 to "null", 200 to "{not-json", 200 to "{}")) {
            enqueue(document, status)
            val failure = (NetworkCall.body(json) { api().verifyCredentials(authorization) } as NetworkResult.Failure).error
            assertEquals(NetworkFailureKind.PROTOCOL, failure.kind)
            takeRequest()
        }
    }

    @Test fun cancellationIsRethrownAndTransportErrorsRemainDistinct() = runBlocking<Unit> {
        // Only exception mapping is isolated; all HTTP classification tests above use real servers.
        val cancelled = CancellationException("offline cancellation")
        try {
            NetworkCall.body<top.abdl_space.app.core.model.Account>(json) { throw cancelled }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
        val timeout = NetworkCall.body<top.abdl_space.app.core.model.Account>(json) { throw SocketTimeoutException("offline") }
        assertEquals(NetworkFailureKind.TIMEOUT, (timeout as NetworkResult.Failure).error.kind)
        val network = NetworkCall.body<top.abdl_space.app.core.model.Account>(json) { throw IOException("offline") }
        assertEquals(NetworkFailureKind.NETWORK, (network as NetworkResult.Failure).error.kind)
    }

    @Test fun failureDiagnosticsNeverEchoUntrustedServerText() = runBlocking<Unit> {
        enqueue("""{"error":"offline-secret","code":"offline-secret"}""", 500)
        val result = NetworkCall.body(json) { api().verifyCredentials(authorization) }
        assertFalse(result.toString().contains("offline-secret"))
        takeRequest()
    }
}
