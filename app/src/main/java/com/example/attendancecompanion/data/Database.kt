package com.example.attendancecompanion.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 1. Entities
@Entity(tableName = "student")
data class StudentEntity(
    @PrimaryKey val id: Int = 1,
    val registerNumber: String,
    val displayName: String = "",
    val masterLink: String = ""
)

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,
    val defaultRoom: String = "",
    val notesUrl: String = ""
)

@Entity(tableName = "periods")
data class PeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: Int, // 1 = Monday, ..., 6 = Saturday, 7 = Sunday
    val startTime: String, // "HH:MM" e.g., "09:00"
    val endTime: String, // "HH:MM" e.g., "09:50"
    val subjectId: Long,
    val roomOverride: String = ""
)

@Entity(tableName = "attendance_records")
data class AttendanceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // "YYYY-MM-DD"
    val periodId: Long,
    val status: String, // "MARKED", "MISSED", "UNKNOWN"
    val markedAt: Long = 0 // Timestamp
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val theme: String = "DARK", // "DARK", "LIGHT"
    val notificationsEnabled: Boolean = true,
    val digestTime: String = "08:45", // "HH:MM"
    val preAlertMinutes: Int = 5,
    val use24h: Boolean = false
)

// 2. Data Access Object (DAO)
@Dao
interface AttendanceDao {
    @Query("SELECT * FROM student WHERE id = 1 LIMIT 1")
    fun getStudentFlow(): Flow<StudentEntity?>

    @Query("SELECT * FROM student WHERE id = 1 LIMIT 1")
    suspend fun getStudentDirect(): StudentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: StudentEntity)

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: SettingsEntity)

    @Query("SELECT * FROM subjects ORDER BY name ASC")
    fun getAllSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE id = :id LIMIT 1")
    suspend fun getSubjectById(id: Long): SubjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Delete
    suspend fun deleteSubject(subject: SubjectEntity)

    @Query("SELECT * FROM periods")
    fun getAllPeriods(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM periods WHERE dayOfWeek = :dayOfWeek")
    fun getPeriodsForDay(dayOfWeek: Int): Flow<List<PeriodEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeriod(period: PeriodEntity): Long

    @Delete
    suspend fun deletePeriod(period: PeriodEntity)

    @Query("SELECT * FROM periods WHERE id = :id LIMIT 1")
    suspend fun getPeriodById(id: Long): PeriodEntity?

    @Query("SELECT * FROM attendance_records")
    fun getAllAttendanceRecords(): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE date = :date")
    fun getAttendanceRecordsForDate(date: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE date = :date AND periodId = :periodId LIMIT 1")
    suspend fun getAttendanceRecordForPeriodAndDate(date: String, periodId: Long): AttendanceRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecord(record: AttendanceRecordEntity): Long

    @Delete
    suspend fun deleteAttendanceRecord(record: AttendanceRecordEntity)

    @Query("DELETE FROM student")
    suspend fun clearStudent()

    @Query("DELETE FROM subjects")
    suspend fun clearSubjects()

    @Query("DELETE FROM periods")
    suspend fun clearPeriods()

    @Query("DELETE FROM attendance_records")
    suspend fun clearAttendanceRecords()

    @Query("DELETE FROM settings")
    suspend fun clearSettings()
}

// 3. Database
@Database(
    entities = [
        StudentEntity::class,
        SubjectEntity::class,
        PeriodEntity::class,
        AttendanceRecordEntity::class,
        SettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "attendance_companion_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
