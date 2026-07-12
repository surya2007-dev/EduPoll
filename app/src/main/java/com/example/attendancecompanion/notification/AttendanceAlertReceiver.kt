package com.example.attendancecompanion.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.attendancecompanion.MainActivity

class AttendanceAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val periodId = intent.getLongExtra("PERIOD_ID", -1)
        val subjectName = intent.getStringExtra("SUBJECT_NAME") ?: "Class"
        val room = intent.getStringExtra("ROOM") ?: "Classroom"
        val startTime = intent.getStringExtra("START_TIME") ?: "09:00"

        val channelId = "attendance_alerts_channel"
        createNotificationChannel(context, channelId)

        // Intent to launch MainActivity with details to auto-open scanner
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("PERIOD_ID", periodId)
            putExtra("AUTO_OPEN_ATTENDANCE", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            periodId.toInt(),
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Using standard system alarm icon as fallback
            .setContentTitle("Attendance opening soon")
            .setContentText("Class for $subjectName starts at $startTime in $room")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(periodId.toInt(), notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        // Reschedule alarms to update for future periods
        AlarmScheduler.scheduleAlarms(context)
    }

    private fun createNotificationChannel(context: Context, channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Attendance Reminders"
            val descriptionText = "Notifications sent before classes start to remind you of attendance windows"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
