package com.baoji.fitness.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import com.baoji.fitness.data.repository.AiShareContext
import com.baoji.fitness.ui.components.AiSharePreviewBottomSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.core.designsystem.icons.LucideIcons
import com.baoji.fitness.core.theme.AppleBlack
import com.baoji.fitness.core.theme.AppleCautionOrange
import com.baoji.fitness.core.theme.AppleFitnessGreen
import com.baoji.fitness.core.theme.AppleGray4
import com.baoji.fitness.core.theme.AppleGray5
import com.baoji.fitness.core.theme.AppleGray6
import com.baoji.fitness.core.theme.AppleHairline
import com.baoji.fitness.core.theme.AppleStrokeStrong
import com.baoji.fitness.core.theme.AppleSystemRed
import com.baoji.fitness.core.theme.AppleTextPrimary
import com.baoji.fitness.core.theme.AppleTextSecondary
import com.baoji.fitness.domain.algorithm.OverloadCalculator
import com.baoji.fitness.domain.model.PresetTemplates
import com.baoji.fitness.domain.model.WorkoutRoutineTemplate
import com.baoji.fitness.ui.components.ExerciseGroupCard
import com.baoji.fitness.ui.workout.components.RestTimerCapsule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun formatWeight(w: Double): String = if (w % 1.0 == 0.0) w.toInt().toString() else w.toString()

/**
 * 输入面板的物理边界。超出就夹到边界并提示，而不是静默接受 ——
 * 训练中手滑多按一个 0（200 → 2000kg）如果照单全收，这条脏数据会污染 PR 与后续加重建议。
 */
private const val MAX_SET_WEIGHT_KG = 500.0
private const val MAX_SET_REPS = 100

/**
 * 已用时 → `32:15`；超过 1 小时给 `1:05:30`（不显示 `65:30`，那读不出“一小时”）。
 * 纯函数，便于单测。
 */
fun formatElapsed(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%02d:%02d", m, sec)
}

/**
 * 训练中 LazyColumn 里第一个动作卡之前有几个 item：
 * 0 顶部条 / 1 Hero / 2 加重建议条 / 3「今日动作」表头 —— 动作卡从 index 4 开始。
 * 手风琴展开前要 `animateScrollToItem(EXERCISE_ITEM_OFFSET + position)`。
 * 上面增删 item 时必须同步改这个数，否则展开后会把目标行推出屏幕。
 */
