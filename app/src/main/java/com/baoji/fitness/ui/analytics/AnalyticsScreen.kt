package com.baoji.fitness.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoji.fitness.core.theme.AppleBlack
import com.baoji.fitness.core.theme.AppleCautionOrange
import com.baoji.fitness.core.theme.AppleFitnessGreen
import com.baoji.fitness.core.theme.AppleGray5
import com.baoji.fitness.core.theme.AppleGray6
import com.baoji.fitness.core.theme.AppleHairline
import com.baoji.fitness.core.theme.AppleSystemRed
import com.baoji.fitness.core.theme.AppleTextPrimary
import com.baoji.fitness.core.theme.AppleTextSecondary
import com.baoji.fitness.core.theme.AppleTextTertiary
import com.baoji.fitness.domain.algorithm.ReviewPeriod
import com.baoji.fitness.domain.algorithm.TrendPoint
import com.baoji.fitness.domain.model.MuscleVolumeStatus
import com.baoji.fitness.domain.model.formatSets
import com.baoji.fitness.ui.components.ChartPoint
import com.baoji.fitness.ui.components.MinimalLineChart
import java.util.Locale
import kotlin.math.roundToInt

/** 外卡圆角（B 稿：外卡 20 / 内部小块 14）。 */
private val SectionShape = RoundedCornerShape(20.dp)

/** 卡内小块圆角。 */
private val InnerShape = RoundedCornerShape(14.dp)

