package pl.asystentfanatyka

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.asystentfanatyka.camera.PhotoStorage
import pl.asystentfanatyka.data.AppLog
import pl.asystentfanatyka.data.CatchRepository
import pl.asystentfanatyka.data.RegulationRepository
import pl.asystentfanatyka.data.SettingsRepository
import pl.asystentfanatyka.data.db.AppDatabase
import pl.asystentfanatyka.recognition.NotConfiguredRecognizer
import pl.asystentfanatyka.recognition.SpeciesRecognizer
import pl.asystentfanatyka.sync.DatasetSync

/** Ręczne wstrzykiwanie zależności – przy tej skali projektu Hilt nie jest potrzebny. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val regulations = RegulationRepository(context, database.datasetDao())
    val catches = CatchRepository(database.catchDao())
    val photos = PhotoStorage(context)
    val sync = DatasetSync(regulations, settings)
    val recognizer: SpeciesRecognizer = NotConfiguredRecognizer()
}

class AsystentFanatykaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            try {
                container.regulations.initialize()
                container.sync.checkIfDue()
            } catch (t: Throwable) {
                AppLog.e("Błąd inicjalizacji danych", t)
            }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as AsystentFanatykaApp).container
