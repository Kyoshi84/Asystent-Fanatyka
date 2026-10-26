package pl.asystentfanatyka

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.asystentfanatyka.core.data.DatasetParser
import pl.asystentfanatyka.core.data.ParseResult
import pl.asystentfanatyka.data.db.AppDatabase
import pl.asystentfanatyka.data.db.toEntities

/**
 * Testy instrumentalne bazy (uruchom na emulatorze lub telefonie: ./gradlew connectedAndroidTest).
 * Sprawdzają, że błędny import nie usuwa poprzednich, poprawnych danych.
 */
@RunWith(AndroidJUnit4::class)
class DatasetDaoTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() { db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext()) }
    @After fun tearDown() { db.close() }

    private fun bundled() = ApplicationProvider.getApplicationContext<android.content.Context>()
        .assets.open("dataset/national.json").bufferedReader().readText()
        .let { (DatasetParser.parse(it) as ParseResult.Ok).dataset }

    @Test fun importAndActivate() = runTest {
        val e = bundled().toEntities("test", 0)
        db.datasetDao().replaceActive(e.meta, e.sources, e.areas, e.species, e.rules)
        val active = db.datasetDao().active()
        assertNotNull(active)
        assertEquals(e.meta.version, active!!.version)
        assertEquals(e.rules.size, db.datasetDao().rules(active.version).size)
    }

    @Test fun failedImportKeepsPreviousVersion() = runTest {
        val ds = bundled()
        val v1 = ds.toEntities("test", 0)
        db.datasetDao().replaceActive(v1.meta, v1.sources, v1.areas, v1.species, v1.rules)

        val v2 = ds.copy(metadata = ds.metadata.copy(version = ds.metadata.version + 1)).toEntities("test", 1)
        val broken = v2.rules + v2.rules.first() // zduplikowany klucz główny → wyjątek w transakcji
        var threw = false
        try {
            db.datasetDao().replaceActive(v2.meta, v2.sources, v2.areas, v2.species, broken)
        } catch (expected: Exception) {
            threw = true
        }
        assertTrue("Oczekiwano wyjątku przy zduplikowanym kluczu", threw)

        val active = db.datasetDao().active()
        assertEquals(v1.meta.version, active!!.version)
        assertEquals(v1.rules.size, db.datasetDao().rules(v1.meta.version).size)
        assertTrue(db.datasetDao().rules(v2.meta.version).isEmpty())
    }

    @Test fun keepsOnlyTwoNewestVersions() = runTest {
        val ds = bundled()
        for (i in 1..4) {
            val e = ds.copy(metadata = ds.metadata.copy(version = 100 + i)).toEntities("test", i.toLong())
            db.datasetDao().replaceActive(e.meta, e.sources, e.areas, e.species, e.rules)
        }
        assertEquals(listOf(104, 103), db.datasetDao().versions())
        db.datasetDao().rollbackTo(103)
        assertEquals(103, db.datasetDao().active()!!.version)
    }
}
