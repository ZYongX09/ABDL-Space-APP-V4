package top.abdl_space.app.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import top.abdl_space.app.core.model.StatusVisibility

/** Verified subset of CreateStatus.Request. Scheduling/edits and optional feature extensions are separate work. */
@Serializable
data class CreateStatusRequest(
    val status: String? = null,
    @SerialName("media_ids") val mediaIds: List<String>? = null,
    @SerialName("media_attributes") val mediaAttributes: List<MediaAttributeRequest>? = null,
    val poll: CreatePollRequest? = null,
    @SerialName("in_reply_to_id") val inReplyToId: String? = null,
    val sensitive: Boolean = false,
    @SerialName("mental_crisis") val mentalCrisis: Boolean = false,
    @SerialName("spoiler_text") val spoilerText: String = "",
    val visibility: StatusVisibility,
    val language: String? = null,
)

@Serializable
data class CreatePollRequest(
    val options: List<String>,
    @SerialName("expires_in") val expiresIn: Int,
    val multiple: Boolean = false,
    @SerialName("hide_totals") val hideTotals: Boolean = false,
)

@Serializable
data class MediaAttributeRequest(
    val id: String,
    val description: String? = null,
    val focus: String? = null,
    val blurhash: String? = null,
)

@Serializable
data class ReblogRequest(val visibility: StatusVisibility? = null)

@Serializable
object EmptyRequest

/** Retrofit uses toString for @Query values; it must be the lowercase wire value. */
enum class SearchType(private val wireValue: String) {
    ACCOUNTS("accounts"), HASHTAGS("hashtags"), STATUSES("statuses");
    override fun toString(): String = wireValue
}
