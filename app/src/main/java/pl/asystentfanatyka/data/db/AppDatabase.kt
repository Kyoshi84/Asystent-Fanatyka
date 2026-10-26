package pl.asystentfanatyka.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DatasetMetadataEntity::class,
        RegulationSourceEntity::class,
        AreaEntity::class,
        FishSpeciesEntity::class,
        ProtectionRuleEntity::class,
        FishingDistrictEntity::class,
        FishingWaterEntity::class,
        CatchRecordEntity::class,
        MeasurementResultEntity::class,
        RecognitionResultEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun datasetDao(): DatasetDao
    abstract fun catchDao(): CatchDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "asystent_fanatyka.db")
                // Przy przyszłych zmianach schematu dodaj migracje – nie używaj destructive migration,
                // bo usunęłaby zapisane połowy użytkownika.
                .build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }
}
