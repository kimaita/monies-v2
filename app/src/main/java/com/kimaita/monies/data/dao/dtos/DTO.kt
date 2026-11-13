package com.kimaita.monies.data.dao.dtos

/**
 * Data class to hold the result of the income vs. expense query.
 */
data class IncExp(
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val incomeCount: Int = 0,
    val incomeCost: Double = 0.0,
    val expenseCost: Double = 0.0,
    val expenseCount: Int = 0
)

/**
 * Data class to hold the result of the category spending query.
 */
data class CategorySpending(
    val name: String,
    val total: Double,
    val totalCost: Double
)

/**
 * Data class to hold the result of the period total query.
 */
data class DailyTotal(
    val day: String,
    val total: Double,
    val count: Int
)

/**
 * Data class to hold the result of the top subject query.
 */
data class TopSubject(
    val typeName: String,
    val subjectName: String,
    val totalAmount: Double
)

data class SubjectStats(
    val incomingTotal: Double = 0.0,
    val outgoingTotal: Double = 0.0,
    val incomingCount: Int = 0,
    val outgoingCount: Int = 0,
    val totalCost: Double = 0.0
)

data class CashFlowBucket(
    val bucket: String,
    val income: Double,
    val expense: Double,
    val cost: Double
)


data class ActivityCell(val weekday: Int, val slot: Int, val count: Int)

/** Latest balance per bucket — the balance-trend line. */
data class BalanceBucket(val bucket: String, val balance: Float)

/** Subject ranked by spend (top subjects, cross-filterable). */
data class SubjectAmount(val subjectId: Long, val name: String, val amount: Double)

/** Subject ranked by frequency (leaderboard). */
data class SubjectCount(val subjectId: Long, val name: String, val count: Int)

/** Income grouped by source (treemap). */
data class SourceAmount(val name: String, val amount: Double)

data class WeekdayCount(val weekday: Int, val count: Int, val total: Double)

/** Raw rows for recurring detection (grouped + analysed in Kotlin). */
data class SubjectOccurrence(
    val subjectId: Long, val name: String, val typeName: String,
    val transactionTime: String, val amount: Double,
)

/** Minimal projection for the balance backfill pass. */
data class BalanceRow(val id: Long, val message: String, val transactionType: Int)

/** A line (root account) with its latest known balance, as read from the database. */
data class LineBalance(
    val accountId: Int,
    val provider: String,
    val label: String?,
    val balance: Double?,
)

/** A line with its display name (see [lineNames]) and latest balance. */
data class AccountBalance(
    val accountId: Int,
    val name: String,
    val balance: Double?,
    val provider: String,
)

/** A Fuliza transaction: type name, time (ISO text), amount, and the stored message. */
data class FulizaRow(
    val typeName: String,
    val transactionTime: String,
    val amount: Double,
    val message: String,
)

/**
 * The one place account names are made: the label if set, else the provider's display name,
 * numbered ("M-PESA", "M-PESA 2") when a provider has several lines. [lines] must be in order of
 * first activity.
 */
fun lineNames(lines: List<LineBalance>): Map<Int, String> {
    val perProvider = lines.groupingBy { it.provider }.eachCount()
    val seen = HashMap<String, Int>()
    return lines.associate { l ->
        val n = (seen[l.provider] ?: 0) + 1
        seen[l.provider] = n
        val base = com.kimaita.monies.models.Provider.entries.firstOrNull { it.name == l.provider }?.displayName ?: l.provider
        l.accountId to (l.label?.takeIf { it.isNotBlank() } ?: if (perProvider.getValue(l.provider) > 1 && n > 1) "$base $n" else base)
    }
}
