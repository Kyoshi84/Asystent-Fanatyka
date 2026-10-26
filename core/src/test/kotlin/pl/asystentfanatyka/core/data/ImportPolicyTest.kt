package pl.asystentfanatyka.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.asystentfanatyka.core.model.DatasetMetadata
import java.time.LocalDate

class ImportPolicyTest {
    private fun meta(v: Int, demo: Boolean = false, verified: String = "2026-10-26", id: String = "pl") =
        DatasetMetadata(id, v, 1, LocalDate.parse(verified), LocalDate.parse(verified), LocalDate.of(2023, 7, 27), "", demo)

    @Test fun `pierwszy import zawsze przyjety`() =
        assertEquals(ImportDecision.Accept, ImportPolicy.decide(null, meta(1), requireNewer = true, allowDemo = false))

    @Test fun `aktualizacja tylko z wyzsza wersja`() {
        assertTrue(ImportPolicy.decide(meta(2), meta(2), requireNewer = true, allowDemo = false) is ImportDecision.Skip)
        assertTrue(ImportPolicy.decide(meta(2), meta(1), requireNewer = true, allowDemo = false) is ImportDecision.Skip)
        assertEquals(ImportDecision.Accept, ImportPolicy.decide(meta(2), meta(3), requireNewer = true, allowDemo = false))
    }

    @Test fun `reczny import moze nadpisac ta sama wersje`() =
        assertEquals(ImportDecision.Accept, ImportPolicy.decide(meta(2), meta(2), requireNewer = false, allowDemo = false))

    @Test fun `dane demonstracyjne nie zastepuja zweryfikowanych`() {
        assertTrue(ImportPolicy.decide(meta(2), meta(3, demo = true), requireNewer = true, allowDemo = false) is ImportDecision.Reject)
        assertEquals(ImportDecision.Accept, ImportPolicy.decide(meta(2), meta(3, demo = true), requireNewer = false, allowDemo = true))
    }

    @Test fun `starsza weryfikacja w nowszej wersji jest odrzucana`() =
        assertTrue(ImportPolicy.decide(meta(2), meta(3, verified = "2026-01-01"), requireNewer = true, allowDemo = false) is ImportDecision.Reject)

    @Test fun `inny zbior danych przy aktualizacji`() =
        assertTrue(ImportPolicy.decide(meta(2), meta(3, id = "inny"), requireNewer = true, allowDemo = false) is ImportDecision.Reject)
}
