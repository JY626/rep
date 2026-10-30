package com.baoji.fitness.ui.body

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.WeightRecord
import com.baoji.fitness.data.healthconnect.HealthConnectWriter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoji.fitness.core.database.entity.BodyMetricEntity
import com.baoji.fitness.core.designsystem.icons.LucideIcons
import com.baoji.fitness.core.theme.AppleBlack
import com.baoji.fitness.core.theme.AppleCautionOrange
import com.baoji.fitness.core.theme.AppleFitnessGreen
import com.baoji.fitness.core.theme.AppleGray4
import com.baoji.fitness.core.theme.AppleGray5
import com.baoji.fitness.core.theme.AppleGray6
import com.baoji.fitness.core.theme.AppleHairline
import com.baoji.fitness.core.theme.AppleSystemRed
import com.baoji.fitness.core.theme.AppleTextPrimary
import com.baoji.fitness.core.theme.AppleTextSecondary
import com.baoji.fitness.data.repository.BodyMetricRepository
import com.baoji.fitness.ui.library.BodyMetricSheet
import com.baoji.fitness.ui.library.formatMetric
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 「身体」Tab：体重 / 体脂 / 围度 / 身高。
 * B 版顺序：先回答「我现在怎么样」（当前值 + 变化 + 趋势），再给记录入口。
 * 一屏之内能看到结论，不需要先读一张卡片阵列。
 */
