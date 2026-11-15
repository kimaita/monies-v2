package com.kimaita.monies.services

import com.kimaita.monies.data.dao.dtos.ActivityCell
import com.kimaita.monies.data.dao.dtos.BalanceBucket
import com.kimaita.monies.data.dao.dtos.CategorySpending
import com.kimaita.monies.data.dao.dtos.LineBalanceBucket
import com.kimaita.monies.data.dao.dtos.DailyTotal
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.models.export.NamedRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AnalyticsCalculationsTest {

    private fun day(date: String, total: Double) = DailyTotal(date, total, 1)

    @Test
    fun monthBoundaries_firstOfMonthAndYearRollover() {
        val pace = computePace(
            listOf(day("2025-12-15", 100.0), day("2025-12-31", 50.0), day("2026-01-01", 30.0)),
            LocalDate.of(2026, 1, 1),
        )
        assertEquals(listOf(30.0), pace.thisMonth)
        assertEquals(31, pace.lastMonth.size)
        assertEquals(150.0, pace.lastMonth.last(), 0.0)
        assertEquals(30.0, pace.spent, 0.0)
        assertNull(pace.changePct)
    }

    @Test
    fun monthBoundaries_leapFebruaryIsLastMonth() {
        val pace = computePace(listOf(day("2024-02-29", 10.0)), LocalDate.of(2024, 3, 2))
        assertEquals(29, pace.lastMonth.size)
        assertEquals(2, pace.thisMonth.size)
    }

    @Test
    fun thirtyOneDayMonthAgainstThirtyDayMonth() {
        val pace = computePace(
            listOf(
                day("2026-04-10", 100.0), day("2026-04-30", 100.0),
                day("2026-05-05", 150.0), day("2026-05-30", 150.0), day("2026-05-31", 40.0),
            ),
            LocalDate.of(2026, 5, 31),
        )
        assertEquals(31, pace.thisMonth.size)
        assertEquals(30, pace.lastMonth.size)
        assertEquals(340.0, pace.spent, 0.0)
        assertEquals(50.0, pace.changePct!!, 1e-9)
    }

    @Test
    fun emptyLastMonth_hasNoComparison() {
        val pace = computePace(listOf(day("2026-05-02", 25.0)), LocalDate.of(2026, 5, 3))
        assertTrue(pace.lastMonth.isEmpty())
        assertNull(pace.changePct)
        assertEquals(listOf(0.0, 25.0, 25.0), pace.thisMonth)
        assertEquals(25.0, pace.spent, 0.0)
    }

    @Test
    fun scopeLabel_namesTheSelectedLineOrAll() {
        val lines = listOf(1 to "M-PESA", 3 to "Airtel Money")
        assertEquals("Airtel Money", scopeLabel(3, lines))
        assertEquals("All accounts", scopeLabel(null, lines))
        assertEquals("All accounts", scopeLabel(9, lines))
    }

    @Test
    fun scopeLabel_singleLineNeedsNoLabel() {
        assertNull(scopeLabel(null, listOf(1 to "M-PESA")))
        assertNull(scopeLabel(null, emptyList()))
    }

    private val types = listOf(
        TransactionType(id = 1, name = "send_money", isInc = false, displayName = "Send Money"),
        TransactionType(id = 2, name = "airtime.own", isInc = false, displayName = "Airtime"),
        TransactionType(id = 3, name = "airtime.other", isInc = false, displayName = "Airtime"),
    )

    @Test
    fun typeSlices_useDisplayNamesAndMergeSharedOnes() {
        val rows = listOf(
            CategorySpending("send_money", 300.0, 0.0),
            CategorySpending("airtime.own", 100.0, 0.0),
            CategorySpending("airtime.other", 250.0, 0.0),
        )
        val slices = typeSlices(rows, types)
        assertEquals(listOf("Airtime", "Send Money"), slices.map { it.label })
        assertEquals(350f, slices[0].value, 0f)
        assertEquals(listOf(2, 3), slices[0].typeIds)
        assertEquals(listOf(1), slices[1].typeIds)
        assertTrue(slices.all { it.categoryIds.isEmpty() })
    }

    @Test
    fun typeGroups_oneChoicePerDisplayName() {
        val groups = typeGroups(types)
        assertEquals(listOf("Send Money", "Airtime"), groups.map { it.label })
        assertEquals(listOf(2, 3), groups[1].ids)
        assertEquals("Mshwari deposit", typeGroups(listOf(TransactionType(id = 9, name = "mshwari.deposit", isInc = true)))[0].label)
    }

    @Test
    fun typeGroups_selectingAGroupSelectsAllItsIdsAndTogglesBack() {
        val airtime = typeGroups(types)[1]
        var picked = setOf(1)
        picked = picked.toggleGroup(airtime)
        assertEquals(setOf(1, 2, 3), picked)
        assertTrue(airtime.isSelected(picked))
        assertFalse(airtime.isSelected(setOf(2)))
        assertEquals(setOf(2, 3), setOf(2).toggleGroup(airtime))
        picked = picked.toggleGroup(airtime)
        assertEquals(setOf(1), picked)
    }

    @Test
    fun typeGroups_countOnceHoweverManyIdsAreIn() {
        val groups = typeGroups(types)
        assertEquals(0, countTypeChoices(emptySet(), groups))
        assertEquals(1, countTypeChoices(setOf(2, 3), groups))
        assertEquals(1, countTypeChoices(setOf(2), groups))
        assertEquals(2, countTypeChoices(setOf(1, 2, 3), groups))
        assertEquals(2, countTypeChoices(setOf(8, 9), groups))
    }

    @Test
    fun categorySlices_carryTheCategoryId() {
        val slices = categorySlices(
            listOf(CategorySpending("Food", 50.0, 0.0), CategorySpending("Rent", 900.0, 0.0)),
            listOf(NamedRef(7, "Food"), NamedRef(8, "Rent")),
        )
        assertEquals(listOf("Rent", "Food"), slices.map { it.label })
        assertEquals(listOf(8), slices[0].categoryIds)
        assertEquals(listOf(7), slices[1].categoryIds)
    }

    @Test
    fun sliceFilter_narrowsInEitherModeAndClearsOnSecondTap() {
        val byCategory = categorySlices(
            listOf(CategorySpending("Food", 50.0, 0.0)), listOf(NamedRef(7, "Food")),
        )
        val byType = typeSlices(listOf(CategorySpending("send_money", 300.0, 0.0)), types)
        val filters: List<Int>? = listOf(9)

        var selected = toggleSlice(null, "Food")
        assertEquals(listOf(7) to filters, sliceFilter(byCategory, selected, filters, filters))
        selected = toggleSlice(null, "Send Money")
        assertEquals(filters to listOf(1), sliceFilter(byType, selected, filters, filters))
        selected = toggleSlice(selected, "Send Money")
        assertNull(selected)
        assertEquals(filters to filters, sliceFilter(byType, selected, filters, filters))
        assertEquals(null to null, sliceFilter(byType, "Food", null, null))
    }

    @Test
    fun yearStarts_oneYearHasNoMarks() {
        assertEquals(listOf<String?>(null, null, null), yearStarts(listOf("2026-01", "2026-02", "2026-03")))
        assertEquals(emptyList<String?>(), yearStarts(emptyList()))
    }

    @Test
    fun yearStarts_marksFirstBucketAndEachNewYear() {
        assertEquals(
            listOf("2025", null, "2026", null),
            yearStarts(listOf("2025-11", "2025-12", "2026-01", "2026-02")),
        )
        assertEquals(listOf("2025", "2026"), yearStarts(listOf("2025-12-31 23", "2026-01-01 00")))
    }

    @Test
    fun thin_keepsFirstAndLastAndRespectsMax() {
        val items = (1..100).toList()
        val t = thin(items, 10)
        assertEquals(10, t.size)
        assertEquals(1, t.first())
        assertEquals(100, t.last())
        assertEquals(t.sorted(), t)
        assertEquals(listOf(1, 2, 3), thin(listOf(1, 2, 3), 10))
    }

    @Test
    fun balancePoints_dailyLabelsCarryTheYearInTheTooltip() {
        val p = balancePoints(listOf(BalanceBucket("2026-11-13", 100f), BalanceBucket("2026-11-20", 150f)))
        assertEquals("13 Nov 2026", p[0].label)
        assertEquals("13 Nov", p[0].axisLabel)
        assertEquals("20 Nov", p[1].axisLabel)
        assertEquals(150f, p[1].balance, 0f)
    }

    @Test
    fun balancePoints_hourlyOnOneDayUsesTheHourOnTheAxis() {
        val p = balancePoints(listOf(BalanceBucket("2026-11-13 09", 1f), BalanceBucket("2026-11-13 14", 2f)))
        assertEquals("13 Nov 09:00", p[0].label)
        assertEquals("09:00", p[0].axisLabel)
        assertEquals("14:00", p[1].axisLabel)
    }

    @Test
    fun balancePoints_hourlyAcrossDaysUsesTheDate() {
        val p = balancePoints(listOf(BalanceBucket("2026-11-12 22", 1f), BalanceBucket("2026-11-13 14", 2f)))
        assertEquals("12 Nov 22:00", p[0].label)
        assertEquals("12 Nov", p[0].axisLabel)
    }

    @Test
    fun balancePoints_acrossYearsAxisCarriesTwoDigitYear() {
        val p = balancePoints(listOf(BalanceBucket("2025-12-30", 1f), BalanceBucket("2026-01-02", 2f)))
        assertEquals("30 Dec 25", p[0].axisLabel)
        assertEquals("2 Jan 26", p[1].axisLabel)
        assertEquals("2 Jan 2026", p[1].label)
    }

    @Test
    fun balancePoints_isThinnedAndEmptyIsEmpty() {
        val rows = (1..28).map { BalanceBucket("2026-02-%02d".format(it), it.toFloat()) }
        assertEquals(10, balancePoints(rows, maxPoints = 10).size)
        assertEquals(emptyList<Any>(), balancePoints(emptyList()))
    }

    @Test
    fun feeShare_isPercentOfSpendAndNullWithoutSpend() {
        assertEquals(2.5, feeShare(25.0, 1000.0)!!, 1e-9)
        assertNull(feeShare(25.0, 0.0))
        assertNull(feeShare(0.0, -5.0))
    }

    private fun cell(weekday: Int, slot: Int, count: Int) = ActivityCell(weekday, slot, count)

    @Test
    fun activeSummary_tooLittleActivityIsNull() {
        assertNull(activeSummary(emptyList()))
        assertNull(activeSummary(listOf(cell(1, 2, 4))))
    }

    @Test
    fun activeSummary_weekdaySlotWhenWeekdaysDominate() {
        val cells = listOf(cell(1, 2, 5), cell(3, 2, 5), cell(6, 2, 1), cell(2, 0, 2))
        assertEquals("Most active on weekday evenings, 5–10 pm", activeSummary(cells))
    }

    @Test
    fun activeSummary_weekendSlotWhenWeekendDominates() {
        val cells = listOf(cell(0, 3, 6), cell(6, 3, 6), cell(2, 3, 2))
        assertEquals("Most active on weekend nights, 10 pm–5 am", activeSummary(cells))
    }

    @Test
    fun activeSummary_evenSpreadNamesOnlyTheSlot() {
        val cells = listOf(cell(1, 1, 5), cell(0, 1, 2), cell(2, 0, 1))
        assertEquals("Most active in the afternoons, 12–5 pm", activeSummary(cells))
        assertEquals(
            "Most active in the mornings, 5 am–12 pm",
            activeSummary(listOf(cell(1, 0, 3), cell(4, 0, 2), cell(6, 0, 1), cell(0, 0, 1))),
        )
    }

    private fun lb(line: Int, bucket: String?, balance: Float) = LineBalanceBucket(line, bucket, balance)

    @Test
    fun sumLineBalances_carriesEachLineForwardAndAddsThemUp() {
        val rows = listOf(
            lb(1, "2026-11-01", 100f), lb(2, "2026-11-02", 50f), lb(1, "2026-11-03", 80f),
        )
        val sum = sumLineBalances(rows)
        assertEquals(listOf("2026-11-01", "2026-11-02", "2026-11-03"), sum.map { it.bucket })
        assertEquals(listOf(100f, 150f, 130f), sum.map { it.balance })
    }

    @Test
    fun sumLineBalances_usesBalancesBeforeTheRangeAsTheStart() {
        val rows = listOf(lb(1, null, 40f), lb(2, null, 10f), lb(2, "2026-11-05", 25f))
        assertEquals(listOf(BalanceBucket("2026-11-05", 65f)), sumLineBalances(rows))
    }

    @Test
    fun sumLineBalances_oneLineIsUnchangedAndEmptyIsEmpty() {
        val rows = listOf(lb(7, "2026-11-01", 5f), lb(7, "2026-11-04", 9f))
        assertEquals(listOf(BalanceBucket("2026-11-01", 5f), BalanceBucket("2026-11-04", 9f)), sumLineBalances(rows))
        assertEquals(emptyList<BalanceBucket>(), sumLineBalances(emptyList()))
    }

    @Test
    fun bucketLabel_hourlyDayShowsTheHour() {
        assertEquals("09h", bucketLabel("2026-03-14 09", TimeFilter.DAY))
        assertEquals("2026-03-14h", bucketLabel("2026-03-14", TimeFilter.DAY))
    }

    @Test
    fun bucketLabel_weekAndCustomShowTheDayOfMonth() {
        assertEquals("14", bucketLabel("2026-03-14", TimeFilter.WEEK))
        val custom = TimeFilter.CustomRange(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 3, 20, 0, 0))
        assertEquals("05", bucketLabel("2026-03-05", custom))
    }

    @Test
    fun bucketLabel_monthShowsTheWeekNumber() {
        assertEquals("Wk 11", bucketLabel("2026-11", TimeFilter.MONTH))
    }

    @Test
    fun bucketLabel_longerRangesShowTheMonthName() {
        assertEquals("Jan", bucketLabel("2026-01", TimeFilter.SIX_MONTHS))
        assertEquals("Dec", bucketLabel("2025-12", TimeFilter.YTD))
        assertEquals("Mar", bucketLabel("2026-03", TimeFilter.ALL))
        assertEquals("2026-13", bucketLabel("2026-13", TimeFilter.ALL))
        assertEquals("junk", bucketLabel("junk", TimeFilter.ALL))
    }

    @Test
    fun daysForFilter_fixedRanges() {
        assertEquals(1, daysForFilter(TimeFilter.DAY, null))
        assertEquals(7, daysForFilter(TimeFilter.WEEK, null))
        assertEquals(30, daysForFilter(TimeFilter.MONTH, null))
        assertEquals(182, daysForFilter(TimeFilter.SIX_MONTHS, null))
    }

    @Test
    fun daysForFilter_yearToDateIsTheDayOfTheYear() {
        assertEquals(1, daysForFilter(TimeFilter.YTD, null, LocalDate.of(2026, 1, 1)))
        assertEquals(74, daysForFilter(TimeFilter.YTD, null, LocalDate.of(2026, 3, 15)))
        assertEquals(366, daysForFilter(TimeFilter.YTD, null, LocalDate.of(2024, 12, 31)))
    }

    @Test
    fun daysForFilter_allUsesTheRecordedSpanOrOne() {
        assertEquals(400, daysForFilter(TimeFilter.ALL, 400))
        assertEquals(1, daysForFilter(TimeFilter.ALL, null))
    }

    @Test
    fun daysForFilter_customRangeCountsBothEndsAndIsAtLeastOne() {
        fun custom(from: LocalDateTime, to: LocalDateTime) = TimeFilter.CustomRange(from, to)
        assertEquals(
            10,
            daysForFilter(custom(LocalDateTime.of(2026, 3, 1, 8, 0), LocalDateTime.of(2026, 3, 10, 23, 59)), null),
        )
        assertEquals(
            1,
            daysForFilter(custom(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 3, 1, 23, 59)), null),
        )
        assertEquals(
            1,
            daysForFilter(custom(LocalDateTime.of(2026, 3, 5, 0, 0), LocalDateTime.of(2026, 3, 1, 0, 0)), null),
        )
    }

    @Test
    fun daysFrom_keepsTheDaysOnOrAfterTheStart() {
        val days = listOf(day("2026-03-09", 1.0), day("2026-03-10", 2.0), day("2026-03-11", 3.0))
        assertEquals(listOf("2026-03-10", "2026-03-11"), daysFrom(days, LocalDate.of(2026, 3, 10)).map { it.day })
        assertEquals(days, daysFrom(days, LocalDate.of(2026, 3, 1)))
    }
}
