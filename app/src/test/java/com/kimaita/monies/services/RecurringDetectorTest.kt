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

    @Test
    fun detectsMonthlyPaymentFromStoredTimes() {
        val now = LocalDateTime.of(2025, 8, 20, 12, 0, 0)
        // Stored format: ISO_LOCAL_DATE_TIME text with a 'T'.
        val rows = listOf(
            "2025-01-05T08:28:00", "2025-02-05T08:30:00", "2025-03-05T09:00:00",
            "2025-04-05T08:15:00", "2025-05-05T08:28:00", "2025-06-05T08:40:00",
            "2025-07-05T08:28:00", "2025-08-05T08:28:00",
        ).map { SubjectOccurrence(1, "ACMEUTIL", "paybill", it, 1000.0) }

        var sinceSeen: String? = null
        val dao = Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(TransactionsDao::class.java)
        ) { _, method, args ->
            assertEquals("getSubjectOccurrences", method.name)
            sinceSeen = args[0] as String
            rows
        } as TransactionsDao

        val result = runBlocking { RecurringDetector(dao).detect(now = now) }

        assertEquals("2024-08-20T12:00:00", sinceSeen)
        assertEquals(1, result.size)
        assertEquals("Monthly", result[0].cadenceLabel)
        assertEquals(8, result[0].occurrences)
        assertTrue(!result[0].irregular)
    }
}
