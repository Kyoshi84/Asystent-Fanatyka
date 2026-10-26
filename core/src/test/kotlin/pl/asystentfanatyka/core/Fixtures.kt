package pl.asystentfanatyka.core

import pl.asystentfanatyka.core.data.DatasetParser
import pl.asystentfanatyka.core.data.ParseResult
import pl.asystentfanatyka.core.model.Area
import pl.asystentfanatyka.core.model.DatasetMetadata
import pl.asystentfanatyka.core.model.FishSpecies
import pl.asystentfanatyka.core.model.ProtectionRule
import pl.asystentfanatyka.core.model.RegulationDataset
import pl.asystentfanatyka.core.model.RegulationSource
import pl.asystentfanatyka.core.model.RuleKind
import pl.asystentfanatyka.core.model.RuleScope
import pl.asystentfanatyka.core.model.SourceType
import pl.asystentfanatyka.core.model.VerificationStatus
import pl.asystentfanatyka.core.model.YearDay
import java.io.File
import java.time.LocalDate

object Fixtures {
    val VERIFIED_ON: LocalDate = LocalDate.of(2026, 10, 26)
    private val FROM: LocalDate = LocalDate.of(2023, 7, 27)

    fun nationalDataset(): RegulationDataset {
        val path = System.getProperty("dataset.path")
            ?: listOf("../app/src/main/assets/dataset/national.json", "app/src/main/assets/dataset/national.json")
                .firstOrNull { File(it).exists() }
            ?: error("Ustaw -Ddataset.path na plik national.json")
        return when (val r = DatasetParser.parse(File(path).readText())) {
            is ParseResult.Ok -> r.dataset
            is ParseResult.Error -> error("Plik danych niepoprawny: ${r.errors}")
        }
    }

    private fun species(id: String, name: String, invasive: Boolean = false) = FishSpecies(
        id = id, namePl = name, nameLatin = "Testus $id", features = listOf("cecha"), habitat = "",
        similarSpeciesIds = emptyList(), waterTypes = emptySet(), imageAsset = null, invasive = invasive, descriptionSource = "test"
    )

    fun rule(
        id: String, speciesId: String, kind: RuleKind,
        scope: RuleScope = RuleScope.NATIONAL, scopeRef: String? = null, area: String? = null,
        length: Double? = null, start: String? = null, end: String? = null, shortening: Boolean = false,
        days: Set<java.time.DayOfWeek> = emptySet(), limit: Int? = null,
        verification: VerificationStatus = VerificationStatus.VERIFIED,
        validFrom: LocalDate = FROM, validTo: LocalDate? = null
    ) = ProtectionRule(
        id = id, speciesId = speciesId, kind = kind, scope = scope, areaCode = area, scopeRef = scopeRef,
        lengthCm = length, seasonStart = start?.let { YearDay.parse(it) }, seasonEnd = end?.let { YearDay.parse(it) },
        holidayShortening = shortening, bannedDays = days, dailyLimit = limit, validFrom = validFrom, validTo = validTo,
        sourceId = "src", sourceLocator = "§ test", verification = verification,
        verifiedOn = if (verification == VerificationStatus.VERIFIED) VERIFIED_ON else null
    )

    /**
     * Mały, kontrolowany zbiór testowy. Reguły lokalne (okręg „okreg-a”, woda „woda-1” i „woda-2”) są
     * FIKCYJNE i służą wyłącznie do testów silnika.
     */
    fun testDataset(
        rules: List<ProtectionRule> = defaultRules(),
        verifiedOn: LocalDate = VERIFIED_ON,
        demo: Boolean = false
    ) = RegulationDataset(
        metadata = DatasetMetadata("test", 1, 1, verifiedOn, verifiedOn, FROM, "test", demo),
        sources = listOf(RegulationSource("src", "Źródło testowe", "test", null, SourceType.LEGAL_ACT, null, verifiedOn)),
        areas = listOf(
            Area("A", "Obszar A", "", implies = listOf("B")),
            Area("B", "Obszar B", ""),
            Area("C", "Obszar C", "")
        ),
        species = listOf(
            species("szczupak", "Szczupak"), species("pstrag", "Pstrąg"), species("wegorz", "Węgorz"),
            species("okon", "Okoń"), species("babka", "Babka", invasive = true), species("karp", "Karp"),
            species("sum", "Sum")
        ),
        rules = rules
    )

    fun defaultRules() = listOf(
        rule("szcz-min", "szczupak", RuleKind.MIN_LENGTH, length = 45.0),
        rule("szcz-os", "szczupak", RuleKind.CLOSED_SEASON, start = "01-01", end = "04-30", shortening = true),
        rule("pstrag-min-a", "pstrag", RuleKind.MIN_LENGTH, area = "A", length = 25.0),
        rule("pstrag-min-inne", "pstrag", RuleKind.MIN_LENGTH, area = "OTHER", length = 30.0),
        rule("pstrag-os-a", "pstrag", RuleKind.CLOSED_SEASON, area = "A", start = "09-01", end = "01-31", shortening = true),
        rule("pstrag-os-inne", "pstrag", RuleKind.CLOSED_SEASON, area = "OTHER", start = "09-01", end = "12-31", shortening = true),
        rule("weg-min", "wegorz", RuleKind.MIN_LENGTH, length = 50.0),
        rule("weg-os", "wegorz", RuleKind.CLOSED_SEASON, start = "12-01", end = "03-31", shortening = true),
        rule("weg-lim", "wegorz", RuleKind.DAILY_LIMIT_COUNT, limit = 2),
        rule("babka-inw", "babka", RuleKind.NO_RELEASE_INVASIVE),
        rule("sum-min", "sum", RuleKind.MIN_LENGTH, length = 70.0, verification = VerificationStatus.UNVERIFIED),
        // Fikcyjne reguły lokalne do testów:
        rule("karp-okreg-min", "karp", RuleKind.MIN_LENGTH, scope = RuleScope.DISTRICT, scopeRef = "okreg-a", length = 30.0),
        rule("karp-woda1-max", "karp", RuleKind.MAX_LENGTH, scope = RuleScope.WATER, scopeRef = "woda-1", length = 60.0),
        rule("szcz-woda2-nokeep", "szczupak", RuleKind.NO_KEEP, scope = RuleScope.WATER, scopeRef = "woda-2"),
        rule("okon-woda1-lim", "okon", RuleKind.DAILY_LIMIT_COUNT, scope = RuleScope.WATER, scopeRef = "woda-1", limit = 5)
    )
}
