package pl.asystentfanatyka.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.asystentfanatyka.core.data.DatasetParser
import pl.asystentfanatyka.core.data.ImportDecision
import pl.asystentfanatyka.core.data.ImportPolicy
import pl.asystentfanatyka.core.data.ParseResult
import pl.asystentfanatyka.core.model.RegulationDataset
import pl.asystentfanatyka.data.db.DatasetDao
import pl.asystentfanatyka.data.db.DatasetEntities
import pl.asystentfanatyka.data.db.toEntities
import pl.asystentfanatyka.data.db.toModel

sealed interface ImportResult {
    data class Imported(val version: Int, val warnings: List<String>) : ImportResult
    data class Skipped(val reason: String) : ImportResult
    data class Failed(val errors: List<String>) : ImportResult
}

/** Stan danych przepisów widoczny dla UI. */
sealed interface DatasetState {
    data object Loading : DatasetState
    data class Ready(val dataset: RegulationDataset, val origin: String) : DatasetState
    data class Error(val message: String) : DatasetState
}

/**
 * Jedno źródło prawdy dla danych o gatunkach i przepisach. Dane są w Room (działają offline).
 * Każdy import przechodzi pełną walidację; dopiero poprawny zbiór zastępuje aktywny – w jednej transakcji.
 */
class RegulationRepository(
    private val context: Context,
    private val dao: DatasetDao
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<DatasetState>(DatasetState.Loading)
    val state: StateFlow<DatasetState> = _state.asStateFlow()

    val current: RegulationDataset? get() = (state.value as? DatasetState.Ready)?.dataset

    /** Wywoływane przy starcie: ładuje aktywną wersję; gdy jej brak lub dane w aplikacji są nowsze – importuje je. */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val bundled = context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
                when (val r = importLocked(bundled, origin = "asset", requireNewer = true, allowDemo = false)) {
                    is ImportResult.Imported -> AppLog.i("Zaimportowano dane dołączone do aplikacji, wersja ${r.version}")
                    is ImportResult.Skipped -> Unit
                    is ImportResult.Failed -> AppLog.e("Dane dołączone do aplikacji są niepoprawne: ${r.errors.take(3)}")
                }
            } catch (t: Throwable) {
                AppLog.e("Nie udało się odczytać danych dołączonych do aplikacji", t)
            }
            loadActiveLocked()
        }
    }

    suspend fun import(json: String, origin: String, requireNewer: Boolean, allowDemo: Boolean = false): ImportResult =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val r = importLocked(json, origin, requireNewer, allowDemo)
                if (r is ImportResult.Imported) loadActiveLocked()
                r
            }
        }

    suspend fun rollbackToPrevious(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val active = dao.active() ?: return@withLock false
            val previous = dao.versions().firstOrNull { it != active.version } ?: return@withLock false
            dao.rollbackTo(previous)
            AppLog.w("Przywrócono poprzednią wersję danych: $previous")
            loadActiveLocked()
            true
        }
    }

    private suspend fun importLocked(json: String, origin: String, requireNewer: Boolean, allowDemo: Boolean): ImportResult {
        val parsed = DatasetParser.parse(json)
        if (parsed is ParseResult.Error) {
            AppLog.w("Odrzucono dane ($origin): ${parsed.errors.size} błędów, np. ${parsed.errors.first()}")
            return ImportResult.Failed(parsed.errors)
        }
        val ok = parsed as ParseResult.Ok
        val active = dao.active()?.let { loadVersion(it.version)?.metadata }
        return when (val d = ImportPolicy.decide(active, ok.dataset.metadata, requireNewer, allowDemo)) {
            is ImportDecision.Skip -> ImportResult.Skipped(d.reason)
            is ImportDecision.Reject -> {
                AppLog.w("Odrzucono dane ($origin): ${d.reason}")
                ImportResult.Failed(listOf(d.reason))
            }
            ImportDecision.Accept -> {
                val e = ok.dataset.toEntities(origin, System.currentTimeMillis())
                try {
                    dao.replaceActive(e.meta, e.sources, e.areas, e.species, e.rules)
                    AppLog.i("Aktywna wersja danych: ${e.meta.version} ($origin)")
                    ImportResult.Imported(e.meta.version, ok.warnings)
                } catch (t: Throwable) {
                    AppLog.e("Zapis danych nie powiódł się – poprzednie dane pozostają aktywne", t)
                    ImportResult.Failed(listOf("Błąd zapisu w bazie: ${t.message}"))
                }
            }
        }
    }

    private suspend fun loadVersion(version: Int): RegulationDataset? {
        val meta = dao.metadata(version) ?: return null
        return try {
            DatasetEntities(meta, dao.sources(version), dao.areas(version), dao.species(version), dao.rules(version)).toModel()
        } catch (t: Throwable) {
            AppLog.e("Uszkodzone dane w bazie (wersja $version)", t)
            null
        }
    }

    private suspend fun loadActiveLocked() {
        val meta = dao.active()
        if (meta == null) {
            _state.value = DatasetState.Error("Brak danych o przepisach. Zaimportuj plik danych w Ustawieniach.")
            return
        }
        val ds = loadVersion(meta.version)
        _state.value = if (ds != null) DatasetState.Ready(ds, meta.origin)
        else DatasetState.Error("Nie udało się wczytać danych (wersja ${meta.version}). Spróbuj przywrócić poprzednią wersję.")
    }

    companion object {
        const val ASSET = "dataset/national.json"
    }
}
