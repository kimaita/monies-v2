package com.kimaita.monies.services

import com.kimaita.monies.models.TimeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class ComparisonTest {
    private val nov14 = LocalDate.of(2026, 11, 14)

    @Test
    fun previousRange_isTheSameLengthJustBefore() {
        assertEquals(LocalDate.of(2026, 11, 13) to nov14, previousRange(TimeFilter.DAY, nov14))
        assertEquals(LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 8), previousRange(TimeFilter.WEEK, nov14))
        assertEquals(LocalDate.of(2026, 9, 14) to LocalDate.of(2026, 10, 14), previousRange(TimeFilter.MONTH, nov14))
        assertEquals(LocalDate.of(2025, 11, 14) to LocalDate.of(2026, 5, 14), previousRange(TimeFilter.SIX_MONTHS, nov14))
        assertEquals(LocalDate.of(2025, 1, 1) to LocalDate.of(2025, 11, 15), previousRange(TimeFilter.YTD, nov14))
        assertNull(previousRange(TimeFilter.ALL, nov14))
    }

    @Test
    fun historyFor_countsTheDaysBeforeThePeriod() {
        // 1M starts 14 Oct; history from 26 Sep gives 18 of the 30 days before.
        val h = historyFor(TimeFilter.MONTH, nov14, LocalDate.of(2026, 9, 26))!!
        assertFalse(h.enough)
        assertEquals(30, h.needDays)
        assertEquals(18, h.haveDays)
        assertEquals("Needs 30 days before this period. You have 18.", historyNote(h))
        assertTrue(historyFor(TimeFilter.MONTH, nov14, LocalDate.of(2025, 3, 1))!!.enough)
        assertNull(historyFor(TimeFilter.ALL, nov14, LocalDate.of(2025, 3, 1)))
    }

    @Test
    fun change_pillsMovesOfTenPercentOrMore() {
        assertEquals("+18%", Change(14_600.0, 12_400.0).pill)
        assertEquals(Move.UP, Change(14_600.0, 12_400.0).move)
        assertEquals("−23%", Change(4_300.0, 5_600.0).pill)
        assertEquals(Move.DOWN, Change(4_300.0, 5_600.0).move)
        assertEquals("−6%", Change(7_900.0, 8_400.0).pill)
        assertEquals(Move.FLAT, Change(7_900.0, 8_400.0).move)
        assertEquals("same", Change(18_000.0, 18_000.0).pill)
        assertEquals("new", Change(1_000.0, 0.0).pill)
        assertEquals(Move.NEW, Change(1_000.0, 0.0).move)
    }

    @Test
    fun changes_listBiggestMovesWithThresholds() {
        val now = mapOf("Rent" to 18_000.0, "Food" to 14_600.0, "Transport" to 7_900.0, "Shopping" to 5_100.0, "Family" to 4_300.0, "Airtime" to 2_090.0)
        val before = mapOf("Rent" to 18_000.0, "Food" to 12_400.0, "Transport" to 8_400.0, "Shopping" to 2_800.0, "Family" to 5_600.0, "Airtime" to 1_800.0)
        val big = changes(now, before).filter { it.second.big }.map { it.first }
        // Airtime moved 16% but only 290: not big. Transport moved 500 but only 6%: not big.
        assertEquals(listOf("Shopping", "Food", "Family"), big)
    }

    @Test
    fun changes_countALabelThatStoppedAsGoingToZero() {
        val c = changes(mapOf("Food" to 100.0), mapOf("Food" to 100.0, "Gym" to 2_500.0))
        assertEquals("Gym", c.first().first)
        assertEquals(-2_500.0, c.first().second.delta, 0.0)
        assertTrue(c.first().second.big)
    }

    @Test
    fun totalChangeSentence_saysMoreOrLess() {
        val f = { v: Double -> "%,.0f".format(v) }
        assertEquals("2,990 more than the previous 30 days.", totalChangeSentence(Change(58_190.0, 55_200.0), "the previous 30 days", f))
        assertEquals("1,000 less than yesterday.", totalChangeSentence(Change(500.0, 1_500.0), "yesterday", f))
    }

    @Test
    fun squarify_fillsTheAreaInProportion() {
        val values = listOf(18_000.0, 14_600.0, 7_900.0, 6_200.0, 5_100.0, 4_300.0, 2_090.0)
        val tiles = squarify(values, 326f, 210f)
        assertEquals(values.size, tiles.size)
        val total = values.sum()
        tiles.forEach { t ->
            assertEquals(values[t.index] / total * 326 * 210, (t.w * t.h).toDouble(), 1.0)
            assertTrue(t.x >= -0.01f && t.y >= -0.01f && t.x + t.w <= 326.01f && t.y + t.h <= 210.01f)
        }
        assertEquals(326.0 * 210, tiles.sumOf { (it.w * it.h).toDouble() }, 1.0)
        // No two tiles overlap.
        for (a in tiles) for (b in tiles) if (a.index < b.index) {
            val ox = minOf(a.x + a.w, b.x + b.w) - maxOf(a.x, b.x)
            val oy = minOf(a.y + a.h, b.y + b.h) - maxOf(a.y, b.y)
            assertTrue(ox <= 0.01f || oy <= 0.01f)
        }
        // The biggest tile is not a sliver.
        val big = tiles.first { it.index == 0 }
        assertTrue(maxOf(big.w / big.h, big.h / big.w) < 3)
    }

    @Test
    fun squarify_skipsZeroAndKeepsOrder() {
        val tiles = squarify(listOf(10.0, 0.0, 5.0), 100f, 50f)
        assertEquals(listOf(0, 2), tiles.map { it.index })
        assertTrue(abs(tiles[0].w * tiles[0].h - 3333.3f) < 1f)
    }
}
