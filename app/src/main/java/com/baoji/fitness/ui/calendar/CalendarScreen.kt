package com.baoji.fitness.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.baoji.fitness.data.backup.BackupSummary
import com.baoji.fitness.data.backup.RestoreMode
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.baoji.fitness.core.database.entity.BodyMetricEntity
import com.baoji.fitness.core.designsystem.icons.LucideIcons
import com.baoji.fitness.data.healthconnect.HealthConnectWriter
import com.baoji.fitness.data.repository.AiShareContext
import com.baoji.fitness.ui.components.AiSharePreviewBottomSheet
import kotlinx.coroutines.launch
import com.baoji.fitness.core.theme.AppleBlack
import com.baoji.fitness.core.theme.AppleCautionOrange
import com.baoji.fitness.core.theme.AppleFitnessGreen
import com.baoji.fitness.core.theme.AppleGray4
import com.baoji.fitness.core.theme.AppleGray5
import com.baoji.fitness.core.theme.AppleGray6
import com.baoji.fitness.core.theme.AppleHairline
import com.baoji.fitness.core.theme.AppleSystemBlue
import com.baoji.fitness.core.theme.AppleTextPrimary
import com.baoji.fitness.core.theme.AppleTextSecondary
import com.baoji.fitness.core.theme.AppleTextTertiary
import com.baoji.fitness.data.settings.ReminderConfig
import com.baoji.fitness.domain.model.WorkoutDayDetail
import com.baoji.fitness.ui.components.ExerciseGroupCard
import com.baoji.fitness.ui.library.BodyMetricSheet
import com.baoji.fitness.ui.library.formatMetric
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier
) {
    val trainedDayTypes by viewModel.trainedDayTypes.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val dayDetail by viewModel.dayDetail.collectAsState()
    val dayMetric by viewModel.dayMetric.collectAsState()
    val habit by viewModel.habit.collectAsState()
    val reminderConfig by viewModel.reminderConfig.collectAsState()
    val monthVolume by viewModel.monthVolume.collectAsState()
    val metricDays by viewModel.metricDays.collectAsState()
    val todayDetail by viewModel.todayDetail.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasHealthPermissions by remember { mutableStateOf(false) }
    val isHealthConnectAvailable = remember { HealthConnectWriter.isAvailable(context) }

    val healthConnectLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        hasHealthPermissions = granted.containsAll(HealthConnectWriter.PERMISSIONS)
    }

    LaunchedEffect(reminderConfig.healthConnectEnabled) {
        if (isHealthConnectAvailable) {
            hasHealthPermissions = HealthConnectWriter.hasPermissions(context)
        }
    }

    var shareContextToPreview by remember { mutableStateOf<AiShareContext?>(null) }
    var showShareSheet by remember { mutableStateOf(false) }

    var showDayMetricSheet by remember { mutableStateOf(false) }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    var pendingBackupSummary by remember { mutableStateOf<BackupSummary?>(null) }
    var pendingBackupJson by remember { mutableStateOf<String?>(null) }

    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                }
                if (!jsonString.isNullOrBlank()) {
                    viewModel.parseBackupSummary(jsonString)
                        .onSuccess { summary ->
                            pendingBackupSummary = summary
                            pendingBackupJson = jsonString
                        }
                        .onFailure {
                            Toast.makeText(context, "备份文件解析失败，请确保格式正确", Toast.LENGTH_SHORT).show()
                        }
                }
            }.onFailure {
                Toast.makeText(context, "读取文件失败: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showSettings by rememberSaveable { mutableStateOf(false) }
    // 系统返回键要先退回日历，而不是直接退出这个 tab
    BackHandler(enabled = showSettings) { showSettings = false }
    // 主页与设置二级页互斥。用 SaveableStateHolder 而不是普通条件渲染：
    // 离开组合时 rememberSaveable 的存档会随子树一起被丢掉，回来就回到顶部、
    // 月份也被重置（rememberScrollState 内部正是 rememberSaveable）。
    val stateHolder = rememberSaveableStateHolder()

    val onToggleHealthConnect: (Boolean) -> Unit = { enabled ->
        viewModel.setHealthConnectEnabled(enabled)
        if (enabled && isHealthConnectAvailable && !hasHealthPermissions) {
            healthConnectLauncher.launch(HealthConnectWriter.PERMISSIONS)
        }
    }
    val onRequestHealthPermissions: () -> Unit = {
        healthConnectLauncher.launch(HealthConnectWriter.PERMISSIONS)
    }
    val onSyncHealthConnect: () -> Unit = {
        if (isHealthConnectAvailable && !hasHealthPermissions) {
            healthConnectLauncher.launch(HealthConnectWriter.PERMISSIONS)
        } else {
            viewModel.syncFromHealthConnect(context) { result ->
                result.onSuccess { count ->
                    if (count > 0) {
                        Toast.makeText(context, "成功从 Health Connect 同步 $count 天身体数据！", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "暂无更新的身体数据需要同步", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { err ->
                    Toast.makeText(context, "同步失败: ${err.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val onExportBackup: () -> Unit = {
        viewModel.exportBackup { result ->
            result.onSuccess { file ->
                viewModel.shareBackup(file)
            }.onFailure { err ->
                Toast.makeText(context, "导出失败: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val onImportBackup: () -> Unit = {
        importFileLauncher.launch(arrayOf("application/json", "*/*"))
    }

    if (showSettings) {
        stateHolder.SaveableStateProvider("calendar-settings") {
            TrainingSettingsPage(
                config = reminderConfig,
                hasHealthPermissions = hasHealthPermissions,
                isHealthConnectAvailable = isHealthConnectAvailable,
                onSetRestSeconds = { viewModel.setRestSeconds(it) },
                onToggleReminder = { viewModel.setReminderEnabled(it) },
                onTimeChange = { h, m -> viewModel.setReminderTime(h, m) },
                onToggleDay = { viewModel.toggleTrainingDay(it) },
                onToggleHealthConnect = onToggleHealthConnect,
                onRequestHealthPermissions = onRequestHealthPermissions,
                onSync = onSyncHealthConnect,
                onExport = onExportBackup,
                onImport = onImportBackup,
                onBack = { showSettings = false },
                modifier = modifier
            )
        }
    } else {
        stateHolder.SaveableStateProvider("calendar-home") {
            CalendarHome(
                trainedDayTypes = trainedDayTypes,
                metricDays = metricDays,
                habit = habit,
                monthVolume = monthVolume,
                config = reminderConfig,
                todayDetail = todayDetail,
                hasHealthPermissions = hasHealthPermissions,
                isHealthConnectAvailable = isHealthConnectAvailable,
                onDaySelected = { viewModel.selectDay(it) },
                onOpenTodayDetail = { viewModel.selectDay(LocalDate.now()) },
                onOpenSettings = { showSettings = true },
                modifier = modifier
            )
        }
    }

    // 当天训练明细弹层
    if (selectedDay != null) {
        DayDetailSheet(
            day = selectedDay!!,
            detail = dayDetail,
            metric = dayMetric,
            typeLabel = trainedDayTypes[selectedDay!!],
            onRecordMetric = { showDayMetricSheet = true },
            onShareToAi = {
                dayDetail?.let { detail ->
                    scope.launch {
                        shareContextToPreview = viewModel.getAiShareContext(detail)
                        showShareSheet = true
                    }
                }
            },
            onDismiss = { viewModel.selectDay(null) },
            onDelete = { showDeleteConfirm = true }
        )
    }

    // 历史纠正：删掉误记的一整天（不可撤销，必须二次确认）
    if (showDeleteConfirm && selectedDay != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = AppleGray6,
            title = {
                Text(
                    text = "删除这天的训练记录？",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleTextPrimary
                )
            },
            text = {
                Text(
                    text = "将删除 ${selectedDay!!.format(DateTimeFormatter.ofPattern("M月d日"))} 的全部组记录，" +
                        "并按剩余数据重算受影响动作的 PR。此操作不可撤销。",
                    fontSize = 13.sp,
                    color = AppleTextSecondary,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val day = selectedDay!!
                        showDeleteConfirm = false
                        viewModel.deleteWorkoutDay(day) { removed ->
                            Toast.makeText(
                                context,
                                if (removed > 0) "已删除 $removed 次训练，PR 已重算" else "这天没有可删除的记录",
                                Toast.LENGTH_SHORT
                            ).show()
                            viewModel.selectDay(null)
                        }
                    }
                ) {
                    Text("删除", color = Color(0xFFFF453A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消", color = AppleTextSecondary)
                }
            }
        )
    }

    // AI 分享预览弹层
    if (showShareSheet) {
        AiSharePreviewBottomSheet(
            shareContext = shareContextToPreview,
            onDismiss = {
                showShareSheet = false
                shareContextToPreview = null
            }
        )
    }

    // M3b：当天顺手记身体数据
    if (showDayMetricSheet && selectedDay != null) {
        BodyMetricSheet(
            day = selectedDay!!,
            latest = dayMetric,
            onDismiss = { showDayMetricSheet = false },
            onSave = { weight, fat, height, chest, waist, arm ->
                viewModel.saveMetric(selectedDay!!, weight, fat, height, chest, waist, arm)
                showDayMetricSheet = false
            }
        )
    }

    // P0：恢复备份确认弹层
    if (pendingBackupSummary != null && pendingBackupJson != null) {
        RestoreConfirmationDialog(
            summary = pendingBackupSummary!!,
            onConfirm = { mode ->
                viewModel.restoreBackup(pendingBackupJson!!, mode) { result ->
                    result.onSuccess { res ->
                        val skipped = if (res.skippedDuplicateSets > 0) {
                            "，跳过 ${res.skippedDuplicateSets} 组已存在数据"
                        } else ""
                        val snapshot = if (res.preRestoreBackupPath != null) "（原数据已自动备份）" else ""
                        Toast.makeText(
                            context,
                            "恢复成功！已导入 ${res.restoredSessions} 次训练、${res.restoredBodyMetrics} 条身体数据$skipped$snapshot",
                            Toast.LENGTH_LONG
                        ).show()
                    }.onFailure { err ->
                        Toast.makeText(context, "恢复失败: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
                    pendingBackupSummary = null
                    pendingBackupJson = null
                }
            },
            onDismiss = {
                pendingBackupSummary = null
                pendingBackupJson = null
            }
        )
    }
}

/**
 * 日历主页：页首三块结论 → 月历 → 今日明细入口 → 设置入口。
 * 设置类内容全部收进二级页，这一页只留日历和数字。
 */
@Composable
private fun CalendarHome(
    trainedDayTypes: Map<LocalDate, String>,
    metricDays: Set<LocalDate>,
    habit: HabitSummary,
    monthVolume: MonthVolume,
    config: ReminderConfig,
    todayDetail: WorkoutDayDetail?,
    hasHealthPermissions: Boolean,
    isHealthConnectAvailable: Boolean,
    onDaySelected: (LocalDate) -> Unit,
    onOpenTodayDetail: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val month = YearMonth.from(today)
    val checkInDays = remember(trainedDayTypes, month) {
        trainedDayTypes.keys.count { YearMonth.from(it) == month }
    }
    // 本月计划天数：把每周训练日铺到当月天数上。纯展示口径，不参与任何统计。
    val plannedDaysThisMonth = remember(config.trainingDays, month) {
        (1..month.lengthOfMonth()).count { month.atDay(it).dayOfWeek in config.trainingDays }
    }
    val context = LocalContext.current
    // 从设置页返回时这棵子树会重建，权限状态因此天然刷新一次
    val hasNotificationPermission = remember { notificationPermissionGranted(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppleBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Column {
            Text(
                text = "TRAINING LOG",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextSecondary,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "每日记录",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary,
                letterSpacing = (-0.5).sp
            )
        }

        SummaryTriple(
            checkInDays = checkInDays,
            checkInSub = if (plannedDaysThisMonth > 0) "计划 $plannedDaysThisMonth 天" else null,
            streakDays = habit.currentStreak,
            streakSub = if (habit.longestStreak > 0) "最长 ${habit.longestStreak} 天" else null,
            volumeKg = monthVolume.currentKg,
            volumeSub = if (monthVolume.previousKg > 0.0) "↑ 上月 ${formatKg(monthVolume.previousKg)} kg" else null
        )

        TrainingCheckInCalendar(
            trainedDayTypes = trainedDayTypes,
            metricDays = metricDays,
            onDaySelected = onDaySelected
        )

        DetailEntryRow(day = today, detail = todayDetail, onClick = onOpenTodayDetail)

        SettingsEntryRow(
            config = config,
            // 只在「说了要做但实际做不到」时提示，低频的「从未备份」不打扰
            warning = (config.enabled && !hasNotificationPermission) ||
                (config.healthConnectEnabled && isHealthConnectAvailable && !hasHealthPermissions),
            onClick = onOpenSettings
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}

private fun notificationPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

/** 容量读数：千分位整数，和负荷页的读数习惯一致。 */
private fun formatKg(value: Double): String = String.format(Locale.US, "%,.0f", value)

/**
 * 页首三块结论：本月打卡 / 连续 / 本月容量。
 * 三块等宽 + 1dp 竖分隔线；数值等宽字体，副标题没有数据时整行省略（不显示 0）。
 */
@Composable
private fun SummaryTriple(
    checkInDays: Int,
    checkInSub: String?,
    streakDays: Int,
    streakSub: String?,
    volumeKg: Double,
    volumeSub: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppleGray6)
            .border(1.dp, AppleHairline, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        SummaryCell(
            modifier = Modifier.weight(1f),
            label = "本月打卡",
            value = "$checkInDays",
            unit = "天",
            sub = checkInSub,
            spoken = "本月打卡 $checkInDays 天" + (checkInSub?.let { "，$it" } ?: "")
        )
        SummaryDivider()
        SummaryCell(
            modifier = Modifier.weight(1f),
            label = "连续",
            value = "$streakDays",
            unit = "天",
            sub = streakSub,
            spoken = "连续 $streakDays 天" + (streakSub?.let { "，$it" } ?: "")
        )
        SummaryDivider()
        SummaryCell(
            modifier = Modifier.weight(1f),
            label = "本月容量",
            value = formatKg(volumeKg),
            unit = "kg",
            sub = volumeSub,
            spoken = "本月容量 ${formatKg(volumeKg)} 千克" + (volumeSub?.let { "，$it" } ?: "")
        )
    }
}

@Composable
private fun RowScope.SummaryDivider() {
    // 固定高度而不是 fillMaxHeight：不依赖 IntrinsicSize 的二次测量，行为可预期。
    // 三块的文字基线都顶部对齐，只有这条线自己在垂直方向居中。
    Box(
        modifier = Modifier
            .align(Alignment.CenterVertically)
            .padding(horizontal = 10.dp)
            .size(width = 1.dp, height = 40.dp)
            .background(AppleHairline)
    )
}

@Composable
private fun SummaryCell(
    label: String,
    value: String,
    unit: String,
    sub: String?,
    spoken: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = AppleTextSecondary,
            maxLines = 1
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                // 等宽块里 32sp 只放得下 3 位数字（如「12」）；「24,860」这类必须逐级降档，
                // 否则会被裁掉末位 —— 裁掉的正好是最重要的位数。
                fontSize = when {
                    value.length <= 3 -> 32.sp
                    value.length == 4 -> 27.sp
                    value.length == 5 -> 24.sp
                    else -> 21.sp
                },
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-1).sp,
                // 全新安装全是 0：0 不该用强调白，否则「没有数据」看起来像「有数据」
                color = if (value == "0") AppleTextSecondary else AppleTextPrimary,
                maxLines = 1
            )
            Text(
                text = unit,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = AppleTextSecondary,
                modifier = Modifier.padding(start = 3.dp, bottom = 3.dp)
            )
        }
        if (sub != null) {
            Text(
                text = sub,
                fontSize = 11.sp,
                color = AppleTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 「查看 10月14日 明细 ›」：明细弹层不再默认展开，改成显式入口。 */
@Composable
private fun DetailEntryRow(day: LocalDate, detail: WorkoutDayDetail?, onClick: () -> Unit) {
    val dateLabel = remember(day) { day.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINESE)) }
    val info = if (detail == null) {
        "还没记录"
    } else {
        "${detail.groups.size} 动作 · ${detail.groups.sumOf { it.sets.size }} 组"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppleGray6)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "查看 $dateLabel 明细，$info"
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "查看", fontSize = 13.sp, color = AppleTextPrimary)
        Text(
            text = dateLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = AppleTextPrimary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(text = "明细", fontSize = 13.sp, color = AppleTextPrimary)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = info,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = AppleTextSecondary,
            maxLines = 1
        )
        Icon(
            imageVector = LucideIcons.ChevronRight,
            contentDescription = null,
            tint = AppleTextTertiary,
            modifier = Modifier
                .padding(start = 6.dp)
                .size(16.dp)
        )
    }
}

/** 设置全部收成一行 56dp 入口；右侧摘要单行截断，读屏只念一句人话。 */
@Composable
private fun SettingsEntryRow(config: ReminderConfig, warning: Boolean, onClick: () -> Unit) {
    val summary = buildString {
        append("组间 ${config.restSeconds}s")
        if (config.trainingDays.isNotEmpty()) append(" · ${config.trainingDays.size} 训练日")
        if (config.healthConnectEnabled) append(" · 已连接 Health Connect")
    }
    val spoken = buildString {
        append("训练设置与数据：组间休息 ${config.restSeconds} 秒")
        if (config.enabled) append("，提醒 ${config.hour} 点")
        if (config.healthConnectEnabled) append("，Health Connect 已开启")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppleGray6)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = spoken
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "训练设置与数据",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppleTextPrimary,
            maxLines = 1
        )
        // weight 而不是 Spacer：摘要再长也只吃剩余宽度，绝不把 chevron 挤掉
        Text(
            text = summary,
            fontSize = 11.sp,
            color = AppleTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
        if (warning) {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(AppleCautionOrange)
            )
        }
        Icon(
            imageVector = LucideIcons.ChevronRight,
            contentDescription = null,
            tint = AppleTextTertiary,
            modifier = Modifier
                .padding(start = 6.dp)
                .size(16.dp)
        )
    }
}

/** 二级页：训练设置与数据。四张卡原样搬过来，只负责返回与容量。 */
@Composable
private fun TrainingSettingsPage(
    config: ReminderConfig,
    hasHealthPermissions: Boolean,
    isHealthConnectAvailable: Boolean,
    onSetRestSeconds: (Int) -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onTimeChange: (Int, Int) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onToggleHealthConnect: (Boolean) -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onSync: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppleBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    // 48dp = 本轮团队统一下限（Android 对齐目标），显式写死不依赖任何默认值；
                    // 注意纯 Modifier.clickable 不会自动把 44 撑到 48，写小了没有任何机制兜底。
                    // 视觉图标仍是 20dp。
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .semantics {
                        role = Role.Button
                        contentDescription = "返回日历"
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = LucideIcons.ChevronRight,
                    contentDescription = null,
                    tint = AppleTextPrimary,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(180f)
                )
            }
            Text(
                text = "训练设置与数据",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // 组间休息时长（打勾后自动开始）
        RestDurationCard(restSeconds = config.restSeconds, onChange = onSetRestSeconds)

        // M2：训练日提醒设置
        ReminderSettingsCard(
            config = config,
            onToggleEnabled = onToggleReminder,
            onTimeChange = onTimeChange,
            onToggleDay = onToggleDay
        )

        // P1：Health Connect 自动同步设置
        HealthConnectSettingsCard(
            enabled = config.healthConnectEnabled,
            hasPermissions = hasHealthPermissions,
            isAvailable = isHealthConnectAvailable,
            onToggle = onToggleHealthConnect,
            onRequestPermissions = onRequestHealthPermissions,
            onSync = onSync
        )

        // P0：数据备份与恢复设置
        DataBackupSettingsCard(onExport = onExport, onImport = onImport)

        Spacer(modifier = Modifier.height(80.dp))
    }
}

/** M2 提醒设置：开关 / 时间 / 每周训练日。 */
@Composable
private fun ReminderSettingsCard(
    config: ReminderConfig,
    onToggleEnabled: (Boolean) -> Unit,
    onTimeChange: (Int, Int) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit
) {    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AppleGray6)
            .border(1.dp, AppleHairline, RoundedCornerShape(24.dp))
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
                    text = "训练日提醒",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary
                )
                Text(
                    text = "实际触发时间允许 ±15 分钟（系统限制）",
                    fontSize = 11.sp,
                    color = AppleTextTertiary
                )
            }
            TogglePill(checked = config.enabled, onCheckedChange = onToggleEnabled)
        }

        if (config.enabled) {
            SettingRowLabel("提醒时间")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (6..22).forEach { hour ->
                    ChipBox(
                        text = String.format(Locale.US, "%02d", hour),
                        selected = config.hour == hour
                    ) { onTimeChange(hour, config.minute) }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0, 15, 30, 45).forEach { minute ->
                    ChipBox(
                        text = String.format(Locale.US, ":%02d", minute),
                        selected = config.minute == minute
                    ) { onTimeChange(config.hour, minute) }
                }
            }

            SettingRowLabel("每周训练日")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WEEK_LABELS.forEach { (day, label) ->
                    ChipBox(
                        text = label,
                        selected = day in config.trainingDays,
                        modifier = Modifier.weight(1f)
                    ) { onToggleDay(day) }
                }
            }
        }
    }
}

/** 组间休息时长：打勾后自动倒计时用的秒数。 */
@Composable
private fun RestDurationCard(restSeconds: Int, onChange: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AppleGray6)
            .border(1.dp, AppleHairline, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(
                text = "组间休息时长",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextPrimary
            )
            Text(
                text = "打勾后自动开始倒计时；大重量复合动作可调到 180s",
                fontSize = 11.sp,
                color = AppleTextTertiary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(60 to "60s", 90 to "90s", 120 to "2min", 180 to "3min", 240 to "4min").forEach { (sec, label) ->
                ChipBox(
                    text = label,
                    selected = restSeconds == sec,
                    modifier = Modifier.weight(1f)
                ) { onChange(sec) }
            }
        }
    }
}


