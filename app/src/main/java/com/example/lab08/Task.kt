package com.example.lab08

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    @ColumnInfo(name = "is_starred", defaultValue = "0") val isStarred: Boolean = false,
    @ColumnInfo(name = "list_name", defaultValue = "'Mis tareas'") val listName: String = "Mis tareas",
    @ColumnInfo(defaultValue = "''") val notes: String = "",
    @ColumnInfo(name = "due_at") val dueAt: Long? = null,
    // Las tareas antiguas conservan fecha desconocida; las nuevas usan la creación real.
    @ColumnInfo(name = "created_at") val createdAt: Long? = System.currentTimeMillis()
)
