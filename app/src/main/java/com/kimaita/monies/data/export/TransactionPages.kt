package com.kimaita.monies.data.export

import com.kimaita.monies.data.database.models.TransactionWithDetails

fun interface TransactionPages {
    suspend fun forEach(onPage: suspend (List<TransactionWithDetails>) -> Unit)
}