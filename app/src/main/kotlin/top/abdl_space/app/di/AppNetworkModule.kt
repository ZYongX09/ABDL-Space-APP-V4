package top.abdl_space.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import top.abdl_space.app.BuildConfig
import top.abdl_space.app.core.network.NativeClientIdentity
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppNetworkModule {
    @Provides
    @Singleton
    fun nativeClientIdentity(): NativeClientIdentity = NativeClientIdentity(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
    )
}
