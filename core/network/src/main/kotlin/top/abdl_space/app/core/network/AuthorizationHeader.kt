package top.abdl_space.app.core.network

/** Pure per-request formatting. No session state, persistence, logging, or JWT decoding. */
object AuthorizationHeader {
    fun bearer(token: String): String {
        require(token.isNotBlank() && token.none { it.isWhitespace() || it.code < 0x20 || it.code == 0x7f }) {
            "Invalid bearer token"
        }
        return "Bearer $token"
    }
}