@Composable
fun BodyScreen(
    bodyMetricRepository: BodyMetricRepository,
    modifier: Modifier = Modifier
) {
    val latest by bodyMetricRepository.observeLatest().collectAsState(initial = null)
    val history by bodyMetricRepository.observeAll().collectAsState(initial = emptyList())

    var showSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isHealthAvailable = remember { HealthConnectWriter.isAvailable(context) }
    var hasHealthPermissions by remember { mutableStateOf(false) }

    val healthConnectLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        hasHealthPermissions = granted.contains(HealthPermission.getReadPermission(WeightRecord::class)) ||
            granted.contains(HealthPermission.getReadPermission(BodyFatRecord::class))
        if (hasHealthPermissions) {
            scope.launch {
                HealthConnectWriter.syncBodyMetricsFromHealthConnect(context, bodyMetricRepository)
                    .onSuccess { count ->
                        val msg = if (count > 0) "成功从 Health Connect 同步 $count 天数据" else "已是最新，无待同步数据"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(context, "同步失败: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (isHealthAvailable) {
            hasHealthPermissions = HealthConnectWriter.hasReadPermissions(context)
        }
    }

    val syncNow: () -> Unit = {
        if (!hasHealthPermissions) {
            healthConnectLauncher.launch(HealthConnectWriter.PERMISSIONS)
        } else {
            scope.launch {
                HealthConnectWriter.syncBodyMetricsFromHealthConnect(context, bodyMetricRepository)
                    .onSuccess { count ->
                        val msg = if (count > 0) "成功从 Health Connect 同步 $count 天数据" else "已是最新，无待同步数据"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(context, "同步失败: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    // 只保留有该指标的记录，升序（repository 保证 date 升序）
    val weightSeries = remember(history) { history.filter { it.weightKg != null } }
    val fatSeries = remember(history) { history.filter { it.bodyFatPct != null } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppleBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        PageHeader(
            latest = latest,
            syncVisible = isHealthAvailable,
            onSync = syncNow,
            onRecord = { showSheet = true }
        )

        CurrentStateCard(latest = latest, weightSeries = weightSeries, fatSeries = fatSeries)

        TrendSection(weightSeries = weightSeries, fatSeries = fatSeries, totalRecords = history.size)

        latest?.let { metric ->
            val bmi = metric.bmiValue()
            // 身高缺失时整行隐藏 —— 显示「BMI --」比不显示更让人困惑
            if (bmi != null) BmiRow(bmi = bmi)
            if (metric.hasGirth()) GirthFold(metric = metric)
        }

        if (history.isNotEmpty()) RecentRecords(history = history)

        RecordTodayCta(
            hasTodayRecord = history.any { millisToDay(it.date) == LocalDate.now() },
            onClick = { showSheet = true }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showSheet) {
        BodyMetricSheet(
            day = LocalDate.now(),
            latest = latest,
            onDismiss = { showSheet = false },
            onSave = { weight, fat, height, chest, waist, arm ->
                scope.launch {
                    bodyMetricRepository.upsert(
                        day = LocalDate.now(),
                        weightKg = weight,
                        bodyFatPct = fat,
                        heightCm = height,
                        chestCm = chest,
                        waistCm = waist,
                        armCm = arm
                    )
                }
                showSheet = false
            }
        )
    }
}

/* ───────────────────────── 顶部 ───────────────────────── */

@Composable
private fun PageHeader(
    latest: BodyMetricEntity?,
    syncVisible: Boolean,
    onSync: () -> Unit,
    onRecord: () -> Unit
) {
    val day = latest?.let { millisToDay(it.date) } ?: LocalDate.now()
    val dateFormat = remember { DateTimeFormatter.ofPattern("M月d日", Locale.CHINESE) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Text(
                text = "BODY · ${day.format(dateFormat)} 记录",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextSecondary,
                letterSpacing = 1.2.sp
            )
            Text(
                text = "身体",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary,
                letterSpacing = (-0.5).sp
            )
        }

        if (syncVisible) {
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(AppleGray5)
                    .clickable(onClick = onSync)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = LucideIcons.Activity,
                        contentDescription = null,
                        tint = AppleFitnessGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "同步",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleFitnessGreen
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(AppleGray5)
                    .clickable(onClick = onRecord)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "记一次",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleFitnessGreen
                )
            }
        }
    }
}

/* ───────────────────────── 我现在怎么样 ───────────────────────── */

/**
 * 当前体重 / 体脂：一个大数字 + 一行变化量。
 * 减重、减脂都算好事 —— 变化量统一走绿色，涨了才用橙色提醒，绝不用红色（红色只留给删除）。
 */
@Composable
private fun CurrentStateCard(
    latest: BodyMetricEntity?,
    weightSeries: List<BodyMetricEntity>,
    fatSeries: List<BodyMetricEntity>
) {
    val weightDelta = seriesDelta(weightSeries) { it.weightKg }
    val fatDelta = seriesDelta(fatSeries) { it.bodyFatPct }
    val weightText = latest?.weightKg?.let { formatMetric(it) } ?: DASH_VALUE
    val fatText = latest?.bodyFatPct?.let { formatMetric(it) } ?: DASH_VALUE
    val dash = latest?.weightKg == null && latest?.bodyFatPct == null

    val weightDesc = buildString {
        append("当前体重 ")
        append(if (latest?.weightKg == null) "未记录" else "${formatMetric(latest.weightKg)} 公斤")
        weightDelta?.let { d ->
            when {
                d.diff < -0.001 -> append("，比上次减少 ${formatMetric(-d.diff)} 公斤")
                d.diff > 0.001 -> append("，比上次增加 ${formatMetric(d.diff)} 公斤")
                else -> append("，与上次持平")
            }
        }
    }

    val fatDesc = buildString {
        append("当前体脂 ")
        append(if (latest?.bodyFatPct == null) "未记录" else "${formatMetric(latest.bodyFatPct)} 百分比")
        fatDelta?.let { d ->
            when {
                d.diff < -0.001 -> append("，比上次减少 ${formatMetric(-d.diff)} 个百分点")
                d.diff > 0.001 -> append("，比上次增加 ${formatMetric(d.diff)} 个百分点")
                else -> append("，与上次持平")
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppleGray6)
            .padding(16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { contentDescription = weightDesc }
        ) {
            Text(text = "当前体重", fontSize = 11.sp, color = AppleTextSecondary)
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = weightText,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-1.5).sp,
                    color = if (dash) AppleTextSecondary else AppleTextPrimary
                )
                Text(
                    text = " kg",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppleTextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            DeltaLine(
                delta = weightDelta,
                unit = "kg",
                trailing = { d -> "较 ${d.previousDate.format(monthDayFormat())}" },
                emptyText = if (latest?.weightKg == null) "" else "首条记录"
            )
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .width(1.dp)
                .height(52.dp)
                .background(AppleHairline)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { contentDescription = fatDesc }
        ) {
            Text(text = "体脂", fontSize = 11.sp, color = AppleTextSecondary)
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = fatText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-1.2).sp,
                    color = if (latest?.bodyFatPct == null) AppleTextSecondary else AppleTextPrimary
                )
                Text(
                    text = " %",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppleTextSecondary,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            DeltaLine(
                delta = fatDelta,
                unit = "pt",
                trailing = { d -> "${d.daysBetween} 天" },
                emptyText = if (latest?.bodyFatPct == null) "" else "首条记录"
            )
        }
    }
}

/**
 * 变化量一行：中性灰 + 方向箭头（↓ 减 / ↑ 增 / — 持平）。
 * 不标红绿 —— App 没有「增重还是减重」的目标设定，增肌期体重上升是成功，
 * 不该被颜色判成坏消息。状态色留给达标/过量/删除这些真状态。
 */
@Composable
private fun DeltaLine(
    delta: MetricDelta?,
    unit: String,
    trailing: (MetricDelta) -> String,
    emptyText: String
) {
    if (delta == null) {
        if (emptyText.isNotEmpty()) {
            Text(
                text = emptyText,
                fontSize = 11.sp,
                color = AppleTextSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        return
    }

    val flat = delta.diff > -0.001 && delta.diff < 0.001
    val arrowText = when {
        flat -> "— 持平"
        delta.diff < 0 -> "↓ ${formatMetric(-delta.diff)} $unit"
        else -> "↑ ${formatMetric(delta.diff)} $unit"
    }

    Row(
        modifier = Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = arrowText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = AppleTextSecondary
        )
        Text(
            text = " · " + trailing(delta),
            fontSize = 11.sp,
            color = AppleTextSecondary
        )
    }
}

/* ───────────────────────── 双列趋势 ───────────────────────── */

@Composable
private fun TrendSection(
    weightSeries: List<BodyMetricEntity>,
    fatSeries: List<BodyMetricEntity>,
    totalRecords: Int
) {
    val weightPoints = remember(weightSeries) { weightSeries.sparkPoints { it.weightKg } }
    val fatPoints = remember(fatSeries) { fatSeries.sparkPoints { it.bodyFatPct } }

    val charts = buildList {
        if (weightPoints.size >= 2) add(
            SparkSpec("体重", "kg", weightPoints)
        )
        if (fatPoints.size >= 2) add(
            SparkSpec("体脂", "%", fatPoints)
        )
    }

    when {
        // 一条记录都没有：给虚线占位说明要几次才有图，别摆两张空卡片
        totalRecords == 0 -> DashedTrendPlaceholder()
        charts.isEmpty() -> Text(
            text = "再记 1 次就能看到趋势",
            fontSize = 11.sp,
            color = AppleTextSecondary,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        else -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            charts.forEach { spec ->
                SparkCard(spec = spec, modifier = Modifier.weight(1f))
            }
        }
    }
}

private class SparkPoint(
    val shortLabel: String,
    val longLabel: String,
    val value: Double
)

private class SparkSpec(
    val title: String,
    val unit: String,
    val points: List<SparkPoint>
)

private fun List<BodyMetricEntity>.sparkPoints(pick: (BodyMetricEntity) -> Double?): List<SparkPoint> =
    takeLast(7).mapNotNull { entity ->
        pick(entity)?.let { value ->
            val day = millisToDay(entity.date)
            SparkPoint(
                shortLabel = "${day.monthValue}/${day.dayOfMonth}",
                longLabel = "${day.monthValue}月${day.dayOfMonth}日",
                value = value
            )
        }
    }

/** 一张小折线：无 Y 轴、无刻度、无网格，只在首尾各标一个数值 + 下方独立一行日期。 */
@Composable
private fun SparkCard(spec: SparkSpec, modifier: Modifier = Modifier) {
    val first = spec.points.first()
    val last = spec.points.last()

    // 折线图必须给文字替代：读屏用户看不到曲线，至少能听到「从多少到多少」
    val description = buildString {
        append(spec.title)
        append("趋势图，")
        append(first.longLabel)
        append("到")
        append(last.longLabel)
        append("，")
        val word = unitWord(spec.unit)
        when {
            last.value < first.value ->
                append("从 ${formatMetric(first.value)} $word 降到 ${formatMetric(last.value)} $word")
            last.value > first.value ->
                append("从 ${formatMetric(first.value)} $word 升到 ${formatMetric(last.value)} $word")
            else ->
                append("一直是 ${formatMetric(last.value)} $word")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppleGray6)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = spec.title,
                fontSize = 11.sp,
                color = AppleTextSecondary
            )
            // 区间而不是单值：首尾各出现一次，图上就不再写字，卡片里没有重复数字
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatMetric(first.value),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AppleTextSecondary
                )
                Text(
                    text = " → ",
                    fontSize = 11.sp,
                    color = AppleTextSecondary
                )
                Text(
                    text = formatMetric(last.value),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-0.3).sp,
                    color = AppleTextPrimary
                )
                Text(
                    text = " ${spec.unit}",
                    fontSize = 11.sp,
                    color = AppleTextSecondary
                )
            }
        }

        SparkChart(
            spec = spec,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(top = 6.dp)
                .semantics { contentDescription = description }
        )

        // 日期行：独立一行，绝不叠在折线上
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = first.shortLabel,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = AppleTextSecondary
            )
            Text(
                text = last.shortLabel,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = AppleTextSecondary
            )
        }
    }
}

