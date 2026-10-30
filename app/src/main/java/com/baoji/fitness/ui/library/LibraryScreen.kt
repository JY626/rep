package com.baoji.fitness.ui.library

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoji.fitness.core.database.entity.BodyMetricEntity
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.designsystem.icons.LucideIcons
import com.baoji.fitness.core.theme.AppleBlack
import com.baoji.fitness.core.theme.AppleFitnessGreen
import com.baoji.fitness.core.theme.AppleGray4
import com.baoji.fitness.core.theme.AppleGray5
import com.baoji.fitness.core.theme.AppleGray6
import com.baoji.fitness.core.theme.AppleHairline
import com.baoji.fitness.core.theme.AppleTextPrimary
import com.baoji.fitness.core.theme.AppleTextSecondary
import com.baoji.fitness.core.theme.AppleTextTertiary
import com.baoji.fitness.core.theme.AppleSystemRed
import com.baoji.fitness.data.repository.WorkoutRepository
import com.baoji.fitness.domain.algorithm.MuscleClassifier
import com.baoji.fitness.domain.model.PersonalRecordCalculator
import com.baoji.fitness.ui.components.ChartPoint
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/** 数值 → 文本：整数不显示小数点，其余保留一位。 */
internal fun formatMetric(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

@Composable
fun LibraryScreen(
    repository: WorkoutRepository,
    modifier: Modifier = Modifier,
    createExercise: suspend (String) -> String? = { null }
) {
    val exercises by repository.getAllExercises().collectAsState(initial = emptyList())

    var selectedMuscle by remember { mutableStateOf("全部") }
    var showCreateSheet by remember { mutableStateOf(false) }
    var selectedExerciseForDetail by remember { mutableStateOf<ExerciseEntity?>(null) }

    val filtered = if (selectedMuscle == "全部") {
        exercises
    } else {
        exercises.filter { it.targetMuscle == selectedMuscle }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppleBlack)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header —— 右上角「新建动作」
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "EXERCISE LIBRARY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "动作库",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleTextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppleGray6)
                        .border(1.dp, AppleHairline, RoundedCornerShape(14.dp))
                        .clickable { showCreateSheet = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = LucideIcons.Plus,
                            contentDescription = null,
                            tint = AppleFitnessGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "新建动作",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleFitnessGreen
                        )
                    }
                }
            }
        }

        // 2. Muscle Filter
        item {
            val muscles = listOf("全部") + MuscleClassifier.PRIMARY_MUSCLES
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                muscles.forEach { muscle ->
                    val isSelected = (muscle == selectedMuscle)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Color.White else AppleGray6)
                            .clickable { selectedMuscle = muscle }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = muscle,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else AppleTextSecondary
                        )
                    }
                }
            }
        }

        // 3. Exercise List Card Container
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppleGray6)
                    .border(1.dp, AppleHairline, RoundedCornerShape(24.dp))
            ) {
                if (filtered.isEmpty()) {
                    Text(
                        text = "这个部位还没有动作，点右上角「新建动作」添加",
                        fontSize = 12.sp,
                        color = AppleTextTertiary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                filtered.forEachIndexed { index, exercise ->
                    ExerciseListItem(
                        exercise = exercise,
                        onClick = { selectedExerciseForDetail = exercise }
                    )
                    if (index < filtered.size - 1) {
                        HorizontalDivider(color = AppleHairline, thickness = 0.5.dp)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    if (showCreateSheet) {
        CreateExerciseSheet(
            /** 由 ViewModel 负责：先试 LLM 归类，失败回落关键词。返回创建好的动作名或 null。 */
            onCreate = createExercise,
            onDismiss = { showCreateSheet = false }
        )
    }

    // P2：PR 荣誉墙与动作历史时间线
    selectedExerciseForDetail?.let { exercise ->
        val historySets by repository.getHistoryForExercise(exercise.id).collectAsState(initial = emptyList())
        val prSummary = remember(exercise, historySets) {
            PersonalRecordCalculator.calculate(exercise, historySets)
        }
        ExerciseDetailSheet(
            prSummary = prSummary,
            onDismiss = { selectedExerciseForDetail = null }
        )
    }
}

@Composable
private fun ExerciseListItem(
    exercise: ExerciseEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = exercise.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextPrimary
            )
            Text(
                text = "${exercise.targetMuscle} · ${exercise.equipment}",
                fontSize = 11.sp,
                color = AppleTextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (exercise.personalRecordWeight > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppleFitnessGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "PR ${formatMetric(exercise.personalRecordWeight)} kg",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleFitnessGreen,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Icon(
                imageVector = LucideIcons.ChevronRight,
                contentDescription = null,
                tint = AppleTextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}


/**
 * M3b 录入弹层。预填最新一条记录（无记录时给中性基准），
 * 用步进 pills 调整 —— 健身房里单手可操作的交互。
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BodyMetricSheet(
    day: LocalDate,
    latest: BodyMetricEntity?,
    onDismiss: () -> Unit,
    onSave: (Double?, Double?, Double?, Double?, Double?, Double?) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    var weight by remember { mutableStateOf(latest?.weightKg ?: 75.0) }
    var fat by remember { mutableStateOf(latest?.bodyFatPct ?: 18.0) }
    var height by remember { mutableStateOf(latest?.heightCm ?: 175.0) }
    var chest by remember { mutableStateOf(latest?.chestCm ?: 100.0) }
    var waist by remember { mutableStateOf(latest?.waistCm ?: 80.0) }
    var arm by remember { mutableStateOf(latest?.armCm ?: 35.0) }

    val dateFormat = remember { DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINESE) }

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
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = "记录身体数据",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleTextPrimary
                )
                Text(
                    text = "${day.format(dateFormat)} · 同一天重复记录会覆盖",
                    fontSize = 11.sp,
                    color = AppleTextSecondary
                )
            }

            MetricInputField(
                label = "体重",
                unit = "kg",
                value = weight,
                onValueChange = { weight = it }
            )
            MetricInputField(
                label = "体脂率",
                unit = "%",
                value = fat,
                onValueChange = { fat = it }
            )
            MetricInputField(
                label = "身高",
                unit = "cm",
                value = height,
                onValueChange = { height = it }
            )
            MetricInputField(
                label = "胸围",
                unit = "cm",
                value = chest,
                onValueChange = { chest = it }
            )
            MetricInputField(
                label = "腰围",
                unit = "cm",
                value = waist,
                onValueChange = { waist = it }
            )
            MetricInputField(
                label = "臂围",
                unit = "cm",
                value = arm,
                onValueChange = { arm = it }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppleFitnessGreen)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSave(weight, fat, height, chest, waist, arm)
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "保存",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun MetricInputField(
    label: String,
    unit: String,
    value: Double,
    onValueChange: (Double) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var text by remember { mutableStateOf(formatMetric(value)) }

    LaunchedEffect(value) {
        if (text.toDoubleOrNull() != value) {
            text = formatMetric(value)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppleGray5)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = AppleTextPrimary
        )

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(AppleGray6)
                .border(0.5.dp, AppleHairline, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BasicTextField(
                value = text,
                onValueChange = { newText ->
                    if (newText.isEmpty() || newText.matches(Regex("""^\d{0,4}(\.\d{0,1})?$"""))) {
                        text = newText
                        val parsed = newText.toDoubleOrNull()
                        if (parsed != null && parsed >= 0.0) {
                            onValueChange(parsed)
                        }
                    }
                },
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .widthIn(min = 44.dp),
                textStyle = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = AppleTextPrimary,
                    textAlign = TextAlign.End
                ),
                cursorBrush = SolidColor(AppleFitnessGreen),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        if (text.isBlank() || text.toDoubleOrNull() == null) {
                            text = formatMetric(value)
                        }
                    }
                )
            )

            Text(
                text = unit,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextTertiary
            )
        }
    }
}

/**
 * 新建自定义动作。**只输名字**，主肌群/器械由本地规则自动判定，
 * 创建后立即出现在动作库与训练页的「添加动作」里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateExerciseSheet(
    onCreate: suspend (String) -> String?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppleGray6
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "新建动作",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleTextPrimary
                )
                Text(
                    text = "只填名字，练到的部位和器械由私教自动识别（会把组数算进对应部位）",
                    fontSize = 11.sp,
                    color = AppleTextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "动作名称", fontSize = 11.sp, color = AppleTextSecondary)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppleGray5)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    if (name.isEmpty()) {
                        Text(
                            text = "如：坐姿划船 / 保加利亚分腿蹲",
                            fontSize = 13.sp,
                            color = AppleTextTertiary
                        )
                    }
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it; error = null },
                        textStyle = TextStyle(fontSize = 13.sp, color = AppleTextPrimary),
                        cursorBrush = SolidColor(AppleFitnessGreen),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            error?.let {
                Text(text = it, fontSize = 11.sp, color = AppleSystemRed)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (name.isNotBlank() && !busy) AppleFitnessGreen else AppleGray4)
                    .clickable(enabled = name.isNotBlank() && !busy) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            busy = true
                            val err = onCreate(name.trim())
                            busy = false
                            if (err == null) onDismiss() else error = err
                        }
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (busy) "识别中…" else "创建",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (name.isNotBlank() && !busy) Color.Black else AppleTextSecondary
                )
            }
        }
    }
}
