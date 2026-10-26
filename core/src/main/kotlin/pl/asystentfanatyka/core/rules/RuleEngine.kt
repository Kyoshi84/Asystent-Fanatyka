package pl.asystentfanatyka.core.rules

import pl.asystentfanatyka.core.model.ProtectionRule
import pl.asystentfanatyka.core.model.RegulationDataset
import pl.asystentfanatyka.core.model.RuleKind
import pl.asystentfanatyka.core.model.RuleScope
import pl.asystentfanatyka.core.model.VerificationStatus
import pl.asystentfanatyka.core.time.SeasonCalculator
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Deterministyczny silnik przepisów. Nie korzysta z modeli AI – decyzja wynika wyłącznie
 * z ustrukturyzowanych reguł w [RegulationDataset] i danych wejściowych.
 */
class RuleEngine(private val dataset: RegulationDataset) {

    private data class Scenario(val label: String?, val codes: Set<String>)

    private data class ScenarioOutcome(val scenario: Scenario, val findings: List<Finding>, val verdict: Verdict)

    fun check(input: CheckInput): CheckResult {
        val species = requireNotNull(dataset.speciesById(input.speciesId)) {
            "Nieznany gatunek: ${input.speciesId}"
        }
        val meta = dataset.metadata
        val notVerified = mutableListOf<String>()

        // --- Warunki globalne ---------------------------------------------------------------
        val ageDays = ChronoUnit.DAYS.between(meta.verifiedOn, input.today)
        val stale = ageDays > input.stalenessDays
        if (stale) notVerified += "Dane przepisów zweryfikowano ${meta.verifiedOn} ($ageDays dni temu) – mogły się zmienić. Zaktualizuj bazę."
        if (meta.demo) notVerified += "Używana jest baza demonstracyjna – nie stanowi podstawy prawnej."
        val dateCovered = !input.catchDate.isBefore(meta.coverageFrom)
        if (!dateCovered) notVerified += "Baza nie zawiera przepisów obowiązujących przed ${meta.coverageFrom}."
        val speciesConfirmed = input.speciesCertainty == SpeciesCertainty.CONFIRMED
        if (!speciesConfirmed) notVerified += "Gatunek nie został potwierdzony – potwierdź go ręcznie, porównując cechy rozpoznawcze."

        val measurement = input.measurement?.takeIf { it.reliability != MeasurementReliability.UNRELIABLE }
        val lengthRulesExist = dataset.rulesFor(species.id).any {
            (it.kind == RuleKind.MIN_LENGTH || it.kind == RuleKind.MAX_LENGTH) && it.isValidOn(input.catchDate) && scopeApplies(it, input.water)
        }
        when {
            !lengthRulesExist -> Unit
            input.measurement == null -> notVerified += "Brak pomiaru długości."
            input.measurement.reliability == MeasurementReliability.UNRELIABLE ->
                notVerified += "Pomiar oznaczono jako niewiarygodny – zmierz rybę ponownie na macie lub miarką."
            input.measurement.reliability == MeasurementReliability.LOW ->
                notVerified += "Pomiar ma niską wiarygodność (±${fmt(input.measurement.uncertaintyCm)} cm)."
        }

        val localChecked = when (val w = input.water) {
            is WaterContext.Selected -> w.regulationAvailable
            WaterContext.NotSelected -> input.localRulesConfirmedByUser
        }
        if (!localChecked) {
            notVerified += when (input.water) {
                is WaterContext.Selected -> "Brak aktualnego regulaminu wybranego łowiska w bazie."
                WaterContext.NotSelected -> "Regulamin łowiska (limity dobowe, lokalne wymiary i okresy, zakazy) nie został sprawdzony."
            }
        }

        // --- Reguły właściwe dla gatunku, daty i łowiska ---------------------------------------
        val candidates = dataset.rulesFor(species.id)
            .filter { it.isValidOn(input.catchDate) && scopeApplies(it, input.water) }
        val mustNotRelease = species.invasive || candidates.any { it.kind == RuleKind.NO_RELEASE_INVASIVE }

        val speciesCodes = candidates.mapNotNull { it.areaCode }.filter { it != OTHER }.toSet()
        val scenarios = scenarios(speciesCodes, input.area)
        val outcomes = scenarios.map { sc ->
            val rules = selectRules(candidates, sc.codes, speciesCodes)
            val findings = rules.map { evaluate(it, input, measurement, sc.label) }
            ScenarioOutcome(sc, findings, scenarioVerdict(findings))
        }

        val areaAmbiguous = outcomes.map { it.verdict }.distinct().size > 1
        if (areaAmbiguous) {
            notVerified += "Wynik zależy od obszaru, na którym łowisz: " + outcomes.joinToString("; ") {
                "${it.scenario.label ?: "pozostałe wody"} → ${it.verdict.code}"
            } + ". Wskaż obszar w ustawieniach kontroli."
        }

        val findings = outcomes.flatMap { it.findings }.distinctBy { (it.ruleId ?: it.title) + it.detail }.toMutableList()
        if (candidates.none { it.scope == RuleScope.NATIONAL && it.kind in SIZE_OR_SEASON } && dateCovered) {
            findings += Finding(
                title = "Przepisy krajowe",
                detail = "Rozporządzenie nie ustanawia dla tego gatunku wymiaru ani okresu ochronnego. Regulamin gospodarza wody może je wprowadzać.",
                status = FindingStatus.INFO,
                sourceId = NATIONAL_SOURCE_HINT.takeIf { dataset.sourceById(it) != null },
                verified = true
            )
        }
        if (mustNotRelease) {
            findings += Finding(
                title = "Gatunek inwazyjny",
                detail = "Złowionego osobnika nie wolno wypuścić do łowiska ani innych wód – należy go niezwłocznie uśmiercić.",
                status = FindingStatus.INFO,
                ruleId = candidates.firstOrNull { it.kind == RuleKind.NO_RELEASE_INVASIVE }?.id,
                sourceId = candidates.firstOrNull { it.kind == RuleKind.NO_RELEASE_INVASIVE }?.sourceId,
                verified = candidates.any { it.kind == RuleKind.NO_RELEASE_INVASIVE && it.verification == VerificationStatus.VERIFIED }
            )
        }

        findings.filter { (it.status == FindingStatus.FAIL || it.status == FindingStatus.PASS) && !it.verified }.forEach {
            notVerified += "Przepis niezweryfikowany ze źródłem: ${it.title}."
        }
        findings.filter { it.status == FindingStatus.UNKNOWN }.forEach { notVerified += "${it.title}: ${it.detail}" }

        // --- Werdykt ----------------------------------------------------------------------------
        val anyFail = findings.any { it.status == FindingStatus.FAIL }
        val allScenariosConfirmedFail = outcomes.all { o -> o.findings.any { it.status == FindingStatus.FAIL && it.verified } }
        val globalBlocker = stale || meta.demo || !dateCovered || !speciesConfirmed
        val verdict = when {
            allScenariosConfirmedFail && !globalBlocker -> Verdict.NOT_ALLOWED
            anyFail -> Verdict.CANNOT_CONFIRM
            mustNotRelease && speciesConfirmed && !globalBlocker -> Verdict.MEETS_CHECKED
            notVerified.isEmpty() && findings.none { it.status == FindingStatus.UNKNOWN } -> Verdict.MEETS_CHECKED
            else -> Verdict.CANNOT_CONFIRM
        }

        val checked = findings.filter { it.verified && it.status in setOf(FindingStatus.PASS, FindingStatus.FAIL) }
            .map { (if (it.status == FindingStatus.PASS) "✓ " else "✗ ") + it.title + ": " + it.detail }
            .toMutableList()
        if (localChecked && input.water == WaterContext.NotSelected) {
            checked += "✓ Regulamin łowiska – sprawdzony samodzielnie przez wędkarza (oświadczenie w aplikacji)."
        }

        val minRules = candidates.filter { it.kind == RuleKind.MIN_LENGTH }
        val maxRules = candidates.filter { it.kind == RuleKind.MAX_LENGTH }
        val seasonRules = candidates.filter { it.kind == RuleKind.CLOSED_SEASON || it.kind == RuleKind.WEEKDAY_BAN }

        return CheckResult(
            verdict = verdict,
            speciesId = species.id,
            speciesName = species.namePl,
            measurement = input.measurement,
            catchDate = input.catchDate,
            requiredMinLengthText = if (minRules.isEmpty()) "brak w przepisach krajowych" else minRules.joinToString("; ") {
                "powyżej ${fmt(it.lengthCm!!)} cm" + areaSuffix(it)
            },
            maxLengthText = maxRules.takeIf { it.isNotEmpty() }?.joinToString("; ") {
                "do ${fmt(it.lengthCm!!)} cm" + areaSuffix(it)
            },
            closedSeasonText = if (seasonRules.isEmpty()) "brak w przepisach krajowych" else seasonRules.joinToString("; ") {
                describeSeason(it) + areaSuffix(it)
            },
            findings = findings,
            checked = checked,
            notVerified = notVerified.distinct(),
            justification = justification(verdict, findings, notVerified),
            sourceIds = candidates.map { it.sourceId }.distinct(),
            dataVerifiedOn = meta.verifiedOn,
            mustNotRelease = mustNotRelease
        )
    }