@Composable
private fun SparkChart(spec: SparkSpec, modifier: Modifier = Modifier) {
    val values = spec.points.map { it.value }

    Canvas(modifier = modifier) {
        val inset = 4.dp.toPx()
        val w = size.width
        val h = size.height - inset * 2
        val lo = values.minOrNull() ?: 0.0
        val hi = values.maxOrNull() ?: 1.0
        val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0

        fun xOf(index: Int): Float =
            if (values.size > 1) index * (w / (values.size - 1)) else w / 2f

        fun yOf(value: Double): Float =
            inset + (h - ((value - lo) / span) * h).toFloat()

        val linePath = Path()
        values.forEachIndexed { i, v ->
            val x = xOf(i)
            val y = yOf(v)
            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }

        // 折线下方的浅绿渐变：只是让「更低 = 更好」读得更快，不承担读数
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(xOf(values.size - 1), inset + h)
            lineTo(xOf(0), inset + h)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    AppleFitnessGreen.copy(alpha = 0.26f),
                    AppleFitnessGreen.copy(alpha = 0f)
                ),
                startY = inset,
                endY = inset + h
            )
        )

        drawPath(
            path = linePath,
            color = AppleFitnessGreen,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // 首点次要色、末点绿色：一眼看出「从哪来、现在在哪」
        drawCircle(
            color = AppleTextSecondary,
            radius = 3.dp.toPx(),
            center = Offset(xOf(0), yOf(values.first()))
        )
        drawCircle(
            color = AppleFitnessGreen,
            radius = 3.dp.toPx(),
            center = Offset(xOf(values.size - 1), yOf(values.last()))
        )
    }
}

