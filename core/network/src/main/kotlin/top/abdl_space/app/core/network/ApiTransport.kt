package top.abdl_space.app.core.network

import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException

/** Shared by the Hilt graph and loopback HTTP tests; there is no token supplier/interceptor. */
internal fun createRetrofit(client: OkHttpClient, json: Json, baseUrl: HttpUrl): Retrofit {
    val isolated = client.newBuilder()
        .cache(null)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .apply {
            // Run before any caller-supplied application interceptor.
            interceptors().add(0, BoundOriginInterceptor(baseUrl))
        }
        .addNetworkInterceptor(BoundOriginInterceptor(baseUrl))
        .build()
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(isolated)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}

/**
 * Fail before sending either bearer headers or password/code bodies to another origin.
 * Equality includes scheme, exact hostname AND port, not suffixes. Redirects are disabled
 * in the client as well; the second check catches custom interceptor URL rewrites.
 */
internal class BoundOriginInterceptor(private val trusted: HttpUrl) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!sameOrigin(trusted, request.url)) throw UntrustedApiOriginException()
        val safeRequest = request.newBuilder()
            .header(ApiConfig.NBW_PROTOCOL_HEADER, ApiConfig.NBW_PROTOCOL_V2)
            .cacheControl(CacheControl.Builder().noCache().noStore().build())
            .build()
        return chain.proceed(safeRequest)
    }
}

internal fun sameOrigin(first: HttpUrl, second: HttpUrl): Boolean =
    first.scheme == second.scheme && first.host == second.host && first.port == second.port &&
        second.username.isEmpty() && second.password.isEmpty()

class UntrustedApiOriginException : IOException("API request origin is not trusted")
