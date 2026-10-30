package com.baoji.fitness.data.repository

import androidx.room.withTransaction
import com.baoji.fitness.core.database.BaojiDatabase
import com.baoji.fitness.core.database.dao.ExerciseDao
import com.baoji.fitness.core.database.dao.WorkoutDao
import com.baoji.fitness.core.database.entity.BodyMetricEntity
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.domain.algorithm.DateWindow
import com.baoji.fitness.domain.algorithm.OverloadCalculator
import com.baoji.fitness.domain.algorithm.PeriodReview
import com.baoji.fitness.domain.algorithm.PeriodReviewCalculator
import com.baoji.fitness.domain.algorithm.ReviewPeriod
import com.baoji.fitness.domain.algorithm.TrendPoint
import com.baoji.fitness.domain.model.Exercise
import com.baoji.fitness.domain.model.ExerciseGroupDetail
import com.baoji.fitness.domain.model.ExerciseWithSets
import com.baoji.fitness.domain.model.MuscleVolumeStatus
import com.baoji.fitness.domain.model.WorkoutDayDetail
import com.baoji.fitness.domain.model.WorkoutSet
import com.baoji.fitness.domain.model.workoutTypeLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val onSessionArchived: (suspend (WorkoutSessionEntity, List<WorkoutSetEntity>, List<ExerciseEntity>) -> Unit)? = null,
    /** 供跨表写操作开事务（删除历史 + PR 重算必须原子，否则会出现「组没了 PR 还在」）。 */
    private val database: BaojiDatabase? = null
) {
    fun getActiveSession(): Flow<WorkoutSessionEntity?> = workoutDao.getActiveSession()

    suspend fun getActiveSessions(): List<WorkoutSessionEntity> = workoutDao.getActiveSessions()

    /** 训练打卡：当天至少完成过一组训练的自然日 → 训练类型（推/拉/腿）。同天多场取最新。 */
    fun getTrainedDayTypes(): Flow<Map<LocalDate, String>> = combine(
        workoutDao.getAllSessions(),
        workoutDao.getCompletedSetsSince(0L)
    ) { sessions, sets ->
        val trainedSessionIds = sets.map { it.sessionId }.toSet()
        // getAllSessions 是新的在前，反转遍历让同天最新一场覆盖旧的
        sessions.asReversed()
            .filter { it.id in trainedSessionIds }
            .associate { it.startTime.toLocalDate() to workoutTypeLabel(it.title) }
    }

    /** 某一天的训练明细：仅统计完成组，按动作分组。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getDayDetail(day: LocalDate): Flow<WorkoutDayDetail?> =
        workoutDao.getAllSessions().flatMapLatest { sessions ->
            val session = sessions.lastOrNull { it.startTime.toLocalDate() == day }
            if (session == null) {
                flowOf(null)
            } else {
                combine(
                    workoutDao.getSetsForSession(session.id),
                    exerciseDao.getAllExercises()
                ) { sets, exercises ->
                    buildDayDetail(day, session, sets, exercises)
                }
            }
        }

    /**
     * 删除某个自然日的全部训练记录（含组），并把受影响动作的 PR 重算回剩余数据。
     *
     * 存在的理由：此前全项目没有任何删除入口，把 60kg 误记成 600kg 之后，PR、容量曲线、
     * 周部位组数会被永久污染，且没有任何办法修回来。
     *
     * 归期口径与日历 / 周期回顾一致：按会话 `startTime` 的本地日期归属，不使用 SQLite 日期函数。
     * PR 重算必须与删除同事务，否则中途失败会留下「组已删、PR 仍是旧峰值」的说谎数据。
     *
     * @return 实际删除的会话数（当天没有训练则为 0）
     */
    suspend fun deleteWorkoutDay(day: LocalDate): Int = inTransaction {
        val sessions = workoutDao.getAllSessionsList().filter { it.startTime.toLocalDate() == day }
        if (sessions.isEmpty()) return@inTransaction 0

        val ids = sessions.map { it.id }
        // 先取受影响动作，删完就查不到这些组了
        val affectedExerciseIds = workoutDao.getSetsForSessions(ids).map { it.exerciseId }.distinct()

        workoutDao.deleteSessionsByIds(ids)

        affectedExerciseIds.forEach { exerciseId ->
            val best = workoutDao.getBestCompletedSet(exerciseId)
            exerciseDao.updatePR(exerciseId, best?.weightKg ?: 0.0, best?.reps ?: 0)
        }
        sessions.size
    }

    /** 有 database 时才开事务；单元测试里可以只注入两个 DAO。 */
    private suspend fun <T> inTransaction(block: suspend () -> T): T =
        database?.withTransaction { block() } ?: block()

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

    fun getAllSessions(): Flow<List<WorkoutSessionEntity>> = workoutDao.getAllSessions()

    fun getAllExercises(): Flow<List<ExerciseEntity>> = exerciseDao.getAllExercises()

    suspend fun insertExercise(exercise: ExerciseEntity): Long = exerciseDao.insertExercise(exercise)

    /** 新建自定义动作前查重：同名（去空格）已存在就不要再插一条。 */
    suspend fun findExerciseByName(name: String): ExerciseEntity? =
        exerciseDao.getAllExercises().first().firstOrNull {
            it.name.trim().equals(name.trim(), ignoreCase = true)
        }

    suspend fun startNewSession(title: String = "自由推力训练"): Long {
        workoutDao.closeAllActiveSessions()
        val session = WorkoutSessionEntity(
            title = title,
            startTime = System.currentTimeMillis(),
            status = "ACTIVE"
        )
        return workoutDao.insertSession(session)
    }

    suspend fun finishSession(session: WorkoutSessionEntity, totalVolume: Double, notes: String = "") {
        val updated = session.copy(
            endTime = System.currentTimeMillis(),
            totalVolumeKg = totalVolume,
            status = "COMPLETED",
            notes = notes
        )
        workoutDao.updateSession(updated)
        onSessionArchived?.let { callback ->
            val sets = workoutDao.getSetsForSession(session.id).first()
            val exercises = exerciseDao.getAllExercises().first()
            callback(updated, sets, exercises)
        }
    }

    /**
     * 跨天归档：把指定会话置为已完成，**容量按实际完成组重算**
     * （不能写 0 —— 昨晚真的练了，数据不能丢），endTime 取最后一组的完成时刻。
     */
    suspend fun archiveSessions(sessionIds: List<Long>) {
        for (id in sessionIds) {
            val session = workoutDao.getSessionById(id).first() ?: continue
            val sets = workoutDao.getSetsForSession(id).first()
            val effective = sets.filter { it.isCompleted && it.setType != "WARMUP" }
            val volume = effective.sumOf { it.weightKg * it.reps }
            val lastAt = sets.filter { it.isCompleted }.mapNotNull { it.completedAt }.maxOrNull()
            val updated = session.copy(
                status = "COMPLETED",
                totalVolumeKg = volume,
                endTime = lastAt ?: session.startTime
            )
            workoutDao.updateSession(updated)
            onSessionArchived?.let { callback ->
                val exercises = exerciseDao.getAllExercises().first()
                callback(updated, sets, exercises)
            }
        }
    }

    suspend fun getSessionById(id: Long): WorkoutSessionEntity? =
        workoutDao.getSessionById(id).first()

    /** M3a：会话备注（`notes` 列 v2 就存在，此前全项目零读写）。 */
    suspend fun updateSessionNotes(sessionId: Long, notes: String) =
        workoutDao.updateSessionNotes(sessionId, notes)

    /** M3a：某组的主观疲劳度 6~10；null = 未记录。 */
    suspend fun updateSetRpe(setId: Long, rpe: Double?) =
        workoutDao.updateSetRpe(setId, rpe)

    /** M3a：整组覆盖写（重量/次数/类型/RPE 一次落库）。 */
    suspend fun saveSet(set: WorkoutSetEntity) = workoutDao.updateSet(set)

    /** 最近 [limit] 条非空备注，供 M4a 本地总结与训练页文案使用。 */
    fun getRecentNotes(limit: Int = 3): Flow<List<String>> = workoutDao.getRecentNotes(limit)

    /** 分析器需要全量完成组（与 `trainingHistory` 同一查询）。 */
    fun getAllCompletedSets(): Flow<List<WorkoutSetEntity>> = workoutDao.getCompletedSetsSince(0L)

    fun getSetsForSession(sessionId: Long): Flow<List<WorkoutSetEntity>> =
        workoutDao.getSetsForSession(sessionId)

    fun getExerciseHistory(exerciseId: Long): Flow<List<WorkoutSetEntity>> =
        workoutDao.getHistoryForExercise(exerciseId)

    suspend fun getPreviousSessionSetsForExercise(exerciseId: Long, currentSessionId: Long): Map<Int, WorkoutSetEntity> {
        val history = workoutDao.getHistoricalCompletedSets(exerciseId, currentSessionId)
        if (history.isEmpty()) return emptyMap()

        val lastSessionId = history.first().sessionId
        val setsFromLastSession = history.filter { it.sessionId == lastSessionId }
        return setsFromLastSession.associateBy { it.setOrder }
    }

    suspend fun getExerciseById(exerciseId: Long): ExerciseEntity? =
        exerciseDao.getExerciseById(exerciseId)

    suspend fun removeExerciseFromSession(sessionId: Long, exerciseId: Long) {
        workoutDao.deleteSetsForExerciseInSession(sessionId, exerciseId)
    }

    suspend fun getPreviousExerciseIdsForRoutine(routineTitle: String): List<Long> {
        val targetSession = workoutDao.getLatestCompletedSessionByTitle(routineTitle)
            ?: workoutDao.getLatestSessionByTitle(routineTitle)
            ?: return emptyList()
        return workoutDao.getDistinctExerciseIdsForSession(targetSession.id)
    }

    suspend fun addSet(
        sessionId: Long,
        exerciseId: Long,
        weightKg: Double,
        reps: Int,
        setOrder: Int,
        setType: String = "NORMAL"
    ): Long {
        val entity = WorkoutSetEntity(
            sessionId = sessionId,
            exerciseId = exerciseId,
            setOrder = setOrder,
            setType = setType,
            weightKg = weightKg,
            reps = reps
        )
        return workoutDao.insertSet(entity)
    }

    suspend fun completeSet(set: WorkoutSetEntity, completed: Boolean) {
        val updated = set.copy(
            isCompleted = completed,
            completedAt = if (completed) System.currentTimeMillis() else null
        )
        workoutDao.updateSet(updated)

        // If completed and higher than personal record, update exercise PR
        if (completed) {
            val exercise = exerciseDao.getExerciseById(set.exerciseId)
            if (exercise != null && set.weightKg > exercise.personalRecordWeight) {
                exerciseDao.updatePR(set.exerciseId, set.weightKg, set.reps)
            }
        }
    }

    suspend fun updateSetWeightAndReps(set: WorkoutSetEntity, weightKg: Double, reps: Int) {
        workoutDao.updateSet(set.copy(weightKg = weightKg, reps = reps))
    }

    suspend fun deleteSet(set: WorkoutSetEntity) {
        workoutDao.deleteSet(set)
    }

    suspend fun deleteSetAndReorder(set: WorkoutSetEntity) {
        workoutDao.deleteSet(set)
        val remaining = workoutDao.getSetsForExerciseInSession(set.sessionId, set.exerciseId)
        remaining.forEachIndexed { index, s ->
            val expectedOrder = index + 1
            if (s.setOrder != expectedOrder) {
                workoutDao.updateSet(s.copy(setOrder = expectedOrder))
            }
        }
    }

    /** 获取动作历史全部已完成组流（供 PR 荣誉墙与历史时间线展示） */
    fun getHistoryForExercise(exerciseId: Long): Flow<List<WorkoutSetEntity>> =
        workoutDao.getHistoryForExercise(exerciseId)

    // ---------------------------------------------------------------- M1 周期回顾

    /**
     * 训练史单一来源：会话 + 全部完成组 + 动作库。
     * 周期窗口一律在 Kotlin 侧按**会话开始日**切（与日历打卡同一口径），
     * 不做 epoch 换算 —— 那是「跳零点训练被拆到两天」这类 bug 的源头，
     * 而且纯函数层的单测能真正钉住这个约定。
     */
    private val trainingHistory = combine(
        workoutDao.getAllSessions(),
        workoutDao.getCompletedSetsSince(0L),
        exerciseDao.getAllExercises()
    ) { sessions, sets, exercises -> Triple(sessions, sets, exercises) }

    /** 一次周期回顾。「本周」与日历页（周一为首列）是同一个窗口。 */
    fun getPeriodReview(
        period: ReviewPeriod,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Flow<PeriodReview> {
        val windows = PeriodReviewCalculator.resolveWindows(period, today)
        return trainingHistory.map { (sessions, sets, _) ->
            val currentSessions = PeriodReviewCalculator.sessionsIn(sessions, windows.current, zone)
            val previousVolume = windows.previous?.let { prev ->
                PeriodReviewCalculator.volumeOf(
                    PeriodReviewCalculator.setsOf(sets, PeriodReviewCalculator.sessionsIn(sessions, prev, zone))
                )
            }
            PeriodReviewCalculator.buildPeriodReview(
                label = period.label,
                sets = PeriodReviewCalculator.setsOf(sets, currentSessions),
                sessions = currentSessions,
                previousVolumeKg = previousVolume
            )
        }
    }

    /** 近 [weeks] 周容量趋势（含本周，最旧 → 最新）。 */
    fun getWeeklyTrend(
        weeks: Int = 8,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Flow<List<TrendPoint>> {
        val window = PeriodReviewCalculator.lastWeeks(today, weeks)
        return trainingHistory.map { (sessions, sets, _) ->
            val windowSessions = PeriodReviewCalculator.sessionsIn(sessions, window, zone)
            PeriodReviewCalculator.buildWeeklyTrend(
                sets = PeriodReviewCalculator.setsOf(sets, windowSessions),
                sessions = windowSessions,
                weeks = weeks,
                today = today,
                zone = zone
            )
        }
    }

    /**
     * 部位有效组数「本周 vs 上周」。MEV/MAV/MRV 是**周**阈值，
     * 所以这里永远按自然周算，不跟随周期 chips（本月/近8周 与周阈值不可比）。
     */
    fun getWeeklyMuscleComparison(
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Flow<WeeklyMuscleComparison> {
        val thisWeek = PeriodReviewCalculator.currentWeek(today)
        val lastWeek = DateWindow(thisWeek.from.minusWeeks(1), thisWeek.from)

        return trainingHistory.map { (sessions, sets, exercises) ->
            fun counts(window: DateWindow): Map<String, Double> =
                PeriodReviewCalculator.muscleSetCounts(
                    PeriodReviewCalculator.setsOf(
                        sets,
                        PeriodReviewCalculator.sessionsIn(sessions, window, zone)
                    ),
                    exercises
                )
            val cur = counts(thisWeek)
            val prev = counts(lastWeek)
            WeeklyMuscleComparison(
                current = PeriodReviewCalculator.TRACKED_MUSCLES.map {
                    OverloadCalculator.evaluateMuscleVolume(it, cur[it] ?: 0.0)
                },
                previous = PeriodReviewCalculator.TRACKED_MUSCLES.map {
                    OverloadCalculator.evaluateMuscleVolume(it, prev[it] ?: 0.0)
                }
            )
        }
    }

    /**
     * 为「一键分享给 AI」打包上下文：
     * 1. 当天训练明细
     * 2. 上次同类型训练的有效容量（算环比）
     * 3. 本周周期统计（训练次数、总容量、环比）
     * 4. 本周各部位有效组数监控状态（胸/背/腿/肩/臂/核心）
     * 5. 最新身体数据与上次变化
     */
    suspend fun getAiShareContext(
        detail: WorkoutDayDetail,
        metrics: List<BodyMetricEntity> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault()
    ): AiShareContext {
        val (sessions, sets, exercises) = trainingHistory.first()
        val windows = PeriodReviewCalculator.resolveWindows(ReviewPeriod.THIS_WEEK, detail.date)

        val currentWeekSessions = PeriodReviewCalculator.sessionsIn(sessions, windows.current, zone)
        val prevWeekVolume = windows.previous?.let { prev ->
            PeriodReviewCalculator.volumeOf(
                PeriodReviewCalculator.setsOf(sets, PeriodReviewCalculator.sessionsIn(sessions, prev, zone))
            )
        }
        val weekReview = PeriodReviewCalculator.buildPeriodReview(
            label = ReviewPeriod.THIS_WEEK.label,
            sets = PeriodReviewCalculator.setsOf(sets, currentWeekSessions),
            sessions = currentWeekSessions,
            previousVolumeKg = prevWeekVolume
        )

        // 本周部位组数
        val currentWeekSets = PeriodReviewCalculator.setsOf(sets, currentWeekSessions)
        val muscleCounts = PeriodReviewCalculator.muscleSetCounts(currentWeekSets, exercises)
        val weeklyMuscles = PeriodReviewCalculator.TRACKED_MUSCLES.map {
            OverloadCalculator.evaluateMuscleVolume(it, muscleCounts[it] ?: 0.0)
        }

        // 寻找上次同类型训练的有效容量
        val typeKeyword = when {
            detail.title.contains("推") -> "推"
            detail.title.contains("拉") -> "拉"
            detail.title.contains("腿") -> "腿"
            else -> null
        }
        val previousSameTypeVolume = if (typeKeyword != null) {
            val detailStartDay = detail.date
            val pastSessions = sessions
                .filter {
                    val sessionDay = Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate()
                    sessionDay.isBefore(detailStartDay) && it.title.contains(typeKeyword)
                }
                .sortedByDescending { it.startTime }

            val lastSameType = pastSessions.firstOrNull()
            if (lastSameType != null) {
                val sessionSets = PeriodReviewCalculator.setsOf(sets, listOf(lastSameType))
                PeriodReviewCalculator.volumeOf(sessionSets)
            } else null
        } else null

        // 身体数据：按日期倒序
        val sortedMetrics = metrics.sortedByDescending { it.date }
        val latest = sortedMetrics.firstOrNull()
        val previous = sortedMetrics.drop(1).firstOrNull()

        return AiShareContext(
            detail = detail,
            previousSameTypeVolume = previousSameTypeVolume,
            weeklySessionCount = weekReview.sessionCount,
            weeklyTotalVolumeKg = weekReview.totalVolumeKg,
            weeklyVolumeDeltaPct = weekReview.volumeDeltaPct,
            weeklyMuscles = weeklyMuscles,
            latestMetric = latest,
            previousMetric = previous
        )
    }
}

/** 部位容量进度条需要「本周 vs 上周」两条数，一一对应。 */
data class WeeklyMuscleComparison(
    val current: List<MuscleVolumeStatus>,
    val previous: List<MuscleVolumeStatus>
)

/**
 * 把某天某会话的完成组聚合成明细：按动作分组、按 setOrder 排序、热身组不计入容量。
 * 无完成组时返回 null（避免空日期点出空弹层）。抽出来便于单测。
 */
internal fun buildDayDetail(
    day: LocalDate,
    session: WorkoutSessionEntity,
    sets: List<WorkoutSetEntity>,
    exercises: List<ExerciseEntity>
): WorkoutDayDetail? {
    val exerciseById = exercises.associateBy { it.id }
    val completed = sets.filter { it.isCompleted }
    val groups = completed.groupBy { it.exerciseId }.map { (exerciseId, groupSets) ->
        val exercise = exerciseById[exerciseId]
        ExerciseGroupDetail(
            exerciseName = exercise?.name ?: "动作 #$exerciseId",
            targetMuscle = exercise?.targetMuscle ?: "",
            sets = groupSets.sortedBy { it.setOrder }.map { s ->
                WorkoutSet(
                    id = s.id,
                    sessionId = s.sessionId,
                    exerciseId = s.exerciseId,
                    exerciseName = exercise?.name ?: "",
                    setOrder = s.setOrder,
                    setType = s.setType,
                    weightKg = s.weightKg,
                    reps = s.reps,
                    rpe = s.rpe,
                    isCompleted = true
                )
            }
        )
    }
    if (groups.isEmpty()) return null

    return WorkoutDayDetail(
        date = day,
        title = session.title,
        totalVolumeKg = completed.filter { it.setType != "WARMUP" }
            .sumOf { it.weightKg * it.reps },
        groups = groups,
        notes = session.notes
    )
}

/** 一键分享给 AI 的完整上下文载荷。 */
data class AiShareContext(
    val detail: WorkoutDayDetail,
    val previousSameTypeVolume: Double? = null,
    val weeklySessionCount: Int? = null,
    val weeklyTotalVolumeKg: Double? = null,
    val weeklyVolumeDeltaPct: Double? = null,
    val weeklyMuscles: List<MuscleVolumeStatus>? = null,
    val latestMetric: BodyMetricEntity? = null,
    val previousMetric: BodyMetricEntity? = null
)

/** 从当前正在进行的会话生成即时 WorkoutDayDetail，用于练完立即分享。 */
fun buildActiveSessionDetail(
    session: WorkoutSessionEntity,
    sets: List<WorkoutSetEntity>,
    exercises: List<ExerciseEntity>,
    notes: String = session.notes
): WorkoutDayDetail {
    val exerciseById = exercises.associateBy { it.id }
    val completed = sets.filter { it.isCompleted }
    val groups = completed.groupBy { it.exerciseId }.map { (exerciseId, groupSets) ->
        val exercise = exerciseById[exerciseId]
        ExerciseGroupDetail(
            exerciseName = exercise?.name ?: "动作 #$exerciseId",
            targetMuscle = exercise?.targetMuscle ?: "",
            sets = groupSets.sortedBy { it.setOrder }.map { s ->
                WorkoutSet(
                    id = s.id,
                    sessionId = s.sessionId,
                    exerciseId = s.exerciseId,
                    exerciseName = exercise?.name ?: "",
                    setOrder = s.setOrder,
                    setType = s.setType,
                    weightKg = s.weightKg,
                    reps = s.reps,
                    rpe = s.rpe,
                    isCompleted = true
                )
            }
        )
    }
    val day = Instant.ofEpochMilli(session.startTime).atZone(ZoneId.systemDefault()).toLocalDate()
    return WorkoutDayDetail(
        date = day,
        title = session.title,
        totalVolumeKg = completed.filter { it.setType != "WARMUP" }.sumOf { it.weightKg * it.reps },
        groups = groups,
        notes = notes
    )
}
