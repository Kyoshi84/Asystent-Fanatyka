package pl.asystentfanatyka.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DatasetDao {

    @Query("SELECT * FROM dataset_metadata WHERE active = 1 LIMIT 1")
    abstract suspend fun active(): DatasetMetadataEntity?

    @Query("SELECT * FROM dataset_metadata ORDER BY version DESC")
    abstract fun observeAll(): Flow<List<DatasetMetadataEntity>>

    @Query("SELECT * FROM dataset_metadata WHERE version = :version")
    abstract suspend fun metadata(version: Int): DatasetMetadataEntity?

    @Query("SELECT * FROM regulation_source WHERE datasetVersion = :v")
    abstract suspend fun sources(v: Int): List<RegulationSourceEntity>

    @Query("SELECT * FROM area WHERE datasetVersion = :v")
    abstract suspend fun areas(v: Int): List<AreaEntity>

    @Query("SELECT * FROM fish_species WHERE datasetVersion = :v ORDER BY namePl")
    abstract suspend fun species(v: Int): List<FishSpeciesEntity>

    @Query("SELECT * FROM protection_rule WHERE datasetVersion = :v")
    abstract suspend fun rules(v: Int): List<ProtectionRuleEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertMetadata(e: DatasetMetadataEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertSources(e: List<RegulationSourceEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertAreas(e: List<AreaEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertSpecies(e: List<FishSpeciesEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertRules(e: List<ProtectionRuleEntity>)

    @Query("UPDATE dataset_metadata SET active = 0") abstract suspend fun deactivateAll()
    @Query("UPDATE dataset_metadata SET active = 1 WHERE version = :v") abstract suspend fun activate(v: Int)

    @Query("DELETE FROM dataset_metadata WHERE version = :v") abstract suspend fun deleteMetadata(v: Int)
    @Query("DELETE FROM regulation_source WHERE datasetVersion = :v") abstract suspend fun deleteSources(v: Int)
    @Query("DELETE FROM area WHERE datasetVersion = :v") abstract suspend fun deleteAreas(v: Int)
    @Query("DELETE FROM fish_species WHERE datasetVersion = :v") abstract suspend fun deleteSpecies(v: Int)
    @Query("DELETE FROM protection_rule WHERE datasetVersion = :v") abstract suspend fun deleteRules(v: Int)

    @Query("SELECT version FROM dataset_metadata ORDER BY version DESC")
    abstract suspend fun versions(): List<Int>

    suspend fun deleteVersion(v: Int) {
        deleteRules(v); deleteSpecies(v); deleteAreas(v); deleteSources(v); deleteMetadata(v)
    }

    /**
     * Zapisuje nową wersję i przełącza na nią w jednej transakcji. Jeśli cokolwiek się nie powiedzie,
     * transakcja jest wycofywana, a poprzednia aktywna wersja pozostaje bez zmian.
     * Zachowuje też jedną poprzednią wersję jako kopię zapasową.
     */
    @Transaction
    open suspend fun replaceActive(
        meta: DatasetMetadataEntity,
        sources: List<RegulationSourceEntity>,
        areas: List<AreaEntity>,
        species: List<FishSpeciesEntity>,
        rules: List<ProtectionRuleEntity>
    ) {
        if (metadata(meta.version) != null) deleteVersion(meta.version)
        insertMetadata(meta.copy(active = false))
        insertSources(sources)
        insertAreas(areas)
        insertSpecies(species)
        insertRules(rules)
        deactivateAll()
        activate(meta.version)
        versions().drop(2).forEach { deleteVersion(it) }
    }

    @Transaction
    open suspend fun rollbackTo(version: Int) {
        if (metadata(version) == null) return
        deactivateAll()
        activate(version)
    }
}

@Dao
interface CatchDao {
    @Query("SELECT * FROM catch_record ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<CatchRecordEntity>>

    @Query("SELECT * FROM catch_record WHERE id = :id")
    suspend fun byId(id: Long): CatchRecordEntity?

    @Query("SELECT * FROM measurement_result WHERE catchId = :id LIMIT 1")
    suspend fun measurement(id: Long): MeasurementResultEntity?

    @Query("SELECT * FROM recognition_result WHERE catchId = :id LIMIT 1")
    suspend fun recognition(id: Long): RecognitionResultEntity?

    @Insert suspend fun insert(e: CatchRecordEntity): Long
    @Insert suspend fun insertMeasurement(e: MeasurementResultEntity)
    @Insert suspend fun insertRecognition(e: RecognitionResultEntity)

    @Query("DELETE FROM catch_record WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM catch_record WHERE speciesId = :speciesId AND catchDate = :date AND verdict = 'MEETS_CHECKED'")
    suspend fun keptCount(speciesId: String, date: String): Int

    @Query("SELECT photoPath FROM catch_record WHERE photoPath IS NOT NULL")
    suspend fun photoPaths(): List<String>

    @Query("UPDATE catch_record SET photoPath = NULL")
    suspend fun clearPhotoPaths()
}
