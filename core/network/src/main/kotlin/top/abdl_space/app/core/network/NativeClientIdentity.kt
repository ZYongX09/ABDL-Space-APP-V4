package top.abdl_space.app.core.network

import okhttp3.Interceptor
import okhttp3.Response

/** Supplied by the app's BuildConfig, never guessed by a library or derived from a session. */
data class NativeClientIdentity(val versionName: String, val versionCode: Int) {
    init {
        require(versionName.isNotBlank() && versionName.none { it.isWhitespace() || it.code < 0x20 || it.code == 0x7f })
        require(versionCode > 0)
    }
}

internal class NativeClientIdentityInterceptor(private val identity: NativeClientIdentity) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
        chain.request().newBuilder()
            .header("User-Agent", "MastodonAndroid/${identity.versionName}")
            .header("X-App-Version-Code", identity.versionCode.toString())
            .build(),
    )
}
