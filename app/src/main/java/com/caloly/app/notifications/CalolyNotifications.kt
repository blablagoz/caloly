package com.caloly.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.caloly.app.MainActivity
import com.caloly.app.R
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object NotificationPreferences {
    private const val FILE = "caloly_notifications"
    private const val ENABLED = "daily_enabled"
    private const val HOUR = "daily_hour"
    private const val ASKED = "permission_asked"

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(ENABLED, true)
    fun hour(context: Context): Int = prefs(context).getInt(HOUR, 20)
    fun permissionWasAsked(context: Context): Boolean = prefs(context).getBoolean(ASKED, false)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(ENABLED, value).apply()
        CalolyNotificationScheduler.schedule(context)
    }

    fun setHour(context: Context, value: Int) {
        prefs(context).edit().putInt(HOUR, value.coerceIn(0, 23)).apply()
        CalolyNotificationScheduler.schedule(context)
    }

    fun markPermissionAsked(context: Context) {
        prefs(context).edit().putBoolean(ASKED, true).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

object CalolyNotificationScheduler {
    private const val UNIQUE_WORK = "caloly_daily_log_reminder"

    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)
        if (!NotificationPreferences.isEnabled(context)) {
            workManager.cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val now = ZonedDateTime.now()
        var next = now.withHour(NotificationPreferences.hour(context)).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next))
            .build()
        workManager.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!NotificationPreferences.isEnabled(applicationContext)) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Günlük Caloly hatırlatmaları", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Beslenme kayıtlarını tamamlamayı hatırlatır"
                },
            )
        }
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 40, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Bugünün kaydı nasıl gidiyor?")
            .setContentText("Yediklerini ekleyerek günlük makro dağılımını tamamlayabilirsin.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "daily_tracking"
        private const val NOTIFICATION_ID = 904
    }
}
