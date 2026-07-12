package com.example.attendancecompanion.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancecompanion.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProfileUiState(
    val student: StudentEntity? = null,
    val settings: SettingsEntity = SettingsEntity()
)

class ProfileViewModel(private val repository: DataRepository) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        repository.getStudent(),
        repository.getSettings()
    ) { student, settings ->
        ProfileUiState(student = student, settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    fun updateStudent(registerNumber: String, displayName: String, masterLink: String) {
        viewModelScope.launch {
            val student = repository.getStudentDirect()?.copy(
                registerNumber = registerNumber.trim().uppercase(),
                displayName = displayName.trim(),
                masterLink = masterLink.trim()
            ) ?: StudentEntity(
                registerNumber = registerNumber.trim().uppercase(),
                displayName = displayName.trim(),
                masterLink = masterLink.trim()
            )
            repository.saveStudent(student)
        }
    }

    fun updateTheme(themeName: String) {
        viewModelScope.launch {
            val current = repository.getSettingsDirect()
            repository.saveSettings(current.copy(theme = themeName))
        }
    }

    fun updateNotifications(enabled: Boolean, preAlertMins: Int) {
        viewModelScope.launch {
            val current = repository.getSettingsDirect()
            repository.saveSettings(current.copy(
                notificationsEnabled = enabled,
                preAlertMinutes = preAlertMins
            ))
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            repository.clearAllData()
        }
    }
}
