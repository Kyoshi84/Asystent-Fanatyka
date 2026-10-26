package pl.asystentfanatyka.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
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

/** Wynik importu: albo kompletny i poprawny zbiór danych, albo lista błędów (nic nie zostaje zapisane). */
sealed interface ParseResult {
    data class Ok(val dataset: RegulationDataset, val warnings: List<String>) : ParseResult
    data class Error(val errors: List<String>) : ParseResult
}

/**
 * Parser i walidator pliku danych (JSON). Działa na drzewie JSON, bez generowania kodu,
 * dzięki czemu komunikaty błędów wskazują dokładne miejsce problemu.
 * Format opisano w docs/FORMAT_DANYCH.md.
 */
object DatasetParser {

    const val SUPPORTED_SCHEMA = 1

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    fun parse(text: String): ParseResult {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject ?: return ParseResult.Error(listOf("Korzeń pliku musi być obiektem JSON"))
        } catch (e: Exception) {
            return ParseResult.Error(listOf("Niepoprawny JSON: ${e.message}"))
        }
        val ctx = Ctx()
        val meta = ctx.obj(root, "metadata", "metadata")?.let { parseMeta(ctx, it) }
        val sources = ctx.arr(root, "sources", "").mapIndexedNotNull { i, e -> ctx.asObj(e, "sources[$i]")?.let { parseSource(ctx, it, "sources[$i]") } }
        val areas = ctx.arr(root, "areas", "").mapIndexedNotNull { i, e -> ctx.asObj(e, "areas[$i]")?.let { parseArea(ctx, it, "areas[$i]") } }
        val species = ctx.arr(root, "species", "").mapIndexedNotNull { i, e -> ctx.asObj(e, "species[$i]")?.let { parseSpecies(ctx, it, "species[$i]") } }
        val rules = ctx.arr(root, "rules", "").mapIndexedNotNull { i, e -> ctx.asObj(e, "rules[$i]")?.let { parseRule(ctx, it, "rules[$i]") } }