@Composable
private fun SettingRowLabel(text: String) {
    Text(text = text, fontSize = 11.sp, color = AppleTextSecondary)
}

@Composable
private fun ChipBox(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White else AppleGray5)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = if (selected) Color.Black else AppleTextSecondary
        )
    }
}

/** 没有真实 Switch 组件依赖时的轻量开关。 */
@Composable
private fun TogglePill(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(CircleShape)
            .background(if (checked) AppleFitnessGreen else AppleGray4)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) Color.Black else Color.White)
        )
    }
}

private val WEEK_LABELS = listOf(
    DayOfWeek.MONDAY to "一",
    DayOfWeek.TUESDAY to "二",
    DayOfWeek.WEDNESDAY to "三",
    DayOfWeek.THURSDAY to "四",
    DayOfWeek.FRIDAY to "五",
    DayOfWeek.SATURDAY to "六",
    DayOfWeek.SUNDAY to "日"
)

@Composable
private fun TrainingCheckInCalendar(
    trainedDayTypes: Map<LocalDate, String>,
    metricDays: Set<LocalDate>,
    onDaySelected: (LocalDate) -> Unit
) {
    // 存成字符串而不是 YearMonth：rememberSaveable 只要可序列化就能跨「去设置页再回来」存活
    val thisMonth = remember { YearMonth.now() }
    var monthKey by rememberSaveable { mutableStateOf(thisMonth.toString()) }
    val month = remember(monthKey) {
        runCatching { YearMonth.parse(monthKey) }.getOrElse { thisMonth }
    }
    val today = LocalDate.now()

    // 周一为首列；末尾补 null 保证每行 7 格对齐。非本月只留空位，不渲染数字
    val cells = remember(month) { monthGridCells(month) }
    val trainedThisMonth = remember(trainedDayTypes, month) {
        trainedDayTypes.keys.any { YearMonth.from(it) == month }
    }

    // ⚠️ 卡片水平内边距 8 与网格/图例的水平内边距 8 是一对：
    // 两者之和恒为 16，网格内宽才是 361-8-8-8-8 = 329 → 7 列每格 47dp。
    // 只改任一边，格宽就变 49.3dp，七列全部要重算。上边距 8（不是 16）是有意的：
    // 那 8dp 让给了底部余量，别当成笔误「修正」。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppleGray6)
            .border(1.dp, AppleHairline, RoundedCornerShape(20.dp))
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 月份标题居中：两侧各 48dp 触控盒，等宽自动对称（点击挂在 48 的盒子上，不是 26 圆）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonthNavButton(forward = false) { monthKey = month.minusMonths(1).toString() }
            Text(
                text = "${month.year}年${month.monthValue}月",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            MonthNavButton(forward = true) { monthKey = month.plusMonths(1).toString() }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    color = AppleTextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                week.forEach { day ->
                    if (day == null) {
                        // 非本月：占位但不出数字、不进无障碍树、不可点
                        Spacer(modifier = Modifier.weight(1f).height(48.dp))
                    } else {
                        DayCell(
                            day = day,
                            isTrained = trainedDayTypes.containsKey(day),
                            hasMetric = metricDays.contains(day),
                            isToday = day == today,
                            onClick = { onDaySelected(day) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (!trainedThisMonth) {
            Text(
                text = "这个月还没有训练记录 · 去底部「训练」开始",
                fontSize = 11.sp,
                color = AppleTextSecondary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        // 图例行固定 48dp 高：「回今天」出现/消失时整卡不许跳
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .padding(horizontal = 8.dp)
                .background(AppleHairline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "图例：绿色为已训练，蓝点为有体测"
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(AppleFitnessGreen)
                )
                Text(
                    text = "已训练",
                    fontSize = 11.sp,
                    color = AppleTextSecondary,
                    modifier = Modifier.padding(start = 5.dp)
                )
                Box(
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(AppleSystemBlue)
                )
                Text(
                    text = "有体测",
                    fontSize = 11.sp,
                    color = AppleTextSecondary,
                    modifier = Modifier.padding(start = 5.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            // 翻到别的月份后必须有回本月的路径；点击挂在 48dp 高的盒子上，不挂在 11sp 文字上
            if (month != thisMonth) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable { monthKey = thisMonth.toString() }
                        .semantics {
                            role = Role.Button
                            contentDescription = "回到本月"
                        }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "回今天", fontSize = 11.sp, color = AppleTextSecondary)
                }
            }
        }
    }
}

/**
 * 一天的格子：47×48 触控格 + 恒定 34dp 视觉槽位。
 *
 * 日历格是**导航入口**，不是开关：空白日也能点开（空态里可以直接补记体测），
 * 所以这里没有 `clickable(enabled = ...)` —— 那是「只记了体测的日子点不开」的根因。
 * 34dp 槽位在未训练日也照常渲染（透明占位），翻月时数字不会上下跳。
 */
@Composable
private fun DayCell(
    day: LocalDate,
    isTrained: Boolean,
    hasMetric: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spoken = remember(day, isTrained, hasMetric, isToday) {
        buildString {
            if (isToday) append("今天，")
            append("${day.monthValue}月${day.dayOfMonth}日，")
            append(if (isTrained) "已训练" else "无训练")
            append("，")
            append(if (hasMetric) "有体测记录" else "无体测记录")
        }
    }
    // 34dp 上的 14dp 圆角是 squircle，不是正圆；41dp 外环用同心圆角（14 + 3.5）
    val shape = RoundedCornerShape(14.dp)
    val ringShape = RoundedCornerShape(17.dp)
    Box(
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = spoken
            },
        contentAlignment = Alignment.Center
    ) {
        // 41dp 包络恒定渲染：今天画环，非今天只是一层空壳，保证 34dp 绿盘在两种状态下都居中且等大
        Box(
            modifier = Modifier
                .size(41.dp)
                .then(
                    if (isToday) Modifier.border(1.5.dp, AppleFitnessGreen, ringShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(shape)
                    .background(if (isTrained) AppleFitnessGreen else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${day.dayOfMonth}",
                    fontSize = 13.sp,
                    fontWeight = if (isTrained || isToday) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    color = when {
                        isTrained -> Color.Black
                        isToday -> AppleFitnessGreen
                        else -> AppleTextSecondary
                    }
                )
            }
        }
        // 有体测：4dp 蓝点放格子右上角（y2–6），落在 41dp 环的圆角切口之外，
        // 与「今天」的双层环永不重叠 —— 所以绿盘不必缩小。
        if (hasMetric) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(AppleSystemBlue)
                    .border(0.5.dp, Color.Black.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}

/**
 * 48×48 触控盒里放 26dp 视觉圆（箭头 14sp）；点击挂在 48 的盒子上。
 *
 * 48 是本轮团队统一下限，**不是框架自动撑出来的**：纯 [clickable] 不做
 * minimumInteractiveComponentSize，写 44 就是 44，不会有任何机制兜住回归，所以要显式写死。
 */
@Composable
private fun MonthNavButton(forward: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(29.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = if (forward) "下个月" else "上个月"
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(AppleGray5),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (forward) "›" else "‹",
                fontSize = 14.sp,
                color = AppleTextPrimary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(
    day: LocalDate,
    detail: WorkoutDayDetail?,
    metric: BodyMetricEntity?,
    typeLabel: String?,
    onRecordMetric: () -> Unit,
    onShareToAi: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
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
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = day.format(dateFormat),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleFitnessGreen,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        // 空白日也能点开（日历格是导航入口）：没有训练就说清楚，而不是假装还在加载。
                        // 有训练记录但明细还没到时才显示「加载中…」。
                        text = detail?.title ?: if (typeLabel != null) "加载中…" else "这天没有训练记录",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleTextPrimary
                    )
                    // 训练类型（推 / 拉 / 腿）从日历格里挪到这里：9sp 的字号在格内读不出来
                    if (typeLabel != null) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppleGray5)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = typeLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppleFitnessGreen
                            )
                        }
                    }
                }

                if (detail != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppleFitnessGreen)
                            .clickable { onShareToAi() }
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }

            if (detail != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppleGray5)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    DetailMetric("累计容量", "${detail.totalVolumeKg.toInt()} kg", AppleFitnessGreen)
                    DetailMetric("完成组数", "${detail.groups.sumOf { it.sets.size }} 组", AppleTextPrimary)
                    DetailMetric("动作数", "${detail.groups.size}", AppleTextPrimary)
                }

                // M3a：训练当天写的自述备注
                if (detail.notes.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppleGray5)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "笔记", fontSize = 11.sp, color = AppleTextSecondary)
                        Text(
                            text = detail.notes,
                            fontSize = 12.sp,
                            color = AppleTextPrimary,
                            lineHeight = 17.sp
                        )
                    }
                }

                detail.groups.forEach { group ->
                    ExerciseGroupCard(group)
                }
            }

            // M3b：当天称重就顺手记
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppleGray5)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "当天身体数据",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppleGray6)
                            .clickable { onRecordMetric() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (metric == null) "记一次" else "修改",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleFitnessGreen
                        )
                    }
                }
                Text(
                    text = metric?.let {
                        listOfNotNull(
                            it.weightKg?.let { v -> "体重 ${formatMetric(v)} kg" },
                            it.bodyFatPct?.let { v -> "体脂 ${formatMetric(v)} %" },
                            it.chestCm?.let { v -> "胸围 ${formatMetric(v)} cm" },
                            it.waistCm?.let { v -> "腰围 ${formatMetric(v)} cm" },
                            it.armCm?.let { v -> "臂围 ${formatMetric(v)} cm" }
                        ).joinToString(" · ").ifBlank { "这一天没有记录数值" }
                    } ?: "这一天还没称",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (metric == null) AppleTextTertiary else AppleTextPrimary
                )
            }

            // 历史纠正入口：误记（如 60kg 打成 600kg）此前完全无法修正，会永久污染 PR 与容量曲线。
            // 与上面的「记一次体测」拉开 24dp 并加一条分割线：两者相邻时极易误触。
            if (detail != null) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .height(0.5.dp)
                            .background(AppleHairline)
                    )
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "删除这天的训练记录",
                            fontSize = 12.sp,
                            color = Color(0xFFFF453A)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailMetric(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 11.sp, color = AppleTextSecondary)
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = valueColor
        )
    }
}

