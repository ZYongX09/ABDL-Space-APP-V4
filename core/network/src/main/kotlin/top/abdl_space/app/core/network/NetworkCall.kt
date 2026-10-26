package top.abdl_space.app.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale

/** Context is explicit: an unsuccessful password/code login must not log out an existing session. */
enum class AuthenticationContext { LOGIN, SESSION, NONE }
enum class UnauthorizedReason { CREDENTIALS_REJECTED, SESSION_EXPIRED, UNCONFIRMED }
enum class NetworkFailureKind {
    NETWORK, TIMEOUT, UNTRUSTED_ORIGIN, UNAUTHORIZED, FORBIDDEN, NBW_BINDING_REQUIRED,
    NOT_FOUND, CONFLICT, VALIDATION, RATE_LIMITED, SERVER, HTTP, PROTOCOL,
}

@Serializable
data class ApiErrorBody(val error: String? = null, val code: String? = null)

data class NetworkFailure(
    val kind: NetworkFailureKind,
    val httpStatus: Int? = null,
    val unauthorizedReason: UnauthorizedReason? = null,
    val serverCode: String? = null,
    val serverMessage: String? = null,
    // Retain either delta-seconds or HTTP-date, rather than incorrectly parsing all as seconds.
    val retryAfter: String? = null,
) {
    /** Diagnostics must never accidentally dump untrusted server text or an echoed credential. */
    override fun toString(): String = "NetworkFailure(kind=$kind, httpStatus=$httpStatus, unauthorizedReason=$unauthorizedReason)"
}

sealed interface NetworkResult<out T> {
    data class Success<T>(val value: T) : NetworkResult<T>
    data class Failure(val error: NetworkFailure) : NetworkResult<Nothing>
}

/**
 * Maps real Retrofit responses without turning HTTP errors into empty successful DTOs.
 * It never retries writes, mutates sessions, launches OAuth, logs, or swallows cancellation.
 */
object NetworkCall {
    suspend fun <T : Any> body(
        json: Json,
        authentication: AuthenticationContext = AuthenticationContext.SESSION,
        request: suspend () -> Response<T>,
    ): NetworkResult<T> = execute(json, authentication, request) { response ->
        response.body() ?: throw EmptyApiBodyException()
    }

    suspend fun completion(
        json: Json,
        authentication: AuthenticationContext = AuthenticationContext.SESSION,
        request: suspend () -> Response<Unit>,
    ): NetworkResult<Unit> = execute(json, authentication, request) { _ ->
        // Completion endpoints consume only HTTP success, not an unverified response schema.
    }

    /** Keeps headers such as Link for callers that need a page or degraded-context metadata. */
    suspend fun <T : Any> response(
        json: Json,
        authentication: AuthenticationContext = AuthenticationContext.SESSION,
        request: suspend () -> Response<T>,
    ): NetworkResult<Response<T>> = execute(json, authentication, request) { response ->
        if (response.body() == null) throw EmptyApiBodyException()
        response
    }

    private suspend fun <T, R> execute(
        json: Json,
        authentication: AuthenticationContext,
        request: suspend () -> Response<T>,
        success: (Response<T>) -> R,
    ): NetworkResult<R> = try {
        val response = request()
        if (response.isSuccessful) NetworkResult.Success(success(response))
        else NetworkResult.Failure(classifyHttp(response, json, authentication))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SerializationException) {
        NetworkResult.Failure(NetworkFailure(NetworkFailureKind.PROTOCOL))
    } catch (_: EmptyApiBodyException) {
        NetworkResult.Failure(NetworkFailure(NetworkFailureKind.PROTOCOL))
    } catch (_: UntrustedApiOriginException) {
        NetworkResult.Failure(NetworkFailure(NetworkFailureKind.UNTRUSTED_ORIGIN))
    } catch (_: SocketTimeoutException) {
        NetworkResult.Failure(NetworkFailure(NetworkFailureKind.TIMEOUT))
    } catch (_: IOException) {
        NetworkResult.Failure(NetworkFailure(NetworkFailureKind.NETWORK))
    }

    private fun classifyHttp(response: Response<*>, json: Json, authentication: AuthenticationContext): NetworkFailure {
        val error = response.errorBody()?.use { body ->
            try {
                // Bound error reads; malformed JSON/HTML must not erase the actual HTTP status.
                val source = body.source()
                source.request(MAX_ERROR_BYTES + 1)
                if (source.buffer.size > MAX_ERROR_BYTES) null
                else json.decodeFromString<ApiErrorBody>(source.readUtf8())
            } catch (_: SerializationException) {
                null
            } catch (_: IOException) {
                null
            }
        }
        val status = response.code()
        val kind = when (status) {
            401 -> NetworkFailureKind.UNAUTHORIZED
            403 -> if (error?.code == "NBW_BINDING_REQUIRED") NetworkFailureKind.NBW_BINDING_REQUIRED else NetworkFailureKind.FORBIDDEN
            404 -> NetworkFailureKind.NOT_FOUND
            409 -> NetworkFailureKind.CONFLICT
            422 -> NetworkFailureKind.VALIDATION
            429 -> NetworkFailureKind.RATE_LIMITED
            in 500..599 -> NetworkFailureKind.SERVER
            else -> NetworkFailureKind.HTTP
        }
        val unauthorized = if (status != 401) null else when {
            authentication == AuthenticationContext.LOGIN -> UnauthorizedReason.CREDENTIALS_REJECTED
            authentication == AuthenticationContext.SESSION && isVerifiedSessionError(error?.error) -> UnauthorizedReason.SESSION_EXPIRED
            else -> UnauthorizedReason.UNCONFIRMED
        }
        return NetworkFailure(kind, status, unauthorized, error?.code, error?.error, response.headers()["Retry-After"])
    }

    /** Only these four legacy code checks are verified; unknown 401s are not automatic logout. */
    private fun isVerifiedSessionError(message: String?): Boolean {
        val lower = message?.lowercase(Locale.ROOT) ?: return false
        return listOf("access token is invalid", "authentication required", "session expired", "invalid token").any(lower::contains)
    }

    private const val MAX_ERROR_BYTES = 64L * 1024
}

private class EmptyApiBodyException : RuntimeException()
