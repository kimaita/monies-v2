package com.kimaita.monies.data
//
//import com.kimaita.monies.data.dao.dtos.CategorySpending
//import com.kimaita.monies.data.dao.dtos.DailyTotal
//import com.kimaita.monies.data.dao.dtos.IncExp
//import com.kimaita.monies.data.dao.dtos.TopSubject
//import com.kimaita.monies.data.database.models.Category
//import com.kimaita.monies.data.database.models.Subject
//import com.kimaita.monies.models.Transaction
//import com.kimaita.monies.data.database.models.TransactionType
//import kotlinx.coroutines.flow.Flow
//import kotlinx.coroutines.flow.flowOf
//import java.time.LocalDateTime
//
////class MockTransactionsRepository : TransactionsRepository {
//    override fun getRecentTransactions(limit: Int?): Flow<List<Transaction>> {
//        TODO("Not yet implemented")
//    }
//
//    override fun getTransactions(
//        startDate: String?,
//        endDate: String?,
//        subjectId: Int?,
//        categoryId: String?,
//        transactionType: Int?,
//        account: Int?
//    ): Flow<List<Transaction>> {
//        TODO("Not yet implemented")
//    }
//
//    override fun getTransactionTypes(): Flow<List<TransactionType>> {
//        return flowOf(
//            listOf(
//                TransactionType(
//                    1, "Test Inc", false,
//                ), TransactionType(
//                    2, "Test Dec", isInc = false,
//                )
//            )
//        )
//    }
//
//    override fun getSubjects(): Flow<List<Subject>> {
//        return flowOf(
//            listOf(
//                Subject(
//                    id = 1, name = "John Doe", number = "0712345678",
//                ),
//                Subject(
//                    id = 1, name = "John Doe", number = "0712345678",
//                )
//            )
//        )
//
//    }
//
//    override fun getCategorySpending(
//        startDate: String?,
//        endDate: String?,
//        transactionCategory: Int?
//    ): Flow<List<CategorySpending>> {
//        return flowOf(
//            listOf(
//                CategorySpending("Groceries", 200.0, totalCost = 5.0),
//                CategorySpending("Transport", 1030.0, totalCost = 11.0)
//            )
//        )
//    }
//
//    override fun getTransactionProviders(): List<String> {
//        TODO("Not yet implemented")
//    }
//
//    override fun getTypeSpending(
//        startDate: String?,
//        endDate: String?
//    ): Flow<List<CategorySpending>> {
//        TODO("Not yet implemented")
//    }
//
//    override fun getTransactionStatsByDay(
//        startDate: String?,
//        endDate: String?
//    ): Flow<List<DailyTotal>> {
//        TODO("Not yet implemented")
//    }
//
//    override fun getTopSubjectPerType(
//        startDate: String?,
//        endDate: String?
//    ): Flow<List<TopSubject>> {
//        TODO("Not yet implemented")
//    }
//
//    override fun getLargestTransaction(
//        isInc: Boolean,
//        startDate: String?,
//        endDate: String?
//    ): Flow<Transaction?> {
//        TODO("Not yet implemented")
//    }
//
//    override fun getMpesaBalance(): Flow<Double> {
//        TODO("Not yet implemented")
//    }
//
//
//    override fun getCategories(searchQuery: String?): Flow<List<Category>> {
//        return flowOf(
//            listOf(
//                Category(name = "Electricity", description = ""),
//                Category(name = "Phone", description = ""),
//                Category(name = "Groceries", description = ""),
//            )
//        )
//    }
//
//
//    override fun getIncomeVsExpense(startDate: String?, endDate: String?): Flow<IncExp> {
//        return flowOf(IncExp(
//            1030.0, 200.0,
//            incomeCount = 2,
//            expenseCount = 3
//        ))
//    }
//
//
//}