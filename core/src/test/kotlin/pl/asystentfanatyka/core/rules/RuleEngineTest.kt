package pl.asystentfanatyka.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.asystentfanatyka.core.Fixtures
import pl.asystentfanatyka.core.model.RuleKind
import java.time.LocalDate

class RuleEngineTest {

    private val engine = RuleEngine(Fixtures.testDataset())
    private val today = LocalDate.of(2026, 10, 26)
    private val june = LocalDate.of(2026, 6, 10)

    private fun mat(cm: Double, u: Double = 0.5) =
        MeasurementInput(cm, u, MeasurementMethod.MAT_READING, MeasurementReliability.RELIABLE)

    private fun input(
        species: String,
        m: MeasurementInput? = mat(50.0),
        date: LocalDate = june,
        area: AreaContext = AreaContext.Known(emptySet()),
        water: WaterContext = WaterContext.NotSelected,
        local: Boolean = true,
        kept: Int? = null,
        certainty: SpeciesCertainty = SpeciesCertainty.CONFIRMED
    ) = CheckInput(species, certainty, m, date, today, area, water, local, kept)

    // --- Wymiar ---------------------------------------------------------------------------------

    @Test fun `ryba krotsza niz wymiar ochronny - A`() {
        val r = engine.check(input("szczupak", mat(40.0)))
        assertEquals(Verdict.NOT_ALLOWED, r.verdict)
        assertTrue(r.justification.contains("Wymiar ochronny"))
    }

    @Test fun `ryba dokladnie na granicy wymiaru bez niepewnosci - A, bo polow dozwolony powyzej wymiaru`() {
        val r = engine.check(input("szczupak", mat(45.0, u = 0.0)))
        assertEquals(Verdict.NOT_ALLOWED, r.verdict)
    }

    @Test fun `ryba na granicy w zakresie niepewnosci pomiaru - C`() {
        val r = engine.check(input("szczupak", mat(45.3, u = 0.5)))
        assertEquals(Verdict.CANNOT_CONFIRM, r.verdict)
        assertTrue(r.notVerified.any { it.contains("niepewności") })
    }

    @Test fun `ryba przekraczajaca wymiar ze sprawdzonym regulaminem - B`() {
        val r = engine.check(input("szczupak", mat(50.0)))
        assertEquals(Verdict.MEETS_CHECKED, r.verdict)
        assertTrue(r.checked.any { it.contains("Wymiar ochronny") })
        assertTrue(r.checked.any { it.contains("Okres ochronny") })
        assertTrue(r.checked.any { it.contains("Regulamin łowiska") })
    }

    @Test fun `ryba przekraczajaca wymiar bez sprawdzenia regulaminu lowiska - C`() {
        val r = engine.check(input("szczupak", mat(50.0), local = false))
        assertEquals(Verdict.CANNOT_CONFIRM, r.verdict)
        assertTrue(r.notVerified.any { it.contains("Regulamin łowiska") })
    }

    // --- Okres ochronny -------------------------------------------------------------------------

    @Test fun `polow w okresie ochronnym - A nawet przy wymiarowej rybie`() {
        val r = engine.check(input("szczupak", mat(70.0), date = LocalDate.of(2026, 3, 10)))
        assertEquals(Verdict.NOT_ALLOWED, r.verdict)
        assertTrue(r.findings.any { it.title.startsWith("Okres ochronny") && it.status == FindingStatus.FAIL })
    }

    @Test fun `okres przechodzacy przez koniec roku - styczen nastepnego roku jest w okresie`() {
        val known = AreaContext.Known(setOf("A"))
        assertEquals(Verdict.NOT_ALLOWED, engine.check(input("pstrag", mat(40.0), LocalDate.of(2027, 1, 15), known)).verdict)
        assertEquals(Verdict.NOT_ALLOWED, engine.check(input("pstrag", mat(40.0), LocalDate.of(2026, 12, 15), known)).verdict)
        assertEquals(Verdict.MEETS_CHECKED, engine.check(input("pstrag", mat(40.0), LocalDate.of(2026, 8, 31), known)).verdict)
        // Pozostałe wody: okres kończy się 31 grudnia
        assertEquals(Verdict.MEETS_CHECKED, engine.check(input("pstrag", mat(40.0), LocalDate.of(2027, 1, 15))).verdict)
    }

