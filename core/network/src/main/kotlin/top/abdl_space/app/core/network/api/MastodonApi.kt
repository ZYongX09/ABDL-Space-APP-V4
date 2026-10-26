package top.abdl_space.app.core.network.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import top.abdl_space.app.core.model.Account
import top.abdl_space.app.core.model.SearchResults
import top.abdl_space.app.core.model.Status
import top.abdl_space.app.core.model.StatusContext
import top.abdl_space.app.core.network.dto.CreateStatusRequest
import top.abdl_space.app.core.network.dto.EmptyRequest
import top.abdl_space.app.core.network.dto.ReblogRequest
import top.abdl_space.app.core.network.dto.SearchType

/** Official first-party session routes. All authorization is an explicit full Bearer header. */
interface MastodonApi {
    @GET("api/v1/accounts/verify_credentials")
    suspend fun verifyCredentials(@Header("Authorization") authorization: String): Response<Account>

    @GET("api/v1/timelines/all")
    suspend fun allTimeline(
        @Header("Authorization") authorization: String,
        @Query("max_id") maxId: String? = null,
        @Query("limit") limit: Int? = null,
    ): Response<List<Status>>

    @GET("api/v1/timelines/home")
    suspend fun homeTimeline(
        @Header("Authorization") authorization: String,
        @Query("max_id") maxId: String? = null,
        @Query("min_id") minId: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("since_id") sinceId: String? = null,
    ): Response<List<Status>>

    // Popular is offset-based, unlike the opaque max_id Link cursor on all/home.
    @GET("api/v1/timelines/popular")
    suspend fun popularTimeline(
        @Header("Authorization") authorization: String,
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<List<Status>>

    @POST("api/v1/statuses")
    suspend fun createStatus(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreateStatusRequest,
    ): Response<Status>

    @GET("api/v1/statuses/{id}")
    suspend fun status(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
    ): Response<Status>

    @GET("api/v1/statuses/{id}/context")
    suspend fun statusContext(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
    ): Response<StatusContext>

    /** Complete local Status only. Use NbwInteractionsApi for remote receipts. */
    @POST("api/v1/statuses/{id}/favourite")
    suspend fun favourite(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body request: EmptyRequest = EmptyRequest,
    ): Response<Status>

    @POST("api/v1/statuses/{id}/unfavourite")
    suspend fun unfavourite(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body request: EmptyRequest = EmptyRequest,
    ): Response<Status>

    @POST("api/v1/statuses/{id}/bookmark")
    suspend fun bookmark(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body request: EmptyRequest = EmptyRequest,
    ): Response<Status>

    @POST("api/v1/statuses/{id}/unbookmark")
    suspend fun unbookmark(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body request: EmptyRequest = EmptyRequest,
    ): Response<Status>

    @POST("api/v1/statuses/{id}/reblog")
    suspend fun reblog(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body request: ReblogRequest = ReblogRequest(),
    ): Response<Status>

    @POST("api/v1/statuses/{id}/unreblog")
    suspend fun unreblog(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body request: ReblogRequest = ReblogRequest(),
    ): Response<Status>

    // The old client accepts 204/empty/partial success; do not invent a deleted Status body.
    @DELETE("api/v1/statuses/{id}")
    suspend fun deleteStatus(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
    ): Response<Unit>

    @GET("api/v2/search")
    suspend fun search(
        @Header("Authorization") authorization: String,
        @Query("q") query: String,
        @Query("type") type: SearchType? = null,
        @Query("resolve") resolve: Boolean? = null,
        @Query("max_id") maxId: String? = null,
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<SearchResults>
}
