package com.kimaita.monies.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kimaita.monies.data.dao.AccountDao
import com.kimaita.monies.data.dao.CategoryDao
import com.kimaita.monies.data.dao.SettingsDao
import com.kimaita.monies.data.dao.SubjectDao
import com.kimaita.monies.data.dao.TransactionTypeDao
import com.kimaita.monies.data.dao.TransactionsDao
import com.kimaita.monies.data.database.AppDatabase
import com.kimaita.monies.data.database.models.UserAccount
import com.kimaita.monies.data.database.models.getDefaultCategories
import com.kimaita.monies.data.database.models.getDefaultTransactionTypes
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        transactionTypeDaoProvider: Provider<TransactionTypeDao>,
        categoryDaoProvider: Provider<CategoryDao>,
        userAccountDaoProvider: Provider<AccountDao>,
    ): AppDatabase {
        return Room.databaseBuilder(
            context, AppDatabase::class.java, "monies-db",
        )
            .fallbackToDestructiveMigration(false)
            .addCallback(
                DatabaseInitializer(
                    transactionTypeDaoProvider,
                    categoryDaoProvider,
                    userAccountDaoProvider
                )
            )
            .build()
    }

    @Provides
    fun provideTransactionsDao(appDatabase: AppDatabase): TransactionsDao {
        return appDatabase.transactionsDao()
    }

    @Provides
    fun provideTransactionTypeDao(appDatabase: AppDatabase): TransactionTypeDao {
        return appDatabase.transactionTypeDao()
    }

    @Provides
    fun provideCategoryDao(appDatabase: AppDatabase): CategoryDao {
        return appDatabase.categoriesDao()
    }

    @Provides
    fun provideSubjectDao(appDatabase: AppDatabase): SubjectDao {
        return appDatabase.subjectDao()
    }

    @Provides
    fun provideSettingsDao(appDatabase: AppDatabase): SettingsDao {
        return appDatabase.settingsDao()
    }

    @Provides
    fun provideAccountDao(appDatabase: AppDatabase): AccountDao {
        return appDatabase.accountDao()
    }


//    @Provides
//    fun provideTransactionRunnerDao(appDatabase: AppDatabase): TransactionRunnerDao {
//        return appDatabase.transactionRunnerDao()
//    }
}


class DatabaseInitializer(
    private val daoProvider: Provider<TransactionTypeDao>,
    private val categoryDaoProvider: Provider<CategoryDao>,
    private val userAccountDaoProvider: Provider<AccountDao>,
) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        CoroutineScope(Dispatchers.IO).launch {
            populateDatabase()
        }
    }

    private suspend fun populateDatabase() {
        daoProvider.get().insertAll(getDefaultTransactionTypes());
        categoryDaoProvider.get().insertAll(getDefaultCategories())
        userAccountDaoProvider.get().insert(
            UserAccount(
                subscriptionId = 0,
                carrier = null,
                number = null
            )
        )

    }
}