package top.abdl_space.app.core.network.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import top.abdl_space.app.core.network.dto.NotificationDto

/**
 * Mastodon V1 notifications on the session domain, as used by the legacy client's non-grouped
 * path. Grouped /api/v2 results and push registration are not wired this round. Continuation is
 * the Link rel="next" max_id cursor handled by LinkPagination, never a URL to follow.
 */
interface NotificationApi {
    @GET("api/v1/notifications")
    suspend fun notifications(
        @Header("Authorization") authorization: String,
        @Query("max_id") maxId: String? = null,
        @Query("limit") limit: Int? = null,
        // The legacy client sends types[]/exclude_types[] only when filtering; null = full page.
        @Query("types[]") types: List<String>? = null,
        @Query("exclude_types[]") excludeTypes: List<String>? = null,
    ): Response<List<NotificationDto>>
}
