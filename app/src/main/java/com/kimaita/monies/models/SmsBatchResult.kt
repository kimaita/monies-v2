package com.kimaita.monies.models

import java.util.Locale

data class SmsBatchResult(
    val count: Int,
    val newestEpochMillis: Long?,
    val timings: SyncTimings = SyncTimings(),
)

/**
 * Time spent in each step of a sync, for measuring imports on real phones. Logged under
 * [com.kimaita.monies.services.SmsSyncer.LOG_TAG] (`adb logcat -s SmsSync`). The steps:
 * - cursor: the saved cursor and the inbox's highest id
 * - read: the SMS provider query
 * - parse: matching patterns, and picking out unmatched messages worth keeping
 * - subjects: finding saved subjects and inserting new ones
 * - save: accounts, transactions, unmatched messages, paired Airtel receipts
 * - merges: recomputing account merges, when it runs
 * - other: opening and committing the write transaction, and the small reads and writes not timed above
 */
class SyncTimings {
    private val nanos = LinkedHashMap<String, Long>()

    val totalNanos: Long get() = nanos.values.sum()

    fun add(step: String, n: Long) {
        nanos[step] = (nanos[step] ?: 0L) + n
    }

    inline fun <T> time(step: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            add(step, System.nanoTime() - start)
        }
    }

    operator fun plusAssign(other: SyncTimings) = other.nanos.forEach { (step, n) -> add(step, n) }

    /**
     * One line for a whole run: messages, batches, wall time, rate, each step's total, and the time
     * spent outside batches (waiting for another sync, progress updates, the notification).
     */
    fun runSummary(messages: Int, batches: Int, wallNanos: Long): String = String.format(
        Locale.ROOT, "%d SMS in %d batches, %s (%.0f SMS/s); %s outside=%s",
        messages, batches, ms(wallNanos), if (wallNanos > 0) messages * 1e9 / wallNanos else 0.0,
        this, ms(wallNanos - totalNanos),
    )

    override fun toString() = nanos.entries.joinToString(" ") { (step, n) -> "$step=${ms(n)}" }

    companion object {
        fun ms(nanos: Long): String = String.format(Locale.ROOT, "%.1fms", nanos / 1e6)
    }
}