        if (meta == null || ctx.errors.isNotEmpty()) return ParseResult.Error(ctx.errors.ifEmpty { listOf("Brak metadanych") })
        val dataset = RegulationDataset(meta, sources, areas, species, rules)
        val (errors, warnings) = DatasetValidator.validate(dataset)
        return if (errors.isEmpty()) ParseResult.Ok(dataset, warnings) else ParseResult.Error(errors)
    }

    private fun parseMeta(c: Ctx, o: JsonObject): DatasetMetadata? {
        val p = "metadata"
        val schema = c.int(o, "schemaVersion", p) ?: return null
        if (schema != SUPPORTED_SCHEMA) {
            c.errors += "$p.schemaVersion: obsługiwana wersja to $SUPPORTED_SCHEMA, w pliku $schema"
            return null
        }
        return DatasetMetadata(
            datasetId = c.str(o, "datasetId", p) ?: return null,
            version = c.int(o, "version", p) ?: return null,
            schemaVersion = schema,
            generatedOn = c.date(o, "generatedOn", p) ?: return null,
            verifiedOn = c.date(o, "verifiedOn", p) ?: return null,
            coverageFrom = c.date(o, "coverageFrom", p) ?: return null,
            coverageNote = c.optStr(o, "coverageNote") ?: "",
            demo = c.optBool(o, "demo") ?: false
        )
    }

    private fun parseSource(c: Ctx, o: JsonObject, p: String): RegulationSource? {
        return RegulationSource(
        id = c.str(o, "id", p) ?: return null,
        title = c.str(o, "title", p) ?: return null,
        reference = c.str(o, "reference", p) ?: return null,
        url = c.optStr(o, "url"),
        type = c.enum<SourceType>(o, "type", p) ?: return null,
        publishedOn = c.optDate(o, "publishedOn", p),
        verifiedOn = c.optDate(o, "verifiedOn", p),
        notes = c.optStr(o, "notes")
    )
    }

    private fun parseArea(c: Ctx, o: JsonObject, p: String): Area? {
        return Area(
        code = c.str(o, "code", p) ?: return null,
        name = c.str(o, "name", p) ?: return null,
        description = c.optStr(o, "description") ?: "",
        implies = c.strList(o, "implies")
    )
    }

    private fun parseSpecies(c: Ctx, o: JsonObject, p: String): FishSpecies? {
        return FishSpecies(
        id = c.str(o, "id", p) ?: return null,
        namePl = c.str(o, "namePl", p) ?: return null,
        nameLatin = c.str(o, "nameLatin", p) ?: return null,
        features = c.strList(o, "features"),
        habitat = c.optStr(o, "habitat") ?: "",
        similarSpeciesIds = c.strList(o, "similar"),
        waterTypes = c.strList(o, "waterTypes").mapNotNull { v ->
            runCatching { WaterType.valueOf(v) }.getOrElse { c.errors += "$p.waterTypes: nieznana wartość $v"; null }
        }.toSet(),
        imageAsset = c.optStr(o, "imageAsset"),
        invasive = c.optBool(o, "invasive") ?: false,
        descriptionSource = c.optStr(o, "descriptionSource") ?: ""
    )
    }

    private fun parseRule(c: Ctx, o: JsonObject, p: String): ProtectionRule? {
        val kind = c.enum<RuleKind>(o, "kind", p) ?: return null
        val rule = ProtectionRule(
            id = c.str(o, "id", p) ?: return null,
            speciesId = c.str(o, "speciesId", p) ?: return null,
            kind = kind,
            scope = c.enum<RuleScope>(o, "scope", p) ?: return null,
            areaCode = c.optStr(o, "areaCode"),
            scopeRef = c.optStr(o, "scopeRef"),
            lengthCm = c.optDouble(o, "lengthCm"),
            seasonStart = c.optStr(o, "seasonStart")?.let { s -> runCatching { YearDay.parse(s) }.getOrElse { c.errors += "$p.seasonStart: ${it.message}"; null } },
            seasonEnd = c.optStr(o, "seasonEnd")?.let { s -> runCatching { YearDay.parse(s) }.getOrElse { c.errors += "$p.seasonEnd: ${it.message}"; null } },
            holidayShortening = c.optBool(o, "holidayShortening") ?: false,
            bannedDays = c.strList(o, "bannedDays").mapNotNull { d ->
                runCatching { DayOfWeek.valueOf(d) }.getOrElse { c.errors += "$p.bannedDays: nieznany dzień $d"; null }
            }.toSet(),
            dailyLimit = c.optInt(o, "dailyLimit"),
            validFrom = c.date(o, "validFrom", p) ?: return null,
            validTo = c.optDate(o, "validTo", p),
            sourceId = c.str(o, "sourceId", p) ?: return null,
            sourceLocator = c.str(o, "sourceLocator", p) ?: return null,
            verification = c.enum<VerificationStatus>(o, "verification", p) ?: return null,
            verifiedOn = c.optDate(o, "verifiedOn", p),
            note = c.optStr(o, "note")
        )
        return rule
    }

    private class Ctx {
        val errors = mutableListOf<String>()

        fun obj(o: JsonObject, key: String, path: String): JsonObject? =
            (o[key] as? JsonObject) ?: run { errors += "$path: brak obiektu"; null }

        fun asObj(e: JsonElement, path: String): JsonObject? = (e as? JsonObject) ?: run { errors += "$path: oczekiwano obiektu"; null }

        fun arr(o: JsonObject, key: String, @Suppress("UNUSED_PARAMETER") path: String): JsonArray =
            (o[key] as? JsonArray) ?: run { errors += "$key: brak tablicy"; JsonArray(emptyList()) }

        private fun prim(o: JsonObject, key: String): JsonPrimitive? = (o[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }

        fun str(o: JsonObject, key: String, path: String): String? =
            prim(o, key)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() } ?: run { errors += "$path.$key: wymagany tekst"; null }

        fun optStr(o: JsonObject, key: String): String? = prim(o, key)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

        fun int(o: JsonObject, key: String, path: String): Int? = prim(o, key)?.intOrNull ?: run { errors += "$path.$key: wymagana liczba całkowita"; null }

        fun optInt(o: JsonObject, key: String): Int? = prim(o, key)?.intOrNull

        fun optDouble(o: JsonObject, key: String): Double? = prim(o, key)?.doubleOrNull

        fun optBool(o: JsonObject, key: String): Boolean? = prim(o, key)?.booleanOrNull

        fun date(o: JsonObject, key: String, path: String): LocalDate? =
            optStr(o, key)?.let { s -> runCatching { LocalDate.parse(s) }.getOrNull() }
                ?: run { errors += "$path.$key: wymagana data RRRR-MM-DD"; null }

        fun optDate(o: JsonObject, key: String, path: String): LocalDate? =
            optStr(o, key)?.let { s -> runCatching { LocalDate.parse(s) }.getOrElse { errors += "$path.$key: niepoprawna data $s"; null } }

        fun strList(o: JsonObject, key: String): List<String> =
            (o[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content } ?: emptyList()

        inline fun <reified E : Enum<E>> enum(o: JsonObject, key: String, path: String): E? {
            val s = str(o, key, path) ?: return null
            return runCatching { enumValueOf<E>(s) }.getOrElse { errors += "$path.$key: nieznana wartość $s"; null }
        }
    }
}
