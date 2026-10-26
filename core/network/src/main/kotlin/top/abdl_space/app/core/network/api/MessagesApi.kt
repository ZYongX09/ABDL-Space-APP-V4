package top.abdl_space.app.core.network.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import top.abdl_space.app.core.network.dto.ChatMessagesResponse
import top.abdl_space.app.core.network.dto.ChatSyncResponse
import top.abdl_space.app.core.network.dto.ConversationsResponse

/** Self-hosted IM /api/messages router, not Mastodon's /api/v1/conversations. */
interface MessagesApi {
    @GET("api/messages/conversations")
    suspend fun conversations(@Header("Authorization") authorization: String): Response<ConversationsResponse>

    @GET("api/messages/sync")
    suspend fun sync(
        @Header("Authorization") authorization: String,
        @Query("after_event_id") afterEventId: String,
        @Query("through_event_id") throughEventId: String? = null,
        @Query("limit") limit: Int = 100,
    ): Response<ChatSyncResponse>

    @GET("api/messages/{uid}")
    suspend fun messages(
        @Header("Authorization") authorization: String,
        @Path("uid") uid: String,
        @Query("before_id") beforeId: String? = null,
        @Query("limit") limit: Int = 50,
    ): Response<ChatMessagesResponse>
}
