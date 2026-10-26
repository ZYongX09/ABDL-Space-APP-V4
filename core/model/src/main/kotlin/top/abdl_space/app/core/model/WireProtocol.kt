package top.abdl_space.app.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/** A valid JSON document that violates a verified wire contract is not a successful result. */
class ProtocolViolation(message: String) : SerializationException(message)

internal fun requireProtocol(condition: Boolean, message: String) {
    if (!condition) throw ProtocolViolation(message)
}

/**
 * Chat currently mixes integer and quoted IDs/timestamps. Preserve the literal digits,
 * including values beyond Long/Double precision; never round, parseLong, or infer prefixes.
 * Only this explicit compatibility seam accepts integer JSON tokens (not booleans/floats).
 */
object StringOrIntegerSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("StringOrInteger", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        if (decoder !is JsonDecoder) return decoder.decodeString()
        val primitive = decoder.decodeJsonElement() as? JsonPrimitive
            ?: throw ProtocolViolation("Expected a string or integer")
        val value = primitive.content
        requireProtocol(
            primitive.isString || INTEGER.matches(value),
            "Expected a string or integer",
        )
        requireProtocol(value.isNotBlank(), "Empty wire identifier/value")
        return value
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)

    private val INTEGER = Regex("-?(0|[1-9][0-9]*)")
}