    @Test fun `pierwszy dzien okresu w dniu ustawowo wolnym skraca okres`() {
        // 1 stycznia 2026 – Nowy Rok: okres ochronny szczupaka zaczyna się 2 stycznia (§ 7 ust. 2)
        assertEquals(Verdict.MEETS_CHECKED, engine.check(input("szczupak", mat(60.0), LocalDate.of(2026, 1, 1))).verdict)
        assertEquals(Verdict.NOT_ALLOWED, engine.check(input("szczupak", mat(60.0), LocalDate.of(2026, 1, 2))).verdict)
    }

    // --- Łowiska --------------------------------------------------------------------------------

    @Test fun `rozne przepisy dla dwoch lowisk`() {
        val woda1 = WaterContext.Selected("woda-1", "okreg-a", regulationAvailable = true)
        val woda3 = WaterContext.Selected("woda-3", "okreg-a", regulationAvailable = true)
        // Karp 65 cm: na wodzie 1 obowiązuje górny wymiar 60 cm, na wodzie 3 tylko wymiar okręgu 30 cm
        assertEquals(Verdict.NOT_ALLOWED, engine.check(input("karp", mat(65.0), water = woda1, local = false)).verdict)
        assertEquals(Verdict.MEETS_CHECKED, engine.check(input("karp", mat(65.0), water = woda3, local = false)).verdict)
        // Na wodzie 2 szczupaka nie wolno zatrzymać wcale
        val woda2 = WaterContext.Selected("woda-2", null, regulationAvailable = true)
        assertEquals(Verdict.NOT_ALLOWED, engine.check(input("szczupak", mat(60.0), water = woda2, local = false)).verdict)
    }

    @Test fun `brak regulaminu lowiska - C`() {
        val w = WaterContext.Selected("woda-9", null, regulationAvailable = false)
        val r = engine.check(input("szczupak", mat(60.0), water = w, local = false))
        assertEquals(Verdict.CANNOT_CONFIRM, r.verdict)
        assertTrue(r.notVerified.any { it.contains("Brak aktualnego regulaminu") })
    }

    @Test fun `zakaz krajowy wygrywa mimo braku regulaminu lowiska`() {
        val r = engine.check(input("szczupak", mat(30.0), local = false))
        assertEquals(Verdict.NOT_ALLOWED, r.verdict)
    }

    // --- Dane i niepewność ----------------------------------------------------------------------

    @Test fun `nieaktualna baza - C`() {
        val stale = RuleEngine(Fixtures.testDataset(verifiedOn = LocalDate.of(2025, 1, 1)))
        val ok = stale.check(input("szczupak", mat(60.0)))
        assertEquals(Verdict.CANNOT_CONFIRM, ok.verdict)
        assertTrue(ok.notVerified.first().contains("mogły się zmienić"))
        // Nawet wykryty zakaz z nieaktualnej bazy nie jest „potwierdzony” – wynik C z informacją o możliwym zakazie
        val bad = stale.check(input("szczupak", mat(30.0)))
        assertEquals(Verdict.CANNOT_CONFIRM, bad.verdict)
        assertTrue(bad.justification.contains("Możliwy zakaz"))
    }

    @Test fun `baza demonstracyjna nigdy nie daje A ani B`() {
        val demo = RuleEngine(Fixtures.testDataset(demo = true))
        assertEquals(Verdict.CANNOT_CONFIRM, demo.check(input("szczupak", mat(60.0))).verdict)
    }

