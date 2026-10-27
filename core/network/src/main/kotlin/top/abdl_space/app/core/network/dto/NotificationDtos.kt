package top.abdl_space.app.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import top.abdl_space.app.core.model.Account
import top.abdl_space.app.core.model.Status
import top.abdl_space.app.core.model.StringOrIntegerSerializer

/**
 * Legacy /api/v1/notifications V1 item. The legacy client tolerates unknown types (gson leaves the
 * enum null and renders generically) and drops individually invalid items instead of failing the
 * page (removeUnsupportedItems). This projection keeps both behaviors explicit instead of crashing
 * on forward-compatible server additions.
 */
@Serializable
data class NotificationDto(
    @Serializable(with = StringOrIntegerSerializer::class) val id: String? = null,
    val type: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val account: Account? = null,
    val status: Status? = null,
) {
    /** Null drops only this item; it never synthesizes a placeholder or fails the whole page. */
    fun toModel(): Notification? {
        val identifier = id?.takeIf { it.isNotBlank() } ?: return null
        val created = createdAt?.takeIf { it.isNotBlank() } ?: return null
        val kind = NotificationKind.fromWire(type) ?: NotificationKind.ExplicitUnknown
        // Legacy postprocess rule: account is required except for the two event-backed types.
        if (account == null && kind != NotificationKind.SeveredRelationships &&
            kind != NotificationKind.ModerationWarning
        ) return null
        return Notification(identifier, kind, type.orEmpty(), created, account, status)
    }
}

/**
 * The legacy client's NotificationType set, verified against the old repository. Any other server
 * string degrades to [ExplicitUnknown] and is shown verbatim from [Notification.rawType]; nothing
 * is remapped to a guessed known type.
 */
enum class NotificationKind(val wire: String?) {
    Follow("follow"),
    FollowRequest("follow_request"),
    Mention("mention"),
    Reblog("reblog"),
    Favourite("favourite"),
    Poll("poll"),
    Status("status"),
    Update("update"),
    SeveredRelationships("severed_relationships"),
    ModerationWarning("moderation_warning"),
    Quote("quote"),
    QuotedUpdate("quoted_update"),

    /** Unknown or missing server type; the raw wire string stays inspectable, never discarded. */
    ExplicitUnknown(null),
    ;

    companion object {
        fun fromWire(raw: String?): NotificationKind? =
            raw?.let { text -> entries.firstOrNull { it.wire == text } }
    }
}

/** Typed read-only notification. Not serializable: it is a projection, never a wire model. */
data class Notification(
    val id: String,
    val kind: NotificationKind,
    /** Verbatim server type string; ExplicitUnknown renders this instead of inventing a label. */
    val rawType: String,
    val createdAt: String,
    val account: Account?,
    val status: Status?,
)
