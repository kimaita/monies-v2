package com.kimaita.monies.services

import com.kimaita.monies.data.dao.TransactionsDao
import com.kimaita.monies.data.dao.dtos.SubjectOccurrence
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.LocalDateTime

class RecurringDetectorTest {
    private val now = LocalDateTime.of(2025, 8, 20, 12, 0, 0)

    /** A DAO that answers getSubjectOccurrences with [rows] and records the `since` it was asked for. */
    private class FakeDao(rows: List<SubjectOccurrence>) {
        var since: String? = null
        val dao = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(TransactionsDao::class.java)) { _, method, args ->
            assertEquals("getSubjectOccurrences", method.name)
            since = args[0] as String
            rows
        } as TransactionsDao
    }

    @Test
    fun detectsMonthlyPaymentInFourMonths() {
        val rows = listOf("2025-05-05T08:28:00", "2025-06-05T08:40:00", "2025-07-05T08:28:00", "2025-08-05T08:28:00")
            .map { SubjectOccurrence(1, "ACMEUTIL", it, 1000.0) }
        val fake = FakeDao(rows)

        val result = runBlocking { RecurringDetector(fake.dao).detect(now = now) }

        assertEquals("2025-04-20T12:00:00", fake.since)
        assertEquals(1, result.size)
        assertEquals("Monthly", result[0].cadenceLabel)
        assertEquals(4, result[0].occurrences)
        assertTrue(!result[0].irregular)
    }

    @Test
    fun mergesPaymentsOnTheSameDay() {
        val rows = listOf(
            "2025-07-08T09:00:00" to 600.0,
            "2025-07-15T09:00:00" to 400.0, "2025-07-15T10:00:00" to 200.0,
            "2025-07-22T09:00:00" to 600.0,
            "2025-07-29T09:00:00" to 300.0, "2025-07-29T09:30:00" to 300.0,
            "2025-08-05T09:00:00" to 600.0,
            "2025-08-12T09:00:00" to 600.0,
            "2025-08-19T09:00:00" to 600.0,
        ).map { (t, a) -> SubjectOccurrence(2, "Wanjiku M.", t, a) }

        val result = runBlocking { RecurringDetector(FakeDao(rows).dao).detect(now = now) }

        assertEquals(1, result.size)
        assertEquals("Weekly", result[0].cadenceLabel)
        assertEquals(7, result[0].occurrences)
        assertEquals(600.0, result[0].typicalAmount, 0.0)
        assertTrue(!result[0].irregular)
    }

    @Test
    fun dropsANameWithFewerThanFourPaymentDays() {
        val rows = listOf(
            "2025-06-10T09:00:00", "2025-06-10T12:00:00", "2025-07-10T09:00:00",
            "2025-08-10T09:00:00", "2025-08-10T18:00:00",
        ).map { SubjectOccurrence(3, "Café Jamii", it, 250.0) }

        val result = runBlocking { RecurringDetector(FakeDao(rows).dao).detect(now = now) }

        assertTrue(result.isEmpty())
    }
}