    @Test fun `niepewne rozpoznanie gatunku - C`() {
        val r = engine.check(input("szczupak", mat(60.0), certainty = SpeciesCertainty.UNCERTAIN))
        assertEquals(Verdict.CANNOT_CONFIRM, r.verdict)
        val r2 = engine.check(input("szczupak", mat(30.0), certainty = SpeciesCertainty.UNCERTAIN))
        assertEquals(Verdict.CANNOT_CONFIRM, r2.verdict)
    }

    @Test fun `niepewny pomiar - C`() {
        val unreliable = MeasurementInput(60.0, 2.0, MeasurementMethod.PHOTO_CALIBRATED, MeasurementReliability.UNRELIABLE)
        assertEquals(Verdict.CANNOT_CONFIRM, engine.check(input("szczupak", unreliable)).verdict)
        val low = MeasurementInput(60.0, 3.0, MeasurementMethod.PHOTO_CALIBRATED, MeasurementReliability.LOW)
        assertEquals(Verdict.CANNOT_CONFIRM, engine.check(input("szczupak", low)).verdict)
        // Pomiar o niskiej wiarygodności, ale ryba wyraźnie za krótka – zakaz potwierdzony
        val lowShort = MeasurementInput(30.0, 2.0, MeasurementMethod.PHOTO_CALIBRATED, MeasurementReliability.LOW)
        assertEquals(Verdict.NOT_ALLOWED, engine.check(input("szczupak", lowShort)).verdict)
    }

    @Test fun `brak pomiaru dla gatunku z wymiarem - C`() {
        val r = engine.check(input("szczupak", m = null))
        assertEquals(Verdict.CANNOT_CONFIRM, r.verdict)
        assertTrue(r.notVerified.contains("Brak pomiaru długości."))
    }

    @Test fun `gorny wymiar - granica wlacznie`() {
        val woda1 = WaterContext.Selected("woda-1", "okreg-a", regulationAvailable = true)
        assertEquals(Verdict.MEETS_CHECKED, engine.check(input("karp", mat(60.0, 0.0), water = woda1, local = false)).verdict)
        assertEquals(Verdict.NOT_ALLOWED, engine.check(input("karp", mat(60.5, 0.0), water = woda1, local = false)).verdict)
        assertEquals(Verdict.CANNOT_CONFIRM, engine.check(input("karp", mat(60.2, 0.5), water = woda1, local = false)).verdict)
    }

    @Test fun `limit dobowy`() {
        val july = LocalDate.of(2026, 7, 1)
        assertEquals(Verdict.NOT_ALLOWED, engine.check(input("wegorz", mat(60.0), july, kept = 2)).verdict)
        assertEquals(Verdict.MEETS_CHECKED, engine.check(input("wegorz", mat(60.0), july, kept = 1)).verdict)
        val unknown = engine.check(input("wegorz", mat(60.0), july, kept = null))
        assertEquals(Verdict.CANNOT_CONFIRM, unknown.verdict)
        assertTrue(unknown.notVerified.any { it.contains("Limit dobowy") })
    }

    @Test fun `niezweryfikowany przepis nie daje A ani B`() {
        assertEquals(Verdict.CANNOT_CONFIRM, engine.check(input("sum", mat(60.0))).verdict)
        assertEquals(Verdict.CANNOT_CONFIRM, engine.check(input("sum", mat(90.0))).verdict)
    }

    // --- Obszary w przepisach ---------------------------------------------------------------------

    @Test fun `nieznany obszar - wynik zalezny od obszaru daje C`() {
        val r = engine.check(input("pstrag", mat(27.0), date = LocalDate.of(2026, 7, 1), area = AreaContext.Unknown))
        assertEquals(Verdict.CANNOT_CONFIRM, r.verdict)
        assertTrue(r.notVerified.any { it.contains("zależy od obszaru") })
    }

