package pl.asystentfanatyka.core.time

import pl.asystentfanatyka.core.model.ProtectionRule
import pl.asystentfanatyka.core.model.RuleKind
import pl.asystentfanatyka.core.model.YearDay
import java.time.LocalDate

/** Konkretne wystąpienie okresu (z uwzględnieniem przejścia przez koniec roku i skrócenia o dni wolne). */
data class DatePeriod(val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

object SeasonCalculator {

    /** Czy przedział (start → end) przechodzi przez koniec roku, np. 01-09 → 31-01. */
    fun wrapsYear(start: YearDay, end: YearDay): Boolean =
        end.month < start.month || (end.month == start.month && end.day < start.day)

    /**
     * Wystąpienie okresu rozpoczynające się w roku [startYear].
     * Gdy [holidayShortening] = true, stosuje § 7 ust. 2 rozp. Dz.U. 2023 poz. 1373:
     * jeśli pierwszy lub ostatni dzień przypada na dzień ustawowo wolny od pracy, okres skraca się o ten dzień.
     */
    fun occurrence(start: YearDay, end: YearDay, startYear: Int, holidayShortening: Boolean): DatePeriod? {
        var s = start.inYear(startYear)
        var e = end.inYear(if (wrapsYear(start, end)) startYear + 1 else startYear)
        if (holidayShortening) {
            if (PolishHolidays.isStatutoryDayOff(s)) s = s.plusDays(1)
            if (PolishHolidays.isStatutoryDayOff(e)) e = e.minusDays(1)
        }
        return if (s.isAfter(e)) null else DatePeriod(s, e)
    }

    /** Wystąpienie okresu obejmujące [date] albo null. Sprawdza wystąpienia rozpoczęte w roku bieżącym i poprzednim. */
    fun occurrenceContaining(start: YearDay, end: YearDay, date: LocalDate, holidayShortening: Boolean): DatePeriod? {
        for (y in listOf(date.year - 1, date.year)) {
            val p = occurrence(start, end, y, holidayShortening) ?: continue
            if (date in p) return p
        }
        return null
    }

    /** Czy reguła okresu ochronnego obowiązuje w danym dniu (bez sprawdzania obszaru i dat obowiązywania reguły). */
    fun isClosed(rule: ProtectionRule, date: LocalDate): Boolean {
        require(rule.kind == RuleKind.CLOSED_SEASON)
        val s = rule.seasonStart ?: return false
        val e = rule.seasonEnd ?: return false
        return occurrenceContaining(s, e, date, rule.holidayShortening) != null
    }

    /** Czy w danym dniu obowiązuje zakaz połowu wynikający z reguły dni tygodnia. */
    fun isWeekdayBanned(rule: ProtectionRule, date: LocalDate): Boolean {
        require(rule.kind == RuleKind.WEEKDAY_BAN)
        val s = rule.seasonStart ?: return false
        val e = rule.seasonEnd ?: return false
        if (occurrenceContaining(s, e, date, holidayShortening = false) == null) return false
        return date.dayOfWeek in rule.bannedDays
    }

    fun describe(start: YearDay, end: YearDay): String =
        "${formatYearDay(start)} – ${formatYearDay(end)}"

    private val MONTHS_GEN = listOf(
        "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
        "lipca", "sierpnia", "września", "października", "listopada", "grudnia"
    )

    fun formatYearDay(d: YearDay): String =
        if (d.month == 2 && d.day == 29) "koniec lutego" else "${d.day} ${MONTHS_GEN[d.month - 1]}"
}
