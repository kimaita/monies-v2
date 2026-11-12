package com.kimaita.monies.di

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
import com.kimaita.monies.services.SmsSyncer
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
        categoryDaoProvider: Provider<CategoryDao>,
        userAccountDaoProvider: Provider<AccountDao>,
    ): AppDatabase {
        return Room.databaseBuilder(
            context, AppDatabase::class.java, "monies-db",
        )
            .fallbackToDestructiveMigration(false)
            .addCallback(
                DatabaseInitializer(
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

}


class DatabaseInitializer(
    private val categoryDaoProvider: Provider<CategoryDao>,
    private val userAccountDaoProvider: Provider<AccountDao>,
) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        CoroutineScope(Dispatchers.IO).launch {
            populateDatabase()
        }
    }

    /** Runs synchronously before any DAO sees the database, so a sync can never miss a new type. */
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        syncDefaultTransactionTypes(db)
    }

    /**
     * Inserts default types that are missing and updates ones whose pattern, direction or label
     * changed, matching rows by name. When anything changes, the SMS cursor is rewound so the
     * next sync re-reads the inbox; already-saved messages are skipped by the unique index on
     * `transactions.message`. The cursor is only rewound if it exists, so a first run stays one.
     */
    private fun syncDefaultTransactionTypes(db: SupportSQLiteDatabase) {
        data class Row(val id: Long, val pattern: String?, val isInc: Boolean, val displayName: String?)

        val existing = HashMap<String, Row>()
        db.query("SELECT id, name, pattern, isInc, displayName FROM transaction_types").use { c ->
            while (c.moveToNext()) {
                existing[c.getString(1)] = Row(
                    id = c.getLong(0),
                    pattern = c.getString(2),
                    isInc = c.getInt(3) != 0,
                    displayName = c.getString(4),
                )
            }
        }

        var changed = false
        db.beginTransaction()
        try {
            for (type in getDefaultTransactionTypes()) {
                val values = ContentValues().apply {
                    put("pattern", type.pattern)
                    put("isInc", if (type.isInc) 1 else 0)
                    put("displayName", type.displayName)
                }
                val row = existing[type.name]
                if (row == null) {
                    values.put("name", type.name)
                    values.put("provider", type.provider)
                    values.put("isActive", if (type.isActive) 1 else 0)
                    db.insert("transaction_types", SQLiteDatabase.CONFLICT_IGNORE, values)
                    changed = true
                } else if (row.pattern != type.pattern || row.isInc != type.isInc || row.displayName != type.displayName) {
                    db.update(
                        "transaction_types", SQLiteDatabase.CONFLICT_REPLACE, values,
                        "id = ?", arrayOf(row.id)
                    )
                    changed = true
                }
            }
            if (changed) {
                db.execSQL(
                    "UPDATE settings SET value = '0' WHERE `key` = ?",
                    arrayOf(SmsSyncer.LAST_SMS_SYNC_ID_KEY)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private suspend fun populateDatabase() {
        // Transaction types are inserted by onOpen, which Room calls straight after onCreate.
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