package pl.asystentfanatyka.core.data

import pl.asystentfanatyka.core.model.RegulationDataset
import pl.asystentfanatyka.core.model.RuleKind
import pl.asystentfanatyka.core.model.RuleScope
import pl.asystentfanatyka.core.model.VerificationStatus
import pl.asystentfanatyka.core.rules.RuleEngine

/** Walidacja spójności całego zbioru. Błąd = zbiór odrzucony w całości; ostrzeżenie = zbiór przyjęty. */
object DatasetValidator {

    fun validate(d: RegulationDataset): Pair<List<String>, List<String>> {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        fun dupes(name: String, ids: List<String>) =
            ids.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { errors += "Zduplikowany identyfikator $name: $it" }
        dupes("gatunku", d.species.map { it.id })
        dupes("reguły", d.rules.map { it.id })
        dupes("źródła", d.sources.map { it.id })
        dupes("obszaru", d.areas.map { it.code })

        val speciesIds = d.species.map { it.id }.toSet()
        val sourceIds = d.sources.map { it.id }.toSet()
        val areaCodes = d.areas.map { it.code }.toSet()

        if (d.species.isEmpty()) errors += "Zbiór nie zawiera gatunków"
        if (d.metadata.verifiedOn.isAfter(d.metadata.generatedOn)) warnings += "Data weryfikacji jest późniejsza niż data wygenerowania pliku"

        d.areas.forEach { a -> a.implies.filter { it !in areaCodes }.forEach { errors += "Obszar ${a.code}: nieznany obszar nadrzędny $it" } }
        d.species.forEach { s ->
            s.similarSpeciesIds.filter { it !in speciesIds }.forEach { warnings += "Gatunek ${s.id}: nieznany gatunek podobny $it" }
            if (s.features.isEmpty()) warnings += "Gatunek ${s.id}: brak cech rozpoznawczych"
        }

        for (r in d.rules) {
            val p = "Reguła ${r.id}"
            if (r.speciesId !in speciesIds) errors += "$p: nieznany gatunek ${r.speciesId}"
            if (r.sourceId !in sourceIds) errors += "$p: nieznane źródło ${r.sourceId}"
            if (r.areaCode != null && r.areaCode != RuleEngine.OTHER && r.areaCode !in areaCodes) errors += "$p: nieznany obszar ${r.areaCode}"
            if (r.validTo != null && r.validTo.isBefore(r.validFrom)) errors += "$p: data końca obowiązywania przed datą początku"
            when (r.scope) {
                RuleScope.NATIONAL -> if (r.scopeRef != null) errors += "$p: reguła krajowa nie może mieć scopeRef"
                RuleScope.DISTRICT, RuleScope.WATER -> if (r.scopeRef == null) errors += "$p: reguła lokalna wymaga scopeRef"
            }
            when (r.kind) {
                RuleKind.MIN_LENGTH, RuleKind.MAX_LENGTH ->
                    if (r.lengthCm == null || r.lengthCm <= 0.0) errors += "$p: wymagany dodatni lengthCm"
                RuleKind.CLOSED_SEASON ->
                    if (r.seasonStart == null || r.seasonEnd == null) errors += "$p: wymagane seasonStart i seasonEnd"
                RuleKind.WEEKDAY_BAN -> {
                    if (r.seasonStart == null || r.seasonEnd == null) errors += "$p: wymagane seasonStart i seasonEnd"
                    if (r.bannedDays.isEmpty()) errors += "$p: wymagane bannedDays"
                }
                RuleKind.DAILY_LIMIT_COUNT -> if (r.dailyLimit == null || r.dailyLimit <= 0) errors += "$p: wymagany dodatni dailyLimit"
                RuleKind.NO_KEEP, RuleKind.NO_RELEASE_INVASIVE -> Unit
            }
            if (r.verification == VerificationStatus.VERIFIED) {
                if (r.verifiedOn == null) errors += "$p: reguła VERIFIED wymaga daty weryfikacji"
                if (d.metadata.demo) errors += "$p: zbiór demonstracyjny nie może zawierać reguł VERIFIED"
            }
            if (r.areaCode == RuleEngine.OTHER &&
                d.rules.none { it.speciesId == r.speciesId && it.areaCode != null && it.areaCode != RuleEngine.OTHER }
            ) warnings += "$p: obszar OTHER bez reguł dla obszarów szczególnych"
        }
        return errors to warnings
    }
}
