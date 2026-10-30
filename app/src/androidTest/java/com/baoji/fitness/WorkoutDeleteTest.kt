package com.baoji.fitness

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baoji.fitness.core.database.BaojiDatabase
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.data.repository.WorkoutRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * 删除历史训练的回归测试。
 *
 * 关键断言是 PR 重算：删掉破纪录的那一场之后，`exercises.personalRecordWeight` 必须跟着降回
 * 剩余数据里的最佳正式组，否则荣誉墙会一直显示一个已经不存在的纪录。
 *
 * 需要真机/模拟器：`./gradlew connectedDebugAndroidTest`。
 */
@RunWith(AndroidJUnit4::class)
class WorkoutDeleteTest {

    private lateinit var db: BaojiDatabase
    private lateinit var repository: WorkoutRepository
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.systemDefault()

    private val today: LocalDate = LocalDate.now()
    private val olderDay: LocalDate = today.minusDays(5)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, BaojiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkoutRepository(
            workoutDao = db.workoutDao(),
            exerciseDao = db.exerciseDao(),
            database = db
        )
        runBlocking { seed() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deleteDay_removesItsSessionsAndSetsOnly() = runBlocking {
        assertEquals(2, db.workoutDao().getAllSessionsList().size)

        val removed = repository.deleteWorkoutDay(today)

        assertEquals("只删当天的会话", 1, removed)
        assertEquals(1, db.workoutDao().getAllSessionsList().size)
        assertEquals("当天 1 组 + 五天前 1 组 → 只剩 1 组", 1, db.workoutDao().getAllSetsList().size)
        assertEquals(
            "五天前的训练不能被误删",
            olderDay,
            db.workoutDao().getAllSessionsList().single().startTime.toLocalDate()
        )
    }

    @Test
    fun deleteDay_recomputesPersonalRecordFromRemainingSets() = runBlocking {
        // 当天 100kg×3 是当前 PR，五天前只有 80kg×5
        val before = db.exerciseDao().getAllExercisesList().single()
        assertEquals(100.0, before.personalRecordWeight, 0.001)

        repository.deleteWorkoutDay(today)

        val after = db.exerciseDao().getAllExercisesList().single()
        assertEquals("PR 必须降回剩余数据的最佳组", 80.0, after.personalRecordWeight, 0.001)
        assertEquals(5, after.personalRecordReps)
    }

    @Test
    fun deleteDay_recomputesToZeroWhenNoSetLeft() = runBlocking {
        repository.deleteWorkoutDay(today)
        repository.deleteWorkoutDay(olderDay)

        val after = db.exerciseDao().getAllExercisesList().single()
        assertEquals("没有组了就不该留着旧纪录", 0.0, after.personalRecordWeight, 0.001)
        assertEquals(0, after.personalRecordReps)
        assertEquals(0, db.workoutDao().getAllSessionsList().size)
    }

    @Test
    fun deleteEmptyDay_returnsZeroAndChangesNothing() = runBlocking {
        val emptyDay = today.minusDays(30)
        val removed = repository.deleteWorkoutDay(emptyDay)

        assertEquals(0, removed)
        assertEquals(2, db.workoutDao().getAllSessionsList().size)
        assertEquals(2, db.workoutDao().getAllSetsList().size)
    }

    private suspend fun seed() {
        val exerciseId = db.exerciseDao().insertExercise(
            ExerciseEntity(
                name = "卧推",
                targetMuscle = "胸部",
                equipment = "杠铃",
                // 当天那一场完成后应有的 PR
                personalRecordWeight = 100.0,
                personalRecordReps = 3
            )
        )

        val olderSessionId = db.workoutDao().insertSession(
            WorkoutSessionEntity(
                title = "DAY 1 · 推日 (胸 肩)",
                startTime = olderDay.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
                endTime = olderDay.atTime(13, 0).atZone(zone).toInstant().toEpochMilli(),
                totalVolumeKg = 400.0,
                status = "COMPLETED"
            )
        )
        db.workoutDao().insertSet(
            WorkoutSetEntity(
                sessionId = olderSessionId,
                exerciseId = exerciseId,
                setOrder = 1,
                weightKg = 80.0,
                reps = 5,
                isCompleted = true,
                completedAt = olderDay.atTime(12, 5).atZone(zone).toInstant().toEpochMilli()
            )
        )

        val todaySessionId = db.workoutDao().insertSession(
            WorkoutSessionEntity(
                title = "DAY 1 · 推日 (胸 肩)",
                startTime = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
                endTime = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
                totalVolumeKg = 300.0,
                status = "COMPLETED"
            )
        )
        db.workoutDao().insertSet(
            WorkoutSetEntity(
                sessionId = todaySessionId,
                exerciseId = exerciseId,
                setOrder = 1,
                weightKg = 100.0,
                reps = 3,
                isCompleted = true,
                completedAt = today.atTime(9, 30).atZone(zone).toInstant().toEpochMilli()
            )
        )
        assertNotNull(db.workoutDao().getBestCompletedSet(exerciseId))
    }

    private fun Long.toLocalDate(): LocalDate =
        java.time.Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}
