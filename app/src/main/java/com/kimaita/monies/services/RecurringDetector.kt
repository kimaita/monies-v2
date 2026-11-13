package com.kimaita.monies.services

import com.kimaita.monies.data.dao.TransactionsDao
import com.kimaita.monies.data.dao.dtos.SubjectOccurrence
import com.kimaita.monies.models.analytics.RecurringPayment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

class RecurringDetector @Inject constructor(
    private val transactionsDao: TransactionsDao,
) {
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    suspend fun detect(
        now: LocalDateTime = LocalDateTime.now(),
        lookbackMonths: Long = 12,
        minOccurrences: Int = 7,
        account: Int? = null,
    ): List<RecurringPayment> = withContext(Dispatchers.IO) {
        val since = now.minusMonths(lookbackMonths).format(fmt)
        transactionsDao.getSubjectOccurrences(since, account)
            .groupBy { it.subjectId }
            .values
            .mapNotNull { analyze(it, now, minOccurrences) }
            .sortedByDescending { it.lastDate }
    }

    private fun analyze(
        group: List<SubjectOccurrence>,
        now: LocalDateTime,
        minOccurrences: Int
    ): RecurringPayment? {
        if (group.size < minOccurrences) return null
        val times = group.mapNotNull {
            runCatching {
                LocalDateTime.parse(
                    it.transactionTime,
                    fmt
                )
            }.getOrNull()
        }.sorted()
        if (times.size < minOccurrences) return null

        val gaps = times.zipWithNext { a, b -> Duration.between(a, b).toMinutes() / 1440.0 }
            .filter { it > 0 }
        if (gaps.isEmpty()) return null
        val cadence = median(gaps)
        if (cadence <= 0) return null

        val last = times.last()
        val daysSinceLast = Duration.between(last, now).toMinutes() / 1440.0
        if (daysSinceLast > cadence * 2) return null

        val amounts = group.map { it.amount }
        val typical = median(amounts)
        val irregular = cv(gaps) > 0.40 || cv(amounts) > 0.30

        return RecurringPayment(
            subjectId = group.first().subjectId,
            name = group.first().name,
            typeName = group.first().typeName,
            cadenceDays = cadence,
            cadenceLabel = label(cadence, irregular),
            typicalAmount = typical,
            lastDate = last,
            nextEstimate = last.plusDays(cadence.roundToLong()),
            occurrences = group.size,
            irregular = irregular,
        )
    }

    private fun label(days: Double, irregular: Boolean): String {
        val base = when {
            days < 2.0 -> "Daily"
            days < 4.0 -> "Every ${days.roundToInt()} days"
            days <= 10.0 -> "Weekly"
            days <= 18.0 -> "Every 2 weeks"
            days <= 45.0 -> "Monthly"
            days <= 75.0 -> "Every 2 months"
            else -> "Every ${(days / 30).roundToInt()} months"
        }
        return if (irregular) "~$base" else base
    }

    private fun median(xs: List<Double>): Double {
        if (xs.isEmpty()) return 0.0
        val s = xs.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2.0
    }

    private fun cv(xs: List<Double>): Double {
        if (xs.size < 2) return 0.0
        val mean = xs.average()
        if (mean == 0.0) return 0.0
        val sd = sqrt(xs.sumOf { (it - mean) * (it - mean) } / xs.size)
        return sd / mean
    }
}