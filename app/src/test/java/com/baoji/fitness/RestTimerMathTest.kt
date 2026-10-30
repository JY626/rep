package com.baoji.fitness

import com.baoji.fitness.domain.algorithm.RestTimerMath
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 组间休息倒计时的回归测试。
 *
 * 历史 bug：倒计时用「每秒自减」实现，应用切后台被系统冻结时没有 tick，
 * 回到前台后时间是停在冻结点继续往下数的 —— 表现为「后台休息不走」。
 * 这些用例把「按截止时刻求差值」的口径钉死。
 */
class RestTimerMathTest {

    @Test
    fun countdown_decreasesWithClock() {
        val endAt = 90_000L

        assertEquals(90, RestTimerMath.remainingSeconds(0L, endAt))
        assertEquals(90, RestTimerMath.remainingSeconds(1L, endAt))   // 向上取整：第 1 秒仍显示 90
        assertEquals(60, RestTimerMath.remainingSeconds(30_000L, endAt))
        assertEquals(1, RestTimerMath.remainingSeconds(89_500L, endAt))
    }

    @Test
    fun reachesZeroAtDeadline() {
        val endAt = 90_000L

        assertEquals(0, RestTimerMath.remainingSeconds(90_000L, endAt))
        assertEquals(0, RestTimerMath.remainingSeconds(200_000L, endAt))
    }

    /** 核心回归：后台冻结 150 秒、期间一次 tick 都没有，回来必须已归零而不是停在 87。 */
    @Test
    fun backgroundFreeze_doesNotPauseCountdown() {
        val endAt = 90_000L
        // 冻结前最后一次 tick 在 3 秒处
        assertEquals(87, RestTimerMath.remainingSeconds(3_000L, endAt))
        // 解冻后第一次 tick 直接跳到 150 秒处
        assertEquals(0, RestTimerMath.remainingSeconds(153_000L, endAt))
    }

    /** 同一进程只是短暂切后台（未冻结）：恢复时剩余时间应已扣掉后台那段。 */
    @Test
    fun shortBackground_isCountedOut() {
        val endAt = 90_000L
        // 30 秒时切后台，60 秒后回来
        assertEquals(0, RestTimerMath.remainingSeconds(90_000L, endAt))
    }

    @Test
    fun zeroOrNegativeDeadline_isZero() {
        assertEquals(0, RestTimerMath.remainingSeconds(1_000L, 0L))
    }

    @Test
    fun extendEnd_addsToRemainingDeadline() {
        // 还剩 60 秒时 +30s → 截止时刻顺延 30 秒
        assertEquals(121_000L, RestTimerMath.extendEnd(31_000L, 91_000L, 30))
    }

    /** 计时已结束（胶囊已隐藏）再点 +30s：必须从现在起算，不能被过去的空白吃掉。 */
    @Test
    fun extendEnd_afterExpiry_restartsFromNow() {
        assertEquals(130_000L, RestTimerMath.extendEnd(100_000L, 5_000L, 30))
    }

    @Test
    fun extendEnd_atExactDeadline_restartsFromNow() {
        assertEquals(120_000L, RestTimerMath.extendEnd(90_000L, 90_000L, 30))
    }
}
