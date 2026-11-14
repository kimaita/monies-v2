package com.kimaita.monies.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.kimaita.monies.data.dao.dtos.SourceAmount
import com.kimaita.monies.data.dao.dtos.SubjectAmount
import com.kimaita.monies.data.dao.dtos.SubjectCount
import com.kimaita.monies.data.dao.dtos.SubjectStats
import com.kimaita.monies.data.database.models.Subject
import com.kimaita.monies.services.SavedSubject
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

    /** Subjects with these raw names, each with the id and category to use: its merge target's when merged. */
    @Query(
        """
        SELECT s.rawName AS rawName, s.number AS number, r.id AS id, r.categoryId AS categoryId
        FROM subjects s JOIN subjects r ON r.id = COALESCE(s.mergedInto, s.id)
        WHERE s.rawName IN (:rawNames)
        """
    )
    suspend fun findByRawNames(rawNames: List<String>): List<SavedSubject>

    @Query("SELECT categoryId FROM subjects WHERE id = :id")
    suspend fun getCategoryId(id: Long): Int?

    @Query("UPDATE subjects SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE transactions SET subject = :target WHERE subject = :source")
    suspend fun moveTransactions(source: Long, target: Long)

    @Query("UPDATE subjects SET mergedInto = :target WHERE id = :source OR mergedInto = :source")
    suspend fun markMerged(source: Long, target: Long)

    @Query("UPDATE subjects SET categoryId = :category WHERE id = :id AND categoryId IS NULL")
    suspend fun setCategoryIfNone(id: Long, category: Int)

    /**
     * Folds [source] into [target]: its transactions move over, rows already merged into it follow,
     * and [source] points at [target]. The target keeps its category; it takes the source's only
     * when it has none. Returns false when the merge makes no sense (same row, or target is merged).
     */
    @Transaction
    suspend fun merge(source: Long, target: Long): Boolean {
        if (source == target || getIdByIdOrRoot(target) != target) return false
        getCategoryId(source)?.let { setCategoryIfNone(target, it) }
        moveTransactions(source, target)
        markMerged(source, target)
        return true
    }

    @Query("SELECT COALESCE(mergedInto, id) FROM subjects WHERE id = :id")
    suspend fun getIdByIdOrRoot(id: Long): Long?

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
    AND $DATE_RANGE
    AND $ACCOUNT_FILTER
    """
    )
    fun getSubjectStats(
        subjectId: Long, startDate: String? = null, endDate: String? = null, account: Int? = null
    ): Flow<SubjectStats>


    data class SubjectRef(val id: Long, val name: String)

    @Query("SELECT id, name FROM subjects WHERE mergedInto IS NULL AND name LIKE '%' || :q || '%' COLLATE NOCASE ORDER BY name LIMIT :limit")
    suspend fun searchSubjects(q: String, limit: Int = 30): List<SubjectRef>

    /** Subjects whose display name matches [like] (see subjectLikeOf), with their transaction count, busiest first. */
    @Query(
        """
        SELECT s.id AS subjectId, s.name AS name, COUNT(t.id) AS count
        FROM subjects s
        JOIN transactions t ON t.subject = s.id
        WHERE s.mergedInto IS NULL AND s.name LIKE :like
          AND $ACCOUNT_FILTER
        GROUP BY s.id ORDER BY count DESC, s.name LIMIT :limit
    """
    )
    fun matchingSubjects(like: String, account: Int? = null, limit: Int = 5): Flow<List<SubjectCount>>

    @Query(
        """
            SELECT s.id, s.name 
            FROM subjects s
            LEFT JOIN transactions t ON t.subject = s.id
            WHERE s.mergedInto IS NULL
            GROUP BY s.id 
            ORDER BY COUNT(t.id) DESC 
            LIMIT :limit
        """
    )
    suspend fun topSubjects(limit: Int = 15): List<SubjectRef>

    // List params use COALESCE(:p, NULL) IS NULL, not :p IS NULL: Room expands a list into one ? per
    // element, so `(?,? IS NULL` is a SQLite row value and fails with "row value misused" for 2+ ids.
    @Query(
        """
    SELECT s.id AS subjectId, s.name AS name, SUM(t.amount) AS amount
    FROM transactions t
    JOIN subjects s ON t.subject = s.id
    JOIN transaction_types tt ON t.transactionType = tt.id
    WHERE $DATE_RANGE
      AND $SUBJECTS_IN
      AND $CATEGORIES_IN
      AND $TYPES_IN
      AND (:natureFilter IS NULL OR tt.isInc = :natureFilter)
      AND tt.isTransfer = 0
      AND $ACCOUNT_FILTER
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
    WHERE $DATE_RANGE
      AND $SUBJECTS_IN
      AND $CATEGORIES_IN
      AND $TYPES_IN
      AND (:natureFilter IS NULL OR tt.isInc = :natureFilter)
      AND tt.isTransfer = 0
      AND $ACCOUNT_FILTER
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
      AND $DATE_RANGE
      AND $SUBJECTS_IN
      AND $CATEGORIES_IN
      AND $TYPES_IN
      AND $ACCOUNT_FILTER
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


