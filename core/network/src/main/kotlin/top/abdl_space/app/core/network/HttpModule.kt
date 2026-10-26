package top.abdl_space.app.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import top.abdl_space.app.core.network.api.AuthApi
import top.abdl_space.app.core.network.api.MastodonApi
import top.abdl_space.app.core.network.api.MessagesApi
import top.abdl_space.app.core.network.api.NbwApi
import top.abdl_space.app.core.network.api.NbwInteractionsApi
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier annotation class WebRetrofit
@Qualifier annotation class ApiRetrofit

@Module
@InstallIn(SingletonComponent::class)
object HttpModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        // Do not coerce unknown NBW grants/visibility into permissive defaults.
        coerceInputValues = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun okHttp(identity: NativeClientIdentity): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(NativeClientIdentityInterceptor(identity))
        .build()

    @Provides
    @Singleton
    @WebRetrofit
    fun webRetrofit(client: OkHttpClient, json: Json): Retrofit = base(client, json, ApiConfig.WEB_BASE)

    @Provides
    @Singleton
    @ApiRetrofit
    fun apiRetrofit(client: OkHttpClient, json: Json): Retrofit = base(client, json, ApiConfig.API_BASE)

    @Provides @Singleton
    fun authApi(@WebRetrofit retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides @Singleton
    fun mastodonApi(@WebRetrofit retrofit: Retrofit): MastodonApi = retrofit.create(MastodonApi::class.java)

    @Provides @Singleton
    fun messagesApi(@WebRetrofit retrofit: Retrofit): MessagesApi = retrofit.create(MessagesApi::class.java)

    @Provides @Singleton
    fun nbwApi(@WebRetrofit retrofit: Retrofit): NbwApi = retrofit.create(NbwApi::class.java)

    @Provides @Singleton
    fun nbwInteractionsApi(@WebRetrofit retrofit: Retrofit): NbwInteractionsApi =
        retrofit.create(NbwInteractionsApi::class.java)

    private fun base(client: OkHttpClient, json: Json, baseUrl: String): Retrofit =
        createRetrofit(client, json, "${baseUrl.trimEnd('/')}/".toHttpUrl())
}