/** 全页唯一允许的最小字号，任何文字都不低于它。 */
private val MinFontSize = 11.sp

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier
) {
    val exercises by viewModel.exercises.collectAsState()
    val selectedExerciseId by viewModel.selectedExerciseId.collectAsState()
    val analyticsState by viewModel.analyticsState.collectAsState()
    val period by viewModel.period.collectAsState()
    val periodReview by viewModel.periodReview.collectAsState()
    val trend by viewModel.trend.collectAsState()
    val weeklyMuscle by viewModel.weeklyMuscleComparison.collectAsState()

    val selectedExerciseName = exercises.find { it.id == selectedExerciseId }?.name ?: "该动作"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppleBlack)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Column {
                Text(
                    text = "PROGRESSIVE OVERLOAD",
                    fontSize = MinFontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextSecondary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "负荷与表现",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleTextPrimary,
                    letterSpacing = (-0.5).sp
                )
            }
        }

        // ===== M1 周期回顾 =====
        // 1. 周期切换 chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReviewPeriod.values().forEach { candidate ->
                    val isSelected = candidate == period
                    Box(
                        modifier = Modifier
                            .clip(InnerShape)
                            .background(if (isSelected) Color.White else AppleGray6)
                            .clickable { viewModel.selectPeriod(candidate) }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = candidate.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else AppleTextSecondary
                        )
                    }
                }
            }
        }

        // 2. 横排三块 KPI：训练次数 / 总容量 / 有效组数（1dp 竖分隔线，不再套卡片）
        item {
            val review = periodReview
            val sessionCount = review?.sessionCount ?: 0
            val totalVolume = review?.totalVolumeKg ?: 0.0
            val totalSets = review?.totalSets ?: 0

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KpiBlock(
                        value = "$sessionCount",
                        unit = "次",
                        label = "训练次数",
                        spoken = "训练次数 $sessionCount 次",
                        modifier = Modifier.weight(1f)
                    )
                    KpiDivider()
                    KpiBlock(
                        value = formatVolume(totalVolume),
                        unit = "kg",
                        label = "总容量",
                        spoken = "累计容量 ${totalVolume.roundToInt()} 公斤",
                        deltaPct = review?.volumeDeltaPct,
                        deltaSpoken = deltaSpoken(review?.volumeDeltaPct),
                        modifier = Modifier.weight(1f)
                    )
                    KpiDivider()
                    KpiBlock(
                        value = "$totalSets",
                        unit = "组",
                        label = "有效组数",
                        spoken = "有效组数 $totalSets 组",
                        modifier = Modifier.weight(1f)
                    )
                }

                val typeCounts = review?.typeCounts.orEmpty()
                if (typeCounts.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        typeCounts.forEach { (type, count) ->
                            Box(
                                modifier = Modifier
                                    .clip(InnerShape)
                                    .background(AppleGray5)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "$type $count",
                                    fontSize = MinFontSize,
                                    fontFamily = FontFamily.Monospace,
                                    color = AppleTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 部位有效组数：轨道 = MRV 上限，填充 = 有效组数，三态配色
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SectionShape)
                    .background(AppleGray6)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column {
                    Text(
                        text = "周肌群有效组数",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                    Text(
                        text = "本周 · 横条满刻度为 MRV 上限，对照 RP 科学区间 (MEV/MRV)",
                        fontSize = MinFontSize,
                        color = AppleTextSecondary
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    weeklyMuscle.current.forEach { status ->
                        VolumeProgressBar(status = status)
                    }
                }
            }
        }

        // 4. 容量趋势折线（近 8 周）
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SectionShape)
                    .background(AppleGray6)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "容量趋势",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleTextPrimary
                        )
                        Text(
                            text = "近 8 周训练容量 (kg)",
                            fontSize = MinFontSize,
                            color = AppleTextSecondary
                        )
                    }
                    Text(
                        text = trend.lastOrNull()?.let { "本周 ${it.volumeKg.toInt()}" } ?: "--",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AppleFitnessGreen
                    )
                }

                if (trend.any { it.volumeKg > 0.0 }) {
                    TrendCanvasChart(
                        points = trend,
                        description = trendDescription(trend),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(InnerShape)
                            .background(AppleGray5),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "还没有足够的训练记录，完成几次训练后这里会出现周容量曲线",
                            fontSize = MinFontSize,
                            color = AppleTextTertiary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        }

        // ===== 单动作负荷（原有）=====
        item {
            Text(
                text = "单动作负荷",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextPrimary
            )
        }

        // Dynamic Exercise Filter Chips from Database
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                exercises.forEach { ex ->
                    val isSelected = (ex.id == selectedExerciseId)
                    Box(
                        modifier = Modifier
                            .clip(InnerShape)
                            .background(if (isSelected) Color.White else AppleGray6)
                            .clickable { viewModel.selectExercise(ex.id) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = ex.name,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else AppleTextSecondary
                        )
                    }
                }
            }
        }

        // Big Metric & Canvas E1RM Chart Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SectionShape)
                    .background(AppleGray6)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "当前极限估算 (E1RM)",
                            fontSize = 12.sp,
                            color = AppleTextSecondary
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = if (analyticsState.currentE1RM > 0) "${analyticsState.currentE1RM}" else "--",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = AppleTextPrimary
                            )
                            Text(
                                text = "kg",
                                fontSize = 13.sp,
                                color = AppleTextSecondary
                            )
                            Text(
                                text = analyticsState.percentageIncreaseText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppleFitnessGreen
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "最佳正式组",
                            fontSize = MinFontSize,
                            color = AppleTextSecondary
                        )
                        Text(
                            text = analyticsState.bestSetText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = AppleTextPrimary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Minimalist Canvas Line Chart
                if (analyticsState.points.isNotEmpty()) {
                    OverloadCanvasChart(
                        points = analyticsState.points,
                        description = e1rmDescription(selectedExerciseName, analyticsState),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(InnerShape)
                            .background(AppleGray5),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无该动作历史完成组，在训练中打勾记录后将自动绘制",
                            fontSize = MinFontSize,
                            color = AppleTextTertiary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        }

        // Overload Protocol Decision Card
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SectionShape)
                    .background(AppleGray6)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "↑",
                    fontSize = MinFontSize,
                    color = AppleFitnessGreen,
                    // 纯装饰箭头，别让读屏念成「上箭头」
                    modifier = Modifier.clearAndSetSemantics { }
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "算法过载决策",
                        fontSize = MinFontSize,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = analyticsState.adviceMessage,
                        fontSize = 13.sp,
                        color = AppleTextPrimary,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/** KPI 之间的 1dp 竖分隔线。 */
@Composable
private fun KpiDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(40.dp)
            .background(AppleHairline)
    )
}