/** P1：Health Connect 设置与本地隐私保障告示。 */
@Composable
private fun HealthConnectSettingsCard(
    enabled: Boolean,
    hasPermissions: Boolean,
    isAvailable: Boolean,
    onToggle: (Boolean) -> Unit,
    onRequestPermissions: () -> Unit,
    onSync: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AppleGray6)
            .border(1.dp, AppleHairline, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "Health Connect 双向同步",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary
                )
                Text(
                    text = "归档训练自动写入健康底座供 Muse 读取；亦可从系统健康底座（如智能秤）自动读取体重/体脂记录进行闭环同步。",
                    fontSize = 11.sp,
                    color = AppleTextSecondary,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            TogglePill(checked = enabled, onCheckedChange = onToggle)
        }

        // 闭环同步触发按钮
        if (enabled && isAvailable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppleGray5)
                    .clickable { onSync() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = LucideIcons.Activity,
                        contentDescription = null,
                        tint = AppleFitnessGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "从 Health Connect 同步最新身体指标",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                }
                Text(
                    text = "立即同步 ›",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleFitnessGreen
                )
            }
        }

        // 权限状态与申请
        if (enabled && isAvailable && !hasPermissions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppleCautionOrange.copy(alpha = 0.12f))
                    .border(0.5.dp, AppleCautionOrange.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "尚未授予系统完整读写权限",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppleCautionOrange
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppleCautionOrange)
                        .clickable { onRequestPermissions() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "立即授权",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }

        // 本地隐私安全告示
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AppleGray5)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                text = "🔒 本地安全：数据仅保存在本机系统 Health Connect 与本地数据库中，不经过任何自建云端或第三方服务器，100% 掌握在您的设备中。",
                fontSize = 11.sp,
                color = AppleTextTertiary,
                lineHeight = 16.sp
            )
        }
    }
}