    /** Gatunki objęte okresem ochronnym w danym dniu (dla kalendarza), z podziałem na reguły zależne od obszaru. */
    fun closedOn(date: LocalDate, area: AreaContext = AreaContext.Unknown): List<ClosedSpecies> =
        dataset.species.mapNotNull { sp ->
            val rules = dataset.rulesFor(sp.id).filter {
                it.scope == RuleScope.NATIONAL && it.isValidOn(date) &&
                    (it.kind == RuleKind.CLOSED_SEASON || it.kind == RuleKind.WEEKDAY_BAN)
            }
            if (rules.isEmpty()) return@mapNotNull null
            val speciesCodes = dataset.rulesFor(sp.id).mapNotNull { it.areaCode }.filter { it != OTHER }.toSet()
            val active = scenarios(speciesCodes, area).flatMap { sc ->
                selectRules(rules, sc.codes, speciesCodes).filter { r ->
                    if (r.kind == RuleKind.CLOSED_SEASON) SeasonCalculator.isClosed(r, date)
                    else SeasonCalculator.isWeekdayBanned(r, date)
                }
            }.distinct()
            if (active.isEmpty()) null else ClosedSpecies(
                speciesId = sp.id,
                speciesName = sp.namePl,
                allAreas = active.size == rules.size || active.all { it.areaCode == null },
                rules = active
            )
        }