private const val EXERCISE_ITEM_OFFSET = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    modifier: Modifier = Modifier
) {
    val activeSession by viewModel.activeSession.collectAsState()
    val exerciseGroups by viewModel.exerciseGroups.collectAsState()
    val allExercises by viewModel.allExercises.collectAsState()
    val restSeconds by viewModel.restSecondsLeft.collectAsState()
    val restTotalSeconds by viewModel.restTotalSeconds.collectAsState()
    val isTimerVisible by viewModel.isRestTimerVisible.collectAsState()
    val todaySummary by viewModel.todaySummary.collectAsState()
    val habit by viewModel.habit.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()

    val haptic = LocalHapticFeedback.current

    var showAddExerciseSheet by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf(false) }
    var showTemplateSheet by remember { mutableStateOf(false) }
    var shareContextToPreview by remember { mutableStateOf<AiShareContext?>(null) }
    var showShareSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var activeEditingSet by remember { mutableStateOf<Pair<ExerciseEntity, WorkoutSetEntity>?>(null) }

    /*
     * 手风琴：同一时刻只允许一个动作展开。
     * rememberSaveable 是为了切 Tab（训练→负荷→训练）回来还停在原来那个动作 ——
     * 组间休息那 90 秒里用户十有八九会去翻负荷页，回来重新找动作是最烦的一步。
     */
    val listState = rememberLazyListState()
    // 换一场训练（activeSession.id 变）就重新决定展开哪个动作，否则会停在上一次的动作 id 上
    var expandedExerciseId by rememberSaveable(activeSession?.id) { mutableStateOf<Long?>(null) }
    var expansionInitialized by rememberSaveable(activeSession?.id) { mutableStateOf(false) }
    LaunchedEffect(exerciseGroups) {
        if (!expansionInitialized && exerciseGroups.isNotEmpty()) {
            // 默认展开第一个还有未完成组的动作；全部完成则全折叠
            expandedExerciseId = exerciseGroups
                .firstOrNull { group -> group.sets.any { !it.isCompleted } }
                ?.exercise?.id
            expansionInitialized = true
        }
    }

    val dateFormat = remember { SimpleDateFormat("M月d日 EEEE", Locale.CHINESE) }
    val currentDateText = remember(activeSession?.startTime) {
        dateFormat.format(Date(activeSession?.startTime ?: System.currentTimeMillis()))
    }
    // 标题括号段在 22sp 下会挤爆右侧按钮，头部只显示短标题（如 "DAY 1 · 推日"），完整名在计划弹层里看
    val headerTitle = (activeSession?.title ?: todaySummary?.title ?: "今日训练").substringBefore("(").trim()

    // 容量口径：热身组不计入 Volume（与 RP 口径一致）；完成组数仍含热身
    val completedSets = exerciseGroups.flatMap { it.sets }.filter { it.isCompleted }
    val totalVolume = completedSets
        .filter { it.setType != "WARMUP" }
        .sumOf { it.weightKg * it.reps }.toInt()
    val totalSetsCount = exerciseGroups.flatMap { it.sets }.size

    // 环形进度与「剩余组数」只认正式组（排除热身），与完成训练时的容量口径保持一致
    val formalSets = exerciseGroups.flatMap { it.sets }.filter { it.setType != "WARMUP" }
    val formalTotalCount = formalSets.size
    val formalCompletedCount = formalSets.count { it.isCompleted }

    // 「下一个」与底部主按钮指向同一个目标：按列表顺序第一个还有未完成组的动作
    val nextGroup = exerciseGroups.firstOrNull { group -> group.sets.any { !it.isCompleted } }
    val nextOpenSet = nextGroup?.sets?.firstOrNull { !it.isCompleted }
    val nextLineText = when {
        nextGroup != null -> "下一个：${nextGroup.exercise.name} · 剩 ${nextGroup.sets.count { !it.isCompleted }} 组"
        exerciseGroups.isNotEmpty() -> "今天全部完成"
        else -> "点上方「计划」开始今日训练"
    }

    // 加重建议：会话首个动作（主项）按双重渐进法，基于前次首组真实基准判定
    val firstGroup = exerciseGroups.firstOrNull()
    val prevFirstSet = firstGroup?.previousSessionSets?.get(1)
        ?: firstGroup?.previousSessionSets?.values?.maxByOrNull { it.weightKg }
    val suggestionText = when {
        firstGroup == null -> "点左上角「计划」开始今日训练"
        prevFirstSet != null && prevFirstSet.weightKg > 0 -> {
            val advice = OverloadCalculator.evaluateProgression(prevFirstSet.weightKg, prevFirstSet.reps)
            "${firstGroup.exercise.name}｜${advice.message}"
        }
        else -> "${firstGroup.exercise.name}｜首次训练，打勾直接采用，重量次数自动带到下一组"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppleBlack)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header Bar
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentDateText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleFitnessGreen,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = headerTitle,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppleTextPrimary,
                            letterSpacing = (-0.5).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Switch Template Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(AppleGray5)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showTemplateSheet = true
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = LucideIcons.Calendar,
                                    contentDescription = null,
                                    tint = AppleFitnessGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "计划",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppleFitnessGreen
                                )
                            }
                        }

                        // Reset button to immediately clean any buggy/duplicate state
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(AppleGray5)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.resetCurrentSession()
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "重置",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppleTextSecondary
                            )
                        }

                        if (activeSession != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AppleFitnessGreen)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showSummaryDialog = true
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "完成",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        } else if (todaySummary != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AppleFitnessGreen)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        scope.launch {
                                            todaySummary?.let {
                                                shareContextToPreview = viewModel.getAiShareContextForDetail(it)
                                                showShareSheet = true
                                            }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = LucideIcons.Sparkles,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "分享给 AI",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. 主仪表（B 稿 Hero）：环形进度 + 三块读数 + 下一件事
            item(key = "hero") {
                if (activeSession == null) {
                    // 归档态：沿用今日已完成训练的真实数据（不虚构进度环）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(AppleGray6)
                            .border(1.dp, AppleHairline, RoundedCornerShape(20.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        MetricItem(
                            label = "累计容量",
                            value = "${todaySummary?.totalVolumeKg?.toInt() ?: 0}",
                            unit = "kg"
                        )
                        MetricItem(
                            label = "完成组数",
                            value = "${todaySummary?.groups?.sumOf { it.sets.size } ?: 0}",
                            unit = "组",
                            valueColor = AppleFitnessGreen
                        )
                    }
                } else {
                    WorkoutHeroCard(
                        completedSets = formalCompletedCount,
                        totalSets = formalTotalCount,
                        volumeKg = totalVolume,
                        elapsedText = formatElapsed(elapsedSeconds),
                        remainingSets = formalTotalCount - formalCompletedCount,
                        nextLine = nextLineText
                    )
                }
            }

            // 3. 双重渐进建议条（真实数据：主项前次基准 → evaluateProgression）
            if (activeSession != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(AppleGray6)
                        .border(1.dp, AppleHairline, RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(AppleGray5),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = LucideIcons.Sparkles,
                            contentDescription = null,
                            tint = AppleFitnessGreen,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = suggestionText,
                        fontSize = 12.sp,
                        color = AppleTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            }

            // 归档态：当天已完成训练 → 展示明细；次日尚未训练 → 提示开始新一轮
            if (activeSession == null) {
                val summary = todaySummary
                if (summary != null) {
                    // M2：今天练完 → 「连续 13 天，明天是休息日」
                    if (habit.currentStreak > 0) {
                        item(key = "archived-habit") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(AppleGray6)
                                    .border(1.dp, AppleHairline, RoundedCornerShape(18.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "🔥", fontSize = 13.sp)
                                Text(
                                    text = buildString {
                                        append("连续 ${habit.currentStreak} 天")
                                        append(
                                            if (habit.isRestDayTomorrow) "，明天是休息日"
                                            else "，明天接着练"
                                        )
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppleFitnessGreen
                                )
                            }
                        }
                    }
                    item(key = "archived-note") {
                        summary.notes.takeIf { it.isNotBlank() }?.let { note ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(AppleGray6)
                                    .border(1.dp, AppleHairline, RoundedCornerShape(18.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "笔记", fontSize = 12.sp, color = AppleTextSecondary)
                                Text(text = note, fontSize = 12.sp, color = AppleTextPrimary)
                            }
                        }
                    }
                    summary.groups.forEach { group ->
                        item(key = "archived-${group.exerciseName}") {
                            ExerciseGroupCard(group)
                        }
                    }
                } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(AppleGray6)
                            .border(1.dp, AppleHairline, RoundedCornerShape(24.dp))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(AppleFitnessGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = LucideIcons.Dumbbell,
                                contentDescription = null,
                                tint = AppleFitnessGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "今天还没开始训练",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppleTextPrimary
                            )
                            Text(
                                text = "选择下方训练日，随时开启新一轮训练",
                                fontSize = 12.sp,
                                color = AppleTextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PresetTemplates.all.forEach { template ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(AppleGray5)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.startNewWorkoutFromTemplate(template)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = template.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppleTextPrimary
                                        )
                                        Text(
                                            text = "${template.focusSplit} · 沿用上次动作与基准",
                                            fontSize = 11.sp,
                                            color = AppleTextSecondary
                                        )
                                    }
                                    Icon(
                                        imageVector = LucideIcons.ChevronRight,
                                        contentDescription = null,
                                        tint = AppleFitnessGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }

            // 4. 今日动作表头（B 稿：动作数 + 组数一句话交代完）
            if (activeSession != null && exerciseGroups.isNotEmpty()) {
                item(key = "exercise-list-header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "今日动作",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleTextPrimary
                        )
                        Text(
                            text = "${exerciseGroups.size} 个动作 · $totalSetsCount 组",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AppleTextSecondary
                        )
                    }
                }
            }

            // 5. Exercise Cards List（手风琴，key 用 "ex-${id}"）
            itemsIndexed(
                exerciseGroups,
                key = { _, group -> "ex-${group.exercise.id}" }
            ) { position, group ->
                val isExpanded = expandedExerciseId == group.exercise.id
                ExerciseCard(
                    index = position + 1,
                    expanded = isExpanded,
                    onToggleExpand = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        // 先滚到该动作表头，再切展开态：展开会把后面的卡推下去，
                        // 反过来做的话目标行会被推走，手指落在别的动作上
                        if (!isExpanded) {
                            scope.launch {
                                listState.animateScrollToItem(EXERCISE_ITEM_OFFSET + position)
                                expandedExerciseId = group.exercise.id
                            }
                        } else {
                            expandedExerciseId = null
                        }
                    },
                    group = group,
                    onToggleSet = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleSetCompletion(it)
                    },
                    onOpenSetEditor = { set ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        activeEditingSet = Pair(group.exercise, set)
                    },
                    onAddSet = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.addSet(group.exercise.id)
                    },
                    onRemoveExercise = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.removeExerciseFromSession(group.exercise.id)
                    }
                )
            }

            // 6. Add Exercise Button
            if (activeSession != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppleGray6)
                        .border(1.dp, AppleHairline, RoundedCornerShape(20.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showAddExerciseSheet = true
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = LucideIcons.Plus,
                            contentDescription = null,
                            tint = AppleFitnessGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "从动作库添加动作",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleFitnessGreen
                        )
                    }
                }
            }
            }

            // Bottom Spacing for Tab Bar and Rest Timer
            item {
                Spacer(modifier = Modifier.height(110.dp))
            }
        }

        // Floating Rest Timer Capsule
        RestTimerCapsule(
            visible = isTimerVisible,
            secondsLeft = restSeconds,
            totalSeconds = restTotalSeconds,
            onAdd30s = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.add30sRest()
            },
            onSkip = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.skipRestTimer()
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        /*
         * 底部常驻主操作：与休息胶囊互斥，共用同一个底部槽位。
         * 组行距屏底约 562dp（393×852），是必须换手的伸展区，而「完成第 N 组」
         * 一天要点几十次 —— 所以它必须常驻在拇指舒适区内。
         * 导航栏 inset 已由 MainScreen 的 Scaffold innerPadding 吃掉，这里不再叠加，
         * 否则按钮会被顶高一整条导航栏。
         */
        if (activeSession != null) {
            AnimatedVisibility(
                visible = !isTimerVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(58.dp)
                        .clip(RoundedCornerShape(29.dp))
                        .background(AppleFitnessGreen)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val target = nextOpenSet
                            if (target != null) {
                                // 等价于勾选当前第一个未完成组（走同一条 ViewModel 路径）
                                viewModel.toggleSetCompletion(target)
                            } else {
                                // 全部完成 → 复用现有完成弹窗（含备注与分享给 AI）
                                showSummaryDialog = true
                            }
                        }
                        .semantics {
                            contentDescription = if (nextOpenSet != null) {
                                "完成${nextGroup?.exercise?.name ?: ""}第 ${nextOpenSet.setOrder} 组，" +
                                    "${formatWeight(nextOpenSet.weightKg)} 公斤 ${nextOpenSet.reps} 次"
                            } else {
                                "完成训练并归档"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = nextOpenSet?.let { "完成第 ${it.setOrder} 组" } ?: "完成训练",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }

        // Add Exercise Bottom Sheet
        if (showAddExerciseSheet) {
            AddExerciseBottomSheet(
                allExercises = allExercises,
                onDismiss = { showAddExerciseSheet = false },
                onExerciseSelected = { exerciseId ->
                    viewModel.addExerciseToSession(exerciseId)
                    showAddExerciseSheet = false
                }
            )
        }

        // Quick Set Editor Bottom Sheet (Solves all typing & glitch issues in gym)
        activeEditingSet?.let { (exercise, set) ->
            QuickAdjustSetBottomSheet(
                exerciseName = exercise.name,
                set = set,
                trackWeight = exercise.trackWeight,
                onDismiss = { activeEditingSet = null },
                onSave = { updatedWeight, updatedReps, updatedType, updatedRpe ->
                    viewModel.applySetEdit(set, updatedWeight, updatedReps, updatedType, updatedRpe)
                    activeEditingSet = null
                },
                onDelete = {
                    viewModel.deleteSet(set)
                    activeEditingSet = null
                }
            )
        }

        // Workout Completion Summary Dialog
        if (showSummaryDialog) {
            WorkoutSummaryDialog(
                totalVolumeKg = totalVolume,
                completedSetsCount = completedSets.size,
                onConfirm = { notes ->
                    viewModel.finishCurrentWorkout(notes)
                    showSummaryDialog = false
                },
                onShareToAi = { notes ->
                    scope.launch {
                        val ctx = viewModel.getAiShareContextForCurrent(notes)
                        viewModel.finishCurrentWorkout(notes)
                        showSummaryDialog = false
                        shareContextToPreview = ctx
                        showShareSheet = true
                    }
                },
                onDismiss = { showSummaryDialog = false }
            )
        }

        // AI Share Preview Bottom Sheet
        if (showShareSheet) {
            AiSharePreviewBottomSheet(
                shareContext = shareContextToPreview,
                onDismiss = {
                    showShareSheet = false
                    shareContextToPreview = null
                }
            )
        }

        // Routine Template Selection Bottom Sheet
        if (showTemplateSheet) {
            RoutineTemplateBottomSheet(
                onDismiss = { showTemplateSheet = false },
                onSelectTemplate = { template ->
                    viewModel.startNewWorkoutFromTemplate(template)
                    showTemplateSheet = false
                },
                onStartCustom = { title ->
                    viewModel.startCustomWorkout(title)
                    showTemplateSheet = false
                }
            )
        }
    }
}

