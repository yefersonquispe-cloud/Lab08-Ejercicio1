package com.example.lab08

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TaskViewModel(private val dao: TaskDao, private val reminders: TaskReminderScheduler) : ViewModel() {

    // Estado para la lista de tareas
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks

    private val _lists = MutableStateFlow<List<TaskList>>(listOf(TaskList("Mis tareas")))
    val lists: StateFlow<List<TaskList>> = _lists

    fun addList(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            dao.insertList(TaskList(name.trim()))
            _lists.value = dao.getLists()
        }
    }

    fun toggleTaskStar(task: Task) {
        viewModelScope.launch {
            dao.toggleStar(task.id)
        }
    }

    init {
        viewModelScope.launch { dao.observeTasks().collect { _tasks.value = it } }
        // Al inicializar, cargamos las listas y restauramos recordatorios aún pendientes.
        viewModelScope.launch {
            dao.insertList(TaskList("Mis tareas"))
            _lists.value = dao.getLists()
            dao.getAllTasks().forEach { reminders.sync(it, replace = false) }
        }
    }


    // Función para añadir una nueva tarea
    fun addTask(description: String, listName: String = "Mis tareas", notes: String = "",
                dueAt: Long? = null, starred: Boolean = false) {
        if (description.isBlank()) return
        val newTask = Task(description = description.trim(), listName = listName, notes = notes.trim(),
            dueAt = dueAt, isStarred = starred)
        viewModelScope.launch {
            val id = dao.insertTask(newTask).toInt()
            reminders.sync(newTask.copy(id = id))
        }
    }

    fun editTask(task: Task, title: String, notes: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            dao.editText(task.id, title.trim(), notes.trim())
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            dao.deleteTask(task)
            reminders.cancel(task.id)
        }
    }

    // Función para alternar el estado de completado de una tarea
    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            dao.toggleCompletion(task.id)
            dao.getTask(task.id)?.let { reminders.sync(it) }
        }
    }

    fun setReminder(task: Task, date: Long?) {
        if (date != null && date <= System.currentTimeMillis()) return
        viewModelScope.launch {
            dao.setReminderDate(task.id, date)
            dao.getTask(task.id)?.let { reminders.sync(it) }
        }
    }

    // Función para eliminar todas las tareas
    fun deleteAllTasks() {
        viewModelScope.launch {
            val old = dao.getAllTasks()
            dao.deleteAllTasks()
            old.forEach { reminders.cancel(it.id) }
        }
    }
}
