package pl.asystentfanatyka.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pl.asystentfanatyka.core.rules.AreaContext

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    /** Po ilu dniach od weryfikacji dane uznawane są za nieaktualne. */
    val stalenessDays: Int = 180,
    /** Adres pliku JSON z aktualizacjami (HTTPS). Pusty = brak automatycznych aktualizacji. */
    val updateUrl: String = "",
    /** Czy użytkownik wskazał obszar w rozumieniu przepisów (false = „nie wiem”). */
    val areaKnown: Boolean = false,
    val areaCodes: Set<String> = emptySet(),
    /** Czy zachowywać zdjęcia przy zapisanych połowach. */
    val keepPhotos: Boolean = true,
    /** Najmniejsza działka maty pomiarowej w cm. */
    val matGraduationCm: Double = 1.0,
    val lastSyncMillis: Long = 0,
    val lastSyncMessage: String = ""
) {
    val areaContext: AreaContext get() = if (areaKnown) AreaContext.Known(areaCodes) else AreaContext.Unknown
}

class SettingsRepository(private val context: Context) {

    private object K {
        val staleness = intPreferencesKey("staleness_days")
        val url = stringPreferencesKey("update_url")
        val areaKnown = booleanPreferencesKey("area_known")
        val areaCodes = stringSetPreferencesKey("area_codes")
        val keepPhotos = booleanPreferencesKey("keep_photos")
        val graduation = doublePreferencesKey("mat_graduation")
        val lastSync = longPreferencesKey("last_sync")
        val lastSyncMsg = stringPreferencesKey("last_sync_msg")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            stalenessDays = p[K.staleness] ?: 180,
            updateUrl = p[K.url] ?: "",
            areaKnown = p[K.areaKnown] ?: false,
            areaCodes = p[K.areaCodes] ?: emptySet(),
            keepPhotos = p[K.keepPhotos] ?: true,
            matGraduationCm = p[K.graduation] ?: 1.0,
            lastSyncMillis = p[K.lastSync] ?: 0,
            lastSyncMessage = p[K.lastSyncMsg] ?: ""
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setStalenessDays(days: Int) = context.dataStore.edit { it[K.staleness] = days.coerceIn(30, 730) }
    suspend fun setUpdateUrl(url: String) = context.dataStore.edit { it[K.url] = url.trim() }
    suspend fun setArea(known: Boolean, codes: Set<String>) = context.dataStore.edit {
        it[K.areaKnown] = known
        it[K.areaCodes] = if (known) codes else emptySet()
    }
    suspend fun setKeepPhotos(keep: Boolean) = context.dataStore.edit { it[K.keepPhotos] = keep }
    suspend fun setMatGraduation(cm: Double) = context.dataStore.edit { it[K.graduation] = cm }
    suspend fun setLastSync(millis: Long, message: String) = context.dataStore.edit {
        it[K.lastSync] = millis
        it[K.lastSyncMsg] = message
    }
}
