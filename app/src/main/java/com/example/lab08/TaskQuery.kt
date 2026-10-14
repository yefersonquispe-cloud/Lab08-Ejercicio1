package com.example.lab08

enum class TaskFilter(val label: String) {
    ALL("Todas"), PENDING("Pendientes"), COMPLETED("Completadas")
}

fun filterTasks(tasks: List<Task>, listName: String, starredOnly: Boolean, filter: TaskFilter, query: String = "", category: String? = null): List<Task> =
    tasks.filter { task ->
        (category == null || task.category == category) &&
        (query.isBlank() || task.description.contains(query.trim(), ignoreCase = true) ||
            task.notes.contains(query.trim(), ignoreCase = true)) &&
        (if (starredOnly) task.isStarred else task.listName == listName) &&
            when (filter) {
                TaskFilter.ALL -> true
                TaskFilter.PENDING -> !task.isCompleted
                TaskFilter.COMPLETED -> task.isCompleted
            }
    }


enum class TaskSort(val label: String) {
    NAME("Nombre"), CREATED("Fecha de creación"), STATE("Estado")
}

fun sortTasks(tasks: List<Task>, sort: TaskSort): List<Task> = when (sort) {
    TaskSort.NAME -> tasks.sortedWith(compareBy<Task> { it.description.lowercase(java.util.Locale.ROOT) }.thenBy { it.id })
    // Más recientes primero; fechas desconocidas al final, sin inventar fechas.
    TaskSort.CREATED -> tasks.sortedWith(compareByDescending<Task> { it.createdAt ?: Long.MIN_VALUE }.thenByDescending { it.id })
    TaskSort.STATE -> tasks.sortedWith(compareBy<Task> { it.isCompleted }.thenBy { it.description.lowercase(java.util.Locale.ROOT) }.thenBy { it.id })
}
