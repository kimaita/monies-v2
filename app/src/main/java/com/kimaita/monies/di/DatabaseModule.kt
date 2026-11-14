package com.kimaita.monies.di

import android.content.Context
import androidx.room.Room
import com.kimaita.monies.data.dao.AccountDao
import com.kimaita.monies.data.dao.CategoryDao
import com.kimaita.monies.data.dao.SettingsDao
import com.kimaita.monies.data.dao.SubjectDao
import com.kimaita.monies.data.dao.TransactionTypeDao
import com.kimaita.monies.data.dao.TransactionsDao
import com.kimaita.monies.data.dao.UnparsedMessageDao
import com.kimaita.monies.data.database.AppDatabase
import com.kimaita.monies.data.database.DatabaseInitializer
import com.kimaita.monies.data.database.MIGRATION_2_3
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase {
        return Room.databaseBuilder(
            context, AppDatabase::class.java, "monies-db",
        )
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigrationFrom(true, 1)
            .addCallback(DatabaseInitializer())
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

    @Provides
    fun provideUnparsedMessageDao(appDatabase: AppDatabase): UnparsedMessageDao {
        return appDatabase.unparsedMessageDao()
    }

}
