package com.example.attendancecompanion

import android.app.Application
import com.example.attendancecompanion.data.AppDatabase
import com.example.attendancecompanion.data.DefaultDataRepository

class AttendanceApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { DefaultDataRepository(database.attendanceDao()) }
}
