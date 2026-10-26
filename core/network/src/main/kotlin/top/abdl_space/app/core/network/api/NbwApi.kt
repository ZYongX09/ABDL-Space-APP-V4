package top.abdl_space.app.core.network.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PUT
import top.abdl_space.app.core.model.NbwCapabilities
import top.abdl_space.app.core.model.NbwForumOptions

/** /api/app on the official web/session origin. Never fall back to the retired V1 auth router. */
interface NbwApi {
    @Headers("X-ABDL-NBW-Protocol: 2")
    @GET("api/app/capabilities")
    suspend fun capabilities(@Header("Authorization") authorization: String): Response<NbwCapabilities>

    @Headers("X-ABDL-NBW-Protocol: 2")
    @GET("api/app/nbw/options")
    suspend fun options(@Header("Authorization") authorization: String): Response<NbwForumOptions>

    // Old code does not verify a response schema. Re-fetch capabilities for authoritative consent.
    @Headers("X-ABDL-NBW-Protocol: 2")
    @PUT("api/app/nbw/fallback-consent")
    suspend fun fallbackConsent(
        @Header("Authorization") authorization: String,
        @Body request: FallbackConsentRequest,
    ): Response<Unit>
}

@Serializable
data class FallbackConsentRequest(
    val accepted: Boolean,
    @SerialName("policy_version") val policyVersion: Int = 1,
) {
    init { require(policyVersion == 1) { "Unsupported fallback consent policy" } }
}
