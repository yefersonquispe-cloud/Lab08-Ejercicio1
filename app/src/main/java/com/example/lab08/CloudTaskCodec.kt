package com.example.lab08

object CloudTaskCodec {
    fun encode(task: Task, changedAt: Long): Map<String, Any?> = mapOf(
        "description" to task.description, "notes" to task.notes,
        "isCompleted" to task.isCompleted, "isStarred" to task.isStarred,
        "listName" to task.listName, "dueAt" to task.dueAt, "createdAt" to task.createdAt,
        "reminderAt" to task.reminderAt, "remindedAt" to task.remindedAt,
        "priority" to task.priority, "repeat" to task.repeat, "nextRepeatAt" to task.nextRepeatAt,
        "repeatZone" to task.repeatZone, "category" to task.category,
        "changedAt" to changedAt, "deleted" to false, "schemaVersion" to 1
    )
    fun decode(data: Map<String, Any?>, localId: Int): Task? {
        val title = (data["description"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        fun number(key: String) = (data[key] as? Number)?.toLong()
        return Task(id = localId, description = title,
            notes = data["notes"] as? String ?: "", isCompleted = data["isCompleted"] as? Boolean ?: false,
            isStarred = data["isStarred"] as? Boolean ?: false,
            listName = (data["listName"] as? String)?.takeIf { it.isNotBlank() } ?: "Mis tareas",
            dueAt = number("dueAt"), createdAt = number("createdAt"),
            reminderAt = number("reminderAt"), remindedAt = number("remindedAt"),
            priority = (data["priority"] as? String)?.takeIf { it in listOf("Alta", "Media", "Baja") } ?: "Media",
            repeat = TaskRepeat.from(data["repeat"] as? String ?: "Ninguna").label,
            nextRepeatAt = number("nextRepeatAt"), repeatZone = data["repeatZone"] as? String ?: "UTC",
            category = data["category"] as? String ?: "")
    }
}
