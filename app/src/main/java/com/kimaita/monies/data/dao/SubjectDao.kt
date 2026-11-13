package com.kimaita.monies.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.kimaita.monies.data.dao.dtos.SourceAmount
import com.kimaita.monies.data.dao.dtos.SubjectAmount
import com.kimaita.monies.data.dao.dtos.SubjectCount
import com.kimaita.monies.data.dao.dtos.SubjectStats
import com.kimaita.monies.data.dao.dtos.TopSubject
import com.kimaita.monies.data.database.models.Subject
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao : BaseDao<Subject> {

    @Query(
        """
        SELECT * FROM subjects
        WHERE (:id IS NULL OR :id=id)
        """
    )
    fun getSubjects(id: Long? = null): Flow<List<Subject>>

    @Query("SELECT id, name FROM subjects WHERE id IN (:ids)")
    suspend fun getSubjects(ids: List<Long>): List<SubjectRef>

    /** The id to use for this subject: its merge target when it has been merged. */
    @Query("SELECT COALESCE(mergedInto, id) FROM subjects WHERE rawName = :rawName AND number = :number")
    suspend fun getIdByRawNameAndNumber(rawName: String, number: String): Long?

    @Query("SELECT categoryId FROM subjects WHERE id = :id")
    suspend fun getCategoryId(id: Long): Int?


    @Transaction
    suspend fun upsertAndGetId(subject: Subject): Long {
        return getIdByRawNameAndNumber(subject.rawName, subject.number) ?: insert(subject)
    }

    @Query(
        """
    SELECT 
        SUM(CASE WHEN isInc = 1 AND isTransfer = 0 THEN amount ELSE 0 END) as incomingTotal,
        SUM(CASE WHEN isInc = 0 AND isTransfer = 0 THEN amount ELSE 0 END) as outgoingTotal,
        COUNT(CASE WHEN isInc = 1 AND isTransfer = 0 THEN 1 END) as incomingCount,
        COUNT(CASE WHEN isInc = 0 AND isTransfer = 0 THEN 1 END) as outgoingCount,
        SUM(cost) as totalCost
    FROM transactions t
    JOIN transaction_types ON t.transactionType = transaction_types.id
    WHERE subject = :subjectId
    AND (:startDate IS NULL OR t.transactionTime >= :startDate)
    AND (:endDate IS NULL OR t.transactionTime <= :endDate)
    AND (:account IS NULL OR t.accountId = :account)
    """
    )
    fun getSubjectStats(
        subjectId: Long, startDate: String? = null, endDate: String? = null, account: Int? = null
    ): Flow<SubjectStats>


    data class SubjectRef(val id: Long, val name: String)

    @Query("SELECT id, name FROM subjects WHERE name LIKE '%' || :q || '%' COLLATE NOCASE ORDER BY name LIMIT :limit")
    suspend fun searchSubjects(q: String, limit: Int = 30): List<SubjectRef>

    @Query("SELECT id, name FROM subjects WHERE id = :id")
    suspend fun getSubject(id: Long): SubjectRef?

    @Query(
        """
            SELECT s.id, s.name 
            FROM subjects s
            LEFT JOIN transactions t ON t.subject = s.id
            GROUP BY s.id 
            ORDER BY COUNT(t.id) DESC 
            LIMIT :limit
        """
    )
    suspend fun topSubjects(limit: Int = 15): List<SubjectRef>

    @Query(
        """
    SELECT s.id AS subjectId, s.name AS name, SUM(t.amount) AS amount
    FROM transactions t
    JOIN subjects s ON t.subject = s.id
    JOIN transaction_types tt ON t.transactionType = tt.id
    WHERE (:startDate IS NULL OR t.transactionTime >= :startDate)
      AND (:endDate IS NULL OR t.transactionTime <= :endDate)
      AND (:subjectId IS NULL OR t.subject IN (:subjectId))
      AND (:categoryId IS NULL OR t.transactionCategory IN (:categoryId))
      AND (:transactionType IS NULL OR t.transactionType IN (:transactionType))
      AND (:natureFilter IS NULL OR tt.isInc = :natureFilter)
      AND tt.isTransfer = 0
      AND (:account IS NULL OR t.accountId = :account)
    GROUP BY s.id ORDER BY amount DESC LIMIT :limit
"""
    )
    fun getTopSubjectsByAmount(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        natureFilter: Boolean?,
        limit: Int,
        account: Int? = null,
    ): Flow<List<SubjectAmount>>

    @Query(
        """
    SELECT s.id AS subjectId, s.name AS name, COUNT(*) AS count
    FROM transactions t
    JOIN subjects s ON t.subject = s.id
    JOIN transaction_types tt ON t.transactionType = tt.id
    WHERE (:startDate IS NULL OR t.transactionTime >= :startDate)
      AND (:endDate IS NULL OR t.transactionTime <= :endDate)
      AND (:subjectId IS NULL OR t.subject IN (:subjectId))
      AND (:categoryId IS NULL OR t.transactionCategory IN (:categoryId))
      AND (:transactionType IS NULL OR t.transactionType IN (:transactionType))
      AND (:natureFilter IS NULL OR tt.isInc = :natureFilter)
      AND tt.isTransfer = 0
      AND (:account IS NULL OR t.accountId = :account)
    GROUP BY s.id ORDER BY count DESC LIMIT :limit
"""
    )
    fun getTopSubjectsByCount(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        natureFilter: Boolean?,
        limit: Int,
        account: Int? = null
    ): Flow<List<SubjectCount>>

    @Query(
        """
    SELECT s.name AS name, SUM(t.amount) AS amount
    FROM transactions t
    JOIN subjects s ON t.subject = s.id
    JOIN transaction_types tt ON t.transactionType = tt.id
    WHERE tt.isInc = 1 AND tt.isTransfer = 0
      AND (:startDate IS NULL OR t.transactionTime >= :startDate)
      AND (:endDate IS NULL OR t.transactionTime <= :endDate)
      AND (:subjectId IS NULL OR t.subject IN (:subjectId))
      AND (:categoryId IS NULL OR t.transactionCategory IN (:categoryId))
      AND (:transactionType IS NULL OR t.transactionType IN (:transactionType))
      AND (:account IS NULL OR t.accountId = :account)
    GROUP BY s.id ORDER BY amount DESC LIMIT :limit
"""
    )
    fun getIncomeBySource(
        startDate: String?,
        endDate: String?,
        subjectId: List<Long>?,
        categoryId: List<Int>?,
        transactionType: List<Int>?,
        limit: Int,
        account: Int? = null
    ): Flow<List<SourceAmount>>

}


