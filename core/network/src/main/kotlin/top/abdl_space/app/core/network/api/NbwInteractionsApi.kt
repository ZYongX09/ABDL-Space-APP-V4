package top.abdl_space.app.core.network.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import top.abdl_space.app.core.model.ProtocolViolation
import top.abdl_space.app.core.model.StringOrIntegerSerializer
import top.abdl_space.app.core.network.dto.EmptyRequest

/** NBW root-thread actions return receipts, not complete renderable Status objects. */
interface NbwInteractionsApi {
    @POST("api/v1/statuses/{id}/favourite")
    suspend fun favourite(@Header("Authorization") authorization: String, @Path("id") id: String, @Body request: EmptyRequest = EmptyRequest): Response<NbwInteractionReceipt>

    @POST("api/v1/statuses/{id}/unfavourite")
    suspend fun unfavourite(@Header("Authorization") authorization: String, @Path("id") id: String, @Body request: EmptyRequest = EmptyRequest): Response<NbwInteractionReceipt>

    @POST("api/v1/statuses/{id}/bookmark")
    suspend fun bookmark(@Header("Authorization") authorization: String, @Path("id") id: String, @Body request: EmptyRequest = EmptyRequest): Response<NbwInteractionReceipt>

    @POST("api/v1/statuses/{id}/unbookmark")
    suspend fun unbookmark(@Header("Authorization") authorization: String, @Path("id") id: String, @Body request: EmptyRequest = EmptyRequest): Response<NbwInteractionReceipt>
}

@Serializable
data class NbwInteractionReceipt(
    @Serializable(with = StringOrIntegerSerializer::class) val id: String,
    val favourited: Boolean? = null,
    val bookmarked: Boolean? = null,
    @SerialName("favourites_count") val favouritesCount: Long? = null,
    @SerialName("bookmarks_count") val bookmarksCount: Long? = null,
) {
    init {
        if (id.isBlank() || favouritesCount != null && favouritesCount < 0 || bookmarksCount != null && bookmarksCount < 0) {
            throw ProtocolViolation("Invalid NBW interaction receipt")
        }
    }

    fun validateFor(requestedId: String, favouriteAction: Boolean) {
        if (id != requestedId || (if (favouriteAction) favourited == null else bookmarked == null)) {
            throw ProtocolViolation("NBW interaction receipt does not match request")
        }
    }
}
