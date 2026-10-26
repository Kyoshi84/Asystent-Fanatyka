package pl.asystentfanatyka.core.data

import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetParserTest {

    private fun minimal(rules: String = "[]", meta: String = META, species: String = SPECIES) = """
        {"metadata": $meta,
         "sources": [{"id":"s1","title":"T","reference":"R","type":"LEGAL_ACT","verifiedOn":"2026-10-26"}],
         "areas": [],
         "species": $species,
         "rules": $rules}
    """.trimIndent()

    @Test fun `poprawny minimalny plik`() {
        val r = DatasetParser.parse(minimal(
            """[{"id":"r1","speciesId":"szczupak","kind":"MIN_LENGTH","scope":"NATIONAL","lengthCm":45,
                 "validFrom":"2023-07-27","sourceId":"s1","sourceLocator":"§6","verification":"VERIFIED","verifiedOn":"2026-10-26"}]"""
        ))
        assertTrue(r.toString(), r is ParseResult.Ok)
    }

    @Test fun `niepoprawny JSON`() {
        val r = DatasetParser.parse("{nie json")
        assertTrue(r is ParseResult.Error)
    }

    @Test fun `nieobslugiwana wersja schematu`() {
        val r = DatasetParser.parse(minimal(meta = META.replace("\"schemaVersion\":1", "\"schemaVersion\":99")))
        assertTrue(r is ParseResult.Error && r.errors.any { it.contains("schemaVersion") })
    }

    @Test fun `regula bez zrodla i z nieznanym gatunkiem jest odrzucana`() {
        val r = DatasetParser.parse(minimal(
            """[{"id":"r1","speciesId":"rekin","kind":"MIN_LENGTH","scope":"NATIONAL","lengthCm":45,
                 "validFrom":"2023-07-27","sourceId":"brak","sourceLocator":"§6","verification":"VERIFIED","verifiedOn":"2026-10-26"}]"""
        ))
        assertTrue(r is ParseResult.Error)
        val e = (r as ParseResult.Error).errors
        assertTrue(e.any { it.contains("nieznany gatunek") })
        assertTrue(e.any { it.contains("nieznane źródło") })
    }

    @Test fun `okres ochronny bez dat i zweryfikowana regula bez daty weryfikacji`() {
        val r = DatasetParser.parse(minimal(
            """[{"id":"r1","speciesId":"szczupak","kind":"CLOSED_SEASON","scope":"NATIONAL",
                 "validFrom":"2023-07-27","sourceId":"s1","sourceLocator":"§7","verification":"VERIFIED"}]"""
        ))
        val e = (r as ParseResult.Error).errors
        assertTrue(e.any { it.contains("seasonStart") })
        assertTrue(e.any { it.contains("daty weryfikacji") })
    }

    @Test fun `baza demonstracyjna nie moze zawierac regul VERIFIED`() {
        val r = DatasetParser.parse(minimal(
            meta = META.replace("\"demo\":false", "\"demo\":true"),
            rules = """[{"id":"r1","speciesId":"szczupak","kind":"MIN_LENGTH","scope":"NATIONAL","lengthCm":45,
                 "validFrom":"2023-07-27","sourceId":"s1","sourceLocator":"§6","verification":"VERIFIED","verifiedOn":"2026-10-26"}]"""
        ))
        assertTrue(r is ParseResult.Error)
    }

    @Test fun `zle daty i dni tygodnia`() {
        val r = DatasetParser.parse(minimal(
            """[{"id":"r1","speciesId":"szczupak","kind":"WEEKDAY_BAN","scope":"NATIONAL","seasonStart":"13-01","seasonEnd":"01-01",
                 "bannedDays":["PIATEK"],"validFrom":"2023-07-27","sourceId":"s1","sourceLocator":"§7","verification":"UNVERIFIED"}]"""
        ))
        val e = (r as ParseResult.Error).errors
        assertTrue(e.toString(), e.any { it.contains("seasonStart") })
        assertTrue(e.toString(), e.any { it.contains("PIATEK") })
    }

    companion object {
        const val META = """{"datasetId":"t","version":1,"schemaVersion":1,"generatedOn":"2026-10-26","verifiedOn":"2026-10-26","coverageFrom":"2023-07-27","demo":false}"""
        const val SPECIES = """[{"id":"szczupak","namePl":"Szczupak","nameLatin":"Esox lucius","features":["x"]}]"""
    }
}
