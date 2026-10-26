package top.abdl_space.app.core.network.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import top.abdl_space.app.core.network.dto.AuthTokenResponse
import top.abdl_space.app.core.network.dto.CodeLoginResponse
import top.abdl_space.app.core.network.dto.LoginByCodeRequest
import top.abdl_space.app.core.network.dto.PasswordLoginRequest
import top.abdl_space.app.core.network.dto.RegisterRequest
import top.abdl_space.app.core.network.dto.SendCodeRequest

/** Bind to @WebRetrofit. No Authorization header or global token lookup on login endpoints. */
interface AuthApi {
    // Old source only consumes the HTTP status; no response JSON schema is verified.
    @POST("api/auth/send-code")
    suspend fun sendCode(@Body request: SendCodeRequest): Response<Unit>

    @POST("api/auth/login-by-code")
    suspend fun loginByCode(@Body request: LoginByCodeRequest): Response<CodeLoginResponse>

    @POST("api/auth/login")
    suspend fun loginWithPassword(@Body request: PasswordLoginRequest): Response<AuthTokenResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthTokenResponse>
}
