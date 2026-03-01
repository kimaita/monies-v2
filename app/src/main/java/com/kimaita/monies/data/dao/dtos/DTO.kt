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
