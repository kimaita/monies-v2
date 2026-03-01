package com.kimaita.monies.data.database

import androidx.room.Dao
import androidx.room.Ignore
import androidx.room.Transaction


/**
 * Interface with operator function which will invoke the suspending lambda within a database
 * transaction.
 */
interface TransactionRunner {
    suspend operator fun invoke(tx: suspend () -> Unit)
}

/**
 * [androidx.room.Room] DAO which provides the implementation for our [TransactionRunner].
 */
@Dao
abstract class TransactionRunnerDao : TransactionRunner {
    @Transaction
    protected open suspend fun runInTransaction(tx: suspend () -> Unit) = tx()

    @Ignore
    override suspend fun invoke(tx: suspend () -> Unit) {
        runInTransaction(tx)
    }
}