@Composable
private fun DashedTrendPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = AppleGray4,
                cornerRadius = CornerRadius(14.dp.toPx()),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 5.dp.toPx())
                    )
                )
            )
        }
        Text(
            text = "记录 2 次以上就能看到趋势",
            fontSize = 11.sp,
            color = AppleTextSecondary
        )
    }
}

/* ───────────────────────── BMI 一行 ───────────────────────── */

@Composable
private fun BmiRow(bmi: Double) {
    val status = bmiStatus(bmi)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppleGray6)
            .padding(horizontal = 14.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "BMI ${formatMetric(bmi)}，${status.label}范围"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(status.color)
        )
        Text(text = "BMI", fontSize = 11.sp, color = AppleTextSecondary)
        Text(
            text = formatMetric(bmi),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (-0.4).sp,
            color = AppleTextPrimary
        )
        Text(
            text = status.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = status.color
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "18.5–24.0",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = AppleTextSecondary
        )
    }
}

private class BmiStatus(val label: String, val color: Color)

private fun bmiStatus(bmi: Double): BmiStatus = when {
    bmi < 18.5 -> BmiStatus("偏瘦", AppleCautionOrange)
    bmi <= 24.0 -> BmiStatus("正常", AppleFitnessGreen)
    bmi < 28.0 -> BmiStatus("偏胖", AppleCautionOrange)
    else -> BmiStatus("肥胖", AppleSystemRed)
}

/* ───────────────────────── 围度（折叠一行） ───────────────────────── */

@Composable
private fun GirthFold(metric: BodyMetricEntity) {
    var expanded by remember { mutableStateOf(false) }

    val items = listOfNotNull(
        metric.chestCm?.let { "胸围" to it },
        metric.waistCm?.let { "腰围" to it },
        metric.armCm?.let { "臂围" to it }
    )
    val description = buildString {
        append("围度")
        metric.chestCm?.let { append("，胸 ${formatMetric(it)} 厘米") }
        metric.waistCm?.let { append("，腰 ${formatMetric(it)} 厘米") }
        metric.armCm?.let { append("，臂 ${formatMetric(it)} 厘米") }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppleGray6)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .toggleable(
                    value = expanded,
                    role = Role.Button,
                    onValueChange = { expanded = it }
                )
                .padding(horizontal = 14.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    stateDescription = if (expanded) "已展开" else "已收起"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "围度",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = AppleTextPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${items.size} 项 · 最近 ${millisToDay(metric.date).format(monthDayFormat())}",
                fontSize = 11.sp,
                color = AppleTextSecondary
            )
            Icon(
                imageVector = LucideIcons.ChevronRight,
                contentDescription = null,
                tint = AppleTextSecondary,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(if (expanded) 90f else 0f)
            )
        }

        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items.forEach { (label, value) ->
                    Column {
                        Text(text = label, fontSize = 11.sp, color = AppleTextSecondary)
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = formatMetric(value),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = AppleTextPrimary
                            )
                            Text(
                                text = " cm",
                                fontSize = 11.sp,
                                color = AppleTextSecondary
                            )
                        }
                    }
                }
            }
            metric.heightCm?.let { height ->
                Text(
                    text = "身高 ${formatMetric(height)} cm",
                    fontSize = 11.sp,
                    color = AppleTextSecondary,
                    modifier = Modifier.padding(start = 14.dp, bottom = 12.dp)
                )
            }
        }
    }
}

