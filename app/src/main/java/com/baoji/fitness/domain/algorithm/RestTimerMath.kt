package com.baoji.fitness.domain.algorithm

/**
 * 组间休息倒计时的纯计算部分。
 *
 * 关键约束：剩余时间只能由「截止时刻 − 当前时刻」推导，不能靠每秒自减累加。
 * 应用切后台会被系统冻结（cached app freezer / Doze），期间不会有 tick，
 * 自减实现会把时间停在那一刻，回到前台才继续走 —— 也就是「后台休息不走」。
 * 差值实现则在任何一次 tick 上都重新对齐真实时间，恢复后立刻正确（甚至直接归零）。
 */
object RestTimerMath {

    /** 剩余秒数，向上取整（保证第 1 秒显示完整的目标值）；已过期返回 0。 */
    fun remainingSeconds(nowElapsedMs: Long, endAtElapsedMs: Long): Int {
        val ms = endAtElapsedMs - nowElapsedMs
        return if (ms <= 0L) 0 else ((ms + 999L) / 1000L).toInt()
    }

    /**
     * 加时后的新截止时刻。
     * 若已过期，从「当前时刻」起算而不是从过期时刻加，否则 +30s 会被过去的空白瞬间吃掉。
     */
    fun extendEnd(nowElapsedMs: Long, endAtElapsedMs: Long, extraSeconds: Int): Long {
        val base = if (endAtElapsedMs > nowElapsedMs) endAtElapsedMs else nowElapsedMs
        return base + extraSeconds * 1000L
    }
}
