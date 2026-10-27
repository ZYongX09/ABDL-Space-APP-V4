package top.abdl_space.app.core.data.nbw

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Scope that owns the NBW session observation and in-flight snapshot refreshes. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NbwRefreshScope

@Module
@InstallIn(SingletonComponent::class)
object NbwDataModule {

    @Provides
    @Singleton
    @NbwRefreshScope
    fun nbwRefreshScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun nbwClock(): NbwClock = NbwClock { android.os.SystemClock.elapsedRealtime() }
}