/**
 * 横排 KPI 的一块：32sp 等宽数值 + 单位，标签在数值下方，涨跌徽标贴在标签行右侧。
 *
 * 整块合并成一个无障碍节点（`mergeDescendants`），读屏只念一句完整的话，
 * 不会把「4,860」「kg」「+2%」拆成三段。
 */
@Composable
private fun KpiBlock(
    value: String,
    unit: String,
    label: String,
    spoken: String,
    modifier: Modifier = Modifier,
    deltaPct: Double? = null,
    deltaSpoken: String? = null
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull(spoken, deltaSpoken).joinToString("，")
        },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = kpiValueSize(value),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                // 负字距把「4,860 kg」压进 1/3 屏宽，又不会换成 30sp
                letterSpacing = (-1.5).sp,
                maxLines = 1,
                color = AppleTextPrimary
            )
            Text(
                text = unit,
                fontSize = MinFontSize,
                color = AppleTextSecondary,
                modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                fontSize = MinFontSize,
                color = AppleTextSecondary
            )
            if (deltaPct != null) DeltaBadge(deltaPct)
        }
    }
}

/**
 * 环比徽章。训练容量是「越多越好」的语义，所以涨用 App 既有的进度绿、
 * 跌用提醒橙（与股票行情的红涨绿跌不是一套语境）。
 */
@Composable
private fun DeltaBadge(deltaPct: Double?) {
    val (text, color) = when {
        deltaPct == null -> "-- 无上期" to AppleTextTertiary
        deltaPct >= 0.05 -> String.format(Locale.US, "↑ +%.0f%%", deltaPct) to AppleFitnessGreen
        deltaPct <= -0.05 -> String.format(Locale.US, "↓ -%.0f%%", -deltaPct) to AppleCautionOrange
        else -> "→ 持平" to AppleTextSecondary
    }
    Box(
        modifier = Modifier
            .clip(InnerShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = MinFontSize,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

/**
 * 部位有效组数：**一条 6dp 轨道，满刻度 = MRV**，填充比例 = 有效组数 / MRV。
 *
 * 旧版「条长表示已做组数、颜色表示欠练/达标/过量」两种语义打架，用户读不出
 * 「离上限还有多远」。现在长度只表达一件事：占 MRV 的比例；颜色只表达状态。
 * 超过 MRV 时轨道末端多一截 2dp 红色标记（比轨道高，能和红色填充区分开）。
 */
@Composable
private fun VolumeProgressBar(
    status: MuscleVolumeStatus,
    modifier: Modifier = Modifier
) {
    val mrv = status.mrv.takeIf { it > 0 } ?: status.mav.coerceAtLeast(1)
    val sets = status.completedSets
    val fraction = (sets / mrv).coerceIn(0.0, 1.0).toFloat()
    val overflow = sets > mrv
    val (barColor, statusWord) = when {
        sets < status.mev -> AppleCautionOrange to "欠练"
        sets <= mrv -> AppleFitnessGreen to "达标"
        else -> AppleSystemRed to "过量"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(sets.toFloat(), 0f..mrv.toFloat(), 0)
                contentDescription =
                    "${status.muscleGroup}，有效 ${formatSets(sets)} 组，" +
                        "目标区间 ${status.mev} 到 $mrv 组，$statusWord"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = status.muscleGroup,
            fontSize = MinFontSize,
            color = AppleTextPrimary,
            maxLines = 1,
            modifier = Modifier.width(32.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AppleGray5)
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(barColor)
                    )
                }
            }
            if (overflow) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(2.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(1.dp))
                        .background(AppleSystemRed)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp)
        ) {
            Text(
                text = "${formatSets(sets)}/$mrv 组",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = barColor,
                maxLines = 1
            )
            Text(
                text = statusWord,
                fontSize = MinFontSize,
                color = barColor,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .width(24.dp)
            )
        }
    }
}

