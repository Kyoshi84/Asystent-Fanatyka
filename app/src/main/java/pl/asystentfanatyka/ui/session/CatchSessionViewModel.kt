package pl.asystentfanatyka.ui.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.asystentfanatyka.container
import pl.asystentfanatyka.core.recognition.RecognitionOutcome
import pl.asystentfanatyka.core.rules.AreaContext
import pl.asystentfanatyka.core.rules.CheckInput
import pl.asystentfanatyka.core.rules.CheckResult
import pl.asystentfanatyka.core.rules.MeasurementInput
import pl.asystentfanatyka.core.rules.RuleEngine
import pl.asystentfanatyka.core.rules.SpeciesCertainty
import pl.asystentfanatyka.data.AppLog
import java.time.LocalDate

/** Stan bieżącej kontroli połowu, współdzielony przez ekrany: zdjęcie → rozpoznanie → pomiar → wynik. */
data class CatchSession(
    val photoPath: String? = null,
    val recognition: RecognitionOutcome? = null,
    val speciesId: String? = null,
    val speciesCertainty: SpeciesCertainty = SpeciesCertainty.UNCERTAIN,
    val measurement: MeasurementInput? = null,
    val measurementWarnings: List<String> = emptyList(),
    val measurementPointsJson: String? = null,
    val catchDate: LocalDate = LocalDate.now(),
    val keptToday: Int? = null,
    val localRulesConfirmed: Boolean = false,
    /** null = użyj ustawień; w przeciwnym razie obszar wskazany dla tej kontroli. */
    val areaOverride: AreaContext? = null,
    val result: CheckResult? = null,
    val savedId: Long? = null
)

class CatchSessionViewModel(app: Application) : AndroidViewModel(app) {

    private val c = app.container
    private val _session = MutableStateFlow(CatchSession())
    val session: StateFlow<CatchSession> = _session.asStateFlow()

    fun reset() { _session.value = CatchSession() }

    fun setPhoto(path: String?) = _session.update { it.copy(photoPath = path, result = null, savedId = null) }

    fun setRecognition(r: RecognitionOutcome?) = _session.update { it.copy(recognition = r) }

    fun setSpecies(id: String, certainty: SpeciesCertainty) =
        _session.update { it.copy(speciesId = id, speciesCertainty = certainty, result = null, savedId = null) }

    fun setMeasurement(m: MeasurementInput?, warnings: List<String> = emptyList(), pointsJson: String? = null) =
        _session.update { it.copy(measurement = m, measurementWarnings = warnings, measurementPointsJson = pointsJson, result = null, savedId = null) }

    fun setDate(d: LocalDate) = _session.update { it.copy(catchDate = d, result = null, savedId = null) }
    fun setKeptToday(n: Int?) = _session.update { it.copy(keptToday = n, result = null, savedId = null) }
    fun setLocalConfirmed(v: Boolean) = _session.update { it.copy(localRulesConfirmed = v, result = null, savedId = null) }
    fun setArea(a: AreaContext?) = _session.update { it.copy(areaOverride = a, result = null, savedId = null) }

    /** Czy dla gatunku obowiązuje jakikolwiek wymiar (decyduje, czy po rozpoznaniu przejść do pomiaru). */
    fun lengthRulesExist(speciesId: String): Boolean =
        c.regulations.current?.rulesFor(speciesId)?.any {
            it.kind == pl.asystentfanatyka.core.model.RuleKind.MIN_LENGTH || it.kind == pl.asystentfanatyka.core.model.RuleKind.MAX_LENGTH
        } ?: true

    /** Podpowiada liczbę zatrzymanych dziś sztuk na podstawie zapisanych połowów (użytkownik może ją zmienić). */
    fun suggestKeptToday() {
        val s = _session.value
        val sp = s.speciesId ?: return
        if (s.keptToday != null) return
        viewModelScope.launch {
            val n = c.catches.keptToday(sp, s.catchDate)
            if (n > 0) _session.update { it.copy(keptToday = n) }
        }
    }

    /** Uruchamia deterministyczny silnik przepisów. Zwraca false, gdy brakuje danych wejściowych. */
    suspend fun runCheck(): Boolean {
        val s = _session.value
        val ds = c.regulations.current ?: return false
        val species = s.speciesId ?: return false
        val settings = c.settings.current()
        return try {
            val result = RuleEngine(ds).check(
                CheckInput(
                    speciesId = species,
                    speciesCertainty = s.speciesCertainty,
                    measurement = s.measurement,
                    catchDate = s.catchDate,
                    today = LocalDate.now(),
                    area = s.areaOverride ?: settings.areaContext,
                    localRulesConfirmedByUser = s.localRulesConfirmed,
                    keptTodayCount = s.keptToday,
                    stalenessDays = settings.stalenessDays.toLong()
                )
            )
            _session.update { it.copy(result = result, savedId = null) }
            true
        } catch (t: Throwable) {
            AppLog.e("Błąd silnika przepisów", t)
            false
        }
    }

    suspend fun save(note: String?): Long? {
        val s = _session.value
        val r = s.result ?: return null
        if (s.savedId != null) return s.savedId
        val ds = c.regulations.current ?: return null
        val settings = c.settings.current()
        val area = s.areaOverride ?: settings.areaContext
        val areaText = when (area) {
            AreaContext.Unknown -> "nie wskazano"
            is AreaContext.Known -> if (area.areaCodes.isEmpty()) "pozostałe wody"
            else area.areaCodes.joinToString(", ") { code -> ds.areas.firstOrNull { it.code == code }?.name ?: code }
        }
        val photo = if (settings.keepPhotos) s.photoPath else null
        val id = c.catches.save(
            result = r,
            speciesConfirmed = s.speciesCertainty == SpeciesCertainty.CONFIRMED,
            photoPath = photo,
            areaDescription = areaText,
            datasetVersion = ds.metadata.version,
            measurementWarnings = s.measurementWarnings,
            measurementPointsJson = s.measurementPointsJson,
            recognition = s.recognition,
            note = note
        )
        _session.update { it.copy(savedId = id) }
        AppLog.i("Zapisano połów #$id (${r.speciesName}, wynik ${r.verdict.code})")
        return id
    }
}
