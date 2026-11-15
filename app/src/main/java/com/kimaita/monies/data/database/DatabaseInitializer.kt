package com.kimaita.monies.data.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kimaita.monies.data.database.models.categoryKey
import com.kimaita.monies.data.database.models.defaultCategoryId
import com.kimaita.monies.data.database.models.defaultCategoryRulesVersion
import com.kimaita.monies.data.database.models.getDefaultCategories
import com.kimaita.monies.data.database.models.getDefaultTransactionTypes
import com.kimaita.monies.services.SmsSyncer
import com.kimaita.monies.services.fitsMask
import com.kimaita.monies.services.splitMaskedName
import com.kimaita.monies.services.typeRegex

/** Adds `accounts.mergedInto` and clears the carrier-derived labels the old sync wrote. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN mergedInto INTEGER")
        db.execSQL("UPDATE accounts SET label = NULL")
    }
}

/**
 * Repairs received messages saved before the patterns accepted masked numbers ("0712***880"). They
 * fell to received.bank with the number left in the subject's name. Each row gets received.person;
 * each such subject gets its name and number apart and, when it is not merged yet and exactly one
 * saved subject has a number that fits, is merged into that subject.
 * Merges the user made by hand stay as they are.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        retypeMaskedRows(db, from = "received.bank", to = "received.person")
        splitMaskedSubjects(db)
    }
}

private fun SupportSQLiteDatabase.typeId(name: String): Long? =
    query("SELECT id FROM transaction_types WHERE name = ?", arrayOf(name))
        .use { c -> if (c.moveToFirst()) c.getLong(0) else null }

private fun retypeMaskedRows(db: SupportSQLiteDatabase, from: String, to: String) {
    val fromId = db.typeId(from) ?: return
    val toId = db.typeId(to) ?: return
    val regex = typeRegex(getDefaultTransactionTypes().first { it.name == to }.pattern!!)
    val ids = ArrayList<Long>()
    db.query("SELECT id, message FROM transactions WHERE transactionType = ? AND message LIKE '%*%'", arrayOf(fromId))
        .use { c -> while (c.moveToNext()) if (regex.containsMatchIn(c.getString(1))) ids += c.getLong(0) }
    for (id in ids) db.execSQL("UPDATE transactions SET transactionType = ? WHERE id = ?", arrayOf<Any>(toId, id))
}

private fun splitMaskedSubjects(db: SupportSQLiteDatabase) {
    class Row(val id: Long, val rawName: String, val merged: Boolean, val categoryId: Int?)

    val rows = ArrayList<Row>()
    db.query("SELECT id, rawName, mergedInto, categoryId FROM subjects WHERE number = '' AND rawName LIKE '%*%'").use { c ->
        while (c.moveToNext()) rows += Row(c.getLong(0), c.getString(1), !c.isNull(2), if (c.isNull(3)) null else c.getInt(3))
    }
    for (row in rows) {
        val (name, number) = splitMaskedName(row.rawName) ?: continue
        if (!row.merged) {
            val roots = HashSet<Long>()
            db.query("SELECT number, COALESCE(mergedInto, id) FROM subjects WHERE rawName = ?", arrayOf(name))
                .use { c -> while (c.moveToNext()) if (fitsMask(number, c.getString(0))) roots += c.getLong(1) }
            // A root equal to this row means the user merged the full-number subject into it: keep that.
            roots.singleOrNull()?.takeIf { it != row.id }?.let { root ->
                row.categoryId?.let {
                    db.execSQL("UPDATE subjects SET categoryId = ? WHERE id = ? AND categoryId IS NULL", arrayOf<Any>(it, root))
                }
                db.execSQL("UPDATE transactions SET subject = ? WHERE subject = ?", arrayOf<Any>(root, row.id))
                db.execSQL("UPDATE subjects SET mergedInto = ? WHERE id = ? OR mergedInto = ?", arrayOf<Any>(root, row.id, row.id))
            }
        }
        db.execSQL(
            "UPDATE OR IGNORE subjects SET rawName = ?, number = ?, name = CASE WHEN name = rawName THEN ? ELSE name END WHERE id = ?",
            arrayOf<Any>(name, number, name, row.id)
        )
    }
}

class DatabaseInitializer : RoomDatabase.Callback() {

    /** Inserts the default categories before any DAO sees the database, so a first sync never misses one. */
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        for (category in getDefaultCategories()) {
            val values = ContentValues().apply {
                put("name", category.name)
                put("description", category.description)
                put("monthlyBudget", category.monthlyBudget)
            }
            db.insert("categories", SQLiteDatabase.CONFLICT_IGNORE, values)
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
}
