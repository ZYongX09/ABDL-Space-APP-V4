package top.abdl_space.app.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import top.abdl_space.app.core.model.ProtocolViolation
import top.abdl_space.app.core.model.StringOrIntegerSerializer

@Serializable
data class ConversationsResponse(val conversations: List<ChatConversation>)

@Serializable
data class ChatConversation(
    @SerialName("user_id") @Serializable(with = StringOrIntegerSerializer::class) val userId: String,
    val username: String = "",
    val avatar: String? = null,
    @SerialName("last_message") val lastMessage: String? = null,
    @SerialName("last_message_at") @Serializable(with = StringOrIntegerSerializer::class)
    val lastMessageAt: String? = null,
    @SerialName("last_message_id") @Serializable(with = StringOrIntegerSerializer::class)
    val lastMessageId: String? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
)

@Serializable
data class ChatMessagesResponse(val messages: List<ChatMessage>)

/** Server time is retained verbatim (old source accepts SQL UTC strings or epoch seconds). */
@Serializable
data class ChatMessage(
    @Serializable(with = StringOrIntegerSerializer::class) val id: String,
    @SerialName("sender_id") @Serializable(with = StringOrIntegerSerializer::class) val senderId: String,
    @SerialName("receiver_id") @Serializable(with = StringOrIntegerSerializer::class)
    val receiverId: String? = null,
    val content: String,
    @SerialName("client_msg_id") val clientMsgId: String? = null,
    @SerialName("created_at") @Serializable(with = StringOrIntegerSerializer::class) val createdAt: String,
    val read: Boolean = false,
)

/**
 * Typed projection of the two persistent event shapes consumed by ChatController.
 * Unknown types remain inspectable via type but cannot silently advance the durable cursor.
 */
@Serializable
data class ChatEvent(
    @SerialName("event_id") @Serializable(with = StringOrIntegerSerializer::class) val eventId: String,
    val type: String,
    val message: ChatMessage? = null,
    // The legacy handler also accepts message.new fields at the event's top level.
    @Serializable(with = StringOrIntegerSerializer::class) val id: String? = null,
    @SerialName("sender_id") @Serializable(with = StringOrIntegerSerializer::class) val senderId: String? = null,
    @SerialName("receiver_id") @Serializable(with = StringOrIntegerSerializer::class) val receiverId: String? = null,
    val content: String? = null,
    @SerialName("client_msg_id") val clientMsgId: String? = null,
    @SerialName("created_at") @Serializable(with = StringOrIntegerSerializer::class) val createdAt: String? = null,
    @SerialName("peer_id") @Serializable(with = StringOrIntegerSerializer::class) val peerId: String? = null,
    @SerialName("read_up_to_id") @Serializable(with = StringOrIntegerSerializer::class) val readUpToId: String? = null,
    @SerialName("reader_id") @Serializable(with = StringOrIntegerSerializer::class) val readerId: String? = null,
) {
    fun requireSupported() {
        when (type) {
            "message.new" -> if (message == null &&
                (id == null || senderId == null || receiverId == null || content == null || createdAt == null)
            ) throw ProtocolViolation("Incomplete chat message event")
            "message.read" -> if (peerId == null || readUpToId == null || readerId == null) {
                throw ProtocolViolation("Incomplete chat read event")
            }
            else -> throw ProtocolViolation("Unsupported persistent chat event")
        }
    }
}

@Serializable
data class ChatSyncResponse(
    val events: List<ChatEvent>,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("next_event_id") @Serializable(with = StringOrIntegerSerializer::class)
    val nextEventId: String? = null,
) {
    /** Never synthesize after+1 or require consecutive event IDs; gaps belong to other users. */
    fun continuationAfter(previous: String): String? {
        events.forEach(ChatEvent::requireSupported)
        if (!hasMore) return null
        val next = nextEventId ?: throw ProtocolViolation("Missing sync continuation")
        if (!isIncreasingDecimalCursor(previous, next)) throw ProtocolViolation("Sync cursor did not advance")
        return next
    }
}

private fun isIncreasingDecimalCursor(previous: String, next: String): Boolean {
    // Only this verified numeric event-cursor contract is compared, never entity IDs.
    if (!previous.matches(Regex("[0-9]+")) || !next.matches(Regex("[0-9]+"))) return false
    val before = previous.trimStart('0').ifEmpty { "0" }
    val after = next.trimStart('0').ifEmpty { "0" }
    return after.length > before.length || after.length == before.length && after > before
}
