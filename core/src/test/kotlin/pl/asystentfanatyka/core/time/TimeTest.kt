package pl.asystentfanatyka.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.asystentfanatyka.core.model.YearDay
import java.time.LocalDate

class PolishHolidaysTest {
    @Test fun `Wielkanoc w znanych latach`() {
        assertEquals(LocalDate.of(2024, 3, 31), PolishHolidays.easterSunday(2024))
        assertEquals(LocalDate.of(2025, 4, 20), PolishHolidays.easterSunday(2025))
        assertEquals(LocalDate.of(2026, 4, 5), PolishHolidays.easterSunday(2026))
        assertEquals(LocalDate.of(2027, 3, 28), PolishHolidays.easterSunday(2027))
    }

    @Test fun `swieta ruchome i stale`() {
        assertTrue(PolishHolidays.isStatutoryDayOff(LocalDate.of(2026, 6, 4)))   // Boże Ciało 2026
        assertTrue(PolishHolidays.isStatutoryDayOff(LocalDate.of(2026, 4, 6)))   // Poniedziałek Wielkanocny
        assertTrue(PolishHolidays.isStatutoryDayOff(LocalDate.of(2026, 11, 11)))
        assertTrue(PolishHolidays.isStatutoryDayOff(LocalDate.of(2026, 12, 24))) // Wigilia od 2025
        assertFalse(PolishHolidays.isStatutoryDayOff(LocalDate.of(2024, 12, 24))) // wtorek, przed zmianą
        assertTrue(PolishHolidays.isStatutoryDayOff(LocalDate.of(2026, 10, 25))) // niedziela
        assertFalse(PolishHolidays.isStatutoryDayOff(LocalDate.of(2026, 10, 24))) // sobota nie jest ustawowo wolna
    }
}

class SeasonCalculatorTest {
    private fun yd(s: String) = YearDay.parse(s)

    @Test fun `koniec lutego w roku przestepnym i zwyklym`() {
        assertEquals(LocalDate.of(2028, 2, 29), yd("02-29").inYear(2028))
        assertEquals(LocalDate.of(2027, 2, 28), yd("02-29").inYear(2027))
    }

    @Test fun `okres przez koniec roku`() {
        val p = SeasonCalculator.occurrenceContaining(yd("12-01"), yd("02-29"), LocalDate.of(2027, 2, 10), false)
        assertNotNull(p)
        assertEquals(LocalDate.of(2026, 12, 1), p!!.start)
        assertEquals(LocalDate.of(2027, 2, 28), p.end)
        assertNull(SeasonCalculator.occurrenceContaining(yd("12-01"), yd("02-29"), LocalDate.of(2027, 3, 1), false))
        assertNotNull(SeasonCalculator.occurrenceContaining(yd("12-01"), yd("02-29"), LocalDate.of(2028, 2, 29), false))
    }

    @Test fun `skrocenie o dni wolne na obu koncach`() {
        // Sandacz 01.03–31.05; w 2026: 1 marca to niedziela, 31 maja to niedziela → 02.03–30.05
        val p = SeasonCalculator.occurrence(yd("03-01"), yd("05-31"), 2026, holidayShortening = true)!!
        assertEquals(LocalDate.of(2026, 3, 2), p.start)
        assertEquals(LocalDate.of(2026, 5, 30), p.end)
    }

    @Test fun `caly rok bez skracania`() {
        val p = SeasonCalculator.occurrence(yd("01-01"), yd("12-31"), 2026, holidayShortening = false)!!
        assertTrue(LocalDate.of(2026, 1, 1) in p)
        assertTrue(LocalDate.of(2026, 12, 31) in p)
    }

    @Test fun `opis dat po polsku`() {
        assertEquals("1 grudnia – koniec lutego", SeasonCalculator.describe(yd("12-01"), yd("02-29")))
    }
}
