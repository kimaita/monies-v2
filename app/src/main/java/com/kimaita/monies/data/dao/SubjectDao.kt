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

    @Query("SELECT id FROM subjects WHERE rawName = :rawName AND number = :number")
    suspend fun getIdByRawNameAndNumber(rawName: String?, number: String?): Long?


    @Transaction
    suspend fun upsertAndGetId(subject: Subject): Long {
        val id = insert(subject)
        return if (id == -1L) {
            getIdByRawNameAndNumber(subject.rawName, subject.number)!!
        } else {
            id
        }
    }

    @Query(
        """
    SELECT 
        SUM(CASE WHEN isInc = 1 THEN amount ELSE 0 END) as incomingTotal,
        SUM(CASE WHEN isInc = 0 THEN amount ELSE 0 END) as outgoingTotal,
        COUNT(CASE WHEN isInc = 1 THEN 1 END) as incomingCount,
        COUNT(CASE WHEN isInc = 0 THEN 1 END) as outgoingCount,
        SUM(cost) as totalCost
    FROM transactions t
    JOIN transaction_types ON t.transactionType = transaction_types.id
    WHERE subject = :subjectId
    AND (:startDate IS NULL OR t.transactionTime >= :startDate)
    AND (:endDate IS NULL OR t.transactionTime <= :endDate)
    """
    )
    fun getSubjectStats(
        subjectId: Long, startDate: String? = null, endDate: String? = null
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
      AND (:transactionType IS NULL OR t.transactionType IN (:transactionType))
      AND (:transactionCategory IS NULL OR t.transactionCategory IN (:transactionCategory))
      AND (:natureFilter IS NULL OR tt.isInc = :natureFilter)
    GROUP BY s.id ORDER BY amount DESC LIMIT :limit
"""
    )
    fun getTopSubjectsByAmount(
        startDate: String?,
        endDate: String?,
        transactionType: List<Int>?,
        transactionCategory: List<Int>?,
        natureFilter: Boolean?,
        limit: Int,
    ): Flow<List<SubjectAmount>>

    @Query(
        """
    SELECT s.id AS subjectId, s.name AS name, COUNT(*) AS count
    FROM transactions t
    JOIN subjects s ON t.subject = s.id
    WHERE (:startDate IS NULL OR t.transactionTime >= :startDate)
      AND (:endDate IS NULL OR t.transactionTime <= :endDate)
    GROUP BY s.id ORDER BY count DESC LIMIT :limit
"""
    )
    fun getTopSubjectsByCount(
        startDate: String?,
        endDate: String?,
        limit: Int
    ): Flow<List<SubjectCount>>

    @Query(
        """
    SELECT s.name AS name, SUM(t.amount) AS amount
    FROM transactions t
    JOIN subjects s ON t.subject = s.id
    JOIN transaction_types tt ON t.transactionType = tt.id
    WHERE tt.isInc = 1
      AND (:startDate IS NULL OR t.transactionTime >= :startDate)
      AND (:endDate IS NULL OR t.transactionTime <= :endDate)
    GROUP BY s.id ORDER BY amount DESC LIMIT :limit
"""
    )
    fun getIncomeBySource(
        startDate: String?,
        endDate: String?,
        limit: Int
    ): Flow<List<SourceAmount>>

}