    data class ClosedSpecies(
        val speciesId: String,
        val speciesName: String,
        /** false = ochrona tylko na części obszarów wymienionych w przepisie. */
        val allAreas: Boolean,
        val rules: List<ProtectionRule>
    )

    // ------------------------------------------------------------------------------------------

    private fun scopeApplies(rule: ProtectionRule, water: WaterContext): Boolean = when (rule.scope) {
        RuleScope.NATIONAL -> true
        RuleScope.DISTRICT -> water is WaterContext.Selected && water.districtId != null && water.districtId == rule.scopeRef
        RuleScope.WATER -> water is WaterContext.Selected && water.waterId == rule.scopeRef
    }

    private fun scenarios(speciesCodes: Set<String>, area: AreaContext): List<Scenario> = when (area) {
        is AreaContext.Known -> listOf(Scenario(null, expand(area.areaCodes)))
        AreaContext.Unknown ->
            if (speciesCodes.isEmpty()) listOf(Scenario(null, emptySet()))
            else speciesCodes.sorted().map { code ->
                Scenario(dataset.areas.firstOrNull { it.code == code }?.name ?: code, expand(setOf(code)))
            } + Scenario("pozostałe wody", emptySet())
    }

    private fun expand(codes: Set<String>): Set<String> {
        val result = codes.toMutableSet()
        var changed = true
        while (changed) {
            changed = false
            for (c in result.toList()) {
                dataset.areas.firstOrNull { it.code == c }?.implies?.forEach { if (result.add(it)) changed = true }
            }
        }
        return result
    }

