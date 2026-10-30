package com.baoji.fitness.data.healthconnect

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import com.baoji.fitness.core.database.entity.BodyMetricEntity
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.baoji.fitness.data.repository.BodyMetricRepository
import java.time.temporal.ChronoUnit

/**
 * Health Connect 数据同步器（支持双向读写）。
 *
 * 负责把 App 内部的训练记录与身体数据写入 Android 系统的 Health Connect（供 AI 教练 Muse 等读取），
 * 以及从系统健康底座（如智能体脂秤、薄荷健康等）自动读取体重/体脂记录进行闭环同步。
 *
 * 核心保证：
 * 1. **口径红线**：排除热身组；写入的累计容量与训练页显示的数字逐字对齐；
 * 2. **幂等读写**：通过固定格式 clientRecordId 去重，重复归档/更新不会产生脏数据；
 * 3. **降级与防崩溃**：设备未安装 Health Connect 或权限未就绪时静默跳过，绝不阻断正常流程。
 */
object HealthConnectWriter {

    /** 写入所需的权限列表。 */
    val WRITE_PERMISSIONS: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class)
    )

    /** 读取所需的权限列表。 */
    val READ_PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    )

    /** 全量权限列表（读+写）。 */
    val PERMISSIONS: Set<String> = WRITE_PERMISSIONS + READ_PERMISSIONS

    /** 检查系统 Health Connect SDK 是否可用。 */
    fun isAvailable(context: Context): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (_: Exception) {
            false
        }
    }

    /** 检查是否已获得全部权限（读+写）。 */
    suspend fun hasPermissions(context: Context): Boolean {
        if (!isAvailable(context)) return false
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(PERMISSIONS)
        } catch (_: Exception) {
            false
        }
    }

    /** 检查是否已获得写入权限。 */
    suspend fun hasWritePermissions(context: Context): Boolean {
        if (!isAvailable(context)) return false
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(WRITE_PERMISSIONS)
        } catch (_: Exception) {
            false
        }
    }

    /** 检查是否已获得读取权限。 */
    suspend fun hasReadPermissions(context: Context): Boolean {
        if (!isAvailable(context)) return false
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            granted.contains(HealthPermission.getReadPermission(WeightRecord::class)) ||
                granted.contains(HealthPermission.getReadPermission(BodyFatRecord::class))
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 写入训练会话记录。
     *
     * @param session 会话实体
     * @param sets 会话的所有组
     * @param exercises 动作库信息（用于解析动作名及器械）
     */
    private const val TAG = "HealthConnectWriter"

    /**
     * 写入训练会话记录。
     *
     * @param session 会话实体
     * @param sets 会话的所有组
     * @param exercises 动作库信息（用于解析动作名及器械）
     */
    suspend fun writeWorkout(
        context: Context,
        session: WorkoutSessionEntity,
        sets: List<WorkoutSetEntity>,
        exercises: List<ExerciseEntity>
    ): Result<Unit> = runCatching {
        if (!isAvailable(context)) return Result.success(Unit)

        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val permExercise = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
        if (!granted.contains(permExercise)) {
            Log.w(TAG, "未获得 Health Connect 写入训练权限 (WRITE_EXERCISE)，跳过训练归档写入")
            return Result.success(Unit)
        }

        // 口径红线：严格排除热身组，只保留完成组
        val effectiveSets = sets.filter { it.isCompleted && it.setType != "WARMUP" }
        if (effectiveSets.isEmpty()) return Result.success(Unit)

        val startTime = Instant.ofEpochMilli(session.startTime)
        val lastCompleted = effectiveSets.mapNotNull { it.completedAt }.maxOrNull()
        val endEpoch = maxOf(
            lastCompleted ?: 0L,
            session.endTime ?: 0L,
            session.startTime + 60_000L
        )
        val endTime = if (endEpoch > session.startTime) {
            Instant.ofEpochMilli(endEpoch)
        } else {
            startTime.plusSeconds(60)
        }
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(startTime)

        val totalVolumeKg = effectiveSets.sumOf { it.weightKg * it.reps }
        val exerciseMap = exercises.associateBy { it.id }

        // 安全构建 segments：必须按时间升序排列，且任意相邻两段绝对不可重叠，且必须在 [startTime, endTime] 范围内
        val sortedSets = effectiveSets.filter { it.completedAt != null }
            .sortedBy { it.completedAt }

        val safeSegments = mutableListOf<ExerciseSegment>()
        var lastSegEnd = startTime

        for (set in sortedSets) {
            val completedAt = set.completedAt ?: continue
            val segEndInstant = Instant.ofEpochMilli(completedAt)
            // 约束在 (startTime, endTime] 范围内
            val boundedEnd = if (segEndInstant.isAfter(endTime)) endTime else segEndInstant
            if (!boundedEnd.isAfter(startTime)) continue

            // 单组默认分配 30 秒，但绝不能早于上一组的结束时间，也不能早于整场训练开始时间
            var segStart = boundedEnd.minusSeconds(30)
            if (segStart.isBefore(lastSegEnd)) {
                segStart = lastSegEnd
            }
            if (segStart.isBefore(startTime)) {
                segStart = startTime
            }

            // 必须严格满足 segStart < boundedEnd
            if (segStart.isBefore(boundedEnd)) {
                val exName = exerciseMap[set.exerciseId]?.name.orEmpty()
                safeSegments.add(
                    ExerciseSegment(
                        startTime = segStart,
                        endTime = boundedEnd,
                        segmentType = mapSegmentType(exName),
                        repetitions = set.reps.coerceAtLeast(1)
                    )
                )
                lastSegEnd = boundedEnd
            }
        }

        val notesText = buildString {
            append("累计容量: ${totalVolumeKg.toInt()} kg · ${effectiveSets.size}组 (不含热身)")
            if (session.notes.isNotBlank()) {
                append(" · 备注: ${session.notes.trim()}")
            }
        }

        val version = maxOf(1L, session.endTime ?: System.currentTimeMillis())

        // 双重保障：优先尝试安全 segments，若底层抛出异常则无缝降级为无 segments 写入（确保整场记录绝对不丢失）
        val record = try {
            ExerciseSessionRecord(
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = endTime,
                endZoneOffset = zoneOffset,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                title = session.title.ifBlank { "力量训练" },
                notes = notesText,
                metadata = Metadata(
                    clientRecordId = "baoji:session:${session.id}",
                    clientRecordVersion = version,
                    recordingMethod = Metadata.RECORDING_METHOD_ACTIVELY_RECORDED
                ),
                segments = safeSegments
            )
        } catch (e: Exception) {
            Log.w(TAG, "构造带 segments 的 ExerciseSessionRecord 异常，降级为无 segments 写入: ${e.message}")
            ExerciseSessionRecord(
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = endTime,
                endZoneOffset = zoneOffset,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                title = session.title.ifBlank { "力量训练" },
                notes = notesText,
                metadata = Metadata(
                    clientRecordId = "baoji:session:${session.id}",
                    clientRecordVersion = version,
                    recordingMethod = Metadata.RECORDING_METHOD_ACTIVELY_RECORDED
                ),
                segments = emptyList()
            )
        }

        insertOrUpdate(client, listOf(record))
    }.onFailure { e ->
        Log.e(TAG, "写入 Health Connect 失败", e)
    }

    /**
     * 写入身体数据记录（体重、体脂率）。
     */
    suspend fun writeBodyMetrics(
        context: Context,
        metric: BodyMetricEntity
    ): Result<Unit> = runCatching {
        if (!isAvailable(context)) return Result.success(Unit)

        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()

        val zoneId = ZoneId.systemDefault()
        val localDate = if (metric.date > 100_000_000L) {
            Instant.ofEpochMilli(metric.date).atZone(zoneId).toLocalDate()
        } else {
            LocalDate.ofEpochDay(metric.date)
        }
        val zonedDateTime = localDate.atTime(8, 0).atZone(zoneId)
        val instant = zonedDateTime.toInstant()
        val zoneOffset = zonedDateTime.offset

        val records = mutableListOf<Record>()
        val version = maxOf(1L, metric.date)

        val permWeight = HealthPermission.getWritePermission(WeightRecord::class)
        if (granted.contains(permWeight) && metric.weightKg != null && metric.weightKg > 0.0) {
            records.add(
                WeightRecord(
                    time = instant,
                    zoneOffset = zoneOffset,
                    weight = Mass.kilograms(metric.weightKg),
                    metadata = Metadata(
                        clientRecordId = "baoji:weight:$localDate",
                        clientRecordVersion = version,
                        recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY
                    )
                )
            )
        }

        val permBodyFat = HealthPermission.getWritePermission(BodyFatRecord::class)
        if (granted.contains(permBodyFat) && metric.bodyFatPct != null && metric.bodyFatPct > 0.0) {
            records.add(
                BodyFatRecord(
                    time = instant,
                    zoneOffset = zoneOffset,
                    percentage = Percentage(metric.bodyFatPct),
                    metadata = Metadata(
                        clientRecordId = "baoji:bodyfat:$localDate",
                        clientRecordVersion = version,
                        recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY
                    )
                )
            )
        }

        if (records.isNotEmpty()) {
            insertOrUpdate(client, records)
        }
    }.onFailure { e ->
        Log.e(TAG, "写入身体指标至 Health Connect 失败", e)
    }

    /**
     * 幂等写入辅助方法：通过带有递增 clientRecordVersion 的 insertRecords 实现官方标准 Upsert。
     */
    private suspend fun insertOrUpdate(client: HealthConnectClient, records: List<Record>) {
        try {
            client.insertRecords(records)
        } catch (e: Exception) {
            Log.w(TAG, "insertRecords 遇到异常，尝试 updateRecords: ${e.message}")
            try {
                client.updateRecords(records)
            } catch (e2: Exception) {
                Log.e(TAG, "updateRecords 亦失败: ${e2.message}", e2)
                throw e2
            }
        }
    }

    private fun mapSegmentType(name: String): Int {
        return when {
            name.contains("卧推") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS
            name.contains("深蹲") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT
            name.contains("硬拉") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT
            name.contains("引体") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP
            name.contains("举腿") || name.contains("卷腹") || name.contains("平板") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH
            else -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING
        }
    }

    /**
     * 从 Health Connect 读取最近指定天数的体重与体脂数据
     */
    suspend fun readBodyMetrics(
        context: Context,
        days: Int = 60
    ): Result<List<BodyMetricEntity>> = runCatching {
        if (!isAvailable(context)) return Result.success(emptyList())

        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val canReadWeight = granted.contains(HealthPermission.getReadPermission(WeightRecord::class))
        val canReadFat = granted.contains(HealthPermission.getReadPermission(BodyFatRecord::class))

        if (!canReadWeight && !canReadFat) return Result.success(emptyList())

        val startTime = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val timeFilter = TimeRangeFilter.after(startTime)

        val weights = if (canReadWeight) {
            val request = ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = timeFilter
            )
            client.readRecords(request).records
        } else emptyList()

        val bodyFats = if (canReadFat) {
            val request = ReadRecordsRequest(
                recordType = BodyFatRecord::class,
                timeRangeFilter = timeFilter
            )
            client.readRecords(request).records
        } else emptyList()

        // 聚合同一天的数据（以当天本地 0 点 epoch millis 为键）
        val metricsByDate = mutableMapOf<Long, Pair<Double?, Double?>>()

        weights.forEach { r ->
            val localDate = r.time.atZone(ZoneId.systemDefault()).toLocalDate()
            val dayEpoch = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val cur = metricsByDate[dayEpoch] ?: Pair(null, null)
            metricsByDate[dayEpoch] = cur.copy(first = r.weight.inKilograms)
        }

        bodyFats.forEach { r ->
            val localDate = r.time.atZone(ZoneId.systemDefault()).toLocalDate()
            val dayEpoch = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val cur = metricsByDate[dayEpoch] ?: Pair(null, null)
            metricsByDate[dayEpoch] = cur.copy(second = r.percentage.value)
        }

        metricsByDate.map { (date, pair) ->
            BodyMetricEntity(
                id = 0,
                date = date,
                weightKg = pair.first,
                bodyFatPct = pair.second,
                note = ""
            )
        }.sortedBy { it.date }
    }

    /**
     * 将 Health Connect 中读取到的身体数据自动闭环同步至本地数据库
     * 规则：若本地已有该日期记录，仅在本地字段为 null 时补全（避免覆盖用户手动记录的围度与备注）
     * @return 成功更新或新增的天数
     */
    suspend fun syncBodyMetricsFromHealthConnect(
        context: Context,
        bodyMetricRepository: BodyMetricRepository,
        days: Int = 60
    ): Result<Int> = runCatching {
        val records = readBodyMetrics(context, days).getOrThrow()
        if (records.isEmpty()) return Result.success(0)

        var syncedCount = 0
        records.forEach { incoming ->
            val existing = bodyMetricRepository.getByDate(incoming.date)
            val day = Instant.ofEpochMilli(incoming.date).atZone(ZoneId.systemDefault()).toLocalDate()
            if (existing == null) {
                bodyMetricRepository.upsert(
                    day = day,
                    weightKg = incoming.weightKg,
                    bodyFatPct = incoming.bodyFatPct,
                    note = "从 Health Connect 同步"
                )
                syncedCount++
            } else {
                var changed = false
                var newWeight = existing.weightKg
                var newFat = existing.bodyFatPct

                if (newWeight == null && incoming.weightKg != null) {
                    newWeight = incoming.weightKg
                    changed = true
                }
                if (newFat == null && incoming.bodyFatPct != null) {
                    newFat = incoming.bodyFatPct
                    changed = true
                }

                if (changed) {
                    bodyMetricRepository.upsert(
                        day = day,
                        weightKg = newWeight,
                        bodyFatPct = newFat,
                        heightCm = existing.heightCm,
                        chestCm = existing.chestCm,
                        waistCm = existing.waistCm,
                        armCm = existing.armCm,
                        note = existing.note.ifBlank { "从 Health Connect 同步" }
                    )
                    syncedCount++
                }
            }
        }
        syncedCount
    }
}
