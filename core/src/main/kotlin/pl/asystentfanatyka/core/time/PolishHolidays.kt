package pl.asystentfanatyka.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

/**
 * Dni ustawowo wolne od pracy w Polsce (ustawa z 18.01.1951 o dniach wolnych od pracy):
 * niedziele oraz święta wymienione w ustawie. Od 2025 r. także Wigilia (24 grudnia).
 */
object PolishHolidays {

    fun isStatutoryDayOff(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SUNDAY || date in holidays(date.year)

    fun holidays(year: Int): Set<LocalDate> {
        val easter = easterSunday(year)
        val set = mutableSetOf(
            LocalDate.of(year, Month.JANUARY, 1),
            LocalDate.of(year, Month.MAY, 1),
            LocalDate.of(year, Month.MAY, 3),
            LocalDate.of(year, Month.AUGUST, 15),
            LocalDate.of(year, Month.NOVEMBER, 1),
            LocalDate.of(year, Month.NOVEMBER, 11),
            LocalDate.of(year, Month.DECEMBER, 25),
            LocalDate.of(year, Month.DECEMBER, 26),
            easter,
            easter.plusDays(1),   // Poniedziałek Wielkanocny
            easter.plusDays(49),  // Zielone Świątki (niedziela)
            easter.plusDays(60)   // Boże Ciało
        )
        if (year >= 2011) set += LocalDate.of(year, Month.JANUARY, 6) // Trzech Króli
        if (year >= 2025) set += LocalDate.of(year, Month.DECEMBER, 24) // Wigilia
        return set
    }

    /** Algorytm Meeusa/Jonesa/Butchera dla kalendarza gregoriańskiego. */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }
}
