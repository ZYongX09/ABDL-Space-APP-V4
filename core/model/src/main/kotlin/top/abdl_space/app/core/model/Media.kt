package top.abdl_space.app.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class MediaAttachment(
    @Serializable(with = StringOrIntegerSerializer::class) val id: String,
    val type: MediaType,
    // Mastodon may return null while media is processing; it is not a usable download URL.
    val url: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("remote_url") val remoteUrl: String? = null,
    val description: String? = null,
    val blurhash: String? = null,
    val meta: MediaMetadata? = null,
) {
    init { requireProtocol(id.isNotBlank(), "Missing media ID") }
}

@Serializable(with = MediaTypeSerializer::class)
enum class MediaType(val wireValue: String) {
    IMAGE("image"), GIFV("gifv"), VIDEO("video"), AUDIO("audio"), UNKNOWN("unknown"),
}

object MediaTypeSerializer : KSerializer<MediaType> {
    override val descriptor = PrimitiveSerialDescriptor("MediaType", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): MediaType {
        val value = decoder.decodeString()
        return MediaType.entries.firstOrNull { it.wireValue == value } ?: MediaType.UNKNOWN
    }
    override fun serialize(encoder: Encoder, value: MediaType) = encoder.encodeString(value.wireValue)
}

@Serializable
data class MediaMetadata(
    val width: Int? = null,
    val height: Int? = null,
    val duration: Double? = null,
    val aspect: Double? = null,
    val focus: MediaFocus? = null,
    val original: MediaSize? = null,
    val small: MediaSize? = null,
)

@Serializable
data class MediaSize(
    val width: Int? = null,
    val height: Int? = null,
    val duration: Double? = null,
    val aspect: Double? = null,
    val bitrate: Int? = null,
)

@Serializable
data class MediaFocus(val x: Double, val y: Double)