/** 近 N 周容量折线：共用 [MinimalLineChart]，zeroBased + x 轴周标签。 */
@Composable
private fun TrendCanvasChart(
    points: List<TrendPoint>,
    description: String,
    modifier: Modifier = Modifier
) {
    MinimalLineChart(
        points = points.map { ChartPoint(label = it.label, value = it.volumeKg) },
        modifier = modifier.semantics { contentDescription = description },
        zeroBased = true,
        showXLabels = true,
        xLabelTextSizePx = minFontPx()
    )
}

@Composable
private fun OverloadCanvasChart(
    points: List<OverloadDataPoint>,
    description: String,
    modifier: Modifier = Modifier
) {
    MinimalLineChart(
        points = points.map {
            ChartPoint(label = it.label, value = it.e1rm, highlight = it.isPR)
        },
        modifier = modifier.semantics { contentDescription = description },
        zeroBased = false,
        xLabelTextSizePx = minFontPx()
    )
}

/** 坐标轴刻度用 `MinFontSize` 而不是写死的小字号；Canvas 画字要 px。 */
@Composable
private fun minFontPx(): Float = with(LocalDensity.current) { MinFontSize.toPx() }

/** 千分位：`4,860`（不跟随系统区域，避免切成 `4.860`）。 */
private fun formatVolume(volumeKg: Double): String =
    String.format(Locale.US, "%,d", volumeKg.toInt())

/**
 * KPI 数字字号：32sp 是基线。三列等宽时单列只有约 1/3 屏宽，
 * 出现 `12,345` 这种 6 位容量就放不下「数值 + 单位」了，降一档保证永不裁字。
 */
private fun kpiValueSize(value: String): TextUnit = when {
    value.length <= 5 -> 32.sp
    value.length == 6 -> 27.sp
    else -> 23.sp
}

/** 徽章的读屏念法：符号不念，念人话。 */
private fun deltaSpoken(deltaPct: Double?): String? = when {
    deltaPct == null -> null
    deltaPct >= 0.05 -> "比上期增长 ${deltaPct.roundToInt()}%"
    deltaPct <= -0.05 -> "比上期下降 ${(-deltaPct).roundToInt()}%"
    else -> "与上期持平"
}

/** 8 周容量趋势的一句话概括，不逐点朗读。 */
private fun trendDescription(points: List<TrendPoint>): String {
    if (points.isEmpty()) return "近 8 周训练容量趋势，暂无数据"
    val first = points.first().volumeKg.roundToInt()
    val last = points.last().volumeKg.roundToInt()
    val peak = points.maxOf { it.volumeKg }.roundToInt()
    val direction = when {
        last > first -> "从 $first 公斤升至 $last 公斤"
        last < first -> "从 $first 公斤降至 $last 公斤"
        else -> "维持在 $first 公斤"
    }
    return "近 ${points.size} 周训练容量$direction，最高 $peak 公斤"
}

/** E1RM 趋势的一句话概括：起点、终点、是否破纪录。 */
private fun e1rmDescription(
    exerciseName: String,
    state: ExerciseAnalyticsState
): String {
    val points = state.points
    if (points.isEmpty()) return "$exerciseName 暂无 E1RM 曲线"
    val last = points.last().e1rm.roundToInt()
    if (points.size == 1) return "$exerciseName 当前 E1RM 约 $last 公斤，只有 1 次记录"
    val first = points.first().e1rm.roundToInt()
    val direction = when {
        last > first -> "从 $first 公斤升至 $last 公斤"
        last < first -> "从 $first 公斤降至 $last 公斤"
        else -> "维持在 $first 公斤"
    }
    val pr = if (points.last().isPR) "，最近一次创个人纪录" else ""
    return "$exerciseName E1RM 近 ${points.size} 次训练$direction$pr"
}
