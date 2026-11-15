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
import org.junit.Assert.assertNotNull
import com.kimaita.monies.ui.analytics.*
import com.kimaita.monies.models.analytics.RecurringPayment
import com.kimaita.monies.data.dao.dtos.LargestPayment
import com.kimaita.monies.data.dao.dtos.WalkTotals
import java.time.LocalDate
import java.time.LocalDateTime

class AnalyticsCalculationsTest {

    private fun day(date: String, total: Double) = DailyTotal(date, total, 1)

    private fun monthPace(daily: List<DailyTotal>, today: LocalDate) =
        computePace(daily, pacePeriod(TimeFilter.MONTH, today)!!, today)

    @Test
    fun monthBoundaries_firstOfMonthAndYearRollover() {
        val pace = monthPace(
            listOf(day("2025-12-15", 100.0), day("2025-12-31", 50.0), day("2026-01-01", 30.0)),
            LocalDate.of(2026, 1, 1),
        )
        assertEquals(listOf(30.0), pace.current)
        assertEquals(31, pace.previous.size)
        assertEquals(150.0, pace.previous.last(), 0.0)
        assertEquals(30.0, pace.spent, 0.0)
        assertNull(pace.changePct)
    }

    @Test
    fun monthBoundaries_leapFebruaryIsLastMonth() {
        val pace = monthPace(listOf(day("2024-02-29", 10.0)), LocalDate.of(2024, 3, 2))
        assertEquals(29, pace.previous.size)
        assertEquals(2, pace.current.size)
    }

    @Test
    fun thirtyOneDayMonthAgainstThirtyDayMonth() {
        val pace = monthPace(
            listOf(
                day("2026-04-10", 100.0), day("2026-04-30", 100.0),
                day("2026-05-05", 150.0), day("2026-05-30", 150.0), day("2026-05-31", 40.0),
            ),
            LocalDate.of(2026, 5, 31),
        )
        assertEquals(31, pace.current.size)
        assertEquals(30, pace.previous.size)
        assertEquals(340.0, pace.spent, 0.0)
        assertEquals(50.0, pace.changePct!!, 1e-9)
    }

    @Test
    fun emptyLastMonth_hasNoComparison() {
        val pace = monthPace(listOf(day("2026-05-02", 25.0)), LocalDate.of(2026, 5, 3))
        assertTrue(pace.previous.isEmpty())
        assertNull(pace.changePct)
        assertEquals(listOf(0.0, 25.0, 25.0), pace.current)
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
        assertEquals("Most active on **weekday evenings**, 5–10 pm.", activeSummary(cells))
    }

    @Test
    fun activeSummary_weekendSlotWhenWeekendDominates() {
        val cells = listOf(cell(0, 3, 6), cell(6, 3, 6), cell(2, 3, 2))
        assertEquals("Most active on **weekend nights**, 10 pm–5 am.", activeSummary(cells))
    }