/** P0：数据备份与恢复卡片 */
@Composable
private fun DataBackupSettingsCard(
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AppleGray6)
            .border(1.dp, AppleHairline, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(
                text = "数据备份与恢复",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextPrimary
            )
            Text(
                text = "100% 离线单机数据安全保障。支持导出全部训练记录与身体指标为 JSON 文件，或从备份文件恢复（支持合并与覆盖）。",
                fontSize = 11.sp,
                color = AppleTextSecondary,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppleFitnessGreen)
                    .clickable { onExport() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = LucideIcons.Share2,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "导出数据备份",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppleGray5)
                    .border(0.5.dp, AppleHairline, RoundedCornerShape(14.dp))
                    .clickable { onImport() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = LucideIcons.Layers,
                        contentDescription = null,
                        tint = AppleTextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "从文件恢复",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                }
            }
        }
    }
}

/** P0：恢复导入确认弹层 */
@Composable
private fun RestoreConfirmationDialog(
    summary: BackupSummary,
    onConfirm: (RestoreMode) -> Unit,
    onDismiss: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINESE) }
    val exportDateText = remember(summary.exportedAt) {
        Instant.ofEpochMilli(summary.exportedAt).atZone(ZoneId.systemDefault()).format(formatter)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppleGray6,
        title = {
            Text(
                text = "发现备份文件",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "备份时间：$exportDateText",
                    fontSize = 12.sp,
                    color = AppleTextSecondary
                )
                Text(
                    text = "• 训练记录：${summary.sessionCount} 次（共 ${summary.setCount} 组）\n" +
                           "• 身体数据：${summary.bodyMetricCount} 条\n" +
                           "• 动作库：${summary.exerciseCount} 个动作",
                    fontSize = 12.sp,
                    color = AppleTextPrimary,
                    lineHeight = 18.sp
                )
                Text(
                    text = "请选择恢复方式：\n• 合并导入（推荐）：保留现有记录，补充历史数据并按时间去重；\n• 覆盖导入：清空当前数据，完全替换为该备份。",
                    fontSize = 11.sp,
                    color = AppleTextTertiary,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(RestoreMode.MERGE) }
            ) {
                Text("合并导入 (推荐)", color = AppleFitnessGreen, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { onConfirm(RestoreMode.OVERWRITE) }
                ) {
                    Text("覆盖导入", color = Color(0xFFFF453A))
                }
                TextButton(
                    onClick = onDismiss
                ) {
                    Text("取消", color = AppleTextSecondary)
                }
            }
        }
    )
}
