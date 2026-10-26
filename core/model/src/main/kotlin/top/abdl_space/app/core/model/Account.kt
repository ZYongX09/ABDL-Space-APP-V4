package top.abdl_space.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Verified Mastodon-compatible account projection, not an NBW grant or a session. */
@Serializable
data class Account(
    @Serializable(with = StringOrIntegerSerializer::class) val id: String,
    val username: String = "",
    val acct: String = "",
    @SerialName("display_name") val displayName: String = "",
    val note: String = "",
    val url: String? = null,
    val avatar: String? = null,
    @SerialName("avatar_static") val avatarStatic: String? = null,
    val header: String? = null,
    @SerialName("header_static") val headerStatic: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_status_at") val lastStatusAt: String? = null,
    @SerialName("followers_count") val followersCount: Long = 0,
    @SerialName("following_count") val followingCount: Long = 0,
    @SerialName("statuses_count") val statusesCount: Long = 0,
    val locked: Boolean = false,
    val bot: Boolean = false,
    val discoverable: Boolean = false,
    val suspended: Boolean = false,
    val verified: Boolean = false,
    @SerialName("nbw_username") val nbwUsername: String? = null,
    val fields: List<AccountField> = emptyList(),
    val emojis: List<CustomEmoji> = emptyList(),
    val source: AccountSource? = null,
) {
    init { requireProtocol(id.isNotBlank(), "Missing account ID") }

    val preferredDisplayName: String get() = displayName.ifBlank { username }
}

@Serializable
data class AccountField(
    val name: String,
    val value: String,
    @SerialName("verified_at") val verifiedAt: String? = null,
)

@Serializable
data class AccountSource(
    val note: String = "",
    val fields: List<AccountField> = emptyList(),
    val privacy: StatusVisibility? = null,
    val sensitive: Boolean = false,
    val language: String? = null,
)

@Serializable
data class CustomEmoji(
    val shortcode: String,
    val url: String,
    @SerialName("static_url") val staticUrl: String? = null,
    @SerialName("visible_in_picker") val visibleInPicker: Boolean = false,
    val category: String? = null,
)
