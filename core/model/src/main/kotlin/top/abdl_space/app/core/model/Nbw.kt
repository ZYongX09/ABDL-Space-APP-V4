package top.abdl_space.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NbwRemoteMode {
    @SerialName("local_only") LOCAL_ONLY,
    @SerialName("read_only") READ_ONLY,
    @SerialName("full") FULL,
}

@Serializable
enum class NbwBindingState {
    @SerialName("bound") BOUND,
    @SerialName("unbound") UNBOUND,
    @SerialName("unknown") UNKNOWN,
}

@Serializable
enum class NbwAuthorizationState {
    @SerialName("ready") READY,
    @SerialName("missing") MISSING,
    @SerialName("revoked") REVOKED,
    @SerialName("unavailable") UNAVAILABLE,
}

/** Authenticated metadata, independent of Account.nbwUsername; unknown grants fail closed. */
@Serializable
data class NbwCapabilities(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("remote_mode") val remoteMode: NbwRemoteMode,
    val reason: String,
    @SerialName("policy_revision") val policyRevision: String,
    val binding: NbwBinding,
    val authorization: NbwAuthorization,
    val fallback: NbwFallbackConsent,
) {
    init {
        requireProtocol(schemaVersion == 1 && policyRevision.isNotBlank(), "Unsupported NBW capabilities")
        requireProtocol(binding.state != NbwBindingState.BOUND || !binding.uid.isNullOrBlank(), "Missing NBW binding identity")
    }

    val canRead: Boolean get() = remoteMode != NbwRemoteMode.LOCAL_ONLY
    val canWrite: Boolean get() = remoteMode == NbwRemoteMode.FULL
    val hasGrant: Boolean get() = binding.state == NbwBindingState.BOUND && authorization.state == NbwAuthorizationState.READY
}

@Serializable
data class NbwBinding(
    val state: NbwBindingState,
    @Serializable(with = StringOrIntegerSerializer::class) val uid: String? = null,
    val username: String? = null,
)

@Serializable
data class NbwAuthorization(
    val state: NbwAuthorizationState,
    @SerialName("lifetime_policy") val lifetimePolicy: String,
) {
    init { requireProtocol(lifetimePolicy == "non_expiring", "Unsupported NBW grant lifetime") }
}

@Serializable
data class NbwFallbackConsent(
    @Serializable(with = StringOrIntegerSerializer::class) val fid: String,
    @SerialName("policy_version") val policyVersion: Int,
    val consented: Boolean,
) {
    init { requireProtocol(fid == "18" && policyVersion == 1, "Unsupported NBW fallback consent") }
}

@Serializable
data class NbwForumOptions(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("config_revision") val configRevision: String,
    val forums: List<NbwForum>,
    val fallback: NbwFallbackPolicy,
) {
    init {
        requireProtocol(schemaVersion == 1 && configRevision.isNotBlank(), "Unsupported NBW options")
        requireProtocol(forums.size <= 256 && forums.map { it.fid }.distinct().size == forums.size, "Invalid NBW forum list")
    }

    fun isLegal(fid: String, typeid: String?): Boolean {
        val forum = forums.firstOrNull { it.fid == fid } ?: return false
        if (typeid == null || typeid == "0") return !forum.required
        return forum.types.any { it.typeid == typeid && !it.moderatorOnly }
    }

    val canFallback: Boolean get() = isLegal(fallback.fid, "0")
}

@Serializable
data class NbwForum(
    @Serializable(with = StringOrIntegerSerializer::class) val fid: String,
    val name: String,
    val required: Boolean,
    val types: List<NbwForumType>,
) {
    init {
        requireProtocol(fid.matches(POSITIVE_DECIMAL_ID) && validName(name), "Invalid NBW forum")
        requireProtocol(types.size <= 512 && types.map { it.typeid }.distinct().size == types.size, "Invalid NBW forum types")
    }
}

@Serializable
data class NbwForumType(
    @Serializable(with = StringOrIntegerSerializer::class) val typeid: String,
    val name: String,
    @SerialName("moderator_only") val moderatorOnly: Boolean,
) {
    init { requireProtocol(typeid.matches(POSITIVE_DECIMAL_ID) && validName(name), "Invalid NBW type") }
}

@Serializable
data class NbwFallbackPolicy(
    @Serializable(with = StringOrIntegerSerializer::class) val fid: String,
    @SerialName("policy_version") val policyVersion: Int,
) {
    init { requireProtocol(fid == "18" && policyVersion == 1, "Unsupported NBW fallback policy") }
}

private val POSITIVE_DECIMAL_ID = Regex("[1-9][0-9]*")
private fun validName(value: String): Boolean = value.isNotBlank() && value.length <= 256
