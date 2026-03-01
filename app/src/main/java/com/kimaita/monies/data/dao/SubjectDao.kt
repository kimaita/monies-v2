package com.kimaita.monies.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT id FROM subjects WHERE name = :name AND number = :number")
    suspend fun getIdByNameAndNumber(name: String?, number: String?): Long?


    @Transaction
    suspend fun upsertAndGetId(subject: Subject): Long {
        val id = insert(subject)
        return if (id == -1L) {
            getIdByNameAndNumber(subject.name, subject.number)!!
        } else {
            id
        }
    }

    /**
     * Gets the top subject (by total spending) for each expense transaction type.
     */
    @Query(
        """
        WITH RankedSubjects AS (
            SELECT
                t.transactionType,
                s.name as subjectName,
                SUM(t.amount) as totalAmount,
                ROW_NUMBER() OVER(PARTITION BY t.transactionType ORDER BY SUM(t.amount) DESC) as rn
            FROM transactions AS t
            JOIN subjects AS s ON t.subject = s.id
            WHERE t.transactionType IN (SELECT id FROM transaction_types WHERE isInc = 0)
            AND (:startDate IS NULL OR t.transactionTime >= :startDate)
            AND (:endDate IS NULL OR t.transactionTime <= :endDate)
            GROUP BY t.transactionType, s.id
        )
        SELECT
            tt.name as typeName,
            rs.subjectName,
            rs.totalAmount
        FROM RankedSubjects AS rs
        JOIN transaction_types AS tt ON rs.transactionType = tt.id
        WHERE rs.rn = 1
        """
    )
    fun getTopSubjectPerType(
        startDate: String?, endDate: String?
    ): Flow<List<TopSubject>>

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

}


