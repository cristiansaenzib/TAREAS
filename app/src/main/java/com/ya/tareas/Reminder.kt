package com.ya.tareas

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

object Reminder {
    /** Programa un aviso diario a las 9:00 (hora aproximada, Android la ajusta para ahorrar batería). */
    fun schedule(c: Context, hour: Int = 9) {
        val now = Calendar.getInstance()
        val t = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(t.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("daily", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class ReminderWorker(private val ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val top = TaskRepo.pending(ctx).firstOrNull() ?: return Result.success()
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("daily", "Recordatorio diario", NotificationManager.IMPORTANCE_DEFAULT))
        val streak = TaskRepo.streak(ctx)
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, "daily")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Empieza por: ${top.title}")
            .setContentText(if (streak > 0) "Llevas $streak días de racha. No la rompas." else "Hazla ahora y quítatela de encima.")
            .setContentIntent(open).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(ctx).notify(1, n) } catch (_: SecurityException) {}
        return Result.success()
    }
}
