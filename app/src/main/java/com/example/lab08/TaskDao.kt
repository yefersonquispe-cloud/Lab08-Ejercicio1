package com.example.lab08

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TaskDao {

    @Query("SELECT * FROM task_lists ORDER BY CASE WHEN name = 'Mis tareas' THEN 0 ELSE 1 END, name")
    suspend fun getLists(): List<TaskList>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertList(list: TaskList)

    // Obtener todas las tareas
    @Query("SELECT * FROM tasks")
    suspend fun getAllTasks(): List<Task>

    @Query("SELECT * FROM tasks")
    fun observeTasks(): kotlinx.coroutines.flow.Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: Int): Task?

    @Query("UPDATE tasks SET reminded_at = :delivered WHERE id = :id AND reminder_at = :expected AND is_completed = 0")
    suspend fun markReminded(id: Int, expected: Long, delivered: Long)

    @Query("UPDATE tasks SET description = :title, notes = :notes, priority = :priority WHERE id = :id")
    suspend fun editText(id: Int, title: String, notes: String, priority: String)

    @Query("UPDATE tasks SET is_starred = NOT is_starred WHERE id = :id")
    suspend fun toggleStar(id: Int)

    @Query("UPDATE tasks SET is_completed = NOT is_completed WHERE id = :id")
    suspend fun toggleCompletion(id: Int)

    @Query("UPDATE tasks SET reminder_at = :date, reminded_at = NULL, due_at = CASE WHEN :date IS NULL THEN due_at ELSE :date END WHERE id = :id")
    suspend fun setReminderDate(id: Int, date: Long?)

    // Insertar una nueva tarea
    @Insert
    suspend fun insertTask(task: Task): Long

    // Marcar una tarea como completada o no completada
    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    // Eliminar todas las tareas
    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()
}
