package com.baoji.fitness.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.baoji.fitness.core.theme.AppleStrokeStrong
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

/**
 * 动作库。B 版：去卡片化 —— 列表就是列表（56dp 单行 + 发丝分隔线），
 * 搜索与肌群 chips 常驻并同时生效（AND），顶部先给「多少动作 / 多少有记录」这句结论。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    repository: WorkoutRepository,
    modifier: Modifier = Modifier,
    createExercise: suspend (String) -> String? = { null }
) {
    val exercises by repository.getAllExercises().collectAsState(initial = emptyList())

    var selectedMuscle by remember { mutableStateOf("全部") }
    var query by remember { mutableStateOf("") }
    var showCreateSheet by remember { mutableStateOf(false) }
    var selectedExerciseForDetail by remember { mutableStateOf<ExerciseEntity?>(null) }

    val trimmedQuery = query.trim()

    /**
     * 搜索（名称/肌群/器械，忽略大小写）与肌群 chips 是 AND 关系；
     * chips 之间仍单选。排序按 PR 从高到低 —— 与列表头那句「按 PR 从高到低」一致，
     * 没记录的一律沉底（0 不算纪录，不参与比较）。
     */
    val filtered = remember(exercises, selectedMuscle, trimmedQuery) {
        exercises
            .filter { e ->
                val muscleOk = selectedMuscle == "全部" || e.targetMuscle == selectedMuscle
                val queryOk = trimmedQuery.isEmpty() ||
                    e.name.contains(trimmedQuery, ignoreCase = true) ||
                    e.targetMuscle.contains(trimmedQuery, ignoreCase = true) ||
                    e.equipment.contains(trimmedQuery, ignoreCase = true)
                muscleOk && queryOk
            }
            .sortedByDescending { it.personalRecordWeight }
    }

    val hasAnyExercise = exercises.isNotEmpty()
    val recordedCount = remember(exercises) { exercises.count { it.personalRecordWeight > 0 } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppleBlack)
            // 键盘弹出时列表随之上移，否则筛选后的结果会被输入框挡住
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        // 1. 标题 + 右上角「新建动作」胶囊（不用 FAB：会被 Tab 栏顶起来遮住列表末行）
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
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
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleTextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                }
                CreatePill(onClick = { showCreateSheet = true })
            }
        }

        if (!hasAnyExercise) {
            // 一个动作都没有（新装）：搜索框与 chips 都是死控件，直接藏起来只留建动作
            item { AllEmptyState(onCreate = { showCreateSheet = true }) }
        } else {
            // 2. 结论行：32 个动作 · 12 个有记录
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = exercises.size.toString(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-0.4).sp,
                        color = AppleTextPrimary
                    )
                    Text(
                        text = " 个动作 · ",
                        fontSize = 11.sp,
                        color = AppleTextSecondary
                    )
                    Text(
                        text = recordedCount.toString(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-0.4).sp,
                        color = AppleTextPrimary
                    )
                    Text(
                        text = " 个有记录",
                        fontSize = 11.sp,
                        color = AppleTextSecondary
                    )
                }
            }

            // 3. 常驻搜索框
            item {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // 4. 肌群 chips（单选，与搜索 AND 交集）
            item {
                val muscles = listOf("全部") + MuscleClassifier.PRIMARY_MUSCLES
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    muscles.forEach { muscle ->
                        val isSelected = (muscle == selectedMuscle)
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) AppleFitnessGreen else AppleGray5)
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = { selectedMuscle = muscle }
                                )
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = muscle,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.Black else AppleTextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 5. 吸顶列表头：滚多远都知道这堆是什么、按什么排
            stickyHeader {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppleBlack)
                        .padding(top = 12.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedMuscle == "全部") "全部动作" else "「$selectedMuscle」动作",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextSecondary
                    )
                    Text(
                        text = "按 PR 从高到低",
                        fontSize = 11.sp,
                        color = AppleTextSecondary
                    )
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyResultState(
                        query = trimmedQuery,
                        muscle = selectedMuscle,
                        onClear = {
                            query = ""
                            selectedMuscle = "全部"
                        },
                        onCreate = { showCreateSheet = true }
                    )
                }
            } else {
                itemsIndexed(items = filtered, key = { _, e -> e.id }) { index, exercise ->
                    ExerciseListItem(
                        exercise = exercise,
                        showDivider = index < filtered.lastIndex,
                        onClick = { selectedExerciseForDetail = exercise }
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
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

/**
 * 右上角「＋ 新建动作」：视觉仍是 30dp 胶囊，但点击热区抬到 44dp ——
 * 30dp 是全页唯一低于 44dp 触控下限的控件，为视觉压扁控件不该让手指遭罪。
 */
@Composable
private fun CreatePill(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(AppleFitnessGreen)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = LucideIcons.Plus,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "新建动作",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            }
        }
    }
}

