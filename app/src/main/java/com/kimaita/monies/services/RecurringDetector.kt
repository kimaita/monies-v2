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
        lookbackMonths: Long = LOOKBACK_MONTHS,
        minOccurrences: Int = MIN_PAYMENT_DAYS,
        account: Int? = null,
    ): List<RecurringPayment> = withContext(Dispatchers.IO) {
        val since = now.minusMonths(lookbackMonths).format(fmt)
        transactionsDao.getSubjectOccurrences(since, minOccurrences, account)
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
        val days = group.mapNotNull { o ->
            runCatching { LocalDateTime.parse(o.transactionTime, fmt) }.getOrNull()?.let { it to o.amount }
        }.groupBy { it.first.toLocalDate() }
            .map { (_, pays) -> pays.minOf { it.first } to pays.sumOf { it.second } }
            .sortedBy { it.first }
        if (days.size < minOccurrences) return null
        val times = days.map { it.first }

        val gaps = times.zipWithNext { a, b -> Duration.between(a, b).toMinutes() / 1440.0 }
        val cadence = median(gaps)
        if (cadence <= 0) return null

        val last = times.last()
        val daysSinceLast = Duration.between(last, now).toMinutes() / 1440.0
        if (daysSinceLast > cadence * 2) return null

        val amounts = days.map { it.second }
        val typical = median(amounts)
        val irregular = cv(gaps) > 0.40 || cv(amounts) > 0.30

        return RecurringPayment(
            name = group.first().name,
            cadenceLabel = label(cadence, irregular),
            typicalAmount = typical,
            lastDate = last,
            nextEstimate = last.plusDays(cadence.roundToLong()),
            occurrences = days.size,
            irregular = irregular,
            periodDays = nominalPeriodDays(cadence),
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

/** How far back recurring payments are looked for. */
const val LOOKBACK_MONTHS = 4L

/** Days with a payment a name needs in [LOOKBACK_MONTHS] to count: a monthly bill gets four. */
const val MIN_PAYMENT_DAYS = 4

/** Days in an average month, for turning a cadence into a monthly figure. */
const val DAYS_PER_MONTH = 30.44

/** The period a median gap of [days] reads as, in days, by the same bands as the cadence label. */
fun nominalPeriodDays(days: Double): Double = when {
    days < 2.0 -> 1.0
    days < 4.0 -> days.roundToInt().toDouble()
    days <= 10.0 -> 7.0
    days <= 18.0 -> 14.0
    days <= 45.0 -> DAYS_PER_MONTH
    days <= 75.0 -> 2 * DAYS_PER_MONTH
    else -> (days / 30).roundToInt() * DAYS_PER_MONTH
}
