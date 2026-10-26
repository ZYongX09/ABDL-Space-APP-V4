package top.abdl_space.app.core.network

/**
 * Verified against the current legacy source, not a blanket "custom API = api host" rule.
 *
 * LoginEmail/Code/Password/RegisterInfo use WEB_BASE under /api/auth/.
 * MastodonAPIRequest uses the session domain for /api/v1 and search /api/v2.
 * GetConversations/GetChatMessages/SyncChatEvents override only the prefix to /api.
 * NBWAppRequest overrides only the prefix to /api/app. Official login's domain is WEB_HOST.
 * API_HOST is reserved for separately verified services (e.g. old novels/QQ), NOT these routes.
 */
object ApiConfig {
    const val WEB_HOST = "abdl-space.top"
    const val API_HOST = "api.abdl-space.top"
    const val WEB_BASE = "https://$WEB_HOST"
    const val API_BASE = "https://$API_HOST"

    const val API_PREFIX_V1 = "/api/v1"
    const val NBW_APP_PREFIX = "/api/app"
    const val NBW_PROTOCOL_HEADER = "X-ABDL-NBW-Protocol"
    const val NBW_PROTOCOL_V2 = "2"
}
