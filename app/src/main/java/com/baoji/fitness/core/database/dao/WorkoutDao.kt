package com.baoji.fitness.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    fun getActiveSession(): Flow<WorkoutSessionEntity?>

    /** 所有未归档会话，供跨天清理使用。 */
    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE'")
    suspend fun getActiveSessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    fun getSessionById(sessionId: Long): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<WorkoutSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Query("UPDATE workout_sessions SET status = 'COMPLETED' WHERE status = 'ACTIVE'")
    suspend fun closeAllActiveSessions()

    // Sets
    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY exerciseId ASC, setOrder ASC, id ASC")
    fun getSetsForSession(sessionId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId AND exerciseId = :exerciseId ORDER BY setOrder ASC, id ASC")
    suspend fun getSetsForExerciseInSession(sessionId: Long, exerciseId: Long): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId AND isCompleted = 1 AND sessionId != :currentSessionId ORDER BY completedAt DESC, id DESC")
    suspend fun getHistoricalCompletedSets(exerciseId: Long, currentSessionId: Long): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId AND isCompleted = 1 ORDER BY completedAt ASC")
    fun getHistoryForExercise(exerciseId: Long): Flow<List<WorkoutSetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: WorkoutSetEntity): Long

    @Update
    suspend fun updateSet(set: WorkoutSetEntity)

    @Delete
    suspend fun deleteSet(set: WorkoutSetEntity)

    @Query("SELECT * FROM workout_sets WHERE completedAt >= :sinceTimestamp AND isCompleted = 1")
    fun getCompletedSetsSince(sinceTimestamp: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sessions WHERE title = :title AND status = 'COMPLETED' ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestCompletedSessionByTitle(title: String): WorkoutSessionEntity?

    /** M3a/M4a：最近的会话备注（跳过空串），用于本地总结。 */
    @Query("SELECT notes FROM workout_sessions WHERE notes != '' AND status = 'COMPLETED' ORDER BY startTime DESC LIMIT :limit")
    fun getRecentNotes(limit: Int): Flow<List<String>>

    @Query("SELECT * FROM workout_sessions WHERE title = :title ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestSessionByTitle(title: String): WorkoutSessionEntity?

    @Query("SELECT DISTINCT exerciseId FROM workout_sets WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getDistinctExerciseIdsForSession(sessionId: Long): List<Long>

    @Query("DELETE FROM workout_sets WHERE sessionId = :sessionId AND exerciseId = :exerciseId")
    suspend fun deleteSetsForExerciseInSession(sessionId: Long, exerciseId: Long)

    // ---- M3a：复活两个从来没被写过的列（列已存在，零迁移）----

    @Query("UPDATE workout_sessions SET notes = :notes WHERE id = :sessionId")
    suspend fun updateSessionNotes(sessionId: Long, notes: String)

    @Query("UPDATE workout_sets SET rpe = :rpe WHERE id = :setId")
    suspend fun updateSetRpe(setId: Long, rpe: Double?)

    // ---- 数据备份与恢复 ----
    @Query("SELECT * FROM workout_sessions ORDER BY startTime ASC")
    suspend fun getAllSessionsList(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sets ORDER BY sessionId ASC, setOrder ASC, id ASC")
    suspend fun getAllSetsList(): List<WorkoutSetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<WorkoutSessionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<WorkoutSetEntity>): List<Long>

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM workout_sets")
    suspend fun deleteAllSets()

    // ---- 历史纠正：误记的整场训练必须能删掉 ----

    /** 删除指定会话；`workout_sets` 由外键 CASCADE 一并清除。 */
    @Query("DELETE FROM workout_sessions WHERE id IN (:sessionIds)")
    suspend fun deleteSessionsByIds(sessionIds: List<Long>)

    /** 删除前取受影响动作，用于删后重算 PR。 */
    @Query("SELECT * FROM workout_sets WHERE sessionId IN (:sessionIds)")
    suspend fun getSetsForSessions(sessionIds: List<Long>): List<WorkoutSetEntity>

    /**
     * 剩余数据里的「最佳正式组」——PR 重算的唯一依据。
     * 口径与 PersonalRecordCalculator 一致：完成 且 非热身，先比重、再比次数。
     */
    @Query(
        "SELECT * FROM workout_sets WHERE exerciseId = :exerciseId AND isCompleted = 1 " +
            "AND setType != 'WARMUP' ORDER BY weightKg DESC, reps DESC LIMIT 1"
    )
    suspend fun getBestCompletedSet(exerciseId: Long): WorkoutSetEntity?
}
