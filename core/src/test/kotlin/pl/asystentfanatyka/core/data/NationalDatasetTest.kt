package pl.asystentfanatyka.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.asystentfanatyka.core.Fixtures
import pl.asystentfanatyka.core.model.RuleKind
import pl.asystentfanatyka.core.model.VerificationStatus
import pl.asystentfanatyka.core.recognition.IdentificationKey
import pl.asystentfanatyka.core.recognition.RecognitionPolicy
import pl.asystentfanatyka.core.rules.AreaContext
import pl.asystentfanatyka.core.rules.CheckInput
import pl.asystentfanatyka.core.rules.MeasurementInput
import pl.asystentfanatyka.core.rules.MeasurementMethod
import pl.asystentfanatyka.core.rules.MeasurementReliability
import pl.asystentfanatyka.core.rules.RuleEngine
import pl.asystentfanatyka.core.rules.SpeciesCertainty
import pl.asystentfanatyka.core.rules.Verdict
import java.time.LocalDate

/** Testy na rzeczywistym pliku danych dołączanym do aplikacji. */
class NationalDatasetTest {

    private val ds = Fixtures.nationalDataset()
    private val engine = RuleEngine(ds)
    private val today = LocalDate.of(2026, 10, 26)

    private fun minFor(id: String) = ds.rulesFor(id).filter { it.kind == RuleKind.MIN_LENGTH }.map { it.lengthCm }

    @Test fun `plik przechodzi walidacje i nie jest demonstracyjny`() {
        assertFalse(ds.metadata.demo)
        assertTrue(ds.rules.all { it.verification == VerificationStatus.VERIFIED && it.sourceId == "DzU-2023-1373" })
        assertTrue(ds.rules.all { it.validFrom == LocalDate.of(2023, 7, 27) })
    }

    @Test fun `wybrane wymiary zgodne z Dz U 2023 poz 1373`() {
        assertEquals(listOf(45.0), minFor("szczupak"))
        assertEquals(listOf(45.0), minFor("sandacz"))
        assertEquals(listOf(70.0), minFor("sum"))
        assertEquals(listOf(50.0), minFor("wegorz"))
        assertEquals(setOf(25.0, 30.0), minFor("pstrag_potokowy").toSet())
        assertTrue(minFor("okon").isEmpty())
        assertTrue(minFor("leszcz").isEmpty())
    }

    @Test fun `kazdy gatunek z rozporzadzenia ma regule`() {
        val regulated = listOf(
            "bolen", "brzana", "certa", "glowacica", "jaz", "jelec", "klen", "lin", "lipien", "losos", "mietus",
            "pstrag_potokowy", "rozpior", "sandacz", "sieja", "sielawa", "sum", "szczupak", "swinka", "troc",
            "troc_jeziorowa", "wegorz", "wzdrega", "jesiotr_ostronosy"
        )
        regulated.forEach { assertTrue("Brak reguł: $it", ds.rulesFor(it).isNotEmpty()) }
    }

    @Test fun `szczupak w 2026 - okres ochronny 2 stycznia do 30 kwietnia`() {
        fun check(d: LocalDate, cm: Double = 60.0) = engine.check(
            CheckInput("szczupak", SpeciesCertainty.CONFIRMED,
                MeasurementInput(cm, 0.5, MeasurementMethod.MAT_READING, MeasurementReliability.RELIABLE),
                d, today, AreaContext.Known(emptySet()), localRulesConfirmedByUser = true)
        ).verdict
        assertEquals(Verdict.MEETS_CHECKED, check(LocalDate.of(2026, 1, 1)))
        assertEquals(Verdict.NOT_ALLOWED, check(LocalDate.of(2026, 1, 2)))
        assertEquals(Verdict.NOT_ALLOWED, check(LocalDate.of(2026, 4, 30)))
        assertEquals(Verdict.MEETS_CHECKED, check(LocalDate.of(2026, 5, 1)))
    }

    @Test fun `troc na Wisle ponizej Wloclawka - zakaz w piatki`() {
        fun check(d: LocalDate) = engine.check(
            CheckInput("troc", SpeciesCertainty.CONFIRMED,
                MeasurementInput(60.0, 0.5, MeasurementMethod.MAT_READING, MeasurementReliability.RELIABLE),
                d, today, AreaContext.Known(setOf("WISLA_PON_WLOCLAWKA")), localRulesConfirmedByUser = true)
        ).verdict
        assertEquals(Verdict.NOT_ALLOWED, check(LocalDate.of(2026, 6, 12)))   // piątek
        assertEquals(Verdict.MEETS_CHECKED, check(LocalDate.of(2026, 6, 10)))  // środa
        assertEquals(Verdict.MEETS_CHECKED, check(LocalDate.of(2026, 10, 14))) // poza okresem i poza zakazem dni
        assertEquals(Verdict.NOT_ALLOWED, check(LocalDate.of(2027, 1, 13)))   // okres ochronny 1.12–koniec lutego
    }

    @Test fun `mietus w Odrze ponizej Warty nie ma okresu ochronnego`() {
        fun check(area: Set<String>) = engine.check(
            CheckInput("mietus", SpeciesCertainty.CONFIRMED,
                MeasurementInput(35.0, 0.5, MeasurementMethod.MAT_READING, MeasurementReliability.RELIABLE),
                LocalDate.of(2026, 12, 15), today, AreaContext.Known(area), localRulesConfirmedByUser = true)
        ).verdict
        assertEquals(Verdict.MEETS_CHECKED, check(setOf("ODRA_WARTA_MORZE")))
        assertEquals(Verdict.NOT_ALLOWED, check(emptySet()))
    }

    @Test fun `jesiotr chroniony przez caly rok`() {
        val r = engine.check(CheckInput("jesiotr_ostronosy", SpeciesCertainty.CONFIRMED, null,
            LocalDate.of(2026, 1, 1), today, AreaContext.Known(emptySet()), localRulesConfirmedByUser = true))
        assertEquals(Verdict.NOT_ALLOWED, r.verdict)
    }

    @Test fun `klucz oznaczania jest spojny z baza`() {
        val keyIds = IdentificationKey.allSpeciesIds()
        val dsIds = ds.species.map { it.id }.toSet()
        assertTrue("Klucz wskazuje gatunki spoza bazy: ${keyIds - dsIds}", dsIds.containsAll(keyIds))
        assertTrue("Gatunki nieosiągalne w kluczu: ${dsIds - keyIds}", keyIds.containsAll(dsIds))
        RecognitionPolicy.CONFUSABLE.flatten().forEach { assertTrue(it in dsIds) }
    }
}
