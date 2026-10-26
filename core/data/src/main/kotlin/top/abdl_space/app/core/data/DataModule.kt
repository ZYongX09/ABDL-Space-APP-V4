package top.abdl_space.app.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import top.abdl_space.app.core.data.session.EncryptedSessionStore
import top.abdl_space.app.core.data.session.SessionStore

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun sessionStore(store: EncryptedSessionStore): SessionStore
}
