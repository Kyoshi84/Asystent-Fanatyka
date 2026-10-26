package pl.asystentfanatyka.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Schemat bazy. Dane przepisów są wersjonowane: każdy import tworzy nową wersję (dataset_version),
 * a aktywna jest dokładnie jedna. Przełączenie następuje w jednej transakcji po pełnej walidacji,
 * więc błąd aktualizacji nigdy nie usuwa poprzednich, poprawnych danych.
 * Opis SQL: docs/schema.sql.
 */

@Entity(tableName = "dataset_metadata")
data class DatasetMetadataEntity(
    @PrimaryKey val version: Int,
    val datasetId: String,
    val schemaVersion: Int,
    val generatedOn: String,
    val verifiedOn: String,
    val coverageFrom: String,
    val coverageNote: String,
    val demo: Boolean,
    val active: Boolean,
    /** Skąd pochodzi: "asset", "file", "url:<adres>". */
    val origin: String,
    val importedAtMillis: Long
)

@Entity(tableName = "regulation_source", primaryKeys = ["datasetVersion", "id"])
data class RegulationSourceEntity(
    val datasetVersion: Int,
    val id: String,
    val title: String,
    val reference: String,
    val url: String?,
    val type: String,
    val publishedOn: String?,
    val verifiedOn: String?,
    val notes: String?
)

@Entity(tableName = "area", primaryKeys = ["datasetVersion", "code"])
data class AreaEntity(
    val datasetVersion: Int,
    val code: String,
    val name: String,
    val description: String,
    /** Kody obszarów nadrzędnych rozdzielone przecinkami. */
    val implies: String
)

@Entity(tableName = "fish_species", primaryKeys = ["datasetVersion", "id"])
data class FishSpeciesEntity(
    val datasetVersion: Int,
    val id: String,
    val namePl: String,
    val nameLatin: String,
    /** Cechy rozpoznawcze rozdzielone znakiem nowej linii. */
    val features: String,
    val habitat: String,
    val similarIds: String,
    val waterTypes: String,
    val imageAsset: String?,
    val invasive: Boolean,
    val descriptionSource: String
)

@Entity(
    tableName = "protection_rule",
    primaryKeys = ["datasetVersion", "id"],
    indices = [Index("datasetVersion", "speciesId")]
)
data class ProtectionRuleEntity(
    val datasetVersion: Int,
    val id: String,
    val speciesId: String,
    val kind: String,
    val scope: String,
    val areaCode: String?,
    val scopeRef: String?,
    val lengthCm: Double?,
    val seasonStart: String?,
    val seasonEnd: String?,
    val holidayShortening: Boolean,
    val bannedDays: String,
    val dailyLimit: Int?,
    val validFrom: String,
    val validTo: String?,
    val sourceId: String,
    val sourceLocator: String,
    val verification: String,
    val verifiedOn: String?,
    val note: String?
)

/** Okręg PZW lub inny gospodarz wody. Przygotowane pod moduł wyboru łowiska (poza zakresem MVP). */
@Entity(tableName = "fishing_district")
data class FishingDistrictEntity(
    @PrimaryKey val id: String,
    val name: String,
    val voivodeship: String,
    val regulationSourceId: String?,
    val regulationVerifiedOn: String?
)

/** Konkretne łowisko. Przygotowane pod moduł wyboru łowiska (poza zakresem MVP). */
@Entity(
    tableName = "fishing_water",
    foreignKeys = [ForeignKey(entity = FishingDistrictEntity::class, parentColumns = ["id"], childColumns = ["districtId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("districtId")]
)
data class FishingWaterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val districtId: String?,
    val waterType: String,
    val areaCodes: String,
    val regulationAvailable: Boolean,
    val regulationSourceId: String?,
    val favorite: Boolean
)

/** Zapisany połów wraz z wynikiem kontroli. */
@Entity(tableName = "catch_record")
data class CatchRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtMillis: Long,
    val catchDate: String,
    val speciesId: String,
    val speciesName: String,
    val speciesConfirmed: Boolean,
    val photoPath: String?,
    val verdict: String,
    val justification: String,
    val requiredMin: String,
    val closedSeason: String,
    val checked: String,
    val notVerified: String,
    val areaDescription: String,
    val datasetVersion: Int,
    val datasetVerifiedOn: String,
    val sourceIds: String,
    val note: String?
)

@Entity(
    tableName = "measurement_result",
    foreignKeys = [ForeignKey(entity = CatchRecordEntity::class, parentColumns = ["id"], childColumns = ["catchId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("catchId")]
)
data class MeasurementResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val catchId: Long,
    val lengthCm: Double,
    val uncertaintyCm: Double,
    val method: String,
    val reliability: String,
    val warnings: String,
    /** Punkty pomiaru w pikselach zdjęcia (JSON), aby można było odtworzyć nakładkę. */
    val pointsJson: String?
)

@Entity(
    tableName = "recognition_result",
    foreignKeys = [ForeignKey(entity = CatchRecordEntity::class, parentColumns = ["id"], childColumns = ["catchId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("catchId")]
)
data class RecognitionResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val catchId: Long,
    val source: String,
    /** Kandydaci „gatunek:prawdopodobieństwo” rozdzieleni przecinkami; prawdopodobieństwo puste, jeśli nieznane. */
    val candidates: String,
    val confirmedSpeciesId: String,
    val needsBetterPhoto: Boolean
)
