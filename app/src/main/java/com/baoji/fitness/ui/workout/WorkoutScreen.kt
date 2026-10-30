package com.baoji.fitness.ui.workout

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import com.baoji.fitness.data.repository.AiShareContext
import com.baoji.fitness.ui.components.AiSharePreviewBottomSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.baoji.fitness.core.theme.AppleSystemRed
import com.baoji.fitness.core.theme.AppleTextPrimary
import com.baoji.fitness.core.theme.AppleTextSecondary
import com.baoji.fitness.core.theme.AppleTextTertiary
import com.baoji.fitness.domain.algorithm.OverloadCalculator
import com.baoji.fitness.domain.model.PresetTemplates
import com.baoji.fitness.domain.model.WorkoutRoutineTemplate
import com.baoji.fitness.ui.components.ExerciseGroupCard
import com.baoji.fitness.ui.workout.components.RestTimerCapsule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatWeight(w: Double): String = if (w % 1.0 == 0.0) w.toInt().toString() else w.toString()

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
    // 折叠已完成动作：练到第 6 个动作时不用一路往下滚
    var hideCompletedExercises by remember { mutableStateOf(false) }
    var activeEditingSet by remember { mutableStateOf<Pair<ExerciseEntity, WorkoutSetEntity>?>(null) }

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

            // 2. Metrics Strip (Apple Fitness Style)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppleGray6)
                        .border(1.dp, AppleHairline, RoundedCornerShape(20.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    if (activeSession == null) {
                        // 归档后：沿用今日已完成训练的真实数据
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
                    } else {
                        MetricItem(label = "累计容量", value = "$totalVolume", unit = "kg")
                        MetricItem(
                            label = "完成组数",
                            value = "${completedSets.size}/$totalSetsCount",
                            valueColor = AppleFitnessGreen
                        )
                        MetricItem(
                            label = "本次用时",
                            value = formatElapsed(elapsedSeconds),
                            unit = null
                        )
                    }
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
                                Text(text = "笔记", fontSize = 10.sp, color = AppleTextSecondary)
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

            // 4. 折叠开关（仅训练中、且有已完成动作时才显示）
            if (activeSession != null && exerciseGroups.any { g -> g.sets.all { it.isCompleted } }) {
                item(key = "collapse-toggle") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppleGray6)
                            .border(1.dp, AppleHairline, RoundedCornerShape(14.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                hideCompletedExercises = !hideCompletedExercises
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (hideCompletedExercises) "显示已完成动作" else "折叠已完成动作",
                            fontSize = 12.sp,
                            color = AppleTextSecondary
                        )
                        val doneCount = exerciseGroups.count { g -> g.sets.all { it.isCompleted } }
                        Text(
                            text = "$doneCount 个已完成",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AppleFitnessGreen
                        )
                    }
                }
            }

            // 5. Exercise Cards List
            items(
                if (hideCompletedExercises) exerciseGroups.filterNot { g -> g.sets.all { it.isCompleted } }
                else exerciseGroups,
                key = { it.exercise.id }
            ) { group ->
                ExerciseCard(
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

@Composable
private fun ExerciseCard(
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AppleGray6)
            .border(1.dp, AppleHairline, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Exercise Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = group.exercise.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppleGray5)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = group.exercise.targetMuscle,
                            fontSize = 10.sp,
                            color = AppleTextSecondary
                        )
                    }
                }
                Text(
                    text = benchmarkSub,
                    fontSize = 11.sp,
                    color = AppleTextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Remove exercise action
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppleGray5)
                    .clickable { onRemoveExercise() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "移除",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppleSystemRed.copy(alpha = 0.9f)
                )
            }
        }

        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "组", fontSize = 11.sp, color = AppleTextTertiary, modifier = Modifier.weight(0.9f), textAlign = TextAlign.Center)
            Text(text = "参考基准", fontSize = 11.sp, color = AppleTextTertiary, modifier = Modifier.weight(1.8f), textAlign = TextAlign.Center)
            Text(text = if (group.exercise.trackWeight) "重量(kg)" else "负重", fontSize = 11.sp, color = AppleTextTertiary, modifier = Modifier.weight(1.7f), textAlign = TextAlign.Center)
            Text(text = "次数", fontSize = 11.sp, color = AppleTextTertiary, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center)
            Text(text = "完成", fontSize = 11.sp, color = AppleTextTertiary, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center)
        }

        // Sets Rows with stable keys
        group.sets.forEach { set ->
            val prevSet = group.previousSessionSets[set.setOrder]
            val trackW = group.exercise.trackWeight
            val setBenchmarkText = when {
                prevSet == null -> "-"
                trackW && prevSet.weightKg > 0.0 -> "${formatWeight(prevSet.weightKg)} × ${prevSet.reps}"
                !trackW -> "× ${prevSet.reps}"
                else -> "-"
            }
            key(set.id) {
                SetRowItem(
                    set = set,
                    benchmarkText = setBenchmarkText,
                    trackWeight = trackW,
                    onToggle = { onToggleSet(set) },
                    onOpenEditor = { onOpenSetEditor(set) }
                )
            }
        }

        // Add Set Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppleGray5.copy(alpha = 0.5f))
                .clickable { onAddSet() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
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
        }
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBg)
            .border(1.dp, AppleHairline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Order / Set Type Badge (Clickable to edit)
        Box(
            modifier = Modifier
                .weight(0.9f)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenEditor() }
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = typeLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (set.isCompleted) AppleFitnessGreen else AppleTextSecondary
            )
        }

        // 2. Reference Benchmark (Previous Best / Target)
        Text(
            text = benchmarkText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = AppleTextTertiary,
            modifier = Modifier.weight(1.8f),
            textAlign = TextAlign.Center
        )

        // 3. Weight Pill (Click to open Stepper Editor)
        Box(
            modifier = Modifier
                .weight(1.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(AppleGray5)
                .clickable { onOpenEditor() }
                .padding(vertical = 6.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (trackWeight) formatWeight(set.weightKg) else "自重",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = AppleTextPrimary
            )
        }

        Spacer(modifier = Modifier.size(4.dp))

        // 4. Reps Pill (Click to open Stepper Editor)
        Box(
            modifier = Modifier
                .weight(1.2f)
                .clip(RoundedCornerShape(8.dp))
                .background(AppleGray5)
                .clickable { onOpenEditor() }
                .padding(vertical = 6.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${set.reps}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = AppleTextPrimary
            )
        }

        // 5. Complete Button (Full cell height 44dp clickable target)
        Box(
            modifier = Modifier
                .weight(1.2f)
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (set.isCompleted) AppleFitnessGreen else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (set.isCompleted) AppleFitnessGreen else AppleHairline,
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
    var currentWeight by remember(set.weightKg) { mutableStateOf(set.weightKg) }
    var currentReps by remember(set.reps) { mutableStateOf(set.reps) }
    var currentType by remember(set.setType) { mutableStateOf(set.setType) }
    // M3a：RPE 默认不填（null）。只作记录与 M4 上下文，不参与双重渐进判定。
    var currentRpe by remember(set.rpe) { mutableStateOf(set.rpe) }

    val haptic = LocalHapticFeedback.current

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
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentType = type
                            }
                            .padding(vertical = 6.dp),
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
                    Text(
                        text = "${formatWeight(currentWeight)} kg",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = AppleFitnessGreen
                    )
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppleGray6)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val next = (currentWeight + delta).coerceAtLeast(0.0)
                                    currentWeight = (next * 10).toInt() / 10.0
                                }
                                .padding(vertical = 8.dp),
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
                    Text(
                        text = "$currentReps 次",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = AppleTextPrimary
                    )
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppleGray6)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentReps = (currentReps + delta).coerceAtLeast(1)
                                }
                                .padding(vertical = 8.dp),
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
                            fontSize = 10.sp,
                            color = AppleTextTertiary
                        )
                    }
                    Text(
                        text = currentRpe?.let { "RPE ${formatWeight(it)}" } ?: "未填",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (currentRpe != null) AppleFitnessGreen else AppleTextTertiary
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "总容量", fontSize = 10.sp, color = AppleTextSecondary)
                    Text(
                        text = "$totalVolumeKg kg",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = AppleFitnessGreen
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "完成组数", fontSize = 10.sp, color = AppleTextSecondary)
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
                            color = AppleTextTertiary
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
                                color = AppleTextTertiary
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