    private fun selectRules(rules: List<ProtectionRule>, codes: Set<String>, speciesCodes: Set<String>) =
        rules.filter { r ->
            when (r.areaCode) {
                null -> true
                OTHER -> codes.intersect(speciesCodes).isEmpty()
                else -> r.areaCode in codes
            }
        }

    private fun evaluate(rule: ProtectionRule, input: CheckInput, m: MeasurementInput?, scenarioLabel: String?): Finding {
        val verified = rule.verification == VerificationStatus.VERIFIED
        val suffix = areaSuffix(rule)
        fun f(title: String, detail: String, status: FindingStatus) = Finding(
            title = title + suffix, detail = detail, status = status, ruleId = rule.id,
            sourceId = rule.sourceId, sourceLocator = rule.sourceLocator, verified = verified
        )
        return when (rule.kind) {
            RuleKind.MIN_LENGTH -> {
                val min = rule.lengthCm!!
                if (m == null) f("Wymiar ochronny", "Wymagana długość powyżej ${fmt(min)} cm – brak wiarygodnego pomiaru.", FindingStatus.UNKNOWN)
                else when {
                    m.lengthCm - m.uncertaintyCm > min -> f("Wymiar ochronny", "${fmt(m.lengthCm)} cm${pm(m)} > ${fmt(min)} cm", FindingStatus.PASS)
                    m.lengthCm + m.uncertaintyCm <= min -> f("Wymiar ochronny", "${fmt(m.lengthCm)} cm${pm(m)} – nie przekracza ${fmt(min)} cm", FindingStatus.FAIL)
                    else -> f("Wymiar ochronny", "${fmt(m.lengthCm)} cm${pm(m)} – w granicach niepewności pomiaru wokół ${fmt(min)} cm. Zmierz dokładniej.", FindingStatus.UNKNOWN)
                }
            }
            RuleKind.MAX_LENGTH -> {
                val max = rule.lengthCm!!
                if (m == null) f("Górny wymiar", "Dozwolone do ${fmt(max)} cm – brak wiarygodnego pomiaru.", FindingStatus.UNKNOWN)
                else when {
                    m.lengthCm + m.uncertaintyCm <= max -> f("Górny wymiar", "${fmt(m.lengthCm)} cm${pm(m)} ≤ ${fmt(max)} cm", FindingStatus.PASS)
                    m.lengthCm - m.uncertaintyCm > max -> f("Górny wymiar", "${fmt(m.lengthCm)} cm${pm(m)} przekracza ${fmt(max)} cm", FindingStatus.FAIL)
                    else -> f("Górny wymiar", "${fmt(m.lengthCm)} cm${pm(m)} – w granicach niepewności wokół ${fmt(max)} cm.", FindingStatus.UNKNOWN)
                }
            }
            RuleKind.CLOSED_SEASON -> {
                val p = SeasonCalculator.occurrenceContaining(rule.seasonStart!!, rule.seasonEnd!!, input.catchDate, rule.holidayShortening)
                if (p != null) f("Okres ochronny", "${input.catchDate} mieści się w okresie ${p.start} – ${p.end}", FindingStatus.FAIL)
                else f("Okres ochronny", "${input.catchDate} poza okresem ${describeSeason(rule)}", FindingStatus.PASS)
            }
            RuleKind.WEEKDAY_BAN -> {
                if (SeasonCalculator.isWeekdayBanned(rule, input.catchDate))
                    f("Zakaz połowu w dniu tygodnia", "${dayName(input.catchDate)} – w tym okresie połów niedozwolony (${describeSeason(rule)})", FindingStatus.FAIL)
                else f("Zakaz połowu w dniu tygodnia", "${dayName(input.catchDate)} – nie obowiązuje", FindingStatus.PASS)
            }
            RuleKind.DAILY_LIMIT_COUNT -> {
                val limit = rule.dailyLimit!!
                val kept = input.keptTodayCount
                when {
                    kept == null -> f("Limit dobowy", "Limit $limit szt. – nie podano, ile sztuk już zatrzymano.", FindingStatus.UNKNOWN)
                    kept >= limit -> f("Limit dobowy", "Zatrzymano już $kept szt. (limit $limit)", FindingStatus.FAIL)
                    else -> f("Limit dobowy", "To będzie ${kept + 1}. sztuka z $limit dozwolonych", FindingStatus.PASS)
                }
            }
            RuleKind.NO_KEEP -> f("Zakaz zatrzymania", rule.note ?: "Na tej wodzie nie wolno zatrzymywać tego gatunku.", FindingStatus.FAIL)
            RuleKind.NO_RELEASE_INVASIVE -> f("Zakaz wypuszczania", "Gatunek nierodzimy – nie wolno wypuścić.", FindingStatus.PASS)
        }
    }

