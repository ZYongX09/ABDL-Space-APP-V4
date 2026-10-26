package top.abdl_space.app.core.data.auth

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.Response
import top.abdl_space.app.core.data.session.SessionCredential
import top.abdl_space.app.core.data.session.SessionLease
import top.abdl_space.app.core.data.session.SessionRepository
import top.abdl_space.app.core.data.session.SessionState
import top.abdl_space.app.core.network.AuthorizationHeader
import top.abdl_space.app.core.network.api.AuthApi
import top.abdl_space.app.core.network.api.MastodonApi
import top.abdl_space.app.core.network.dto.CodeLoginAction
import top.abdl_space.app.core.network.dto.LoginByCodeRequest
import top.abdl_space.app.core.network.dto.PasswordLoginRequest
import top.abdl_space.app.core.network.dto.RegisterRequest
import top.abdl_space.app.core.network.dto.SendCodeRequest
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthOutcome {
    data object SignedIn : AuthOutcome
    data object CodeSent : AuthOutcome
    data class RegistrationRequired(val email: String) : AuthOutcome
    data class Failure(val message: String) : AuthOutcome
}

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val mastodonApi: MastodonApi,
    private val sessions: SessionRepository,
) {
    suspend fun sendCode(email: String): AuthOutcome = safely {
        val response = authApi.sendCode(SendCodeRequest(email.trim()))
        if (response.isSuccessful) AuthOutcome.CodeSent else error(response)
    }

    suspend fun passwordSignIn(login: String, password: String): AuthOutcome = safely {
        val lease = lease() ?: return@safely storageError()
        val response = authApi.loginWithPassword(PasswordLoginRequest(login.trim(), password))
        if (!response.isSuccessful) return@safely error(response)
        val token = response.body()?.token ?: return@safely protocolError()
        verifyAndStore(token, lease)
    }

    suspend fun codeSignIn(email: String, code: String): AuthOutcome = safely {
        val lease = lease() ?: return@safely storageError()
        val response = authApi.loginByCode(LoginByCodeRequest(email.trim(), code.trim()))
        if (!response.isSuccessful) return@safely error(response)
        val body = response.body() ?: return@safely protocolError()
        if (!sessions.isCurrent(lease)) return@safely sessionChanged()
        when (body.action) {
            CodeLoginAction.REGISTER -> AuthOutcome.RegistrationRequired(body.email ?: email.trim())
            CodeLoginAction.LOGIN -> verifyAndStore(body.token ?: return@safely protocolError(), lease)
        }
    }

    suspend fun register(username: String, email: String, password: String, code: String): AuthOutcome = safely {
        val lease = lease() ?: return@safely storageError()
        val response = authApi.register(RegisterRequest(username.trim(), email.trim(), password, code.trim()))
        if (!response.isSuccessful) return@safely error(response)
        verifyAndStore(response.body()?.token ?: return@safely protocolError(), lease)
    }

    private suspend fun verifyAndStore(token: String, lease: SessionLease): AuthOutcome {
        if (!sessions.isCurrent(lease)) return sessionChanged()
        val response = mastodonApi.verifyCredentials(AuthorizationHeader.bearer(token))
        if (!response.isSuccessful) return error(response)
        val account = response.body() ?: return protocolError()
        val stored = sessions.signIn(
            credential = SessionCredential(
                accountId = account.id,
                accessToken = token,
                username = account.username,
                displayName = account.preferredDisplayName,
                avatarUrl = account.avatarStatic ?: account.avatar,
            ),
            expectedLease = lease,
        )
        return when {
            stored -> AuthOutcome.SignedIn
            sessions.state.value is SessionState.StorageUnavailable -> storageError()
            else -> sessionChanged()
        }
    }

    private fun lease(): SessionLease? = (sessions.state.value as? SessionState.Ready)?.lease

    private fun error(response: Response<*>): AuthOutcome.Failure {
        response.errorBody()?.close()
        return AuthOutcome.Failure(
            when (response.code()) {
                400, 422 -> "请检查填写的信息后重试。"
                401 -> "登录信息无效，请检查账号、密码或验证码。"
                403 -> "当前账号无法执行此操作。"
                429 -> "请求过于频繁，请稍后再试。"
                in 500..599 -> "服务暂时不可用，请稍后重试。"
                else -> "请求未完成（HTTP ${response.code()}）。"
            }
        )
    }

    private suspend fun safely(block: suspend () -> AuthOutcome): AuthOutcome = try {
        block()
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: IOException) {
        AuthOutcome.Failure("网络连接失败，请检查网络后重试。")
    } catch (_: SerializationException) {
        protocolError()
    } catch (_: IllegalArgumentException) {
        protocolError()
    }

    private fun storageError() = AuthOutcome.Failure("账号存储暂不可用，未保存登录信息。")
    private fun protocolError() = AuthOutcome.Failure("服务响应格式不兼容，未保存登录信息。")
    private fun sessionChanged() = AuthOutcome.Failure("账号已切换，请重新登录。")
}
