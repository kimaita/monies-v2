package com.kimaita.monies.data.database

import com.kimaita.monies.data.database.models.TransactionWithDetails
import com.kimaita.monies.data.database.models.Category as DbCategory
import com.kimaita.monies.data.database.models.Subject as DbSubject
import com.kimaita.monies.data.database.models.TransactionType as DbTransactionType
import com.kimaita.monies.models.Category as DomainCategory
import com.kimaita.monies.models.Subject as DomainSubject
import com.kimaita.monies.models.Transaction as DomainTransaction
import com.kimaita.monies.models.TransactionType as DomainTransactionType


fun TransactionWithDetails.toDomainModel(): DomainTransaction {
    return DomainTransaction(
        id = this.transaction.id,
        amount = this.transaction.amount,
        code = this.transaction.transactionCode ?: "N/A",
        transactionTime = this.transaction.transactionTime,
        subject = this.subject?.toDomainModel(),
        type = this.type.toDomainModel(),
        category = this.category?.toDomainModel(),
        isInc = this.type.isInc,
        message = this.transaction.message,
        cost = this.transaction.cost
    )
}

fun DbSubject.toDomainModel(): DomainSubject {
    return DomainSubject(
        id = this.id,
        name = this.name,
        number = this.number
    )
}

fun DbCategory.toDomainModel(): DomainCategory {
    return DomainCategory(
        id = this.id,
        name = this.name
    )
}

fun DbTransactionType.toDomainModel(): DomainTransactionType {
    return DomainTransactionType(
        id = this.id,
        name = this.name,
        isInc = this.isInc,
        displayName = this.displayName
    )
}
