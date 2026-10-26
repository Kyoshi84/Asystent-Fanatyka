package pl.asystentfanatyka.core.rules

import java.time.LocalDate

/** Pewność co do gatunku. Tylko CONFIRMED pozwala na stan A lub B. */
enum class SpeciesCertainty {
    /** Gatunek wybrany lub potwierdzony przez użytkownika. */
    CONFIRMED,
    /** Propozycja modelu/klucza, niepotwierdzona. */
    UNCERTAIN
}

enum class MeasurementMethod(val label: String) {
    MAT_READING("Odczyt z maty / miarki"),
    PHOTO_CALIBRATED("Zdjęcie z kalibracją skali"),
    MANUAL_ESTIMATE("Szacunek użytkownika")
}

enum class MeasurementReliability { RELIABLE, LOW, UNRELIABLE }

data class MeasurementInput(
    val lengthCm: Double,
    /** Niepewność ± w cm (połowa szerokości przedziału). */
    val uncertaintyCm: Double,
    val method: MeasurementMethod,
    val reliability: MeasurementReliability
) {
    init {
        require(lengthCm > 0) { "Długość musi być dodatnia" }
        require(uncertaintyCm >= 0) { "Niepewność nie może być ujemna" }
    }
}

/** Wiedza o obszarze, na którym łowimy, w rozumieniu obszarów wymienionych w przepisach. */
sealed interface AreaContext {
    /** Użytkownik nie wie / nie podał. Silnik sprawdzi wszystkie warianty. */
    data object Unknown : AreaContext
    /** Użytkownik wskazał obszary szczególne (pusty zbiór = „żaden z wymienionych, pozostałe wody”). */
    data class Known(val areaCodes: Set<String>) : AreaContext
}

/**
 * Łowisko. Moduł wyboru łowiska nie jest częścią tego MVP, ale silnik obsługuje
 * reguły okręgów i wód, aby można go było dodać bez zmiany logiki.
 */
sealed interface WaterContext {
    data object NotSelected : WaterContext
    data class Selected(
        val waterId: String,
        val districtId: String?,
        /** Czy w bazie jest aktualny, zweryfikowany regulamin tej wody. */
        val regulationAvailable: Boolean
    ) : WaterContext
}

data class CheckInput(
    val speciesId: String,
    val speciesCertainty: SpeciesCertainty,
    val measurement: MeasurementInput?,
    val catchDate: LocalDate,
    val today: LocalDate,
    val area: AreaContext = AreaContext.Unknown,
    val water: WaterContext = WaterContext.NotSelected,
    /** Użytkownik oświadcza, że sam sprawdził regulamin łowiska i nie zawiera on dodatkowych ograniczeń dla gatunku. */
    val localRulesConfirmedByUser: Boolean = false,
    /** Ile sztuk tego gatunku użytkownik już zatrzymał w bieżącej dobie (null = nie podano). */
    val keptTodayCount: Int? = null,
    /** Po ilu dniach od weryfikacji dane uznaje się za nieaktualne. */
    val stalenessDays: Long = 180
)

enum class Verdict(val code: Char, val title: String) {
    NOT_ALLOWED('A', "Nie spełnia wymagań"),
    MEETS_CHECKED('B', "Spełnia sprawdzone wymagania"),
    CANNOT_CONFIRM('C', "Nie można potwierdzić legalności")
}

enum class FindingStatus { PASS, FAIL, UNKNOWN, INFO }

data class Finding(
    val title: String,
    val detail: String,
    val status: FindingStatus,
    val ruleId: String? = null,
    val sourceId: String? = null,
    val sourceLocator: String? = null,
    val verified: Boolean = false
)

data class CheckResult(
    val verdict: Verdict,
    val speciesId: String,
    val speciesName: String,
    val measurement: MeasurementInput?,
    val catchDate: LocalDate,
    val requiredMinLengthText: String,
    val maxLengthText: String?,
    val closedSeasonText: String,
    val findings: List<Finding>,
    /** Co zostało sprawdzone i spełnione/niespełnione. */
    val checked: List<String>,
    /** Czego nie udało się zweryfikować. */
    val notVerified: List<String>,
    val justification: String,
    val sourceIds: List<String>,
    val dataVerifiedOn: LocalDate,
    val mustNotRelease: Boolean
)