@Composable
private fun WorkoutHeroCard(
    completedSets: Int,
    totalSets: Int,
    volumeKg: Int,
    elapsedText: String,
    remainingSets: Int,
    nextLine: String
) {
    val progress = if (totalSets > 0) completedSets.toFloat() / totalSets else 0f
    // 2/12 → 17%（设计稿按四舍五入显示，截断会显示 16% 与环的视觉比例对不上）
    val percent = if (totalSets > 0) (completedSets * 100.0 / totalSets).roundToInt() else 0
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "heroRing")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppleGray6)
            .border(1.dp, AppleHairline, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 环形进度：72dp / 6dp 描边。环中央两行，别再加第三行 —— 72dp 放不下
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .semantics {
                        // ProgressBarRangeInfo 的 range 不能是空区间（0..0 会直接抛异常），
                        // 所以分母兜到 1；读屏文案仍报真实组数
                        val safeTotal = totalSets.coerceAtLeast(1)
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = completedSets.toFloat(),
                            range = 0f..safeTotal.toFloat(),
                            steps = safeTotal
                        )
                        contentDescription = "今日进度，共 $totalSets 组，已完成 $completedSets 组"
                    },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(72.dp),
                    color = AppleFitnessGreen,
                    trackColor = AppleGray5,
                    strokeWidth = 6.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$completedSets/$totalSets",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = AppleTextPrimary
                    )
                    Text(
                        text = "组 · $percent%",
                        fontSize = 11.sp,
                        color = AppleTextSecondary
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HeroReadout(label = "累计容量", value = "$volumeKg", unit = "kg")
                HeroReadout(label = "本次用时", value = elapsedText, unit = null)
                HeroReadout(
                    label = "剩余组数",
                    value = "$remainingSets",
                    unit = null,
                    highlight = remainingSets > 0
                )
            }
        }

        HorizontalDivider(color = AppleHairline, thickness = 0.5.dp)

        Text(
            text = nextLine,
            fontSize = 12.sp,
            color = AppleTextSecondary
        )
    }
}