/**
 * 常驻搜索框：40dp 高、AppleGray6 底 + 35% 白描边。
 * 纯黑上 AppleGray6 的对比只有 1.85:1，不描边根本看不出输入区边界。
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppleGray6)
            .border(1.dp, AppleStrokeStrong, RoundedCornerShape(12.dp))
            .semantics { contentDescription = "搜索动作，可输入动作名或器械" }
            .padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchGlyph(modifier = Modifier.size(16.dp))

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "搜索动作 / 肌群 / 器械",
                    fontSize = 13.sp,
                    color = AppleTextSecondary,
                    maxLines = 1
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 13.sp, color = AppleTextPrimary),
                cursorBrush = SolidColor(AppleFitnessGreen),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
            )
        }

        if (query.isNotEmpty()) {
            // 24dp 视觉 / 40dp 热区
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") }
                    .semantics { contentDescription = "清除搜索" },
                contentAlignment = Alignment.Center
            ) {
                ClearGlyph(modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** 放大镜：不引图标库，Canvas 手画（与项目内其余图标风格一致）。 */
@Composable
private fun SearchGlyph(
    modifier: Modifier = Modifier,
    color: Color = AppleTextSecondary
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.13f
        val r = size.minDimension * 0.32f
        val center = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(color = color, radius = r, center = center, style = Stroke(width = stroke))
        drawLine(
            color = color,
            start = Offset(center.x + r * 0.75f, center.y + r * 0.75f),
            end = Offset(size.width * 0.94f, size.height * 0.94f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/** 清除按钮的 ×：AppleGray4 圆底 + 两条短线。 */
@Composable
private fun ClearGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val d = size.minDimension
        drawCircle(color = AppleGray4, radius = d / 2f, center = Offset(d / 2f, d / 2f))
        val inset = d * 0.32f
        val stroke = d * 0.09f
        drawLine(
            color = AppleTextPrimary,
            start = Offset(inset, inset),
            end = Offset(d - inset, d - inset),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = AppleTextPrimary,
            start = Offset(d - inset, inset),
            end = Offset(inset, d - inset),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/**
 * 列表项：56dp 紧凑单行，行与行之间只有发丝线。不加卡片底 / 圆角 / 阴影 ——
 * 一张卡包一行的写法在 30+ 动作时会变成一堵墙。
 */
@Composable
private fun ExerciseListItem(
    exercise: ExerciseEntity,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    val pr = exercise.personalRecordWeight
    val description = buildString {
        append(exercise.name)
        append("，")
        append(exercise.targetMuscle)
        append("，")
        append(exercise.equipment)
        if (pr > 0) append("，个人纪录 ${formatMetric(pr)} 公斤")
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(exercise.targetMuscle)
                        append(" · ")
                        append(exercise.equipment)
                        // 没记录写进副行：PR 列是纯数字列，塞文字会让右边缘与其它行对不齐
                        if (pr <= 0) append(" · 未记录")
                    },
                    fontSize = 12.sp,
                    color = AppleTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // 纯数字列：13sp 等宽绿字，不加底色 —— 每行一个绿 pill 会变成一片亮点，反而看不出重点
            if (pr > 0) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Text(
                        text = formatMetric(pr),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-0.3).sp,
                        color = AppleFitnessGreen,
                        maxLines = 1
                    )
                    Text(
                        text = " kg",
                        fontSize = 11.sp,
                        color = AppleTextSecondary,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
            }
        }
        // 最后一行不画线（与列表底部的留白相接）
        if (showDivider) {
            HorizontalDivider(color = AppleHairline.copy(alpha = 0.6f), thickness = 0.5.dp)
        }
    }
}

/**
 * 搜索/筛选无结果：说清「哪个词、哪个部位没命中」，并给两条退路 ——
 * 清除筛选、直接新建。页眉会随列表滚走，所以「新建动作」在这里必须再给一次，
 * 否则用户滚到无结果的位置就找不到入口了。
 */
@Composable
private fun EmptyResultState(
    query: String,
    muscle: String,
    onClear: () -> Unit,
    onCreate: () -> Unit
) {
    val message = when {
        query.isNotEmpty() && muscle != "全部" -> "「$muscle」里没有匹配「$query」的动作"
        query.isNotEmpty() -> "没有匹配「$query」的动作"
        muscle != "全部" -> "「$muscle」里还没有动作"
        else -> "还没有动作"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = message,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = AppleTextPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = "换个词试试，或新建一个",
            fontSize = 11.sp,
            color = AppleTextSecondary,
            textAlign = TextAlign.Center
        )
        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "清除筛选",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleFitnessGreen
                )
            }
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(AppleFitnessGreen)
                    .clickable(onClick = onCreate)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "＋ 新建动作",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

/** 一个动作都没有（新装）：只留一句 + 一个 44dp 主按钮，别摆死控件。 */
@Composable
private fun AllEmptyState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "还没有动作，先建一个",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = AppleTextPrimary
        )
        Box(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(AppleFitnessGreen)
                .clickable(onClick = onCreate)
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "新建动作",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
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

            // 弹层内主按钮：48 / 圆角 16 / 14sp。刻意**不**跟屏级 CTA 的 58 / 29 / 17 统一 ——
            // 这是两级尺寸：弹层是「就地确认」，屏幕底部才是页面主动作。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppleFitnessGreen)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSave(weight, fat, height, chest, waist, arm)
                    },
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
