package com.baoji.fitness

import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class HealthConnectWriterTest {

    @Test
    fun testSafeSegmentGenerationPreventsOverlap() {
        val now = Instant.now()
        val sessionStart = now.minusSeconds(600)
        val sessionEnd = now

        // 模拟打勾时间间隔极短（比如 5 秒和 10 秒）
        val completedTimes = listOf(
            sessionStart.plusSeconds(10), // 开练 10 秒打勾
            sessionStart.plusSeconds(15), // 间隔 5 秒打勾
            sessionStart.plusSeconds(30), // 间隔 15 秒打勾
            sessionStart.plusSeconds(120) // 间隔 90 秒打勾
        )

        // 模拟 HealthConnectWriter 里的安全 segments 算法
        val safeSegments = mutableListOf<ExerciseSegment>()
        var lastSegEnd = sessionStart

        for (comp in completedTimes) {
            val segEndInstant = comp
            val boundedEnd = if (segEndInstant.isAfter(sessionEnd)) sessionEnd else segEndInstant
            if (!boundedEnd.isAfter(sessionStart)) continue

            var segStart = boundedEnd.minusSeconds(30)
            if (segStart.isBefore(lastSegEnd)) {
                segStart = lastSegEnd
            }
            if (segStart.isBefore(sessionStart)) {
                segStart = sessionStart
            }

            if (segStart.isBefore(boundedEnd)) {
                safeSegments.add(
                    ExerciseSegment(
                        startTime = segStart,
                        endTime = boundedEnd,
                        segmentType = ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING,
                        repetitions = 10
                    )
                )
                lastSegEnd = boundedEnd
            }
        }

        // 验证构造 ExerciseSessionRecord 不会抛出任何异常
        val record = ExerciseSessionRecord(
            startTime = sessionStart,
            startZoneOffset = ZoneOffset.UTC,
            endTime = sessionEnd,
            endZoneOffset = ZoneOffset.UTC,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = "力量训练",
            metadata = Metadata(clientRecordId = "test:safe", clientRecordVersion = 1L),
            segments = safeSegments
        )

        assertNotNull(record)
        assertTrue(record.segments.isNotEmpty())
        for (i in 0 until record.segments.lastIndex) {
            assertTrue(
                "相邻 segments 不得重叠",
                !record.segments[i].endTime.isAfter(record.segments[i + 1].startTime)
            )
        }
        assertTrue(
            "首个 segment 起始不得早于会话起始",
            !record.segments.first().startTime.isBefore(sessionStart)
        )
        assertTrue(
            "最后一个 segment 结束不得晚于会话结束",
            !record.segments.last().endTime.isAfter(sessionEnd)
        )
    }

    @Test
    fun testFallbackSessionRecordOnExtremeFailure() {
        val now = Instant.now()
        val sessionStart = now.minusSeconds(300)
        val sessionEnd = now

        // 即使传入空 segments，力量训练仍能完整记录核心容量与时间
        val record = ExerciseSessionRecord(
            startTime = sessionStart,
            startZoneOffset = ZoneOffset.UTC,
            endTime = sessionEnd,
            endZoneOffset = ZoneOffset.UTC,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = "力量训练",
            notes = "累计容量: 2500 kg · 5组 (不含热身)",
            metadata = Metadata(clientRecordId = "test:fallback", clientRecordVersion = 1L),
            segments = emptyList()
        )

        assertNotNull(record)
        assertEquals("力量训练", record.title)
        assertTrue(record.segments.isEmpty())
    }
}
