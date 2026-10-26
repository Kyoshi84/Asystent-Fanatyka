package pl.asystentfanatyka.core.model

import java.time.DayOfWeek
import java.time.LocalDate

/** Status weryfikacji rekordu z danymi. Tylko VERIFIED może być podstawą stanu A lub B. */
enum class VerificationStatus { VERIFIED, UNVERIFIED, DEMO }

/** Zasięg przepisu: krajowy, okręg gospodarza wody albo konkretna woda. */
enum class RuleScope { NATIONAL, DISTRICT, WATER }

enum class RuleKind {
    /** Wymiar ochronny: zatrzymać można rybę o długości POWYŻEJ wartości (§ 6 ust. 2 rozp. 2023/1373). */
    MIN_LENGTH,
    /** Górny wymiar (np. w regulaminie łowiska): zatrzymać można rybę nie dłuższą niż wartość. */
    MAX_LENGTH,
    /** Okres ochronny w ujęciu rocznym, może przechodzić przez koniec roku. */
    CLOSED_SEASON,
    /** Zakaz połowu w określone dni tygodnia w wyznaczonym przedziale roku. */
    WEEKDAY_BAN,
    /** Dobowy limit sztuk. */
    DAILY_LIMIT_COUNT,
    /** Gatunek inwazyjny – nie wolno wypuszczać (§ 8 rozp. 2023/1373). */
    NO_RELEASE_INVASIVE,
    /** Całkowity zakaz zatrzymania na danej wodzie (np. regulamin łowiska „no kill”). */
    NO_KEEP
}

/** Dzień w roku zapisany jako MM-DD. "02-29" oznacza „koniec lutego” (w latach nieprzestępnych 28.02). */
data class YearDay(val month: Int, val day: Int) {
    init {
        require(month in 1..12) { "Niepoprawny miesiąc: $month" }
        require(day in 1..31) { "Niepoprawny dzień: $day" }
    }

    fun inYear(year: Int): LocalDate {
        val ym = java.time.YearMonth.of(year, month)
        return ym.atDay(minOf(day, ym.lengthOfMonth()))
    }

    override fun toString(): String = "%02d-%02d".format(month, day)

    companion object {
        fun parse(s: String): YearDay {
            val parts = s.trim().split("-")
            require(parts.size == 2) { "Oczekiwano formatu MM-DD, otrzymano: $s" }
            return YearDay(parts[0].toInt(), parts[1].toInt())
        }
    }
}

data class RegulationSource(
    val id: String,
    val title: String,
    val reference: String,
    val url: String?,
    /** Rodzaj źródła: akt prawny, regulamin okręgu, regulamin łowiska, artykuł pomocniczy. */
    val type: SourceType,
    val publishedOn: LocalDate?,
    val verifiedOn: LocalDate?,
    val notes: String? = null
)

enum class SourceType { LEGAL_ACT, DISTRICT_REGULATION, WATER_REGULATION, SECONDARY }

/** Obszar szczególny wymieniony w przepisie (np. „Wisła od zapory we Włocławku do ujścia”). */
data class Area(
    val code: String,
    val name: String,
    val description: String,
    /** Obszary, które ten obszar w całości zawiera się w (np. „Wisła do ujścia Sanu” ⊂ „Wisła powyżej Włocławka”). */
    val implies: List<String> = emptyList()
)

data class FishSpecies(
    val id: String,
    val namePl: String,
    val nameLatin: String,
    val features: List<String>,
    val habitat: String,
    val similarSpeciesIds: List<String>,
    val waterTypes: Set<WaterType>,
    val imageAsset: String?,
    val invasive: Boolean,
    val descriptionSource: String
)

enum class WaterType { RIVER, LAKE, STREAM, POND, RESERVOIR, ESTUARY }

data class ProtectionRule(
    val id: String,
    val speciesId: String,
    val kind: RuleKind,
    val scope: RuleScope,
    /** Kod rejonu z listy [Area]. null = wszystkie wody w zasięgu. "OTHER" = wody spoza obszarów innych reguł tego gatunku i rodzaju. */
    val areaCode: String?,
    /** Identyfikator okręgu lub wody, gdy scope ≠ NATIONAL. */
    val scopeRef: String?,
    val lengthCm: Double? = null,
    val seasonStart: YearDay? = null,
    val seasonEnd: YearDay? = null,
    /** § 7 ust. 2: skrócenie okresu, gdy pierwszy/ostatni dzień przypada na dzień ustawowo wolny od pracy. */
    val holidayShortening: Boolean = false,
    val bannedDays: Set<DayOfWeek> = emptySet(),
    val dailyLimit: Int? = null,
    val validFrom: LocalDate,
    val validTo: LocalDate?,
    val sourceId: String,
    val sourceLocator: String,
    val verification: VerificationStatus,
    val verifiedOn: LocalDate?,
    val note: String? = null
) {
    fun isValidOn(date: LocalDate): Boolean =
        !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo))
}

data class DatasetMetadata(
    val datasetId: String,
    val version: Int,
    val schemaVersion: Int,
    val generatedOn: LocalDate,
    /** Najstarsza data weryfikacji spośród źródeł – podstawa ostrzeżenia o nieaktualnych danych. */
    val verifiedOn: LocalDate,
    /** Od tej daty zbiór reguł krajowych jest kompletny względem źródeł prawnych. Wcześniejsze daty → brak danych. */
    val coverageFrom: LocalDate,
    val coverageNote: String,
    val demo: Boolean
)

data class RegulationDataset(
    val metadata: DatasetMetadata,
    val sources: List<RegulationSource>,
    val areas: List<Area>,
    val species: List<FishSpecies>,
    val rules: List<ProtectionRule>
) {
    fun speciesById(id: String): FishSpecies? = species.firstOrNull { it.id == id }
    fun sourceById(id: String): RegulationSource? = sources.firstOrNull { it.id == id }
    fun rulesFor(speciesId: String): List<ProtectionRule> = rules.filter { it.speciesId == speciesId }
}
