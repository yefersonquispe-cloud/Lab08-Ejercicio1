package com.example.lab08

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class TaskRepeat(val label: String, val days: Int) {
    NONE("Ninguna", 0), DAILY("Diaria", 1), WEEKLY("Semanal", 7);
    companion object { fun from(label: String) = entries.firstOrNull { it.label == label } ?: NONE }
}

// Conserva la hora local de la serie, incluso en cambios de horario de verano.
fun nextRepeatDate(base: Long, repeat: TaskRepeat, now: Long, zone: String): Long? {
    if (repeat == TaskRepeat.NONE) return null
    val calendar = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply { timeInMillis = base }
    do { calendar.add(Calendar.DAY_OF_MONTH, repeat.days) } while (calendar.timeInMillis <= now)
    return calendar.timeInMillis
}

class TaskRecurrenceScheduler(context: Context) {
    private val work = WorkManager.getInstance(context)
    fun sync(task: Task, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val date = task.nextRepeatAt
        if (TaskRepeat.from(task.repeat) == TaskRepeat.NONE || date == null) {
            cancel(task.id); return
        }
        val request = OneTimeWorkRequestBuilder<TaskRecurrenceWorker>()
            .setInputData(workDataOf("task_id" to task.id, "repeat_at" to date))
            .setInitialDelay((date - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS).build()
        work.enqueueUniqueWork("task-repeat-${task.id}", policy, request)
    }
    fun cancel(id: Int) { work.cancelUniqueWork("task-repeat-$id") }
}

class TaskRecurrenceWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getInt("task_id", -1)
        val expected = inputData.getLong("repeat_at", -1)
        val now = System.currentTimeMillis()
        if (now < expected) return Result.retry()
        val db = TaskDatabase.getInstance(applicationContext)
        var updated: Task? = null
        db.withTransaction {
            val task = db.taskDao().getTask(id) ?: return@withTransaction
            val repeat = TaskRepeat.from(task.repeat)
            if (repeat == TaskRepeat.NONE || task.nextRepeatAt != expected) return@withTransaction
            val next = nextRepeatDate(expected, repeat, now, task.repeatZone) ?: return@withTransaction
            // Reinicia la misma tarea: no duplica filas ni inventa una nueva fecha de creación.
            db.taskDao().advanceRepeat(id, expected, next)
            updated = db.taskDao().getTask(id)
        }
        updated?.let { task ->
            // Encadena la próxima ejecución sin cancelar al trabajador que está terminando.
            TaskRecurrenceScheduler(applicationContext).sync(task, ExistingWorkPolicy.APPEND_OR_REPLACE)
            TaskReminderScheduler(applicationContext).sync(task)
        }
        return Result.success()
    }
}