    @Test fun `nieznany obszar - zgodny wynik we wszystkich wariantach`() {
        val july = LocalDate.of(2026, 7, 1)
        assertEquals(Verdict.MEETS_CHECKED, engine.check(input("pstrag", mat(35.0), july, AreaContext.Unknown)).verdict)
        assertEquals(Verdict.NOT_ALLOWED, engine.check(input("pstrag", mat(20.0), july, AreaContext.Unknown)).verdict)
    }

    @Test fun `obszar nadrzedny i pozostale wody`() {
        val july = LocalDate.of(2026, 7, 1)
        // A ⊂ B: obszar A stosuje regułę A (25 cm)
        assertEquals(Verdict.MEETS_CHECKED, engine.check(input("pstrag", mat(27.0), july, AreaContext.Known(setOf("A")))).verdict)
        // Tylko B (bez A) → pozostałe wody dla pstrąga (30 cm)
        assertEquals(Verdict.NOT_ALLOWED, engine.check(input("pstrag", mat(27.0), july, AreaContext.Known(setOf("B")))).verdict)
    }

    // --- Inne --------------------------------------------------------------------------------------

    @Test fun `gatunek inwazyjny - nie wolno wypuscic`() {
        val r = engine.check(input("babka", m = null, local = false))
        assertEquals(Verdict.MEETS_CHECKED, r.verdict)
        assertTrue(r.mustNotRelease)
    }

    @Test fun `gatunek bez przepisow krajowych nie wymaga pomiaru`() {
        val r = engine.check(input("okon", m = null))
        assertEquals(Verdict.MEETS_CHECKED, r.verdict)
        assertTrue(r.findings.any { it.title == "Przepisy krajowe" && it.status == FindingStatus.INFO })
    }

    @Test fun `limit z regulaminu wody bez podanej liczby sztuk - C`() {
        val woda1 = WaterContext.Selected("woda-1", "okreg-a", regulationAvailable = true)
        assertEquals(Verdict.CANNOT_CONFIRM, engine.check(input("okon", m = null, water = woda1, local = false)).verdict)
        assertEquals(Verdict.MEETS_CHECKED, engine.check(input("okon", m = null, water = woda1, local = false, kept = 0)).verdict)
    }

    @Test fun `data sprzed zakresu bazy - C`() {
        val r = engine.check(input("szczupak", mat(60.0), date = LocalDate.of(2022, 6, 1)))
        assertEquals(Verdict.CANNOT_CONFIRM, r.verdict)
        assertTrue(r.notVerified.any { it.contains("przed") })
    }

    @Test fun `regula z data konca obowiazywania nie jest stosowana po tej dacie`() {
        val rules = listOf(
            Fixtures.rule("old", "szczupak", RuleKind.MIN_LENGTH, length = 50.0, validTo = LocalDate.of(2025, 12, 31)),
            Fixtures.rule("new", "szczupak", RuleKind.MIN_LENGTH, length = 45.0, validFrom = LocalDate.of(2026, 1, 1))
        )
        val e = RuleEngine(Fixtures.testDataset(rules))
        assertEquals(Verdict.NOT_ALLOWED, e.check(input("szczupak", mat(47.0), LocalDate.of(2025, 6, 1))).verdict)
        assertEquals(Verdict.MEETS_CHECKED, e.check(input("szczupak", mat(47.0), LocalDate.of(2026, 6, 1))).verdict)
    }

    @Test fun `kalendarz - lista gatunkow chronionych w dniu`() {
        val closed = engine.closedOn(LocalDate.of(2026, 1, 15))
        val ids = closed.map { it.speciesId }.toSet()
        assertTrue("szczupak" in ids)
        assertTrue("wegorz" in ids)
        assertTrue("pstrag" in ids)
        assertFalse(closed.first { it.speciesId == "pstrag" }.allAreas)
        assertFalse("okon" in ids)
    }
}