    @Test
    fun activeSummary_evenSpreadNamesOnlyTheSlot() {
        val cells = listOf(cell(1, 1, 5), cell(0, 1, 2), cell(2, 0, 1))
        assertEquals("Most active in the **afternoons**, 12–5 pm.", activeSummary(cells))
        assertEquals(
            "Most active in the **mornings**, 5 am–12 pm.",
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

    // The boards' sample: 14 Oct opening 30,040; 62,400 in, 58,190 out, 412 fees, 3,000 to M-Shwari.
    private val sample = WalkTotals(income = 62_400.0, expense = 58_190.0, fees = 412.0, mshwariOut = 3_000.0)
    private val sampleSources = listOf(
        "Acme Ltd" to 45_000.0, "Otieno K." to 8_000.0, "Bank transfer" to 6_000.0,
        "Wanjiku M." to 2_400.0, "Dividend" to 1_000.0,
    )

    @Test
    fun moneyWalk_sampleAddsUpToTheBalanceChange() {
        val w = moneyWalk(sample, 30_040.0, 30_838.0)
        assertEquals(4_210.0, w.kept, 1e-9)
        assertEquals(798.0, w.change, 1e-9)
        assertEquals(-3_000.0, w.mshwari, 1e-9)
        assertEquals(0.0, w.fuliza, 1e-9)
        assertEquals(0.0, w.other, 1e-9)
    }

    @Test
    fun moneyWalk_fulizaFeeCountsWithTheBorrowing() {
        // 1,100 paid from a 100 balance with 1,000 of Fuliza (fee 10), then 2,000 in and 1,010 repaid.
        val t = WalkTotals(income = 2_000.0, expense = 1_100.0, fees = 10.0, fulizaIn = 1_000.0, fulizaOut = 1_010.0, fulizaFees = 10.0)
        val w = moneyWalk(t, 100.0, 990.0)
        assertEquals(0.0, w.fuliza, 1e-9)
        assertEquals(0.0, w.other, 1e-9)
    }

    @Test
    fun moneyWalk_whatMessagesDontExplainIsOtherAndRoundingIsDropped() {
        assertEquals(-120.0, moneyWalk(sample, 30_040.0, 30_718.0).other, 1e-9)
        assertEquals(0.0, moneyWalk(sample, 30_040.0, 30_838.4).other, 1e-9)
    }

    private fun FlowSides.sum(left: Boolean) = (if (left) this.left else right).sumOf { it.amount }

    @Test
    fun flowSides_sampleHasTopPayerOthersAndWhereItWent() {
        val f = flowSides(moneyWalk(sample, 30_040.0, 30_838.0), sampleSources)
        assertEquals(listOf(FlowKind.SOURCE, FlowKind.SOURCE, FlowKind.SOURCE, FlowKind.OTHERS), f.left.map { it.kind })
        assertEquals(listOf("Acme Ltd", "Otieno K.", "Bank transfer", null), f.left.map { it.name })
        assertEquals(3_400.0, f.left[3].amount, 1e-9)
        assertEquals(listOf("Wanjiku M.", "Dividend"), f.others)
        assertEquals("2 others", flowName(f.left[3], f.others.size))
        assertEquals(
            listOf(FlowKind.SPENDING, FlowKind.FEES, FlowKind.MSHWARI, FlowKind.STAYED),
            f.right.map { it.kind },
        )
        assertEquals(798.0, f.right.last().amount, 1e-9)
        assertEquals(f.sum(true), f.sum(false), 1e-6)
    }

    @Test
    fun flowSides_overspentDrawsOnTheBalance() {
        val t = WalkTotals(income = 50_000.0, expense = 58_190.0, fees = 412.0)
        val sources = listOf("Acme Ltd" to 32_600.0) + sampleSources.drop(1)
        val f = flowSides(moneyWalk(t, 30_040.0, 21_438.0), sources)
        assertEquals(
            listOf(FlowKind.SOURCE, FlowKind.SOURCE, FlowKind.SOURCE, FlowKind.OTHERS, FlowKind.BALANCE),
            f.left.map { it.kind },
        )
        assertEquals(8_602.0, f.left.last().amount, 1e-9)
        assertEquals(listOf(FlowKind.SPENDING, FlowKind.FEES), f.right.map { it.kind })
        assertEquals(f.sum(true), f.sum(false), 1e-6)
    }

    @Test
    fun flowSides_oneLeftoverPayerIsNamedAndSavingsComingBackIsOnTheLeft() {
        val t = WalkTotals(income = 10_000.0, expense = 8_000.0, mshwariIn = 5_000.0)
        val sources = listOf("Acme" to 6_000.0, "B" to 2_000.0, "C" to 1_000.0, "Otieno K." to 1_000.0)
        val f = flowSides(moneyWalk(t, 1_000.0, 8_000.0), sources)
        assertEquals(FlowKind.OTHERS, f.left[3].kind)
        assertEquals("Otieno K.", flowName(f.left[3], f.others.size))
        assertTrue(f.others.isEmpty())
        assertEquals(FlowKind.MSHWARI, f.left[4].kind)
        assertEquals(f.sum(true), f.sum(false), 1e-6)
    }

    @Test
    fun flowSides_nothingMovedIsEmpty() {
        val f = flowSides(moneyWalk(WalkTotals(), 500.0, 500.0), emptyList())
        assertTrue(f.left.isEmpty() && f.right.isEmpty())
    }

    @Test
    fun paydayDate_isTheBiggestPaymentInWhenItIsHalfOrMore() {
        val big = LargestPayment(true, 45_000.0, "2026-10-31T09:12:00", null)
        val out = LargestPayment(false, 18_000.0, "2026-11-01T08:00:00", "Rent")
        assertEquals(LocalDate.of(2026, 10, 31), paydayDate(listOf(out, big), 62_400.0))
        assertNull(paydayDate(listOf(big), 100_000.0))
        assertNull(paydayDate(listOf(out), 62_400.0))
    }

    private fun regular(name: String, amount: Double, next: LocalDate, period: Double) = RecurringPayment(
        name, "", amount, next.minusDays(period.toLong()).atStartOfDay(), next.atStartOfDay(), 10, false, period,
    )

    private val nov14: LocalDate = LocalDate.of(2026, 11, 14)
    private val sampleRegulars = listOf(
        regular("Wanjiku M.", 600.0, LocalDate.of(2026, 11, 16), 7.0),
        regular("Power tokens", 1_750.0, LocalDate.of(2026, 11, 19), 14.0),
        regular("Home Wi-Fi", 2_999.0, LocalDate.of(2026, 11, 20), DAYS_PER_MONTH),
        regular("Baraka Apartments", 18_000.0, LocalDate.of(2026, 12, 1), DAYS_PER_MONTH),
        regular("FitHub Gym", 2_500.0, LocalDate.of(2026, 12, 3), DAYS_PER_MONTH),
    )

    @Test
    fun dueDates_stepByThePeriodWithinTheRange() {
        val due = dueDates(sampleRegulars, nov14.plusDays(1), LocalDate.of(2026, 11, 30))
        assertEquals(listOf(16, 19, 20, 23, 30), due.map { it.date.dayOfMonth })
        assertEquals(6_549.0, stillDue(sampleRegulars, pacePeriod(TimeFilter.MONTH, nov14)!!, nov14), 1e-9)
    }

    @Test
    fun dueDates_monthlyStepsByCalendarMonth() {
        val rent = regular("Rent", 18_000.0, LocalDate.of(2026, 1, 31), DAYS_PER_MONTH)
        val due = dueDates(listOf(rent), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31))
        assertEquals(listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 28)), due.map { it.date })
    }

    @Test
    fun monthlyRecurringTotal_turnsEachCadenceIntoAMonth() {
        assertEquals(29_900.0, roughly(monthlyRecurringTotal(sampleRegulars)), 0.0)
    }

    @Test
    fun nominalPeriodDays_matchesTheCadenceBands() {
        assertEquals(1.0, nominalPeriodDays(1.2), 0.0)
        assertEquals(3.0, nominalPeriodDays(3.2), 0.0)
        assertEquals(7.0, nominalPeriodDays(6.0), 0.0)
        assertEquals(14.0, nominalPeriodDays(15.0), 0.0)
        assertEquals(DAYS_PER_MONTH, nominalPeriodDays(31.0), 0.0)
        assertEquals(2 * DAYS_PER_MONTH, nominalPeriodDays(61.0), 0.0)
        assertEquals(3 * DAYS_PER_MONTH, nominalPeriodDays(92.0), 0.0)
    }

    private fun days(from: LocalDate, vararg totals: Double) =
        totals.mapIndexed { i, t -> day(from.plusDays(i.toLong()).toString(), t) }

    @Test
    fun projectPeriodEnd_monthIsTypicalDayTimesDaysLeftPlusRegularsDue() {
        // Rent on the 1st, then 600 a day; the 14th (today) is not over and not in the median.
        val month = days(LocalDate.of(2026, 11, 1), 18_000.0, *DoubleArray(12) { 600.0 }, 200.0)
        val p = pacePeriod(TimeFilter.MONTH, nov14)!!
        val spent = computePace(month, p, nov14).spent
        assertEquals(spent + 6_549.0 + 600.0 * 16, projectPeriodEnd(month, p, nov14, spent, 6_549.0)!!, 1e-9)
    }

    @Test
    fun projectPeriodEnd_waitsForTheSeventhWednesdayAndFebruary() {
        val nov6 = LocalDate.of(2026, 11, 6)
        assertNull(projectPeriodEnd(emptyList(), pacePeriod(TimeFilter.MONTH, nov6)!!, nov6, 0.0, 0.0))
        val nov7 = LocalDate.of(2026, 11, 7)
        assertNotNull(projectPeriodEnd(emptyList(), pacePeriod(TimeFilter.MONTH, nov7)!!, nov7, 0.0, 0.0))
        val tue = LocalDate.of(2026, 11, 10)
        assertNull(projectPeriodEnd(emptyList(), pacePeriod(TimeFilter.WEEK, tue)!!, tue, 0.0, 0.0))
        val wed = LocalDate.of(2026, 11, 11)
        assertNotNull(projectPeriodEnd(emptyList(), pacePeriod(TimeFilter.WEEK, wed)!!, wed, 0.0, 0.0))
        val jan31 = LocalDate.of(2026, 1, 31)
        assertNull(projectPeriodEnd(emptyList(), pacePeriod(TimeFilter.YTD, jan31)!!, jan31, 0.0, 0.0))
        val feb1 = LocalDate.of(2026, 2, 1)
        assertNotNull(projectPeriodEnd(emptyList(), pacePeriod(TimeFilter.YTD, feb1)!!, feb1, 0.0, 0.0))
    }

    @Test
    fun projectPeriodEnd_weekRunsToSunday() {
        // Sat 14 Nov: Mon–Fri finished (median 200), Sunday left.
        val week = days(LocalDate.of(2026, 11, 9), 100.0, 300.0, 200.0, 0.0, 400.0, 50.0)
        val p = pacePeriod(TimeFilter.WEEK, nov14)!!
        assertEquals(1_050.0 + 600.0 + 200.0, projectPeriodEnd(week, p, nov14, 1_050.0, 600.0)!!, 1e-9)
    }

    @Test
    fun projectPeriodEnd_rollingAndDayPeriodsHaveNone() {
        assertNull(projectPeriodEnd(emptyList(), pacePeriod(TimeFilter.SIX_MONTHS, nov14)!!, nov14, 10.0, 0.0))
        assertNull(projectPeriodEnd(emptyList(), pacePeriod(TimeFilter.DAY, nov14)!!, nov14, 10.0, 0.0))
    }

    @Test
    fun pacePeriod_mapsEachChipToItsCalendarPeriod() {
        val week = pacePeriod(TimeFilter.WEEK, nov14)!!
        assertEquals(LocalDate.of(2026, 11, 9), week.start)
        assertEquals(LocalDate.of(2026, 11, 15), week.end)
        assertEquals(LocalDate.of(2026, 11, 2), week.compareStart)
        assertEquals(LocalDate.of(2026, 11, 11), week.projectFrom)

        val month = pacePeriod(TimeFilter.MONTH, nov14)!!
        assertEquals(LocalDate.of(2026, 11, 30), month.end)
        assertEquals(LocalDate.of(2026, 10, 1), month.compareStart)

        val year = pacePeriod(TimeFilter.YTD, nov14)!!
        assertEquals(LocalDate.of(2026, 12, 31), year.end)
        assertEquals(LocalDate.of(2025, 1, 1), year.compareStart)
        assertEquals(7, year.stepDays)
        assertEquals(53, year.steps)
        assertEquals(46, year.stepOf(nov14))

        val half = pacePeriod(TimeFilter.SIX_MONTHS, nov14)!!
        assertEquals(LocalDate.of(2026, 5, 14), half.start)
        assertEquals(nov14, half.end)
        assertEquals(LocalDate.of(2025, 11, 14), half.compareStart)
        assertFalse(half.sameTime)
        assertNull(half.projectFrom)

        assertFalse(pacePeriod(TimeFilter.DAY, nov14)!!.compare)
        assertNull(pacePeriod(TimeFilter.ALL, nov14))
    }

    @Test
    fun computePace_weekComparesTheSameWeekday() {
        // Last week 100 a day, this week (to Saturday) 50 a day.
        val daily = days(LocalDate.of(2026, 11, 2), *DoubleArray(7) { 100.0 }) +
                days(LocalDate.of(2026, 11, 9), *DoubleArray(6) { 50.0 })
        val pace = computePace(daily, pacePeriod(TimeFilter.WEEK, nov14)!!, nov14)
        assertEquals(6, pace.current.size)
        assertEquals(7, pace.previous.size)
        assertEquals(700.0, pace.previous.last(), 0.0)
        assertEquals(300.0, pace.spent, 0.0)
        assertEquals(-50.0, pace.changePct!!, 1e-9)
    }

    @Test
    fun computePace_yearGroupsByWeekAndComparesTheSameDate() {
        val jan20 = LocalDate.of(2026, 1, 20)
        val daily = listOf(
            day("2025-01-02", 200.0), day("2025-06-01", 999.0),
            day("2026-01-01", 100.0), day("2026-01-08", 50.0), day("2026-01-20", 10.0),
        )
        val pace = computePace(daily, pacePeriod(TimeFilter.YTD, jan20)!!, jan20)
        assertEquals(listOf(100.0, 150.0, 160.0), pace.current)
        assertEquals(53, pace.previous.size)
        assertEquals(-20.0, pace.changePct!!, 1e-9)
    }

    @Test
    fun computePace_sixMonthsComparesWholeWindows() {
        val daily = listOf(day("2026-01-10", 400.0), day("2026-09-01", 500.0))
        val pace = computePace(daily, pacePeriod(TimeFilter.SIX_MONTHS, nov14)!!, nov14)
        assertEquals(500.0, pace.spent, 0.0)
        assertEquals(400.0, pace.previous.last(), 0.0)
        assertEquals(25.0, pace.changePct!!, 1e-9)
    }

    @Test
    fun computePace_todayShowsYesterdayButNoChange() {
        val pace = computePace(
            listOf(day("2026-11-13", 1_160.0), day("2026-11-14", 300.0)),
            pacePeriod(TimeFilter.DAY, nov14)!!, nov14,
        )
        assertEquals(300.0, pace.spent, 0.0)
        assertEquals(1_160.0, pace.previous.last(), 0.0)
        assertNull(pace.changePct)
    }

    @Test
    fun stillDue_countsTheRestOfEachPeriodWithRepeats() {
        val mon = LocalDate.of(2026, 11, 16)
        assertEquals(600.0, stillDue(sampleRegulars, pacePeriod(TimeFilter.DAY, mon)!!, mon), 1e-9)
        assertEquals(1_750.0 + 2_999.0, stillDue(sampleRegulars, pacePeriod(TimeFilter.WEEK, mon)!!, mon), 1e-9)
        // To 31 Dec: Wanjiku 7 times, power 4, Wi-Fi 2, rent and gym once each.
        val year = 7 * 600.0 + 4 * 1_750.0 + 2 * 2_999.0 + 18_000.0 + 2_500.0
        assertEquals(year, stillDue(sampleRegulars, pacePeriod(TimeFilter.YTD, nov14)!!, nov14), 1e-9)
        assertEquals(0.0, stillDue(sampleRegulars, pacePeriod(TimeFilter.SIX_MONTHS, nov14)!!, nov14), 0.0)
    }

    @Test
    fun roughly_fiftiesThenHundreds() {
        assertEquals(6_550.0, roughly(6_549.0), 0.0)
        assertEquals(8_900.0, roughly(8_890.0), 0.0)
        assertEquals(47_400.0, roughly(47_386.0), 0.0)
    }

    @Test
    fun percentShares_addUpTo100() {
        val shares = percentShares(listOf(18_000.0, 14_600.0, 7_900.0, 6_200.0, 11_490.0))
        assertEquals(listOf(31, 25, 13, 11, 20), shares)
        assertEquals(100, percentShares(listOf(1.0, 1.0, 1.0)).sum())
        assertEquals(listOf(0, 0), percentShares(listOf(0.0, 0.0)))
    }

    @Test
    fun slotShares_arePerSlotPercentages() {
        val cells = listOf(cell(1, 0, 24), cell(2, 1, 44), cell(5, 2, 57), cell(6, 3, 12))
        assertEquals(listOf(17, 32, 42, 9), slotShares(cells))
    }

    @Test
    fun mostlyCategory_onlyWhenOverHalf() {
        val rent = CategorySpending("Rent", 18_000.0, 0.0)
        assertEquals("Rent", mostlyCategory(listOf(rent, CategorySpending("Food", 2_990.0, 0.0))))
        assertNull(mostlyCategory(listOf(rent, CategorySpending("Food", 10_000.0, 0.0), CategorySpending("Bills", 10_000.0, 0.0))))
        assertNull(mostlyCategory(emptyList()))
    }

    @Test
    fun shillingsPerFee_roundsToTwoFiguresFromAHundred() {
        assertEquals(140, shillingsPerFee(412.0, 58_190.0))
        assertEquals(57, shillingsPerFee(10.0, 570.0))
        assertEquals(1_200, shillingsPerFee(10.0, 12_340.0))
        assertNull(shillingsPerFee(0.0, 100.0))
    }

    @Test
    fun copy_paceSentence() {
        val month = pacePeriod(TimeFilter.MONTH, nov14)!!
        assertEquals("You've spent **12% less** than this time last month.", paceSentence(month, -12.2, 31_400.0, null))
        assertEquals("You've spent **18% more** than this time last month.", paceSentence(month, 17.6, 31_400.0, null))
        assertEquals("**KES 31,400** spent so far.", paceSentence(month, null, 31_400.0, null))
        assertEquals("You've spent **7% less** than this time last month. Mostly rent.", paceSentence(month, -7.0, 20_990.0, "Rent"))
        assertEquals("You've spent **12% less** than this time last week.", paceSentence(pacePeriod(TimeFilter.WEEK, nov14)!!, -12.0, 1.0, null))
        assertEquals("You've spent **3% more** than this time last year.", paceSentence(pacePeriod(TimeFilter.YTD, nov14)!!, 3.0, 1.0, null))
        assertEquals("You've spent **5% less** than the 6 months before.", paceSentence(pacePeriod(TimeFilter.SIX_MONTHS, nov14)!!, -5.0, 1.0, null))
        assertEquals("**KES 1,160** spent so far today.", paceSentence(pacePeriod(TimeFilter.DAY, nov14)!!, null, 1_160.0, null))
        assertEquals("**KES 1,124,300** spent since Mar 2025.", allTimeSentence(1_124_300.0, LocalDate.of(2025, 3, 2)))
    }

    @Test
    fun copy_paceFiguresAndLabels() {
        val week = pacePeriod(TimeFilter.WEEK, nov14)!!
        val month = pacePeriod(TimeFilter.MONTH, nov14)!!
        val year = pacePeriod(TimeFilter.YTD, nov14)!!
        val half = pacePeriod(TimeFilter.SIX_MONTHS, nov14)!!
        assertEquals("This week · day 6 of 7", paceLabel(week, nov14, null))
        assertEquals("This month · day 14 of 30", paceLabel(month, nov14, null))
        assertEquals("This year · day 318 of 365", paceLabel(year, nov14, null))
        assertEquals("Last 6 months", paceLabel(half, nov14, null))
        assertEquals("All time · since Mar 2025", paceLabel(null, nov14, LocalDate.of(2025, 3, 2)))
        assertEquals(listOf("By Sun", "By 30 Nov", "By 31 Dec", null), listOf(week, month, year, half).map { paceEndLabel(it) })
        assertEquals(listOf("From Wed", "From the 7th", "From Feb"), listOf(week, month, year).map { paceFromLabel(it) })
        assertEquals(
            listOf("Last week", "October", "2025", "6 months before", "Yesterday"),
            listOf(week, month, year, half, pacePeriod(TimeFilter.DAY, nov14)!!).map { previousLabel(it) },
        )
        assertEquals(listOf("This week", "November", "2026"), listOf(week, month, year).map { currentLabel(it) })
        assertNull(paceProgress(half, nov14))
        assertEquals(6 / 7f, paceProgress(week, nov14)!!, 1e-6f)
    }

    @Test
    fun copy_paceAxis() {
        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), paceAxis(pacePeriod(TimeFilter.WEEK, nov14)!!, nov14).map { it.second })
        assertEquals(
            listOf(1, 5, 10, 14, 15, 20, 25, 30),
            paceAxis(pacePeriod(TimeFilter.MONTH, nov14)!!, nov14).map { it.first },
        )
        assertEquals(
            listOf(1 to "Jan", 13 to "Apr", 26 to "Jul", 40 to "Oct", 46 to "Nov"),
            paceAxis(pacePeriod(TimeFilter.YTD, nov14)!!, nov14),
        )
        assertEquals(listOf("14 May", "13 Aug", "14 Nov"), paceAxis(pacePeriod(TimeFilter.SIX_MONTHS, nov14)!!, nov14).map { it.second })
    }

    @Test
    fun copy_balanceAndHeadline() {
        val w = moneyWalk(sample, 30_040.0, 30_838.0)
        assertEquals("**Up KES 798** since 14 Oct, now **30,838**.", balanceSentence(w.change, w.closing, sinceLabel(TimeFilter.MONTH, LocalDate.of(2026, 10, 14))))
        assertEquals("**Down KES 8,602** today, now **21,438**.", balanceSentence(-8_602.0, 21_438.0, sinceLabel(TimeFilter.DAY, nov14)))
        assertEquals("You came out **KES 4,210 ahead**.", inOutHeadline(w, TimeFilter.MONTH, false))
        assertEquals("Nothing in or out yet today.", inOutHeadline(w, TimeFilter.DAY, true))
    }

    @Test
    fun copy_flowNoteFollowsTheBoards() {
        assertEquals(
            "798 stayed in your balance. 3,000 went to M-Shwari, 412 to fees.",
            flowNote(moneyWalk(sample, 30_040.0, 30_838.0)),
        )
        val short = WalkTotals(income = 50_000.0, expense = 58_190.0, fees = 412.0)
        assertEquals("Your balance covered it: 8,602 with fees.", flowNote(moneyWalk(short, 30_040.0, 21_438.0)))
        val back = sample.copy(mshwariOut = 0.0, mshwariIn = 5_000.0)
        assertEquals(
            "8,798 stayed in your balance. 5,000 came from M-Shwari, 412 went to fees.",
            flowNote(moneyWalk(back, 30_040.0, 38_838.0)),
        )
        assertEquals(
            "3,678 stayed in your balance. 412 went to fees. Other changes: −120.",
            flowNote(moneyWalk(sample.copy(mshwariOut = 0.0), 30_040.0, 33_718.0)),
        )
    }

    @Test
    fun copy_othersLineAndFees() {
        assertEquals("2 others: Wanjiku M., Dividend.", othersLine(listOf("Wanjiku M.", "Dividend")))
        assertEquals("5 others: A, B, C and 2 more.", othersLine(listOf("A", "B", "C", "D", "E")))
        assertNull(othersLine(listOf("A")))
        assertEquals("**KES 412** in fees, about **1 shilling in every 140**.", feesSentence(412.0, 140))
        assertEquals("**KES 0** in fees.", feesSentence(0.0, null))
    }

    @Test
    fun copy_periodLabels() {
        assertEquals("last 30 days", periodLabel(TimeFilter.MONTH))
        assertEquals("today", periodLabel(TimeFilter.DAY))
        assertEquals("all time, since Mar 2025", periodLabel(TimeFilter.ALL, LocalDate.of(2025, 3, 2)))
    }

    @Test
    fun ledger_readsLikeTheBoardsStatement() {
        val lines = moneyWalk(sample, 30_040.0, 30_838.0).ledger()
        assertEquals(listOf(LedgerKind.IN, LedgerKind.OUT, LedgerKind.KEPT, LedgerKind.FEES, LedgerKind.MSHWARI), lines.map { it.kind })
        assertEquals(listOf(92_440.0, 34_250.0, null, 33_838.0, 30_838.0), lines.map { it.balance })
        assertEquals("Kept: in minus out", ledgerName(lines[2]))
        assertEquals("To M-Shwari", ledgerName(lines[4]))
        assertEquals("Kept +4,210 · fees −412 · to M-Shwari −3,000", whyItMovedSummary(moneyWalk(sample, 30_040.0, 30_838.0)))
    }

    @Test
    fun ledger_shortAndFeesOnlyHaveNoTransferRow() {
        val w = moneyWalk(WalkTotals(income = 50_000.0, expense = 58_190.0, fees = 412.0), 30_040.0, 21_438.0)
        val lines = w.ledger()
        assertEquals(4, lines.size)
        assertEquals("Short: out minus in", ledgerName(lines[2]))
        assertEquals(21_438.0, lines.last().balance!!, 1e-9)
        assertEquals("Short −8,190 · fees −412", whyItMovedSummary(w))
    }

    @Test
    fun weekDays_followSqliteMondayWeeks() {
        assertEquals(LocalDate.of(2026, 11, 2)..LocalDate.of(2026, 11, 8), weekDays("2026-44"))
        assertEquals("2–8 Nov", dayRangeLabel(weekDays("2026-44")!!))
        assertEquals(LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 1, 4), weekDays("2026-00"))
        assertEquals("26 Oct – 1 Nov", dayRangeLabel(weekDays("2026-43")!!))
        assertNull(weekDays("2026-11-14"))
    }
}

