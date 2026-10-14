package com.example.lab08

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class TaskReminderScheduler(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val work = WorkManager.getInstance(context)

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Recordatorios de tareas",
            NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Avisos para completar las tareas pendientes"
        })
    }

    fun sync(task: Task, replace: Boolean = true) {
        val date = task.reminderAt
        if (date == null || task.isCompleted || task.remindedAt != null) {
            cancel(task.id)
            return
        }
        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInputData(workDataOf("task_id" to task.id, "reminder_at" to date))
            .setInitialDelay((date - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniqueWork("task-reminder-${task.id}",
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(id: Int) {
        work.cancelUniqueWork("task-reminder-$id")
        manager.cancel(id)
    }

    companion object { const val CHANNEL = "task_reminders" }
}

class TaskReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getInt("task_id", -1)
        val expected = inputData.getLong("reminder_at", -1)
        val db = TaskDatabase.getInstance(applicationContext)
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        if (manager.getNotificationChannel(TaskReminderScheduler.CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return Result.success()
        if (System.currentTimeMillis() < expected) return Result.retry()
        if (!manager.areNotificationsEnabled()) return Result.success()
        // La transacción evita avisar sobre una tarea eliminada o completada durante la ejecución.
        db.withTransaction {
            val task = db.taskDao().getTask(id) ?: return@withTransaction
            if (task.isCompleted || task.reminderAt != expected || task.remindedAt != null) return@withTransaction
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("list_name", task.listName)
            }
            val open = PendingIntent.getActivity(applicationContext, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(applicationContext, TaskReminderScheduler.CHANNEL)
                .setSmallIcon(R.drawable.ic_task_notification)
                .setContentTitle(task.description)
                .setContentText(task.notes.ifBlank { "Recuerda completar tu tarea pendiente." })
                .setStyle(NotificationCompat.BigTextStyle().bigText(task.notes.ifBlank { "Recuerda completar tu tarea pendiente." }))
                .setContentIntent(open).setAutoCancel(true).build()
            try {
                manager.notify(id, notification)
                db.taskDao().markReminded(id, expected, System.currentTimeMillis())
            } catch (_: SecurityException) {
                // El usuario puede retirar el permiso mientras el recordatorio se ejecuta.
            }
        }
        return Result.success()
    }
}
