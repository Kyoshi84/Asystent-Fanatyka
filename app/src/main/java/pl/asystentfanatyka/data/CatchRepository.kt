package pl.asystentfanatyka.data

import kotlinx.coroutines.flow.Flow
import pl.asystentfanatyka.core.recognition.RecognitionOutcome
import pl.asystentfanatyka.core.rules.CheckResult
import pl.asystentfanatyka.core.rules.MeasurementInput
import pl.asystentfanatyka.data.db.CatchDao
import pl.asystentfanatyka.data.db.CatchRecordEntity
import pl.asystentfanatyka.data.db.MeasurementResultEntity
import pl.asystentfanatyka.data.db.RecognitionResultEntity
import java.time.LocalDate

class CatchRepository(private val dao: CatchDao) {

    val all: Flow<List<CatchRecordEntity>> = dao.observeAll()

    suspend fun byId(id: Long) = dao.byId(id)
    suspend fun measurement(id: Long) = dao.measurement(id)
    suspend fun recognition(id: Long) = dao.recognition(id)

    suspend fun save(
        result: CheckResult,
        speciesConfirmed: Boolean,
        photoPath: String?,
        areaDescription: String,
        datasetVersion: Int,
        measurementWarnings: List<String>,
        measurementPointsJson: String?,
        recognition: RecognitionOutcome?,
        note: String?
    ): Long {
        val id = dao.insert(
            CatchRecordEntity(
                createdAtMillis = System.currentTimeMillis(),
                catchDate = result.catchDate.toString(),
                speciesId = result.speciesId,
                speciesName = result.speciesName,
                speciesConfirmed = speciesConfirmed,
                photoPath = photoPath,
                verdict = result.verdict.name,
                justification = result.justification,
                requiredMin = result.requiredMinLengthText,
                closedSeason = result.closedSeasonText,
                checked = result.checked.joinToString("\n"),
                notVerified = result.notVerified.joinToString("\n"),
                areaDescription = areaDescription,
                datasetVersion = datasetVersion,
                datasetVerifiedOn = result.dataVerifiedOn.toString(),
                sourceIds = result.sourceIds.joinToString(","),
                note = note
            )
        )
        result.measurement?.let { m: MeasurementInput ->
            dao.insertMeasurement(
                MeasurementResultEntity(
                    catchId = id, lengthCm = m.lengthCm, uncertaintyCm = m.uncertaintyCm, method = m.method.name,
                    reliability = m.reliability.name, warnings = measurementWarnings.joinToString("\n"), pointsJson = measurementPointsJson
                )
            )
        }
        recognition?.let { r ->
            dao.insertRecognition(
                RecognitionResultEntity(
                    catchId = id, source = r.source.name,
                    candidates = r.candidates.joinToString(",") { c -> c.speciesId + ":" + (c.probability?.toString() ?: "") },
                    confirmedSpeciesId = result.speciesId, needsBetterPhoto = r.needsBetterPhoto
                )
            )
        }
        return id
    }

    /** Liczba sztuk danego gatunku zapisanych dziś jako zatrzymane (wynik B) – podpowiedź dla limitu dobowego. */
    suspend fun keptToday(speciesId: String, date: LocalDate): Int = dao.keptCount(speciesId, date.toString())

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun photoPaths(): Set<String> = dao.photoPaths().toSet()
    suspend fun clearPhotoPaths() = dao.clearPhotoPaths()
}