/** Hero 右侧一行读数：左标签 12sp 次要色，右数值 17sp 等宽。 */
@Composable
private fun HeroReadout(
    label: String,
    value: String,
    unit: String?,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(text = label, fontSize = 12.sp, color = AppleTextSecondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (highlight) AppleFitnessGreen else AppleTextPrimary
            )
            unit?.let {
                Text(
                    text = " $it",
                    fontSize = 11.sp,
                    color = AppleTextSecondary
                )
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    unit: String? = null,
    valueColor: Color = AppleTextPrimary
) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            color = AppleTextSecondary
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = valueColor
            )
            unit?.let {
                Text(
                    text = " $it",
                    fontSize = 11.sp,
                    color = AppleTextSecondary
                )
            }
        }
    }
}

/**
 * 动作卡（B 稿手风琴）。
 *
 * 折叠态只有一行 48dp：序号 + 动作名 + 肌群 + 进度 + chevron。
 * 展开态才铺开组行 —— 训练时 90% 的时间只在「当前那个动作」上操作，
 * 五个动作全部铺开时组行会被推到屏幕外的伸展区。
 */
@Composable
private fun ExerciseCard(
    index: Int,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    group: ExerciseGroup,
    onToggleSet: (WorkoutSetEntity) -> Unit,
    onOpenSetEditor: (WorkoutSetEntity) -> Unit,
    onAddSet: () -> Unit,
    onRemoveExercise: () -> Unit
) {
    val benchmarkSummary = group.benchmarkSummary
    val benchmarkSub = if (benchmarkSummary != "-") {
        "基准参考: $benchmarkSummary" + if (group.benchmark1RM > 0.0) " · 估算1RM ${formatWeight(group.benchmark1RM)} kg" else ""
    } else {
        "基准参考: 首次记录 · 暂无历史数据"
    }

    val completedCount = group.sets.count { it.isCompleted }
    val allDone = group.sets.isNotEmpty() && completedCount == group.sets.size
    val progressText = when {
        completedCount == 0 -> "未开始"
        else -> "$completedCount/${group.sets.size}"
    }
    val progressColor = when {
        allDone -> AppleFitnessGreen
        completedCount == 0 -> AppleTextSecondary
        else -> AppleTextPrimary
    }
    // ChevronRight 指向右；转 90° 朝下（折叠），转 -90° 朝上（展开）
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) -90f else 90f,
        label = "chevron"
    )

    val shape = RoundedCornerShape(if (expanded) 16.dp else 14.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppleGray6)
            .border(1.dp, if (expanded) AppleHairline else AppleHairline.copy(alpha = 0.6f), shape)
            // 展开态左侧 3dp 绿条：画在整张卡上（含表头行）。
            // 用 drawBehind 而不是 fillMaxHeight —— 后者在 wrap-content 的卡片里没有有界高度，会塌成 0
            .drawBehind {
                if (expanded) {
                    drawRect(color = AppleFitnessGreen, size = Size(3.dp.toPx(), size.height))
                }
            }
    ) {
        // 表头行：折叠 48dp / 展开 56dp，两种状态都是可点区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (expanded) 56.dp else 48.dp)
                .clickable(
                    onClickLabel = if (expanded) "收起" else "展开",
                    onClick = onToggleExpand
                )
                .padding(start = if (expanded) 15.dp else 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 20.dp, height = 20.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (expanded) AppleFitnessGreen else AppleGray5),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$index",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = if (expanded) Color.Black else AppleTextSecondary
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 名称可截断，肌群标签永远可见：非定宽子项先量，weight 子项吃剩余
                Text(
                    text = group.exercise.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = group.exercise.targetMuscle,
                    fontSize = 12.sp,
                    color = AppleTextSecondary,
                    maxLines = 1
                )
            }

            Text(
                text = progressText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = progressColor
            )

            Icon(
                imageVector = LucideIcons.ChevronRight,
                contentDescription = null,
                tint = AppleTextSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevronAngle)
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 15.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = benchmarkSub,
                    fontSize = 11.sp,
                    color = AppleTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                ColumnLabelRow(trackWeight = group.exercise.trackWeight)

                // 组行：key 让勾选/编辑后不错位（折叠切换时列表会重排）
                group.sets.forEach { set ->
                    val prevSet = group.previousSessionSets[set.setOrder]
                    val trackW = group.exercise.trackWeight
                    val setBenchmarkText = when {
                        prevSet == null -> "-"
                        trackW && prevSet.weightKg > 0.0 -> "${formatWeight(prevSet.weightKg)} × ${prevSet.reps}"
                        !trackW -> "× ${prevSet.reps}"
                        else -> "-"
                    }
                    key("set-${set.id}") {
                        SetRowItem(
                            set = set,
                            benchmarkText = setBenchmarkText,
                            trackWeight = trackW,
                            onToggle = { onToggleSet(set) },
                            onOpenEditor = { onOpenSetEditor(set) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClickLabel = "添加一组", onClick = onAddSet)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = LucideIcons.Plus,
                            contentDescription = null,
                            tint = AppleFitnessGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "添加一组",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleFitnessGreen
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClickLabel = "从今日训练移除该动作", onClick = onRemoveExercise)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "移除动作",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppleSystemRed.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

/** 组行表头：列宽必须和 [SetRowItem] 完全一致，否则数字看起来不齐会显得很廉价。 */
@Composable
private fun ColumnLabelRow(trackWeight: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "组",
            fontSize = 11.sp,
            color = AppleTextSecondary,
            modifier = Modifier.width(22.dp),
            textAlign = TextAlign.Center
        )
        Text(
            text = "参考基准",
            fontSize = 11.sp,
            color = AppleTextSecondary,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = if (trackWeight) "重量" else "负重",
            fontSize = 11.sp,
            color = AppleTextSecondary,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.Center
        )
        Text(
            text = "次数",
            fontSize = 11.sp,
            color = AppleTextSecondary,
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.Center
        )
        Text(
            text = "完成",
            fontSize = 11.sp,
            color = AppleTextSecondary,
            modifier = Modifier.width(52.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SetRowItem(
    set: WorkoutSetEntity,
    benchmarkText: String,
    trackWeight: Boolean = true,
    onToggle: () -> Unit,
    onOpenEditor: () -> Unit
) {
    val rowBg = if (set.isCompleted) AppleGray5.copy(alpha = 0.45f) else Color.Transparent

    val typeLabel = when (set.setType) {
        "WARMUP" -> "W${set.setOrder}"
        "DROP" -> "D${set.setOrder}"
        "FAILURE" -> "F${set.setOrder}"
        else -> "${set.setOrder}"
    }

    // 整个动作的重量/次数对读屏是一句话：分组「第 3 组，40 公斤 8 次」比「40」「8」可用得多
    val weightText = if (trackWeight) formatWeight(set.weightKg) else "自重"
    val setDescription = "第 ${set.setOrder} 组，$weightText ${set.reps} 次"

    /*
     * 行高 52dp 定死，所有可点元素的外层容器一律 48dp 高：
     * 训练时手上带汗带粉，26dp 的 pill 是误触/漏触高发区。
     * 注意不能用 Modifier.minimumInteractiveComponentSize() —— 它会给定高行加最小尺寸，
     * 把 52dp 的行撑变形。
     */
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(rowBg)
            .border(1.dp, AppleHairline.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. 序号 / 组类型徽标 —— 视觉 22dp 宽，热区 48dp 高
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = "调整第 ${set.setOrder} 组", onClick = onOpenEditor)
                .semantics { contentDescription = "$setDescription，点按调整" },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = typeLabel,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = if (set.isCompleted) AppleFitnessGreen else AppleTextSecondary
            )
        }

        // 2. 参考基准（前次成绩），只读，不参与点击
        Text(
            text = benchmarkText,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = AppleTextSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        // 3. 重量 pill —— 热区 48dp 高，视觉 32dp
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = "修改重量", onClick = onOpenEditor),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppleGray5),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (trackWeight) formatWeight(set.weightKg) else "自重",
                    fontSize = if (trackWeight) 17.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = AppleTextPrimary,
                    maxLines = 1
                )
                if (trackWeight) {
                    Text(
                        text = " kg",
                        fontSize = 11.sp,
                        color = AppleTextSecondary,
                        maxLines = 1
                    )
                }
            }
        }

        // 4. 次数 pill —— 热区 48dp 高，视觉 32dp
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = "修改次数", onClick = onOpenEditor),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppleGray5),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${set.reps}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = AppleTextPrimary,
                    maxLines = 1
                )
                Text(
                    text = " 次",
                    fontSize = 11.sp,
                    color = AppleTextSecondary,
                    maxLines = 1
                )
            }
        }

        // 5. 勾选 —— 视觉圆环 26dp，热区 52×48dp
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .toggleable(
                    value = set.isCompleted,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() }
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = setDescription
                    stateDescription = if (set.isCompleted) "已完成" else "未完成"
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (set.isCompleted) AppleFitnessGreen else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        // 未勾选圆环用 35% 白：10% 白在黑底上几乎看不见
                        color = if (set.isCompleted) AppleFitnessGreen else AppleStrokeStrong,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (set.isCompleted) {
                    Icon(
                        imageVector = LucideIcons.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAdjustSetBottomSheet(
    exerciseName: String,
    set: WorkoutSetEntity,
    trackWeight: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (Double, Int, String, Double?) -> Unit,
    onDelete: () -> Unit
) {
    // 文本态与落库值分开：允许输入中途的空串/半个小数，但落库只认夹逼后的合法值
    var currentWeight by remember(set.weightKg) {
        mutableStateOf(set.weightKg.coerceIn(0.0, MAX_SET_WEIGHT_KG))
    }
    var currentReps by remember(set.reps) { mutableStateOf(set.reps.coerceIn(0, MAX_SET_REPS)) }
    var currentType by remember(set.setType) { mutableStateOf(set.setType) }
    // M3a：RPE 默认不填（null）。只作记录与 M4 上下文，不参与双重渐进判定。
    var currentRpe by remember(set.rpe) { mutableStateOf(set.rpe) }

    var weightField by remember(set.weightKg) {
        val text = formatWeight(set.weightKg.coerceIn(0.0, MAX_SET_WEIGHT_KG))
        // 进来就全选：健身房最常见的操作是「直接打一个新数字」，不是定位光标
        mutableStateOf(TextFieldValue(text, selection = TextRange(0, text.length)))
    }
    var repsField by remember(set.reps) {
        val text = "${set.reps.coerceIn(0, MAX_SET_REPS)}"
        mutableStateOf(TextFieldValue(text, selection = TextRange(0, text.length)))
    }
    var weightHint by remember(set.weightKg) { mutableStateOf<String?>(null) }
    var repsHint by remember(set.reps) { mutableStateOf<String?>(null) }

    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val weightFocus = remember { FocusRequester() }
    val repsFocus = remember { FocusRequester() }

    fun onWeightText(new: TextFieldValue) {
        // Decimal 键盘在部分机型仍会给出逗号或负号，这里统一过滤成「数字 + 至多一个点」
        val filtered = buildString {
            var hasDot = false
            new.text.forEach { c ->
                when {
                    c.isDigit() -> append(c)
                    (c == '.' || c == ',') && !hasDot -> {
                        append('.')
                        hasDot = true
                    }
                }
            }
        }
        val selection = TextRange(
            new.selection.start.coerceIn(0, filtered.length),
            new.selection.end.coerceIn(0, filtered.length)
        )
        val parsed = filtered.toDoubleOrNull()
        when {
            parsed == null -> {
                // 空串/只有小数点：让他继续打，落库值先按 0
                weightField = TextFieldValue(filtered, selection)
                currentWeight = 0.0
                weightHint = null
            }
            parsed > MAX_SET_WEIGHT_KG -> {
                val clamped = formatWeight(MAX_SET_WEIGHT_KG)
                weightField = TextFieldValue(clamped, selection = TextRange(clamped.length))
                currentWeight = MAX_SET_WEIGHT_KG
                weightHint = "单组上限 ${MAX_SET_WEIGHT_KG.toInt()}kg"
            }
            else -> {
                weightField = TextFieldValue(filtered, selection)
                currentWeight = parsed
                weightHint = null
            }
        }
    }

    fun onRepsText(new: TextFieldValue) {
        val filtered = new.text.filter { it.isDigit() }
        val selection = TextRange(
            new.selection.start.coerceIn(0, filtered.length),
            new.selection.end.coerceIn(0, filtered.length)
        )
        val parsed = filtered.toIntOrNull()
        when {
            parsed == null -> {
                repsField = TextFieldValue(filtered, selection)
                currentReps = 0
                repsHint = null
            }
            parsed > MAX_SET_REPS -> {
                val clamped = "$MAX_SET_REPS"
                repsField = TextFieldValue(clamped, selection = TextRange(clamped.length))
                currentReps = MAX_SET_REPS
                repsHint = "单组上限 $MAX_SET_REPS 次"
            }
            else -> {
                repsField = TextFieldValue(filtered, selection)
                currentReps = parsed
                repsHint = null
            }
        }
    }

    LaunchedEffect(Unit) {
        // 等两帧再要焦点：ModalBottomSheet 的内容挂载 + 窗口拿焦点都需要时间，
        // 太早调用 requestFocus 会抛 IllegalStateException（FocusRequester 还没 attach）
        withFrameNanos { }
        withFrameNanos { }
        runCatching { if (trackWeight) weightFocus.requestFocus() else repsFocus.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppleGray6,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(AppleGray4)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                // 现在进面板就自动聚焦、弹数字键盘（enableEdgeToEdge 下 adjustResize 不再生效，
                // 不吃 IME inset 的话键盘会盖住下半截面板）
                .imePadding()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = exerciseName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleTextPrimary
                    )
                    Text(
                        text = "调整第 ${set.setOrder} 组" + if (trackWeight) "负荷与次数" else "次数",
                        fontSize = 12.sp,
                        color = AppleTextSecondary
                    )
                }

                // Delete Set Action
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppleSystemRed.copy(alpha = 0.12f))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDelete()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "删除此组",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleSystemRed
                    )
                }
            }

            // Set Type Segmented Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppleGray5)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("NORMAL" to "正式组", "WARMUP" to "热身", "DROP" to "递减", "FAILURE" to "力竭").forEach { (type, label) ->
                    val isSelected = (currentType == type)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            // 44dp：组类型是训练中要频繁改的开关，24dp 高按不准
                            .height(44.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentType = type
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else AppleTextSecondary
                        )
                    }
                }
            }

            // 1. Weight Stepper Control（自重动作如悬垂举腿直接隐藏整段，仅记次数）
            if (trackWeight) Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppleGray5)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "负荷重量", fontSize = 12.sp, color = AppleTextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(104.dp)
                                .clip(RoundedCornerShape(10.dp))
                                // 输入框边框用 35% 白：10% 白在黑底上等于没有边框
                                .border(1.dp, AppleStrokeStrong, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            BasicTextField(
                                value = weightField,
                                onValueChange = { onWeightText(it) },
                                textStyle = TextStyle(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = AppleFitnessGreen,
                                    textAlign = TextAlign.End
                                ),
                                cursorBrush = SolidColor(AppleFitnessGreen),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(weightFocus)
                                    .semantics {
                                        contentDescription = "第 ${set.setOrder} 组重量，单位公斤，可输入"
                                    }
                            )
                        }
                        Text(
                            text = " kg",
                            fontSize = 12.sp,
                            color = AppleTextSecondary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                weightHint?.let { hint ->
                    Text(text = hint, fontSize = 11.sp, color = AppleCautionOrange)
                }

                // Quick Weight Increment / Decrement Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(-5.0, -2.5, -1.25, 1.25, 2.5, 5.0).forEach { delta ->
                        val label = if (delta > 0) "+${formatWeight(delta)}" else formatWeight(delta)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppleGray6)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val next = (currentWeight + delta)
                                        .coerceIn(0.0, MAX_SET_WEIGHT_KG)
                                    val stepped = (next * 10).toInt() / 10.0
                                    currentWeight = stepped
                                    val text = formatWeight(stepped)
                                    weightField = TextFieldValue(text, selection = TextRange(text.length))
                                    weightHint = if (next >= MAX_SET_WEIGHT_KG) {
                                        "单组上限 ${MAX_SET_WEIGHT_KG.toInt()}kg"
                                    } else {
                                        null
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppleTextPrimary
                            )
                        }
                    }
                }
            }

            // 2. Reps Stepper Control
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppleGray5)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "完成次数", fontSize = 12.sp, color = AppleTextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(76.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, AppleStrokeStrong, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            BasicTextField(
                                value = repsField,
                                onValueChange = { onRepsText(it) },
                                textStyle = TextStyle(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = AppleTextPrimary,
                                    textAlign = TextAlign.End
                                ),
                                cursorBrush = SolidColor(AppleFitnessGreen),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(repsFocus)
                                    .semantics {
                                        contentDescription = "第 ${set.setOrder} 组次数，可输入"
                                    }
                            )
                        }
                        Text(
                            text = " 次",
                            fontSize = 12.sp,
                            color = AppleTextSecondary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                repsHint?.let { hint ->
                    Text(text = hint, fontSize = 11.sp, color = AppleCautionOrange)
                }

                // Quick Reps Steppers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(-5, -2, -1, 1, 2, 5).forEach { delta ->
                        val label = if (delta > 0) "+$delta" else "$delta"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppleGray6)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val next = (currentReps + delta).coerceIn(0, MAX_SET_REPS)
                                    currentReps = next
                                    val text = "$next"
                                    repsField = TextFieldValue(text, selection = TextRange(text.length))
                                    repsHint = if (next >= MAX_SET_REPS) "单组上限 $MAX_SET_REPS 次" else null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppleTextPrimary
                            )
                        }
                    }
                }
            }

            // 3. RPE（主观疲劳度）—— 可选，默认不填
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppleGray5)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "RPE 主观疲劳度", fontSize = 12.sp, color = AppleTextSecondary)
                        Text(
                            text = "可选 · 只记录不参与加重判定",
                            fontSize = 11.sp,
                            color = AppleTextSecondary
                        )
                    }
                    Text(
                        text = currentRpe?.let { "RPE ${formatWeight(it)}" } ?: "未填",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (currentRpe != null) AppleFitnessGreen else AppleTextSecondary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(6.0, 7.0, 8.0, 9.0, 10.0).forEach { value ->
                        val isSelected = currentRpe == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color.White else AppleGray6)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentRpe = if (isSelected) null else value
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = formatWeight(value),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else AppleTextSecondary
                            )
                        }
                    }
                }
            }

            // Confirm Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppleFitnessGreen)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSave(currentWeight, currentReps, currentType, currentRpe)
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "确认并应用",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExerciseBottomSheet(
    allExercises: List<ExerciseEntity>,
    onDismiss: () -> Unit,
    onExerciseSelected: (Long) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedMuscle by remember { mutableStateOf("全部") }

    val muscles = listOf("全部", "胸部", "背部", "腿部", "肩部", "手臂")

    val filtered = allExercises.filter { ex ->
        val matchesMuscle = (selectedMuscle == "全部" || ex.targetMuscle == selectedMuscle)
        val matchesSearch = (searchQuery.isBlank() || ex.name.contains(searchQuery, ignoreCase = true))
        matchesMuscle && matchesSearch
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppleGray6,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(AppleGray4)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "添加动作至今日训练",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary
            )

            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppleGray5)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(color = AppleTextPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(AppleFitnessGreen),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "搜索动作名称或部位...",
                                color = AppleTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }

            // Muscle category pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                muscles.forEach { muscle ->
                    val isSelected = (muscle == selectedMuscle)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color.White else AppleGray5)
                            .clickable { selectedMuscle = muscle }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = muscle,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else AppleTextSecondary
                        )
                    }
                }
            }

            // Exercise List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered) { exercise ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onExerciseSelected(exercise.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = exercise.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppleTextPrimary
                            )
                            Text(
                                text = "${exercise.targetMuscle} · ${exercise.equipment}",
                                fontSize = 11.sp,
                                color = AppleTextSecondary
                            )
                        }

                        Icon(
                            imageVector = LucideIcons.Plus,
                            contentDescription = null,
                            tint = AppleFitnessGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    HorizontalDivider(color = AppleHairline, thickness = 0.5.dp)
                }
            }
        }
    }
}

