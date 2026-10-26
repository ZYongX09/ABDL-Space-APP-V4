package top.abdl_space.app.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import top.abdl_space.app.core.model.ProtocolViolation

/** LoginEmailFragment and LoginCodeFragment both send type=register, even for existing users. */
@Serializable
data class SendCodeRequest(val email: String, val type: SendCodeType = SendCodeType.REGISTER) {
    override fun toString(): String = "SendCodeRequest([REDACTED])"
}

@Serializable
enum class SendCodeType { @SerialName("register") REGISTER }

@Serializable
data class LoginByCodeRequest(val email: String, val code: String) {
    override fun toString(): String = "LoginByCodeRequest([REDACTED])"
}

@Serializable
enum class CodeLoginAction {
    @SerialName("login") LOGIN,
    @SerialName("register") REGISTER,
}

@Serializable
data class CodeLoginResponse(
    val action: CodeLoginAction,
    val token: String? = null,
    val email: String? = null,
) {
    init {
        if (action == CodeLoginAction.LOGIN && token.isNullOrBlank()) {
            throw ProtocolViolation("Login response is missing its token")
        }
    }
    override fun toString(): String = "CodeLoginResponse(action=$action, [REDACTED])"
}

/** Wire key is login, not email; the legacy UI accepts email or username. */
@Serializable
data class PasswordLoginRequest(val login: String, val password: String) {
    override fun toString(): String = "PasswordLoginRequest([REDACTED])"
}

/** First-party email registration only. Retired V1 nbw_token linking is deliberately excluded. */
@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val code: String,
) {
    override fun toString(): String = "RegisterRequest([REDACTED])"
}

/** The verified wire field is token. No assumption that it is a JWT or an OAuth access_token. */
@Serializable
data class AuthTokenResponse(val token: String) {
    init { if (token.isBlank()) throw ProtocolViolation("Missing authentication token") }
    override fun toString(): String = "AuthTokenResponse([REDACTED])"
}
