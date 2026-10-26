package pl.asystentfanatyka.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.asystentfanatyka.core.data.DatasetParser
import pl.asystentfanatyka.core.data.ParseResult
import java.io.File

/** Zapis do encji Room i odczyt muszą dawać identyczny model – inaczej silnik działałby na innych danych niż zaimportowane. */
class MappersTest {

    private fun loadAsset(): String {
        val candidates = listOf(
            System.getProperty("dataset.path"),
            "src/main/assets/dataset/national.json",
            "app/src/main/assets/dataset/national.json"
        )
        val f = candidates.filterNotNull().map { File(it) }.first { it.exists() }
        return f.readText()
    }

    @Test fun `round trip encje - model zachowuje wszystkie dane`() {
        val parsed = DatasetParser.parse(loadAsset()) as ParseResult.Ok
        val original = parsed.dataset
        val entities = original.toEntities(origin = "test", nowMillis = 0L)
        assertFalse(entities.meta.active)
        assertEquals(original.rules.size, entities.rules.size)
        val restored = entities.toModel()
        assertEquals(original, restored)
    }

    @Test fun `wersja zbioru trafia do kazdej encji`() {
        val ds = (DatasetParser.parse(loadAsset()) as ParseResult.Ok).dataset
        val e = ds.toEntities("asset", 1L)
        val v = ds.metadata.version
        assertTrue(e.rules.all { it.datasetVersion == v })
        assertTrue(e.species.all { it.datasetVersion == v })
        assertTrue(e.sources.all { it.datasetVersion == v })
        assertTrue(e.areas.all { it.datasetVersion == v })
    }
}
