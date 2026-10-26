package top.abdl_space.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class StatusVisibility {
    @SerialName("public") PUBLIC,
    @SerialName("unlisted") UNLISTED,
    @SerialName("private") PRIVATE,
    @SerialName("direct") DIRECT,
    @SerialName("local") LOCAL,
}

/** Core wire projection. Optional unimplemented extensions are ignored, not generic JSON payloads. */
@Serializable
data class Status(
    @Serializable(with = StringOrIntegerSerializer::class) val id: String,
    val uri: String,
    @SerialName("created_at") val createdAt: String,
    val account: Account,
    val visibility: StatusVisibility,
    val content: String = "",
    val text: String? = null,
    val url: String? = null,
    @SerialName("edited_at") val editedAt: String? = null,
    val sensitive: Boolean = false,
    @SerialName("mental_crisis") val mentalCrisis: Boolean = false,
    @SerialName("spoiler_text") val spoilerText: String = "",
    @SerialName("media_attachments") val mediaAttachments: List<MediaAttachment> = emptyList(),
    val mentions: List<Mention> = emptyList(),
    val tags: List<Hashtag> = emptyList(),
    val emojis: List<CustomEmoji> = emptyList(),
    @SerialName("in_reply_to_id") @Serializable(with = StringOrIntegerSerializer::class)
    val inReplyToId: String? = null,
    @SerialName("in_reply_to_account_id") @Serializable(with = StringOrIntegerSerializer::class)
    val inReplyToAccountId: String? = null,
    val reblog: Status? = null,
    val poll: Poll? = null,
    val language: String? = null,
    @SerialName("reblogs_count") val reblogsCount: Long = 0,
    @SerialName("favourites_count") val favouritesCount: Long = 0,
    @SerialName("replies_count") val repliesCount: Long = 0,
    @SerialName("bookmarks_count") val bookmarksCount: Long = 0,
    @SerialName("quotes_count") val quotesCount: Long = 0,
    @SerialName("shares_count") val sharesCount: Long = 0,
    @SerialName("views_count") val viewsCount: Long = 0,
    val heat: Double = 0.0,
    val favourited: Boolean = false,
    val reblogged: Boolean = false,
    val bookmarked: Boolean = false,
    val muted: Boolean = false,
    val pinned: Boolean = false,
    @SerialName("nbw_fid") @Serializable(with = StringOrIntegerSerializer::class)
    val nbwFid: String? = null,
    @SerialName("nbw_tid") @Serializable(with = StringOrIntegerSerializer::class)
    val nbwTid: String? = null,
    @SerialName("nbw_sync") val nbwSync: NbwSyncState? = null,
) {
    init {
        requireProtocol(id.isNotBlank(), "Missing status ID")
        requireProtocol(uri.isNotBlank() && createdAt.isNotBlank(), "Incomplete status identity")
    }
}

@Serializable
data class Mention(
    @Serializable(with = StringOrIntegerSerializer::class) val id: String,
    val username: String = "",
    val acct: String = "",
    val url: String? = null,
)

@Serializable
data class Hashtag(val name: String, val url: String, val following: Boolean = false)

@Serializable
data class NbwSyncState(val state: String, val reason: String? = null)

@Serializable
data class StatusContext(
    val ancestors: List<Status>,
    val descendants: List<Status>,
    @SerialName("nbw_unavailable") val nbwUnavailable: Boolean = false,
)

@Serializable
data class SearchResults(
    val accounts: List<Account> = emptyList(),
    val statuses: List<Status> = emptyList(),
    val hashtags: List<Hashtag> = emptyList(),
)
