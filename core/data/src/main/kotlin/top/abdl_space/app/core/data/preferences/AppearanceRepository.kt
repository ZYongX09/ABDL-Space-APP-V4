package top.abdl_space.app.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore("v4-appearance")

enum class ThemePreference { SYSTEM, LIGHT, DARK }

data class AppearancePreferences(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val reduceTransparency: Boolean = true,
    val reduceMotion: Boolean = false,
)

@Singleton
class AppearanceRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.appearanceDataStore
    val preferences: Flow<AppearancePreferences> = store.data
        .map { value ->
            AppearancePreferences(
                theme = ThemePreference.entries.firstOrNull { it.name == value[THEME] } ?: ThemePreference.SYSTEM,
                reduceTransparency = value[REDUCE_TRANSPARENCY] ?: true,
                reduceMotion = value[REDUCE_MOTION] ?: false,
            )
        }
        .catch { exception ->
            if (exception is IOException) emit(AppearancePreferences()) else throw exception
        }

    suspend fun setTheme(theme: ThemePreference) {
        store.edit { it[THEME] = theme.name }
    }

    suspend fun setReduceTransparency(reduce: Boolean) {
        store.edit { it[REDUCE_TRANSPARENCY] = reduce }
    }

    suspend fun setReduceMotion(reduce: Boolean) {
        store.edit { it[REDUCE_MOTION] = reduce }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val REDUCE_TRANSPARENCY = booleanPreferencesKey("reduce_transparency")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    }
}
