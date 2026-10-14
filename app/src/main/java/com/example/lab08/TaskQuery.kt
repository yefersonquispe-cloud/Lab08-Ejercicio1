package com.example.lab08

enum class TaskFilter(val label: String) {
    ALL("Todas"), PENDING("Pendientes"), COMPLETED("Completadas")
}

fun filterTasks(tasks: List<Task>, listName: String, starredOnly: Boolean, filter: TaskFilter): List<Task> =
    tasks.filter { task ->
        (if (starredOnly) task.isStarred else task.listName == listName) &&
            when (filter) {
                TaskFilter.ALL -> true
                TaskFilter.PENDING -> !task.isCompleted
                TaskFilter.COMPLETED -> task.isCompleted
            }
    }
