package com.kimaita.monies.data.dao

// Query clauses shared by the DAOs, all written against the alias `t` for `transactions`.

/** Keeps a line's transactions, merged accounts included. A null account means every account. */
internal const val ACCOUNT_FILTER =
    "(:account IS NULL OR t.accountId IN (SELECT id FROM accounts WHERE COALESCE(mergedInto, id) = :account))"

/** Both ends are optional; dates are ISO text and compare as text. */
internal const val DATE_RANGE =
    "(:startDate IS NULL OR t.transactionTime >= :startDate) AND (:endDate IS NULL OR t.transactionTime <= :endDate)"

// List params use COALESCE(:p, NULL) IS NULL, not :p IS NULL: Room expands a list into one ? per
// element, so `(?,? IS NULL` is a SQLite row value and fails with "row value misused" for 2+ ids.
internal const val SUBJECTS_IN = "(COALESCE(:subjectId, NULL) IS NULL OR t.subject IN (:subjectId))"
internal const val CATEGORIES_IN = "(COALESCE(:categoryId, NULL) IS NULL OR t.transactionCategory IN (:categoryId))"
internal const val TYPES_IN = "(COALESCE(:transactionType, NULL) IS NULL OR t.transactionType IN (:transactionType))"
