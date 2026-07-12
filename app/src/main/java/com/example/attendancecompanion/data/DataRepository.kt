package com.example.attendancecompanion.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface DataRepository {
    fun getStudent(): Flow<StudentEntity?>
    suspend fun getStudentDirect(): StudentEntity?
    suspend fun saveStudent(student: StudentEntity)
    
    fun getSettings(): Flow<SettingsEntity>
    suspend fun getSettingsDirect(): SettingsEntity
    suspend fun saveSettings(settings: SettingsEntity)
    
    fun getAllSubjects(): Flow<List<SubjectEntity>>
    suspend fun getSubjectById(id: Long): SubjectEntity?
    suspend fun saveSubject(subject: SubjectEntity): Long
    suspend fun deleteSubject(subject: SubjectEntity)
    
    fun getAllPeriods(): Flow<List<PeriodEntity>>
    fun getPeriodsForDay(dayOfWeek: Int): Flow<List<PeriodEntity>>
    suspend fun savePeriod(period: PeriodEntity): Long
    suspend fun deletePeriod(period: PeriodEntity)
    suspend fun getPeriodById(id: Long): PeriodEntity?
    
    fun getAllAttendanceRecords(): Flow<List<AttendanceRecordEntity>>
    fun getAttendanceRecordsForDate(date: String): Flow<List<AttendanceRecordEntity>>
    suspend fun getAttendanceRecordForPeriodAndDate(date: String, periodId: Long): AttendanceRecordEntity?
    suspend fun saveAttendanceRecord(record: AttendanceRecordEntity): Long
    suspend fun deleteAttendanceRecord(record: AttendanceRecordEntity)
    
    suspend fun clearAllData()
}

class DefaultDataRepository(private val dao: AttendanceDao) : DataRepository {
    override fun getStudent(): Flow<StudentEntity?> = dao.getStudentFlow()
    
    override suspend fun getStudentDirect(): StudentEntity? = dao.getStudentDirect()
    
    override suspend fun saveStudent(student: StudentEntity) {
        dao.insertStudent(student)
    }
    
    override fun getSettings(): Flow<SettingsEntity> = dao.getSettingsFlow().map { it ?: SettingsEntity() }
    
    override suspend fun getSettingsDirect(): SettingsEntity = dao.getSettingsDirect() ?: SettingsEntity()
    
    override suspend fun saveSettings(settings: SettingsEntity) {
        dao.insertSettings(settings)
    }
    
    override fun getAllSubjects(): Flow<List<SubjectEntity>> = dao.getAllSubjects()
    
    override suspend fun getSubjectById(id: Long): SubjectEntity? = dao.getSubjectById(id)
    
    override suspend fun saveSubject(subject: SubjectEntity): Long = dao.insertSubject(subject)
    
    override suspend fun deleteSubject(subject: SubjectEntity) = dao.deleteSubject(subject)
    
    override fun getAllPeriods(): Flow<List<PeriodEntity>> = dao.getAllPeriods()
    
    override fun getPeriodsForDay(dayOfWeek: Int): Flow<List<PeriodEntity>> = dao.getPeriodsForDay(dayOfWeek)
    
    override suspend fun savePeriod(period: PeriodEntity): Long = dao.insertPeriod(period)
    
    override suspend fun deletePeriod(period: PeriodEntity) = dao.deletePeriod(period)
    
    override suspend fun getPeriodById(id: Long): PeriodEntity? = dao.getPeriodById(id)
    
    override fun getAllAttendanceRecords(): Flow<List<AttendanceRecordEntity>> = dao.getAllAttendanceRecords()
    
    override fun getAttendanceRecordsForDate(date: String): Flow<List<AttendanceRecordEntity>> = dao.getAttendanceRecordsForDate(date)
    
    override suspend fun getAttendanceRecordForPeriodAndDate(date: String, periodId: Long): AttendanceRecordEntity? =
        dao.getAttendanceRecordForPeriodAndDate(date, periodId)
        
    override suspend fun saveAttendanceRecord(record: AttendanceRecordEntity): Long = dao.insertAttendanceRecord(record)
    
    override suspend fun deleteAttendanceRecord(record: AttendanceRecordEntity) = dao.deleteAttendanceRecord(record)
    
    override suspend fun clearAllData() {
        dao.clearStudent()
        dao.clearSubjects()
        dao.clearPeriods()
        dao.clearAttendanceRecords()
        dao.clearSettings()
    }
}
