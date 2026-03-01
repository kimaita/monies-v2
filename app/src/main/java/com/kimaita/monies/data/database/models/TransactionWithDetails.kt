package com.kimaita.monies.data.database.models 

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A data class to hold a Transaction and its related information.
 * This is used to efficiently load and display comprehensive transaction
 * details in the UI from a single database query.
 */
data class TransactionWithDetails(

    @Embedded val transaction: Transaction,

    @Relation(
        parentColumn = "subject", entityColumn = "id"
    ) val subject: Subject?,

    @Relation(
        parentColumn = "transactionType", entityColumn = "id"
    ) val type: TransactionType,

    @Relation(
        parentColumn = "transactionCategory", entityColumn = "id"
    ) val category: Category?
)