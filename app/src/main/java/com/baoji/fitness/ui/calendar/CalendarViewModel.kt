package com.baoji.fitness.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baoji.fitness.core.database.entity.BodyMetricEntity
import com.baoji.fitness.data.repository.AiShareContext
import com.baoji.fitness.data.repository.BodyMetricRepository
import com.baoji.fitness.data.repository.WorkoutRepository
import com.baoji.fitness.data.settings.ReminderConfig
import com.baoji.fitness.data.settings.ReminderSettings
import com.baoji.fitness.domain.algorithm.DateWindow
import com.baoji.fitness.domain.algorithm.PeriodReviewCalculator
import com.baoji.fitness.domain.algorithm.ReviewPeriod
import com.baoji.fitness.domain.algorithm.StreakCalculator
import com.baoji.fitness.domain.model.WorkoutDayDetail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

import android.content.Context
import com.baoji.fitness.data.backup.BackupManager
import com.baoji.fitness.data.backup.BackupSummary
import com.baoji.fitness.data.backup.RestoreMode
import com.baoji.fitness.data.backup.RestoreResult
import com.baoji.fitness.data.healthconnect.HealthConnectWriter
import java.io.File

/** M2 习惯面板：连续天数 + 本周还剩几练。 */
data class HabitSummary(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val remainingThisWeek: Int = 0
)

/** 本月容量结论块：当月与上月对照（口径见 [PeriodReviewCalculator]）。 */
data class MonthVolume(
    val currentKg: Double = 0.0,
    val previousKg: Double = 0.0
)

