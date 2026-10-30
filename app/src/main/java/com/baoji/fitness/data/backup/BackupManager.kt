package com.baoji.fitness.data.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.baoji.fitness.core.database.BaojiDatabase
import com.baoji.fitness.core.database.entity.BodyMetricEntity
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** 备份文件标识与格式版本；恢复时据此拒收其它 App 或未来版本的文件。 */
private const val BACKUP_APP = "Rep"
private const val BACKUP_VERSION = 1

/** 自动快照后缀（覆盖导入前兜底），与用户主动导出分开保留、分开计数。 */
private const val PRE_RESTORE_SUFFIX = "-pre-restore"

/** 缓存目录里最多留几份用户导出、几份自动快照；更旧的在下次落盘时清掉。 */
private const val EXPORT_FILES_KEPT = 3
private const val SNAPSHOT_FILES_KEPT = 2

/** 恢复模式：合并导入（去重）或覆盖导入 */
enum class RestoreMode {
    MERGE,
    OVERWRITE
}

/** 备份文件信息摘要，供弹窗预览展示 */
data class BackupSummary(
    val app: String,
    val version: Int,
    val exportedAt: Long,
    val sessionCount: Int,
    val setCount: Int,
    val exerciseCount: Int,
    val bodyMetricCount: Int
)

/** 恢复结果统计 */
data class RestoreResult(
    val restoredSessions: Int,
    val restoredSets: Int,
    val restoredBodyMetrics: Int,
    val restoredExercises: Int,
    /** 合并导入时按「会话 + 动作 + 组序」判定为已存在、因此跳过写入的组数。 */
    val skippedDuplicateSets: Int = 0,
    /** 覆盖导入前自动落盘的当前数据快照路径（无旧数据时为 null）。 */
    val preRestoreBackupPath: String? = null
)

