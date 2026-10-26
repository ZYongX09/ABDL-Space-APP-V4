package top.abdl_space.app.core.model

import kotlinx.serialization.Serializable

/**
 * Opaque compatibility wrapper. Wire DTOs use String IDs without numeric conversion.
 *
 * An ID alone does not establish origin, entity kind, or authorization. In particular,
 * search-cache wrapper IDs must not be interpreted as server account/status IDs.
 */
@JvmInline
@Serializable
value class TypedId(val raw: String) {
    init {
        require(raw.isNotBlank()) { "TypedId 不能为空" }
    }
}
