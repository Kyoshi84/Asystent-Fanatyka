package pl.asystentfanatyka.data.db

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
import pl.asystentfanatyka.core.model.WaterType
import pl.asystentfanatyka.core.model.YearDay
import java.time.DayOfWeek
import java.time.LocalDate

private fun String.csv(): List<String> = split(",").map { it.trim() }.filter { it.isNotEmpty() }
private fun String?.date(): LocalDate? = this?.let { LocalDate.parse(it) }

fun RegulationDataset.toEntities(origin: String, nowMillis: Long): DatasetEntities {
    val v = metadata.version
    return DatasetEntities(
        meta = DatasetMetadataEntity(
            version = v, datasetId = metadata.datasetId, schemaVersion = metadata.schemaVersion,
            generatedOn = metadata.generatedOn.toString(), verifiedOn = metadata.verifiedOn.toString(),
            coverageFrom = metadata.coverageFrom.toString(), coverageNote = metadata.coverageNote,
            demo = metadata.demo, active = false, origin = origin, importedAtMillis = nowMillis
        ),
        sources = sources.map {
            RegulationSourceEntity(v, it.id, it.title, it.reference, it.url, it.type.name,
                it.publishedOn?.toString(), it.verifiedOn?.toString(), it.notes)
        },
        areas = areas.map { AreaEntity(v, it.code, it.name, it.description, it.implies.joinToString(",")) },
        species = species.map {
            FishSpeciesEntity(v, it.id, it.namePl, it.nameLatin, it.features.joinToString("\n"), it.habitat,
                it.similarSpeciesIds.joinToString(","), it.waterTypes.joinToString(",") { w -> w.name },
                it.imageAsset, it.invasive, it.descriptionSource)
        },
        rules = rules.map {
            ProtectionRuleEntity(
                datasetVersion = v, id = it.id, speciesId = it.speciesId, kind = it.kind.name, scope = it.scope.name,
                areaCode = it.areaCode, scopeRef = it.scopeRef, lengthCm = it.lengthCm,
                seasonStart = it.seasonStart?.toString(), seasonEnd = it.seasonEnd?.toString(),
                holidayShortening = it.holidayShortening, bannedDays = it.bannedDays.joinToString(",") { d -> d.name },
                dailyLimit = it.dailyLimit, validFrom = it.validFrom.toString(), validTo = it.validTo?.toString(),
                sourceId = it.sourceId, sourceLocator = it.sourceLocator, verification = it.verification.name,
                verifiedOn = it.verifiedOn?.toString(), note = it.note
            )
        }
    )
}

data class DatasetEntities(
    val meta: DatasetMetadataEntity,
    val sources: List<RegulationSourceEntity>,
    val areas: List<AreaEntity>,
    val species: List<FishSpeciesEntity>,
    val rules: List<ProtectionRuleEntity>
)

fun DatasetEntities.toModel(): RegulationDataset = RegulationDataset(
    metadata = DatasetMetadata(
        datasetId = meta.datasetId, version = meta.version, schemaVersion = meta.schemaVersion,
        generatedOn = LocalDate.parse(meta.generatedOn), verifiedOn = LocalDate.parse(meta.verifiedOn),
        coverageFrom = LocalDate.parse(meta.coverageFrom), coverageNote = meta.coverageNote, demo = meta.demo
    ),
    sources = sources.map {
        RegulationSource(it.id, it.title, it.reference, it.url, SourceType.valueOf(it.type),
            it.publishedOn.date(), it.verifiedOn.date(), it.notes)
    },
    areas = areas.map { Area(it.code, it.name, it.description, it.implies.csv()) },
    species = species.map {
        FishSpecies(
            id = it.id, namePl = it.namePl, nameLatin = it.nameLatin,
            features = it.features.split("\n").filter { f -> f.isNotBlank() }, habitat = it.habitat,
            similarSpeciesIds = it.similarIds.csv(), waterTypes = it.waterTypes.csv().map { w -> WaterType.valueOf(w) }.toSet(),
            imageAsset = it.imageAsset, invasive = it.invasive, descriptionSource = it.descriptionSource
        )
    },
    rules = rules.map {
        ProtectionRule(
            id = it.id, speciesId = it.speciesId, kind = RuleKind.valueOf(it.kind), scope = RuleScope.valueOf(it.scope),
            areaCode = it.areaCode, scopeRef = it.scopeRef, lengthCm = it.lengthCm,
            seasonStart = it.seasonStart?.let { s -> YearDay.parse(s) }, seasonEnd = it.seasonEnd?.let { s -> YearDay.parse(s) },
            holidayShortening = it.holidayShortening, bannedDays = it.bannedDays.csv().map { d -> DayOfWeek.valueOf(d) }.toSet(),
            dailyLimit = it.dailyLimit, validFrom = LocalDate.parse(it.validFrom), validTo = it.validTo.date(),
            sourceId = it.sourceId, sourceLocator = it.sourceLocator, verification = VerificationStatus.valueOf(it.verification),
            verifiedOn = it.verifiedOn.date(), note = it.note
        )
    }
)