    private fun scenarioVerdict(findings: List<Finding>): Verdict = when {
        findings.any { it.status == FindingStatus.FAIL && it.verified } -> Verdict.NOT_ALLOWED
        findings.any { it.status == FindingStatus.FAIL || it.status == FindingStatus.UNKNOWN || !it.verified } -> Verdict.CANNOT_CONFIRM
        else -> Verdict.MEETS_CHECKED
    }

    private fun justification(verdict: Verdict, findings: List<Finding>, notVerified: List<String>): String = when (verdict) {
        Verdict.NOT_ALLOWED -> "Wykryto potwierdzony zakaz: " +
            findings.filter { it.status == FindingStatus.FAIL && it.verified }.joinToString("; ") { "${it.title} – ${it.detail}" } +
            ". Rybę należy wypuścić."
        Verdict.MEETS_CHECKED -> "Wszystkie sprawdzone warunki są spełnione. Wynik dotyczy wyłącznie warunków wymienionych poniżej."
        Verdict.CANNOT_CONFIRM -> buildString {
            val fails = findings.filter { it.status == FindingStatus.FAIL }
            if (fails.isNotEmpty()) append("Możliwy zakaz (niepotwierdzony): ").append(fails.joinToString("; ") { it.title }).append(". ")
            append("Nie wszystkie warunki można było sprawdzić")
            if (notVerified.isNotEmpty()) append(" – najważniejsze: ").append(notVerified.first())
            append(" W razie wątpliwości wypuść rybę.")
        }
    }

    private fun areaSuffix(rule: ProtectionRule): String = when (rule.areaCode) {
        null -> ""
        OTHER -> " (pozostałe wody)"
        else -> " (" + (dataset.areas.firstOrNull { it.code == rule.areaCode }?.name ?: rule.areaCode) + ")"
    }

    private fun describeSeason(rule: ProtectionRule): String {
        val base = SeasonCalculator.describe(rule.seasonStart!!, rule.seasonEnd!!)
        return if (rule.kind == RuleKind.WEEKDAY_BAN)
            "w okresie $base zakaz w: " + rule.bannedDays.sorted().joinToString(", ") { DAY_NAMES[it.value - 1] }
        else base
    }

    companion object {
        const val OTHER = "OTHER"
        private const val NATIONAL_SOURCE_HINT = "DzU-2023-1373"
        private val SIZE_OR_SEASON = setOf(RuleKind.MIN_LENGTH, RuleKind.CLOSED_SEASON, RuleKind.MAX_LENGTH)
        private val DAY_NAMES = listOf("poniedziałek", "wtorek", "środa", "czwartek", "piątek", "sobota", "niedziela")
        fun dayName(d: LocalDate) = DAY_NAMES[d.dayOfWeek.value - 1]
        fun fmt(v: Double): String =
            if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.forLanguageTag("pl"), "%.1f", v)
        private fun pm(m: MeasurementInput) = if (m.uncertaintyCm > 0) " (±${fmt(m.uncertaintyCm)})" else ""
    }
}