/**
 * 完成训练弹窗。M3a：确认前加一句「今天状态如何？」写进 `workout_sessions.notes`，
 * 这份自述会出现在 M4a 的本地私教总结里。
 */
@Composable
private fun WorkoutSummaryDialog(
    totalVolumeKg: Int,
    completedSetsCount: Int,
    onConfirm: (String) -> Unit,
    onShareToAi: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var noteText by remember { mutableStateOf("") }
    val presets = listOf("状态好", "一般", "累", "睡眠不足")

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(AppleGray6)
                .border(1.dp, AppleHairline, RoundedCornerShape(28.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Checkmark Trophy Badge
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(AppleFitnessGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = LucideIcons.Check,
                    contentDescription = null,
                    tint = AppleFitnessGreen,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "训练圆满完成！",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleTextPrimary
                )
                Text(
                    text = "数据已同步沉淀至渐进负荷模型",
                    fontSize = 12.sp,
                    color = AppleTextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Summary Stats Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppleGray5)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // 两列都用 weight(1f)：标签从 10sp 提到 12sp 后两列宽度若按内容算会左右不等，
                // weight 定死半宽、内容居中，视觉上才是真对齐
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "总容量", fontSize = 12.sp, color = AppleTextSecondary)
                    Text(
                        text = "$totalVolumeKg kg",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = AppleFitnessGreen
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "完成组数", fontSize = 12.sp, color = AppleTextSecondary)
                    Text(
                        text = "$completedSetsCount 组",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = AppleTextPrimary
                    )
                }
            }

            // M3a：今天状态如何？（一行输入 + 预设 chips）→ 写入会话备注
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppleGray5)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "今天状态如何？",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppleGray6)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (noteText.isEmpty()) {
                        Text(
                            text = "记一句感受，私教会读它",
                            fontSize = 12.sp,
                            color = AppleTextSecondary
                        )
                    }
                    BasicTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        textStyle = TextStyle(fontSize = 12.sp, color = AppleTextPrimary),
                        cursorBrush = SolidColor(AppleFitnessGreen),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        val isSelected = noteText == preset
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color.White else AppleGray6)
                                .clickable { noteText = if (isSelected) "" else preset }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = preset,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.Black else AppleTextSecondary
                            )
                        }
                    }
                }
            }

            // Actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 主推荐操作：保存并分享给 AI (Muse)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppleFitnessGreen)
                        .clickable { onShareToAi(noteText) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = LucideIcons.Sparkles,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "保存并分享给 AI (Muse)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppleGray5)
                            .clickable { onDismiss() }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "继续训练",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppleTextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppleGray5)
                            .clickable { onConfirm(noteText) }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "仅保存归档",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleTextPrimary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineTemplateBottomSheet(
    onDismiss: () -> Unit,
    onSelectTemplate: (WorkoutRoutineTemplate) -> Unit,
    onStartCustom: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var customTitle by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppleGray6,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppleGray4)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "选择训练日",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleTextPrimary
                    )
                    Text(
                        text = "动作组合将根据你上一次的实际训练自动更新记忆",
                        fontSize = 12.sp,
                        color = AppleFitnessGreen,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Preset templates list
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PresetTemplates.all.forEach { template ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppleGray5)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectTemplate(template)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = template.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppleTextPrimary
                            )
                            Text(
                                text = "${template.focusSplit} · 沿用上次动作与基准",
                                fontSize = 11.sp,
                                color = AppleTextSecondary
                            )
                        }
                        Icon(
                            imageVector = LucideIcons.ChevronRight,
                            contentDescription = null,
                            tint = AppleFitnessGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Custom workout section
            HorizontalDivider(color = AppleHairline, thickness = 0.5.dp)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "新建自定义训练日",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppleGray5)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        if (customTitle.isEmpty()) {
                            Text(
                                text = "输入训练名称 (如: 肩臂专项)",
                                fontSize = 13.sp,
                                color = AppleTextSecondary
                            )
                        }
                        BasicTextField(
                            value = customTitle,
                            onValueChange = { customTitle = it },
                            textStyle = TextStyle(
                                fontSize = 13.sp,
                                color = AppleTextPrimary
                            ),
                            cursorBrush = SolidColor(AppleFitnessGreen),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (customTitle.isNotBlank()) AppleFitnessGreen else AppleGray4)
                            .clickable(enabled = customTitle.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStartCustom(customTitle.trim())
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "开启",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (customTitle.isNotBlank()) Color.Black else AppleTextSecondary
                        )
                    }
                }
            }
        }
    }
}
