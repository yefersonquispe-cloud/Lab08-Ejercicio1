package com.example.lab08

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_lists")
data class TaskList(@PrimaryKey val name: String)