class BackupManager(
    private val context: Context,
    private val database: BaojiDatabase
) {
    private val workoutDao = database.workoutDao()
    private val exerciseDao = database.exerciseDao()
    private val bodyMetricDao = database.bodyMetricDao()

    /**
     * 将全部数据导出为 JSON 文件
     * 保存于 cacheDir/backups/rep-backup-YYYY-MM-DD.json
     */
    suspend fun exportBackup(): Result<File> = withContext(Dispatchers.IO) {
        runCatching { writeBackupFile() }
    }

    /**
     * 落盘一份全量快照。
     * [suffix] 用于区分「用户主动导出」与「覆盖导入前的自动兜底」，两者字段完全一致。
     */
    private suspend fun writeBackupFile(suffix: String? = null): File {
        val root = buildBackupJson()
        val dir = File(context.cacheDir, "backups").apply { if (!exists()) mkdirs() }
        val name = "rep-backup-${LocalDate.now()}${suffix ?: ""}.json"
        val file = File(dir, name).apply { writeText(root.toString(2), Charsets.UTF_8) }
        pruneOldBackups(dir, keepExports = EXPORT_FILES_KEPT, keepSnapshots = SNAPSHOT_FILES_KEPT)
        return file
    }

    /**
     * 清理历史备份文件。
     *
     * 这些 JSON 含体重、体脂等身体数据，长期堆在 cache 里既占空间也是隐私尾巴；
     * 但只删「较旧的」，刚写出的那份永远保留，避免 Sharesheet 还在读 Uri 时文件被删。
     * 自动快照与用户导出分开计数，避免一次批量恢复把用户手动导出的文件挤掉。
     */
    private fun pruneOldBackups(dir: File, keepExports: Int, keepSnapshots: Int) {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("rep-backup-") && f.name.endsWith(".json") }
            ?: return
        val (snapshots, exports) = files.partition { it.name.contains(PRE_RESTORE_SUFFIX) }
        fun trim(group: List<File>, keep: Int) {
            group.sortedByDescending { it.lastModified() }.drop(keep).forEach { it.delete() }
        }
        trim(exports, keepExports)
        trim(snapshots, keepSnapshots)
    }

    /**
     * 全量序列化当前库。导出与「覆盖导入前自动备份」共用这一份实现，
     * 避免两处字段清单漂移导致备份悄悄缺列。
     */
    private suspend fun buildBackupJson(): JSONObject {
            val sessions = workoutDao.getAllSessionsList()
            val sets = workoutDao.getAllSetsList()
            val exercises = exerciseDao.getAllExercisesList()
            val bodyMetrics = bodyMetricDao.getAllList()

            val exerciseMap = exercises.associateBy { it.id }

            val root = JSONObject().apply {
                put("app", BACKUP_APP)
                put("version", BACKUP_VERSION)
                put("exportedAt", System.currentTimeMillis())
                put("exportDate", LocalDate.now().toString())

                // 动作库
                val exercisesArray = JSONArray()
                exercises.forEach { ex ->
                    exercisesArray.put(JSONObject().apply {
                        put("id", ex.id)
                        put("name", ex.name)
                        put("targetMuscle", ex.targetMuscle)
                        put("secondaryMuscles", ex.secondaryMuscles)
                        put("equipment", ex.equipment)
                        put("isCustom", ex.isCustom)
                        put("trackWeight", ex.trackWeight)
                        put("personalRecordWeight", ex.personalRecordWeight)
                        put("personalRecordReps", ex.personalRecordReps)
                    })
                }
                put("exercises", exercisesArray)

                // 训练会话
                val sessionsArray = JSONArray()
                sessions.forEach { s ->
                    sessionsArray.put(JSONObject().apply {
                        put("id", s.id)
                        put("title", s.title)
                        put("startTime", s.startTime)
                        put("endTime", s.endTime ?: JSONObject.NULL)
                        put("totalVolumeKg", s.totalVolumeKg)
                        put("status", s.status)
                        put("notes", s.notes)
                    })
                }
                put("sessions", sessionsArray)

                // 训练组
                val setsArray = JSONArray()
                sets.forEach { set ->
                    setsArray.put(JSONObject().apply {
                        put("id", set.id)
                        put("sessionId", set.sessionId)
                        put("exerciseId", set.exerciseId)
                        put("exerciseName", exerciseMap[set.exerciseId]?.name ?: "")
                        put("setOrder", set.setOrder)
                        put("setType", set.setType)
                        put("weightKg", set.weightKg)
                        put("reps", set.reps)
                        put("rpe", set.rpe ?: JSONObject.NULL)
                        put("isCompleted", set.isCompleted)
                        put("completedAt", set.completedAt ?: JSONObject.NULL)
                    })
                }
                put("sets", setsArray)

                // 身体指标
                val bodyMetricsArray = JSONArray()
                bodyMetrics.forEach { b ->
                    bodyMetricsArray.put(JSONObject().apply {
                        put("id", b.id)
                        put("date", b.date)
                        put("weightKg", b.weightKg ?: JSONObject.NULL)
                        put("bodyFatPct", b.bodyFatPct ?: JSONObject.NULL)
                        put("heightCm", b.heightCm ?: JSONObject.NULL)
                        put("chestCm", b.chestCm ?: JSONObject.NULL)
                        put("waistCm", b.waistCm ?: JSONObject.NULL)
                        put("armCm", b.armCm ?: JSONObject.NULL)
                        put("note", b.note)
                    })
                }
                put("bodyMetrics", bodyMetricsArray)
            }

            return root
    }

    /**
     * 调起系统分享 Sharesheet 保存或转发备份文件
     */
    fun shareBackup(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "导出 Rep 训练备份").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * 校验并解析备份 JSON 摘要
     */
    fun parseSummary(jsonString: String): Result<BackupSummary> = runCatching {
        val root = JSONObject(jsonString)
        validateBackup(root)
        val app = root.optString("app", "")
        val version = root.optInt("version", 1)
        val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())

        val sessionCount = root.optJSONArray("sessions")?.length() ?: 0
        val setCount = root.optJSONArray("sets")?.length() ?: 0
        val exerciseCount = root.optJSONArray("exercises")?.length() ?: 0
        val bodyMetricCount = root.optJSONArray("bodyMetrics")?.length() ?: 0

        BackupSummary(
            app = app,
            version = version,
            exportedAt = exportedAt,
            sessionCount = sessionCount,
            setCount = setCount,
            exerciseCount = exerciseCount,
            bodyMetricCount = bodyMetricCount
        )
    }

    /**
     * 拒收非 Rep 备份与未来版本的备份文件。
     * 此前完全不校验，任意 JSON 都能走到「恢复成功 0 条」或被静默部分导入。
     */
    private fun validateBackup(root: JSONObject) {
        val app = root.optString("app", "")
        require(app == BACKUP_APP) { "不是 Rep 的备份文件（app=$app）" }
        val version = root.optInt("version", 0)
        require(version in 1..BACKUP_VERSION) { "备份版本 $version 不受支持（当前支持 1..$BACKUP_VERSION）" }
    }

    /**
     * 从 JSON 文本恢复数据
     */
    suspend fun restore(jsonString: String, mode: RestoreMode): Result<RestoreResult> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(jsonString)
            validateBackup(root)

            // 覆盖导入是不可逆操作：删库前先无条件落一份当前快照（空库则跳过）。
            val preRestore = if (mode == RestoreMode.OVERWRITE && workoutDao.getAllSessionsList().isNotEmpty()) {
                writeBackupFile(PRE_RESTORE_SUFFIX)
            } else {
                null
            }

            database.withTransaction {
                if (mode == RestoreMode.OVERWRITE) {
                    workoutDao.deleteAllSets()
                    workoutDao.deleteAllSessions()
                    bodyMetricDao.deleteAll()
                }

                // 合并导入的判重基准：会话 + 动作 + 组序 视为同一组。
                // 会话按 startTime+title 能对上，但组此前是无条件全量插入 —— 同一份备份导入两次，
                // 容量直接翻倍、PR 虚高、部位周组数冲过 MRV 误报过度训练。
                val existingSetKeys = workoutDao.getAllSetsList()
                    .mapTo(mutableSetOf()) { Triple(it.sessionId, it.exerciseId, it.setOrder) }
                var skippedDuplicateSets = 0

                // 1. 恢复与映射动作库 (Exercise)
                val localExercises = exerciseDao.getAllExercisesList().associateBy { it.name }.toMutableMap()
                val exerciseIdMap = mutableMapOf<Long, Long>()
                var restoredExercisesCount = 0

                val exercisesArray = root.optJSONArray("exercises") ?: JSONArray()
                for (i in 0 until exercisesArray.length()) {
                    val obj = exercisesArray.getJSONObject(i)
                    val oldId = obj.getLong("id")
                    val name = obj.getString("name")
                    val targetMuscle = obj.optString("targetMuscle", "胸部")
                    val secondaryMuscles = obj.optString("secondaryMuscles", "")
                    val equipment = obj.optString("equipment", "杠铃")
                    val isCustom = obj.optBoolean("isCustom", true)
                    val trackWeight = obj.optBoolean("trackWeight", true)
                    val prWeight = obj.optDouble("personalRecordWeight", 0.0)
                    val prReps = obj.optInt("personalRecordReps", 0)

                    val existing = localExercises[name]
                    if (existing != null) {
                        exerciseIdMap[oldId] = existing.id
                        if (prWeight > existing.personalRecordWeight) {
                            exerciseDao.updatePR(existing.id, prWeight, prReps)
                        }
                    } else {
                        val newEntity = ExerciseEntity(
                            id = 0,
                            name = name,
                            targetMuscle = targetMuscle,
                            secondaryMuscles = secondaryMuscles,
                            equipment = equipment,
                            isCustom = isCustom,
                            trackWeight = trackWeight,
                            personalRecordWeight = prWeight,
                            personalRecordReps = prReps
                        )
                        val newId = exerciseDao.insertExercise(newEntity)
                        exerciseIdMap[oldId] = newId
                        localExercises[name] = newEntity.copy(id = newId)
                        restoredExercisesCount++
                    }
                }

                // 2. 恢复训练会话 (WorkoutSession)
                val existingSessions = workoutDao.getAllSessionsList()
                val existingKeys = existingSessions.map { "${it.startTime}_${it.title}" }.toSet()
                val existingSessionMap = existingSessions.associateBy { "${it.startTime}_${it.title}" }

                val sessionIdMap = mutableMapOf<Long, Long>()
                var restoredSessionsCount = 0

                val sessionsArray = root.optJSONArray("sessions") ?: JSONArray()
                for (i in 0 until sessionsArray.length()) {
                    val obj = sessionsArray.getJSONObject(i)
                    val oldId = obj.getLong("id")
                    val title = obj.getString("title")
                    val startTime = obj.getLong("startTime")
                    val endTime = if (obj.isNull("endTime")) null else obj.getLong("endTime")
                    val totalVolumeKg = obj.optDouble("totalVolumeKg", 0.0)
                    val status = obj.optString("status", "COMPLETED")
                    val notes = obj.optString("notes", "")

                    val key = "${startTime}_${title}"
                    if (mode == RestoreMode.MERGE && key in existingKeys) {
                        val existing = existingSessionMap[key]!!
                        sessionIdMap[oldId] = existing.id
                    } else {
                        val newEntity = WorkoutSessionEntity(
                            id = 0,
                            title = title,
                            startTime = startTime,
                            endTime = endTime,
                            totalVolumeKg = totalVolumeKg,
                            status = status,
                            notes = notes
                        )
                        val newId = workoutDao.insertSession(newEntity)
                        sessionIdMap[oldId] = newId
                        restoredSessionsCount++
                    }
                }

                // 3. 恢复训练组 (WorkoutSet)
                var restoredSetsCount = 0
                val setsArray = root.optJSONArray("sets") ?: JSONArray()
                val setsToInsert = mutableListOf<WorkoutSetEntity>()

                for (i in 0 until setsArray.length()) {
                    val obj = setsArray.getJSONObject(i)
                    val oldSessionId = obj.getLong("sessionId")
                    val oldExerciseId = obj.getLong("exerciseId")
                    val exerciseName = obj.optString("exerciseName", "")

                    val mappedSessionId = sessionIdMap[oldSessionId] ?: continue
                    val mappedExerciseId = exerciseIdMap[oldExerciseId]
                        ?: localExercises[exerciseName]?.id
                        ?: continue

                    val setOrder = obj.optInt("setOrder", 1)

                    // 已存在同一组（会话 + 动作 + 组序）就跳过，重复导入不会把容量翻倍。
                    if (!existingSetKeys.add(Triple(mappedSessionId, mappedExerciseId, setOrder))) {
                        skippedDuplicateSets++
                        continue
                    }

                    val setType = obj.optString("setType", "NORMAL")
                    val weightKg = obj.optDouble("weightKg", 0.0)
                    val reps = obj.optInt("reps", 0)
                    val rpe = if (obj.isNull("rpe")) null else obj.optDouble("rpe")
                    val isCompleted = obj.optBoolean("isCompleted", true)
                    val completedAt = if (obj.isNull("completedAt")) null else obj.optLong("completedAt")

                    setsToInsert.add(
                        WorkoutSetEntity(
                            id = 0,
                            sessionId = mappedSessionId,
                            exerciseId = mappedExerciseId,
                            setOrder = setOrder,
                            setType = setType,
                            weightKg = weightKg,
                            reps = reps,
                            rpe = rpe,
                            isCompleted = isCompleted,
                            completedAt = completedAt
                        )
                    )
                }

                if (setsToInsert.isNotEmpty()) {
                    workoutDao.insertSets(setsToInsert)
                    restoredSetsCount = setsToInsert.size
                }

                // 4. 恢复身体数据 (BodyMetric)
                var restoredBodyMetricsCount = 0
                val bodyMetricsArray = root.optJSONArray("bodyMetrics") ?: JSONArray()

                for (i in 0 until bodyMetricsArray.length()) {
                    val obj = bodyMetricsArray.getJSONObject(i)
                    val date = obj.getLong("date")
                    val weightKg = if (obj.isNull("weightKg")) null else obj.optDouble("weightKg")
                    val bodyFatPct = if (obj.isNull("bodyFatPct")) null else obj.optDouble("bodyFatPct")
                    val heightCm = if (obj.isNull("heightCm")) null else obj.optDouble("heightCm")
                    val chestCm = if (obj.isNull("chestCm")) null else obj.optDouble("chestCm")
                    val waistCm = if (obj.isNull("waistCm")) null else obj.optDouble("waistCm")
                    val armCm = if (obj.isNull("armCm")) null else obj.optDouble("armCm")
                    val note = obj.optString("note", "")

                    if (mode == RestoreMode.MERGE) {
                        val existing = bodyMetricDao.getByDate(date)
                        if (existing != null) {
                            val merged = existing.copy(
                                weightKg = existing.weightKg ?: weightKg,
                                bodyFatPct = existing.bodyFatPct ?: bodyFatPct,
                                heightCm = existing.heightCm ?: heightCm,
                                chestCm = existing.chestCm ?: chestCm,
                                waistCm = existing.waistCm ?: waistCm,
                                armCm = existing.armCm ?: armCm,
                                note = existing.note.ifBlank { note }
                            )
                            bodyMetricDao.upsert(merged)
                        } else {
                            bodyMetricDao.upsert(
                                BodyMetricEntity(
                                    id = 0,
                                    date = date,
                                    weightKg = weightKg,
                                    bodyFatPct = bodyFatPct,
                                    heightCm = heightCm,
                                    chestCm = chestCm,
                                    waistCm = waistCm,
                                    armCm = armCm,
                                    note = note
                                )
                            )
                        }
                    } else {
                        bodyMetricDao.upsert(
                            BodyMetricEntity(
                                id = 0,
                                date = date,
                                weightKg = weightKg,
                                bodyFatPct = bodyFatPct,
                                heightCm = heightCm,
                                chestCm = chestCm,
                                waistCm = waistCm,
                                armCm = armCm,
                                note = note
                            )
                        )
                    }
                    restoredBodyMetricsCount++
                }

                RestoreResult(
                    restoredSessions = restoredSessionsCount,
                    restoredSets = restoredSetsCount,
                    restoredBodyMetrics = restoredBodyMetricsCount,
                    restoredExercises = restoredExercisesCount,
                    skippedDuplicateSets = skippedDuplicateSets,
                    preRestoreBackupPath = preRestore?.absolutePath
                )
            }
        }
    }
}
