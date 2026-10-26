package top.abdl_space.app.core.network

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import java.nio.charset.StandardCharsets.UTF_8
import org.junit.Assert.*
import org.junit.Test
import top.abdl_space.app.core.network.api.AuthApi
import top.abdl_space.app.core.network.api.MastodonApi
import top.abdl_space.app.core.network.dto.*

class AuthApiHttpTest : ProtocolHttpTest() {
    private fun auth(): AuthApi = retrofit.create(AuthApi::class.java)

    @Test fun sendCodeUsesVerifiedRegisterPurposeAndNoBearer() = runBlocking<Unit> {
        enqueue("{\"unverified_extra\":true}")
        assertTrue(auth().sendCode(SendCodeRequest("offline@example.invalid")).isSuccessful)
        val request = takeRequest()
        assertOfficial(request, authenticated = false)
        assertEquals("POST", request.method)
        assertEquals("/api/auth/send-code", request.path)
        val contentType = checkNotNull(request.getHeader("Content-Type")).toMediaType()
        assertEquals("application", contentType.type)
        assertEquals("json", contentType.subtype)
        assertEquals(UTF_8, contentType.charset())
        assertEquals(json.parseToJsonElement("""{"email":"offline@example.invalid","type":"register"}"""), json.parseToJsonElement(request.body.readUtf8()))
    }

    @Test fun codeLoginReadsTokenNotOAuthAccessToken() = runBlocking<Unit> {
        enqueue("""{"action":"login","token":"offline-code-token","email":"offline@example.invalid","future":42}""")
        val result = auth().loginByCode(LoginByCodeRequest("offline@example.invalid", "012345")).body()!!
        assertEquals(CodeLoginAction.LOGIN, result.action)
        assertEquals("offline-code-token", result.token)
        val request = takeRequest()
        assertOfficial(request, authenticated = false)
        assertEquals("/api/auth/login-by-code", request.path)
        assertEquals("POST", request.method)
        assertEquals(json.parseToJsonElement("""{"email":"offline@example.invalid","code":"012345"}"""), json.parseToJsonElement(request.body.readUtf8()))
    }

    @Test fun registrationBranchDoesNotFabricateToken() = runBlocking<Unit> {
        enqueue("""{"action":"register","email":"new@example.invalid"}""")
        val result = auth().loginByCode(LoginByCodeRequest("new@example.invalid", "000001")).body()!!
        assertEquals(CodeLoginAction.REGISTER, result.action)
        assertNull(result.token)
        takeRequest()
    }

    @Test fun passwordLoginUsesLoginWireKey() = runBlocking<Unit> {
        enqueue("""{"token":"offline-password-token"}""")
        assertEquals("offline-password-token", auth().loginWithPassword(PasswordLoginRequest("offline", "Offline123!&")).body()!!.token)
        val request = takeRequest()
        assertOfficial(request, authenticated = false)
        assertEquals("/api/auth/login", request.path)
        assertEquals("POST", request.method)
        val document = json.parseToJsonElement(request.body.readUtf8()) as JsonObject
        assertEquals(setOf("login", "password"), document.keys)
        assertEquals("offline", document["login"]!!.jsonPrimitive.content)
        assertEquals("Offline123!&", document["password"]!!.jsonPrimitive.content)
    }

    @Test fun registerSendsOnlyVerifiedFirstPartyFields() = runBlocking<Unit> {
        enqueue("""{"token":"offline-register-token"}""")
        assertEquals("offline-register-token", auth().register(RegisterRequest("offline", "new@example.invalid", "Offline123!", "123456")).body()!!.token)
        val request = takeRequest()
        assertOfficial(request, authenticated = false)
        assertEquals("/api/auth/register", request.path)
        assertEquals("POST", request.method)
        assertEquals(json.parseToJsonElement("""{"username":"offline","email":"new@example.invalid","password":"Offline123!","code":"123456"}"""), json.parseToJsonElement(request.body.readUtf8()))
    }

    @Test fun tokenIsExplicitPerRequestAndNeverInheritedByLogin() = runBlocking<Unit> {
        enqueue(ACCOUNT_DOCUMENT)
        val account = retrofit.create(MastodonApi::class.java).verifyCredentials(authorization).body()!!
        assertEquals("18446744073709551616001", account.id)
        assertEquals("Offline Account", account.preferredDisplayName)
        assertEquals(12L, account.followersCount)
        val verification = takeRequest()
        assertOfficial(verification)
        assertEquals("/api/v1/accounts/verify_credentials", verification.path)
        enqueue("", 204)
        auth().sendCode(SendCodeRequest("offline@example.invalid"))
        assertOfficial(takeRequest(), authenticated = false)
    }

    @Test fun malformedLoginAndUnknownActionAreProtocolFailures() = runBlocking<Unit> {
        for (body in listOf("""{"action":"login"}""", """{"action":"unexpected","token":"x"}""", """{"action":"login","access_token":"x"}""", "not-json")) {
            enqueue(body)
            val result = NetworkCall.body(json, AuthenticationContext.LOGIN) { auth().loginByCode(LoginByCodeRequest("a@example.invalid", "000001")) }
            assertEquals(NetworkFailureKind.PROTOCOL, (result as NetworkResult.Failure).error.kind)
            takeRequest()
        }
    }

    @Test fun secretBearingDtosHaveRedactedDiagnostics() {
        val secret = "offline-secret-do-not-log"
        val values = listOf(
            LoginByCodeRequest("mail@example.invalid", secret), PasswordLoginRequest("name", secret),
            RegisterRequest("name", "mail@example.invalid", secret, secret),
            AuthTokenResponse(secret), CodeLoginResponse(CodeLoginAction.LOGIN, secret),
        )
        values.forEach { assertFalse(it.toString().contains(secret)) }
        assertEquals("Bearer $secret", AuthorizationHeader.bearer(secret))
        for (invalid in listOf("", " ", "token\r\nInjected: x", "token token", "tab\tvalue")) {
            assertThrows(IllegalArgumentException::class.java) { AuthorizationHeader.bearer(invalid) }
        }
    }
}
