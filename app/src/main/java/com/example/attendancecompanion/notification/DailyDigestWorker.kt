package com.example.attendancecompanion.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.example.attendancecompanion.AttendanceApplication
import com.example.attendancecompanion.MainActivity
import com.example.attendancecompanion.data.PeriodEntity
import com.example.attendancecompanion.data.SubjectEntity
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DailyDigestWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val app = context.applicationContext as AttendanceApplication
        val repository = app.repository
        
        val settings = repository.getSettingsDirect()
        if (!settings.notificationsEnabled) return Result.success()

        val calendar = Calendar.getInstance()
        val dayOfWeek = getAppDayOfWeek(calendar)
        
        // Fetch classes for today
        val periods = repository.getAllPeriods().first().filter { it.dayOfWeek == dayOfWeek }
            .sortedBy { it.startTime }
        val subjects = repository.getAllSubjects().first().associateBy { it.id }

        if (periods.isEmpty()) {
            return Result.success() // No classes today, skip notification
        }

        // Build digest summary text
        val summary = StringBuilder()
        periods.forEachIndexed { index, period ->
            val subName = subjects[period.subjectId]?.name ?: "Class"
            summary.append("${index + 1}. $subName — ${period.startTime}\n")
        }

        val channelId = "daily_digest_channel"
        createNotificationChannel(channelId)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            999,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Today's Classes")
            .setContentText("You have ${periods.size} classes today.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary.toString().trim()))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(999, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        return Result.success()
    }

    private fun createNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Daily Timetable Digest"
            val descriptionText = "Morning class summaries"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
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

    companion object {
        fun scheduleDailyDigest(context: Context) {
            val calendar = Calendar.getInstance()
            val nowMillis = calendar.timeInMillis
            
            // Set target time to 8:45 AM today
            val targetCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 45)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            if (targetCal.timeInMillis <= nowMillis) {
                // If it's already past 8:45 AM today, schedule for tomorrow
                targetCal.add(Calendar.DAY_OF_YEAR, 1)
            }
            
            val delayMinutes = (targetCal.timeInMillis - nowMillis) / (60 * 1000)

            val digestRequest = PeriodicWorkRequestBuilder<DailyDigestWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                "daily_digest_work",
                ExistingPeriodicWorkPolicy.UPDATE,
                digestRequest
            )
        }
    }
}
