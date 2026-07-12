package com.example.attendancecompanion.ui.history

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancecompanion.data.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class HistoryViewMode {
    BY_DAY, BY_SUBJECT
}

data class HistoryDayItem(
    val record: AttendanceRecordEntity,
    val period: PeriodEntity?,
    val subject: SubjectEntity?
)

data class HistorySubjectAnalytic(
    val subject: SubjectEntity,
    val attendedCount: Int,
    val totalCount: Int,
    val percentage: Float // 0f to 100f
)

data class HistoryUiState(
    val viewMode: HistoryViewMode = HistoryViewMode.BY_DAY,
    val recordsByDate: List<Pair<String, List<HistoryDayItem>>> = emptyList(),
    val subjectAnalytics: List<HistorySubjectAnalytic> = emptyList()
)

class HistoryViewModel(private val repository: DataRepository) : ViewModel() {

    private val _viewMode = MutableStateFlow(HistoryViewMode.BY_DAY)
    val viewMode: StateFlow<HistoryViewMode> = _viewMode.asStateFlow()

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.getAllSubjects(),
        repository.getAllPeriods(),
        repository.getAllAttendanceRecords(),
        _viewMode
    ) { subjects, periods, records, mode ->
        val subjectsMap = subjects.associateBy { it.id }
        val periodsMap = periods.associateBy { it.id }

        // 1. Group records by Date
        val sortedRecords = records.sortedWith(compareByDescending<AttendanceRecordEntity> { it.date }
            .thenBy { periodsMap[it.periodId]?.startTime ?: "" })
            
        val recordsByDateMap = sortedRecords.groupBy { it.date }
            .map { entry ->
                val dateText = formatUiDate(entry.key)
                val items = entry.value.map { r ->
                    val p = periodsMap[r.periodId]
                    val s = p?.let { subjectsMap[it.subjectId] }
                    HistoryDayItem(r, p, s)
                }
                dateText to items
            }

        // 2. Compute Subject Analytics
        // Count how many records exist for each subject
        val subjectAnalyticsList = subjects.map { subject ->
            val subPeriods = periods.filter { it.subjectId == subject.id }.map { it.id }.toSet()
            val subRecords = records.filter { subPeriods.contains(it.periodId) }
            
            val attended = subRecords.count { it.status == "MARKED" }
            val total = subRecords.size
            val percent = if (total > 0) (attended.toFloat() / total * 100f) else 100f // Default to 100% if no classes taken yet

            HistorySubjectAnalytic(
                subject = subject,
                attendedCount = attended,
                totalCount = total,
                percentage = percent
            )
        }.sortedBy { it.subject.name }

        HistoryUiState(
            viewMode = mode,
            recordsByDate = recordsByDateMap,
            subjectAnalytics = subjectAnalyticsList
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    fun setViewMode(mode: HistoryViewMode) {
        _viewMode.value = mode
    }

    suspend fun exportToCsv(context: Context): Uri? {
        val subjects = uiState.value.subjectAnalytics.map { it.subject }.associateBy { it.id }
        val periods = repository.getAllPeriods().first().associateBy { it.id }
        val records = repository.getAllAttendanceRecords().first()

        val csvString = StringBuilder()
        csvString.append("Date,Time Slot,Subject,Status,Marked At\n")
        
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        records.sortedByDescending { it.date }.forEach { r ->
            val p = periods[r.periodId]
            val s = p?.let { subjects[it.subjectId] }
            val subjectName = s?.name ?: "Unknown"
            val timeSlot = p?.let { "${it.startTime}-${it.endTime}" } ?: "Unknown"
            val markedAtText = if (r.markedAt > 0) sdf.format(Date(r.markedAt)) else "N/A"
            
            // CSV escape fields
            csvString.append("\"${r.date}\",\"$timeSlot\",\"$subjectName\",\"${r.status}\",\"$markedAtText\"\n")
        }

        return try {
            val cacheFile = File(context.cacheDir, "attendance_history.csv")
            cacheFile.writeText(csvString.toString())
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun formatUiDate(dateStr: String): String {
        return try {
            val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdfInput.parse(dateStr)
            val sdfOutput = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
            date?.let { sdfOutput.format(it).uppercase() } ?: dateStr
        } catch (e: Exception) {
            dateStr
        }
    }
}
