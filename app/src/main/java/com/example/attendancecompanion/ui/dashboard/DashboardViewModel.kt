package com.example.attendancecompanion.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancecompanion.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

enum class PeriodState {
    OPEN, CLOSED, OPENS_SOON
}

data class DashboardPeriodItem(
    val period: PeriodEntity,
    val subject: SubjectEntity?,
    val isMarked: Boolean,
    val isActive: Boolean
)

data class CurrentPeriodState(
    val period: PeriodEntity?,
    val subject: SubjectEntity?,
    val state: PeriodState,
    val countdownText: String = ""
)

data class DashboardUiState(
    val student: StudentEntity? = null,
    val todayDateText: String = "",
    val timeText: String = "",
    val showBlinkingColon: Boolean = true,
    val currentPeriod: CurrentPeriodState = CurrentPeriodState(null, null, PeriodState.CLOSED),
    val nextPeriod: CurrentPeriodState = CurrentPeriodState(null, null, PeriodState.CLOSED),
    val todayPeriods: List<DashboardPeriodItem> = emptyList(),
    val progressMarked: Int = 0,
    val progressTotal: Int = 0
)

class DashboardViewModel(private val repository: DataRepository) : ViewModel() {

    private val timeTicker = flow {
        while (true) {
            emit(Calendar.getInstance())
            delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Calendar.getInstance())

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.getStudent(),
        repository.getAllSubjects(),
        repository.getAllPeriods(),
        repository.getAllAttendanceRecords(),
        timeTicker
    ) { student, subjects, periods, records, calendar ->
        val subjectsMap = subjects.associateBy { it.id }
        
        // 1. Get Day Info
        val dayOfWeek = getAppDayOfWeek(calendar)
        val todayDateText = getTodayDateText(calendar)
        val timeText = getTimeText(calendar)
        val showBlinkingColon = calendar.get(Calendar.SECOND) % 2 == 0
        
        // 2. Fetch today's schedule
        val todayPeriodsList = periods.filter { it.dayOfWeek == dayOfWeek }
            .sortedBy { it.startTime }
        
        val todayDateStr = getTodayDateString(calendar)
        val todayRecords = records.filter { it.date == todayDateStr }
        val markedPeriodIds = todayRecords.filter { it.status == "MARKED" }.map { it.periodId }.toSet()

        val currentMin = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val currentSec = calendar.get(Calendar.SECOND)

        // 3. Map periods to UI list item
        val periodItems = todayPeriodsList.map { p ->
            val startMin = parseTimeToMinutes(p.startTime)
            val endMin = parseTimeToMinutes(p.endTime)
            val isActive = currentMin in startMin until endMin
            val isMarked = markedPeriodIds.contains(p.id)
            DashboardPeriodItem(
                period = p,
                subject = subjectsMap[p.subjectId],
                isMarked = isMarked,
                isActive = isActive
            )
        }

        // 4. Calculate Current Period State
        var currentPeriodState = CurrentPeriodState(null, null, PeriodState.CLOSED)
        var nextPeriodState = CurrentPeriodState(null, null, PeriodState.CLOSED)

        // Find active period
        val activePeriodItem = periodItems.find { it.isActive }
        if (activePeriodItem != null) {
            currentPeriodState = CurrentPeriodState(
                period = activePeriodItem.period,
                subject = activePeriodItem.subject,
                state = PeriodState.OPEN
            )
        } else {
            // Check if any period opens soon (within 15 minutes)
            val upcomingPeriodItem = periodItems.find {
                val startMin = parseTimeToMinutes(it.period.startTime)
                currentMin < startMin && (startMin - currentMin) <= 15
            }
            if (upcomingPeriodItem != null) {
                val startMin = parseTimeToMinutes(upcomingPeriodItem.period.startTime)
                val diffSec = (startMin - currentMin) * 60 - currentSec
                val minStr = String.format("%02d", diffSec / 60)
                val secStr = String.format("%02d", diffSec % 60)
                currentPeriodState = CurrentPeriodState(
                    period = upcomingPeriodItem.period,
                    subject = upcomingPeriodItem.subject,
                    state = PeriodState.OPENS_SOON,
                    countdownText = "$minStr:$secStr"
                )
            }
        }

        // 5. Calculate Next predicted attendance (next period that is not marked done and starts in the future)
        val nextPeriodItem = periodItems.find {
            val startMin = parseTimeToMinutes(it.period.startTime)
            !it.isMarked && (currentMin < startMin || it.isActive)
        }
        if (nextPeriodItem != null) {
            val startMin = parseTimeToMinutes(nextPeriodItem.period.startTime)
            val diffSec = if (currentMin < startMin) {
                (startMin - currentMin) * 60 - currentSec
            } else {
                0
            }
            val minStr = String.format("%02d", diffSec / 60)
            val secStr = String.format("%02d", diffSec % 60)
            nextPeriodState = CurrentPeriodState(
                period = nextPeriodItem.period,
                subject = nextPeriodItem.subject,
                state = if (currentMin >= startMin) PeriodState.OPEN else PeriodState.OPENS_SOON,
                countdownText = if (diffSec > 0) "$minStr:$secStr" else "NOW"
            )
        }

        DashboardUiState(
            student = student,
            todayDateText = todayDateText,
            timeText = timeText,
            showBlinkingColon = showBlinkingColon,
            currentPeriod = currentPeriodState,
            nextPeriod = nextPeriodState,
            todayPeriods = periodItems,
            progressMarked = periodItems.count { it.isMarked },
            progressTotal = periodItems.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    // Actions
    suspend fun markPeriodStatus(periodId: Long, isMarked: Boolean) {
        val calendar = Calendar.getInstance()
        val dateStr = getTodayDateString(calendar)
        val existing = repository.getAttendanceRecordForPeriodAndDate(dateStr, periodId)
        
        if (isMarked) {
            val record = existing?.copy(status = "MARKED", markedAt = System.currentTimeMillis())
                ?: AttendanceRecordEntity(
                    date = dateStr,
                    periodId = periodId,
                    status = "MARKED",
                    markedAt = System.currentTimeMillis()
                )
            repository.saveAttendanceRecord(record)
        } else {
            if (existing != null) {
                repository.deleteAttendanceRecord(existing)
            }
        }
    }

    // Helpers
    private fun getAppDayOfWeek(calendar: Calendar): Int {
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    private fun getTodayDateText(calendar: Calendar): String {
        val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        return sdf.format(calendar.time).uppercase()
    }

    private fun getTimeText(calendar: Calendar): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    private fun getTodayDateString(calendar: Calendar): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    private fun parseTimeToMinutes(time: String): Int {
        return try {
            val parts = time.split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (e: Exception) {
            0
        }
    }
}
