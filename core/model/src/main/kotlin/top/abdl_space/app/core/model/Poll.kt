package top.abdl_space.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Poll(
    @Serializable(with = StringOrIntegerSerializer::class) val id: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    val expired: Boolean = false,
    val multiple: Boolean = false,
    @SerialName("votes_count") val votesCount: Long = 0,
    @SerialName("voters_count") val votersCount: Long? = null,
    val voted: Boolean = false,
    @SerialName("own_votes") val ownVotes: List<Int> = emptyList(),
    val options: List<PollOption>,
    val emojis: List<CustomEmoji> = emptyList(),
) {
    init { requireProtocol(id.isNotBlank(), "Missing poll ID") }
}

@Serializable
data class PollOption(
    val title: String,
    // Hidden totals are unknown, not zero.
    @SerialName("votes_count") val votesCount: Long? = null,
)
