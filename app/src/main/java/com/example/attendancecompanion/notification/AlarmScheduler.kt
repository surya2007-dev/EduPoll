package com.example.attendancecompanion.notification

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.attendancecompanion.AttendanceApplication
import com.example.attendancecompanion.data.PeriodEntity
import com.example.attendancecompanion.data.SubjectEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

object AlarmScheduler {

    fun scheduleAlarms(context: Context) {
        val app = context.applicationContext as AttendanceApplication
        val repository = app.repository
        
        CoroutineScope(Dispatchers.IO).launch {
            val student = repository.getStudentDirect() ?: return@launch
            val settings = repository.getSettingsDirect()
            
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // Query all periods and subjects
            val periods = repository.getAllPeriods().first()
            val subjects = repository.getAllSubjects().first().associateBy { it.id }

            // Clear previous intents
            periods.forEach { period ->
                cancelAlarmForPeriod(context, alarmManager, period)
            }

            if (!settings.notificationsEnabled) return@launch

            // Check if we can schedule exact alarms
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                // If not allowed, fallback or log (cannot schedule exact alarm, will fail if we call setExact)
                return@launch
            }

            periods.forEach { period ->
                val subject = subjects[period.subjectId] ?: return@forEach
                val alarmTime = calculateAlarmTime(period, settings.preAlertMinutes)
                
                if (alarmTime > System.currentTimeMillis()) {
                    scheduleExactAlarm(context, alarmManager, period, subject, alarmTime)
                }
            }
        }
    }

    private fun cancelAlarmForPeriod(context: Context, alarmManager: AlarmManager, period: PeriodEntity) {
        val intent = Intent(context, AttendanceAlertReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            period.id.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleExactAlarm(
        context: Context,
        alarmManager: AlarmManager,
        period: PeriodEntity,
        subject: SubjectEntity,
        triggerAtMillis: Long
    ) {
        val intent = Intent(context, AttendanceAlertReceiver::class.java).apply {
            putExtra("PERIOD_ID", period.id)
            putExtra("SUBJECT_NAME", subject.name)
            putExtra("ROOM", period.roomOverride.ifEmpty { subject.defaultRoom })
            putExtra("START_TIME", period.startTime)
            putExtra("END_TIME", period.endTime)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            period.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    private fun calculateAlarmTime(period: PeriodEntity, leadMinutes: Int): Long {
        val calendar = Calendar.getInstance()
        val currentDay = getAppDayOfWeek(calendar)
        
        // Parse period start time
        val timeParts = period.startTime.split(":")
        val periodHour = timeParts[0].toInt()
        val periodMin = timeParts[1].toInt()

        val targetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, periodHour)
            set(Calendar.MINUTE, periodMin)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Calculate days to add to get to the period's day of week
        var daysDiff = period.dayOfWeek - currentDay
        if (daysDiff < 0 || (daysDiff == 0 && targetCal.timeInMillis - (leadMinutes * 60 * 1000) <= System.currentTimeMillis())) {
            // If the day is in the past, or today but the alarm time has already passed, schedule for next week
            daysDiff += 7
        }

        targetCal.add(Calendar.DAY_OF_YEAR, daysDiff)
        // Subtract lead minutes
        targetCal.add(Calendar.MINUTE, -leadMinutes)

        return targetCal.timeInMillis
    }

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
}
