package com.example.lab08

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TaskViewModel(private val dao: TaskDao) : ViewModel() {

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
            dao.updateTask(task.copy(isStarred = !task.isStarred))
            _tasks.value = dao.getAllTasks()
        }
    }

    init {
        // Al inicializar, cargamos las listas y las tareas guardadas.
        viewModelScope.launch {
            dao.insertList(TaskList("Mis tareas"))
            _lists.value = dao.getLists()
            _tasks.value = dao.getAllTasks()
        }
    }


    // Función para añadir una nueva tarea
    fun addTask(description: String, listName: String = "Mis tareas", notes: String = "",
                dueAt: Long? = null, starred: Boolean = false) {
        if (description.isBlank()) return
        val newTask = Task(description = description.trim(), listName = listName, notes = notes.trim(),
            dueAt = dueAt, isStarred = starred)
        viewModelScope.launch {
            dao.insertTask(newTask)
            _tasks.value = dao.getAllTasks() // Recargamos la lista
        }
    }

    fun editTask(task: Task, title: String, notes: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            dao.updateTask(task.copy(description = title.trim(), notes = notes.trim()))
            _tasks.value = dao.getAllTasks()
        }
    }

    // Función para alternar el estado de completado de una tarea
    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val updatedTask = task.copy(isCompleted = !task.isCompleted)
            dao.updateTask(updatedTask)
            _tasks.value = dao.getAllTasks() // Recargamos la lista
        }
    }

    // Función para eliminar todas las tareas
    fun deleteAllTasks() {
        viewModelScope.launch {
            dao.deleteAllTasks()
            _tasks.value = emptyList() // Vaciamos la lista en el estado
        }
    }
}