/* ───────────────────────── 最近记录 ───────────────────────── */

@Composable
private fun RecentRecords(history: List<BodyMetricEntity>) {
    val today = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppleGray6)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "最近记录",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextPrimary
            )
            Text(
                text = "共 ${history.size} 条",
                fontSize = 11.sp,
                color = AppleTextSecondary
            )
        }

        history.asReversed().take(4).forEachIndexed { index, metric ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(AppleHairline)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = millisToDay(metric.date)
                        .format(DateTimeFormatter.ofPattern("M/d", Locale.US)),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AppleTextSecondary,
                    modifier = Modifier.width(48.dp)
                )
                Text(
                    text = metric.compactSummary(),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-0.2).sp,
                    color = AppleTextPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = metric.sourceTag(today),
                    fontSize = 11.sp,
                    color = AppleTextSecondary
                )
            }
        }
    }
}

/* ───────────────────────── 底部主按钮 ───────────────────────── */

@Composable
private fun RecordTodayCta(hasTodayRecord: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(29.dp))
            .background(AppleFitnessGreen)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = LucideIcons.Plus,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = if (hasTodayRecord) "修改今日体测" else "记录今日体测",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                letterSpacing = 0.4.sp
            )
        }
    }
}

/* ───────────────────────── 计算 / 小工具 ───────────────────────── */

private const val DASH_VALUE = "--"

private class MetricDelta(
    val diff: Double,
    val previousDate: LocalDate,
    val daysBetween: Long
)

/** 取该指标最近两条记录的差值；只有一条或没有时返回 null（页面就不写「较上次」）。 */
private fun seriesDelta(
    series: List<BodyMetricEntity>,
    pick: (BodyMetricEntity) -> Double?
): MetricDelta? {
    if (series.size < 2) return null
    val last = series.last()
    val previous = series[series.size - 2]
    val lastValue = pick(last) ?: return null
    val previousValue = pick(previous) ?: return null
    val lastDay = millisToDay(last.date)
    val previousDay = millisToDay(previous.date)
    return MetricDelta(
        diff = kotlin.math.round((lastValue - previousValue) * 10) / 10,
        previousDate = previousDay,
        daysBetween = ChronoUnit.DAYS.between(previousDay, lastDay)
    )
}

/** BMI = 体重 / 身高²，保留 1 位；缺任一返回 null（与 BodyProfile.bmi 同一口径）。 */
private fun BodyMetricEntity.bmiValue(): Double? {
    val w = weightKg ?: return null
    val h = heightCm?.takeIf { it > 0 } ?: return null
    val m = h / 100.0
    return kotlin.math.round(w / (m * m) * 10) / 10
}

private fun BodyMetricEntity.hasGirth(): Boolean =
    chestCm != null || waistCm != null || armCm != null

private fun BodyMetricEntity.compactSummary(): String {
    val parts = listOfNotNull(
        weightKg?.let { "${formatMetric(it)}kg" },
        bodyFatPct?.let { "${formatMetric(it)}%" }
    )
    return parts.joinToString(" · ").ifBlank { "--" }
}

/**
 * 来源标记。表里没有 source 字段（只有 date 与各指标），
 * 所以这里只用可得信息：今天 / 记了围度，其余留白而不是瞎猜「Health Connect」。
 */
private fun BodyMetricEntity.sourceTag(today: LocalDate): String = when {
    millisToDay(date) == today -> "今天"
    hasGirth() -> "围度已记"
    else -> ""
}

private fun unitWord(unit: String): String = when (unit) {
    "kg" -> "公斤"
    "%" -> "百分比"
    else -> unit
}

private fun monthDayFormat(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("M月d日", Locale.CHINESE)

private fun millisToDay(dateMillis: Long): LocalDate =
    java.time.Instant.ofEpochMilli(dateMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
