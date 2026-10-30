package com.baoji.fitness

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baoji.fitness.core.database.BaojiDatabase
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.data.backup.BackupManager
import com.baoji.fitness.data.backup.RestoreMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 备份与恢复的回归测试。
 *
 * 存在的理由：这块代码此前**零测试覆盖**，却藏着最伤数据的 bug —— 合并导入只按
 * `startTime + title` 对上了会话，组却是无条件全量插入，同一份备份导入两次就会
 * 让容量翻倍、PR 虚高、部位周组数冲过 MRV 误报过度训练。
 *
 * 需要真机/模拟器：`./gradlew connectedDebugAndroidTest`。
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {

    private lateinit var db: BaojiDatabase
    private lateinit var manager: BackupManager
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, BaojiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        manager = BackupManager(context, db)
        runBlocking { seedOneSession() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** 同一份备份合并导入两次，组数必须保持不变。 */
    @Test
    fun mergeImportTwice_doesNotDuplicateSets() = runBlocking {
        val json = exportJson()

        val first = manager.restore(json, RestoreMode.MERGE).getOrThrow()
        assertEquals("已有数据应全部跳过，而不是再插一遍", 3, first.skippedDuplicateSets)
        assertEquals(0, first.restoredSets)
        assertEquals(3, db.workoutDao().getAllSetsList().size)

        val second = manager.restore(json, RestoreMode.MERGE).getOrThrow()
        assertEquals(3, second.skippedDuplicateSets)
        assertEquals("组数不能翻倍", 3, db.workoutDao().getAllSetsList().size)
        assertEquals("会话不能重复", 1, db.workoutDao().getAllSessionsList().size)
    }

    /** 空库合并导入仍要真的把数据导进来（判重不能把正常导入也挡掉）。 */
    @Test
    fun mergeImportIntoEmptyDb_restoresAllSets() = runBlocking {
        val json = exportJson()

        db.workoutDao().deleteAllSets()
        db.workoutDao().deleteAllSessions()

        val res = manager.restore(json, RestoreMode.MERGE).getOrThrow()
        assertEquals(3, res.restoredSets)
        assertEquals(1, res.restoredSessions)
        assertEquals(0, res.skippedDuplicateSets)
        assertEquals(3, db.workoutDao().getAllSetsList().size)
    }

    /** 覆盖导入必须先落一份当前数据快照，否则误选一次就是永久损失。 */
    @Test
    fun overwriteImport_writesPreRestoreSnapshot() = runBlocking {
        val json = exportJson()

        val res = manager.restore(json, RestoreMode.OVERWRITE).getOrThrow()
        val path = res.preRestoreBackupPath
        assertNotNull("覆盖导入必须自动备份当前数据", path)
        assertTrue("快照文件必须真实存在", File(path!!).exists())
        assertTrue("快照应带 -pre-restore 后缀", File(path).name.contains("-pre-restore"))
    }

    /** 非 Rep 的 JSON 与未来版本都必须被拒收，而不是静默导入 0 条。 */
    @Test
    fun foreignOrFutureBackup_isRejected() = runBlocking {
        assertTrue(
            "非 Rep 文件应拒收",
            manager.restore("""{"app":"OtherApp","version":1}""", RestoreMode.MERGE).isFailure
        )
        assertTrue(
            "未来版本应拒收",
            manager.restore("""{"app":"Rep","version":99}""", RestoreMode.MERGE).isFailure
        )
        assertTrue(
            "摘要解析同样要拒收",
            manager.parseSummary("""{"app":"OtherApp","version":1}""").isFailure
        )
    }

    private suspend fun exportJson(): String =
        manager.exportBackup().getOrThrow().readText()

    private suspend fun seedOneSession() {
        val exerciseId = db.exerciseDao().insertExercise(
            ExerciseEntity(name = "卧推", targetMuscle = "胸部", equipment = "杠铃")
        )
        val sessionId = db.workoutDao().insertSession(
            WorkoutSessionEntity(
                title = "DAY 1 · 推日 (胸 肩)",
                startTime = System.currentTimeMillis() - 3_600_000L,
                endTime = System.currentTimeMillis(),
                totalVolumeKg = 1440.0,
                status = "COMPLETED"
            )
        )
        listOf(1, 2, 3).forEach { order ->
            db.workoutDao().insertSet(
                WorkoutSetEntity(
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    setOrder = order,
                    setType = "NORMAL",
                    weightKg = 60.0,
                    reps = 8,
                    isCompleted = true,
                    completedAt = System.currentTimeMillis() - (4 - order) * 300_000L
                )
            )
        }
    }
}
