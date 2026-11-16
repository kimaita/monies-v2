package com.kimaita.monies.services

import com.kimaita.monies.data.dao.dtos.DailyTotal
import com.kimaita.monies.data.dao.dtos.WeekdayCount
import com.kimaita.monies.models.analytics.RecurringPayment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class ActivityCalendarTest {
    private val nov = YearMonth.of(2026, 11)
    private val nov14 = LocalDate.of(2026, 11, 14)
    private fun d(day: Int, month: Int = 11) = LocalDate.of(2026, month, day)

    /** The board's November: spending every day from 26 Oct except 29 Oct and 5 Nov, rent on the 1st. */
    private val daily: List<DailyTotal> = buildList {
        var day = d(26, 10)
        while (!day.isAfter(nov14)) {
            if (day != d(29, 10) && day != d(5)) add(DailyTotal(day.toString(), if (day == d(1)) 19_200.0 else 1_000.0, 3))
            day = day.plusDays(1)
        }
    }
    private val byDay = daily.associate { LocalDate.parse(it.day) to it.total }

    @Test
    fun calendarRange_coversWholeMondayToSundayWeeks() {
        assertEquals(d(26, 10)..d(6, 12), calendarRange(nov))
    }

    @Test
    fun streakNow_countsBackFromToday() {
        val s = streakNow(byDay, nov14)
        assertEquals(9, s.current)
        assertEquals(d(6)..nov14, s.run)
        assertEquals(9, s.record)
    }

    @Test
    fun streakNow_startsFromYesterdayWhileTodayHasNothing() {
        val s = streakNow(byDay, d(15))
        assertEquals(9, s.current)
        assertEquals(d(6)..nov14, s.run)
    }

    @Test
    fun streakNow_isZeroAfterAMissedDay() {
        val s = streakNow(byDay, d(16))
        assertEquals(0, s.current)
        assertNull(s.run)
    }

    @Test
    fun monthCalendar_buildsTheBoardsNovember() {
        val biggest = Triple(d(1), 18_000.0, "Rent")
        val c = monthCalendar(nov, daily, nov14, streakNow(byDay, nov14), setOf(d(16), d(20)), biggest)
        assertEquals(6, c.weeks.size)
        assertTrue(c.weeks.all { it.size == 7 })
        assertEquals(listOf(d(5)), c.noSpendDays)
        assertEquals(13, c.spendDays)
        assertEquals(14, c.daysSoFar)
        assertEquals(19_200.0 + 12 * 1_000.0, c.spent, 0.0)
        assertEquals(d(6)..nov14, c.run)
        assertEquals(setOf(d(16), d(20)), c.dueDays)
        val days = c.weeks.flatten().associateBy { it.date }
        assertEquals("Rent", days.getValue(d(1)).note)
        assertEquals(0.95f, days.getValue(d(1)).shade, 0.001f)
        assertEquals(0f, days.getValue(d(5)).shade)
        assertTrue(days.getValue(d(20)).future)
        assertTrue(!days.getValue(d(31, 10)).inMonth)
    }

    @Test
    fun monthCalendar_namesNoDayWhenTheBiggestPaymentIsSmall() {
        val c = monthCalendar(nov, daily, nov14, null, biggest = Triple(d(3), 1_000.0, "Food"))
        assertTrue(c.weeks.flatten().none { it.note != null })
    }

    @Test
    fun monthCalendar_pastMonthDrawsItsLongestRun() {
        val c = monthCalendar(YearMonth.of(2026, 10), daily, nov14, streakNow(byDay, nov14))
        assertEquals(d(26, 10)..d(28, 10), c.run)
        assertEquals(3, c.longestRun)
        assertEquals(emptySet<LocalDate>(), c.dueDays)
        assertEquals("Spending on **5 of 31 days**. Longest run: 3.", pastMonthSentence(c))
    }

    @Test
    fun noSpendDayNote_saysOnlyAndStreakStart() {
        val c = monthCalendar(nov, daily, nov14, streakNow(byDay, nov14))
        assertEquals(
            "Your only no-spend day this month. Your streak started the next day.",
            noSpendDayNote(d(5), c),
        )
    }

    @Test
    fun sentences_matchTheBoard() {
        assertEquals("**9-day** spending streak. Your record is 13.", streakSentence(Streak(9, 13, null)))
        assertEquals("**13-day** spending streak, your longest yet.", streakSentence(Streak(13, 13, null)))
        assertEquals("1 no-spend day this month: the 5th.", noSpendSentence(listOf(d(5))))
        assertEquals(
            "3 no-spend days this month: the 1st, 2nd and 23rd.",
            noSpendSentence(listOf(d(1), d(2), d(23))),
        )
        assertEquals("137 payments, about 4.6 a day.", paymentsPerDaySentence(137, 30))
        assertEquals("Most happen in the **evening, 5 to 10 pm**.", timeOfDaySentence(listOf(24, 44, 57, 12)))
        assertNull(timeOfDaySentence(listOf(1, 1, 1, 1)))
    }

    @Test
    fun weekdaySentence_namesBusiestAndQuietest() {
        val counts = listOf(18, 14, 20, 20, 30, 25).mapIndexed { i, n -> WeekdayCount(i + 1, n, 0.0) } +
                WeekdayCount(0, 10, 0.0)
        assertEquals("**Fridays** are busiest, **Sundays** quietest.", weekdaySentence(counts))
        assertNull(weekdaySentence(emptyList()))
    }

    @Test
    fun recurringSummary_putsTheMonthAgainstSpending() {
        val rent = RecurringPayment(
            "Baraka Apartments", "Monthly", 18_000.0, LocalDateTime.of(2026, 11, 1, 9, 0),
            LocalDateTime.of(2026, 12, 1, 9, 0), 12, false, DAYS_PER_MONTH,
        )
        val fmt = { v: Double -> "%,.0f".format(v) }
        assertEquals("About **18,000 a month**, half of what goes out.", recurringSummary(listOf(rent), 36_000.0, fmt))
        assertEquals("About **18,000 a month**, 41% of what goes out.", recurringSummary(listOf(rent), 44_000.0, fmt))
        assertEquals("About **18,000 a month**.", recurringSummary(listOf(rent), null, fmt))
    }

    @Test
    fun initials_takeTwoWords() {
        assertEquals("BM", initials("Baraka Mart"))
        assertEquals("CJ", initials("Café Jamii"))
        assertEquals("KP", initials("KPLC"))
        assertEquals("ON", initials("Otieno K. Njoroge".replace("K. ", "")))
    }

    @Test
    fun ordinal_handlesTheTeens() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22).map(::ordinal))
    }
}
