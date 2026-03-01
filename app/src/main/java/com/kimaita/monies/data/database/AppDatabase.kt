package com.kimaita.monies.data.database


import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kimaita.monies.data.dao.AccountDao
import com.kimaita.monies.data.dao.CategoryDao
import com.kimaita.monies.data.dao.SettingsDao
import com.kimaita.monies.data.dao.SubjectDao
import com.kimaita.monies.data.dao.TransactionTypeDao
import com.kimaita.monies.data.dao.TransactionsDao
import com.kimaita.monies.data.database.models.Category
import com.kimaita.monies.data.database.models.Setting
import com.kimaita.monies.data.database.models.Subject
import com.kimaita.monies.data.database.models.Transaction
import com.kimaita.monies.data.database.models.TransactionFts
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.data.database.models.UserAccount


/**
 * The [RoomDatabase] for the app.
 */
@Database(
    entities = [
        Transaction::class,
        TransactionFts::class,
        Category::class,
        Subject::class,
        UserAccount::class,
        TransactionType::class,
        Setting::class
    ], version = 1, exportSchema = false
)
@TypeConverters(DateTimeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionsDao(): TransactionsDao
    abstract fun transactionTypeDao(): TransactionTypeDao
    abstract fun categoriesDao(): CategoryDao
    abstract fun subjectDao(): SubjectDao
    abstract fun settingsDao(): SettingsDao
    abstract fun accountDao(): AccountDao
//    abstract fun transactionRunnerDao(): TransactionRunnerDao
}