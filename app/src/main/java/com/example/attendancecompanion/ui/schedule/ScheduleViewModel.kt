package com.example.attendancecompanion.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancecompanion.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class TimetableItem(
    val period: PeriodEntity,
    val subject: SubjectEntity?
)

data class ScheduleUiState(
    val subjects: List<SubjectEntity> = emptyList(),
    val periods: List<PeriodEntity> = emptyList(),
    val weekSelectedDay: Int = 1, // 1 = Monday ... 7 = Sunday
    val timetableByDay: Map<Int, List<TimetableItem>> = emptyMap()
)

class ScheduleViewModel(private val repository: DataRepository) : ViewModel() {

    private val _weekSelectedDay = MutableStateFlow(getAppDayOfWeek())
    val weekSelectedDay: StateFlow<Int> = _weekSelectedDay.asStateFlow()

    val uiState: StateFlow<ScheduleUiState> = combine(
        repository.getAllSubjects(),
        repository.getAllPeriods(),
        _weekSelectedDay
    ) { subjects, periods, selectedDay ->
        val subjectsMap = subjects.associateBy { it.id }
        
        // Group periods by day of week
        val grouped = periods.groupBy { it.dayOfWeek }
            .mapValues { entry ->
                entry.value.map { TimetableItem(it, subjectsMap[it.subjectId]) }
                    .sortedBy { it.period.startTime }
            }

        ScheduleUiState(
            subjects = subjects,
            periods = periods,
            weekSelectedDay = selectedDay,
            timetableByDay = grouped
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleUiState())

    fun selectDay(day: Int) {
        _weekSelectedDay.value = day
    }

    fun savePeriod(
        periodId: Long = 0,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        subjectName: String,
        subjectColorHex: String,
        room: String,
        notesUrl: String
    ) {
        viewModelScope.launch {
            // 1. Find or create subject
            val existingSubjects = uiState.value.subjects
            val matchedSubject = existingSubjects.find { it.name.trim().lowercase() == subjectName.trim().lowercase() }
            
            val subjectId = if (matchedSubject != null) {
                // Update default room or notes URL if changed
                if (matchedSubject.defaultRoom != room || matchedSubject.notesUrl != notesUrl || matchedSubject.colorHex != subjectColorHex) {
                    repository.saveSubject(
                        matchedSubject.copy(
                            colorHex = subjectColorHex,
                            defaultRoom = room,
                            notesUrl = notesUrl
                        )
                    )
                }
                matchedSubject.id
            } else {
                repository.saveSubject(
                    SubjectEntity(
                        name = subjectName.trim(),
                        colorHex = subjectColorHex,
                        defaultRoom = room,
                        notesUrl = notesUrl
                    )
                )
            }

            // 2. Save period
            val period = PeriodEntity(
                id = periodId,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                subjectId = subjectId,
                roomOverride = room
            )
            repository.savePeriod(period)
        }
    }

    fun deletePeriod(period: PeriodEntity) {
        viewModelScope.launch {
            repository.deletePeriod(period)
        }
    }

    private fun getAppDayOfWeek(): Int {
        val calendar = Calendar.getInstance()
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
}
