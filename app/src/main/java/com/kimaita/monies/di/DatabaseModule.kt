package com.kimaita.monies.di

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kimaita.monies.data.dao.AccountDao
import com.kimaita.monies.data.dao.CategoryDao
import com.kimaita.monies.data.dao.SettingsDao
import com.kimaita.monies.data.dao.SubjectDao
import com.kimaita.monies.data.dao.TransactionTypeDao
import com.kimaita.monies.data.dao.TransactionsDao
import com.kimaita.monies.data.dao.UnparsedMessageDao
import com.kimaita.monies.data.database.AppDatabase
import com.kimaita.monies.data.database.models.categoryKey
import com.kimaita.monies.data.database.models.defaultCategoryId
import com.kimaita.monies.data.database.models.defaultCategoryRulesVersion
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

/** Adds `accounts.mergedInto` and clears the carrier-derived labels the old sync wrote. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN mergedInto INTEGER")
        db.execSQL("UPDATE accounts SET label = NULL")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        categoryDaoProvider: Provider<CategoryDao>,
    ): AppDatabase {
        return Room.databaseBuilder(
            context, AppDatabase::class.java, "monies-db",
        )
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigrationFrom(true, 1)
            .addCallback(
                DatabaseInitializer(categoryDaoProvider)
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

    @Provides
    fun provideUnparsedMessageDao(appDatabase: AppDatabase): UnparsedMessageDao {
        return appDatabase.unparsedMessageDao()
    }

}


class DatabaseInitializer(
    private val categoryDaoProvider: Provider<CategoryDao>,
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
        applyDefaultCategoryRules(db)
    }

    /**
     * Puts saved subjects that have no category into their built-in one (see defaultCategoryRules),
     * along with their transactions that have none. Runs once per version of the rules, so a category
     * the user clears is not put back on every open; subjects created later get theirs during sync.
     */
    private fun applyDefaultCategoryRules(db: SupportSQLiteDatabase) {
        val version = defaultCategoryRulesVersion
        val saved = db.query("SELECT value FROM settings WHERE `key` = ?", arrayOf(CATEGORY_RULES_KEY))
            .use { c -> if (c.moveToFirst()) c.getString(0) else null }
        if (saved == version) return

        val categoryIds = HashMap<String, Int>()
        db.query("SELECT id, name FROM categories").use { c ->
            while (c.moveToNext()) categoryIds[categoryKey(c.getString(1))] = c.getInt(0)
        }
        val matches = ArrayList<Pair<Long, Int>>()
        db.query("SELECT id, rawName FROM subjects WHERE categoryId IS NULL AND mergedInto IS NULL").use { c ->
            while (c.moveToNext()) {
                defaultCategoryId(c.getString(1), categoryIds)?.let { matches += c.getLong(0) to it }
            }
        }

        db.beginTransaction()
        try {
            for ((subject, category) in matches) {
                db.execSQL("UPDATE subjects SET categoryId = ? WHERE id = ?", arrayOf<Any>(category, subject))
                db.execSQL(
                    "UPDATE transactions SET transactionCategory = ? WHERE subject = ? AND transactionCategory IS NULL",
                    arrayOf<Any>(category, subject)
                )
            }
            db.execSQL(
                "INSERT OR REPLACE INTO settings (`key`, value) VALUES (?, ?)",
                arrayOf(CATEGORY_RULES_KEY, version)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Inserts default types that are missing and updates ones whose pattern, direction or label
     * changed, matching rows by name. When anything changes, the SMS cursor is rewound so the
     * next sync re-reads the inbox; already-saved messages are skipped by the unique index on
     * `transactions.message`. The cursor is only rewound if it exists, so a first run stays one.
     */
    private fun syncDefaultTransactionTypes(db: SupportSQLiteDatabase) {
        data class Row(
            val id: Long, val pattern: String?, val isInc: Boolean, val displayName: String?,
            val isTransfer: Boolean, val provider: String,
        )

        val existing = HashMap<String, Row>()
        db.query("SELECT id, name, pattern, isInc, displayName, isTransfer, provider FROM transaction_types").use { c ->
            while (c.moveToNext()) {
                existing[c.getString(1)] = Row(
                    id = c.getLong(0),
                    pattern = c.getString(2),
                    isInc = c.getInt(3) != 0,
                    displayName = c.getString(4),
                    isTransfer = c.getInt(5) != 0,
                    provider = c.getString(6),
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
                    put("isTransfer", if (type.isTransfer) 1 else 0)
                    put("provider", type.provider)
                }
                val row = existing[type.name]
                if (row == null) {
                    values.put("name", type.name)
                    values.put("isActive", if (type.isActive) 1 else 0)
                    db.insert("transaction_types", SQLiteDatabase.CONFLICT_IGNORE, values)
                    changed = true
                } else if (row.pattern != type.pattern || row.isInc != type.isInc || row.displayName != type.displayName ||
                    row.isTransfer != type.isTransfer || row.provider != type.provider
                ) {
                    db.update(
                        "transaction_types", SQLiteDatabase.CONFLICT_REPLACE, values,
                        "id = ?", arrayOf(row.id)
                    )
                    changed = true
                }
            }
            if (changed) {
                db.execSQL("DELETE FROM unparsed_messages")
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

    private companion object {
        const val CATEGORY_RULES_KEY = "category_rules_version"
    }

    private suspend fun populateDatabase() {
        // Transaction types are inserted by onOpen, which Room calls straight after onCreate.
        categoryDaoProvider.get().insertAll(getDefaultCategories())
    }
}