class CalendarViewModel(
    private val repository: WorkoutRepository,
    private val bodyMetricRepository: BodyMetricRepository,
    private val reminderSettings: ReminderSettings,
    private val backupManager: BackupManager
) : ViewModel() {

    /** 已打卡的训练日 → 训练类型（推/拉/腿），供月历显示。 */
    val trainedDayTypes: StateFlow<Map<LocalDate, String>> = repository.getTrainedDayTypes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ---- M2 习惯 -------------------------------------------------------------

    /** 连续训练天数：直接复用已经在内存里的打卡日期集合，不需要新查询。 */
    val habit: StateFlow<HabitSummary> = combine(
        trainedDayTypes,
        reminderSettings.config
    ) { days, config ->
        val today = LocalDate.now()
        HabitSummary(
            currentStreak = StreakCalculator.currentStreak(days.keys, today),
            longestStreak = StreakCalculator.longestStreak(days.keys),
            remainingThisWeek = StreakCalculator.remainingPlannedDaysThisWeek(
                trainedDays = days.keys,
                today = today,
                plannedDays = config.trainingDays
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HabitSummary())

    val reminderConfig: StateFlow<ReminderConfig> = reminderSettings.config

    /**
     * 本月容量 + 上月对照，供页首「结论三块」使用。
     *
     * 口径**不自己发明**：直接走 [PeriodReviewCalculator] 的纯函数
     * （容量 = Σ weightKg × reps，排除 `setType == "WARMUP"`，按会话开始日归属），
     * 与训练页 / 负荷页同一份实现，将来口径只改一处。
     * 数据源是已有的两条 flow，没有新增 DAO 查询。
     */
    val monthVolume: StateFlow<MonthVolume> = combine(
        repository.getAllSessions(),
        repository.getAllCompletedSets()
    ) { sessions, sets ->
        val zone = ZoneId.systemDefault()
        val windows = PeriodReviewCalculator.resolveWindows(ReviewPeriod.THIS_MONTH, LocalDate.now())
        fun volumeIn(window: DateWindow): Double = PeriodReviewCalculator.volumeOf(
            PeriodReviewCalculator.setsOf(
                sets,
                PeriodReviewCalculator.sessionsIn(sessions, window, zone)
            )
        )
        MonthVolume(
            currentKg = volumeIn(windows.current),
            previousKg = windows.previous?.let { volumeIn(it) } ?: 0.0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthVolume())

    /** 有体测记录的本地日期集合 —— 日历上的「有体测」小圆点。 */
    val metricDays: StateFlow<Set<LocalDate>> = bodyMetricRepository.observeAll()
        .map { metrics -> metrics.mapTo(mutableSetOf()) { bodyMetricRepository.toLocalDate(it.date) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** 「查看今天明细」入口右侧的摘要（动作数 / 组数）；今天没练 = null。 */
    val todayDetail: StateFlow<WorkoutDayDetail?> = repository.getDayDetail(LocalDate.now())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * 打开提醒开关 → 由 Activity 去申请 POST_NOTIFICATIONS。
     * ViewModel 不碰 Context，这里只发一个「该问了」的信号。
     */
    private val _permissionRequest = MutableStateFlow(false)
    val permissionRequest: StateFlow<Boolean> = _permissionRequest.asStateFlow()

    fun setReminderEnabled(enabled: Boolean) {
        reminderSettings.setEnabled(enabled)
        if (enabled) _permissionRequest.value = true
    }

    fun onPermissionRequestHandled() {
        _permissionRequest.value = false
    }

    fun setReminderTime(hour: Int, minute: Int) =
        reminderSettings.setTime(hour, minute)

    fun toggleTrainingDay(day: DayOfWeek) {
        val current = reminderSettings.config.value.trainingDays
        val next = if (day in current) current - day else current + day
        if (next.isNotEmpty()) reminderSettings.setTrainingDays(next)
    }

    /** 组间休息时长（打勾后自动倒计时）。 */
    fun setRestSeconds(seconds: Int) = reminderSettings.setRestSeconds(seconds)

    // ---- 日历明细 ------------------------------------------------------------

    private val _selectedDay = MutableStateFlow<LocalDate?>(null)
    val selectedDay: StateFlow<LocalDate?> = _selectedDay.asStateFlow()

    /** 点选当天的训练明细；未选中或该天无完成组时为 null。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val dayDetail: StateFlow<WorkoutDayDetail?> = _selectedDay
        .flatMapLatest { day -> if (day == null) flowOf(null) else repository.getDayDetail(day) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 当天称的数据（明细弹层尾部顺手记）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val dayMetric: StateFlow<BodyMetricEntity?> = _selectedDay
        .flatMapLatest { day ->
            if (day == null) flowOf(null) else bodyMetricRepository.observeForDate(day)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveMetric(
        day: LocalDate,
        weightKg: Double?,
        bodyFatPct: Double?,
        heightCm: Double?,
        chestCm: Double?,
        waistCm: Double?,
        armCm: Double?
    ) {
        viewModelScope.launch {
            bodyMetricRepository.upsert(day, weightKg, bodyFatPct, heightCm, chestCm, waistCm, armCm)
        }
    }

    fun selectDay(day: LocalDate?) {
        _selectedDay.value = day
    }

    suspend fun getAiShareContext(detail: WorkoutDayDetail): AiShareContext {
        val metrics = bodyMetricRepository.observeAll().first()
        return repository.getAiShareContext(detail, metrics)
    }

    fun setHealthConnectEnabled(enabled: Boolean) {
        reminderSettings.setHealthConnectEnabled(enabled)
    }

    // ---- P0 数据备份与恢复 ----

    fun exportBackup(onResult: (Result<File>) -> Unit) {
        viewModelScope.launch {
            val result = backupManager.exportBackup()
            onResult(result)
        }
    }

    fun shareBackup(file: File) {
        backupManager.shareBackup(file)
    }

    fun parseBackupSummary(jsonString: String): Result<BackupSummary> {
        return backupManager.parseSummary(jsonString)
    }

    fun restoreBackup(jsonString: String, mode: RestoreMode, onResult: (Result<RestoreResult>) -> Unit) {
        viewModelScope.launch {
            val result = backupManager.restore(jsonString, mode)
            onResult(result)
        }
    }

    // ---- 历史纠正：删掉误记的一整天训练 ----

    fun deleteWorkoutDay(day: LocalDate, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            onResult(repository.deleteWorkoutDay(day))
        }
    }

    // ---- P1 Health Connect 闭环读取与同步 ----

    fun syncFromHealthConnect(context: Context, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = HealthConnectWriter.syncBodyMetricsFromHealthConnect(context, bodyMetricRepository)
            onResult(result)
        }
    }
}
