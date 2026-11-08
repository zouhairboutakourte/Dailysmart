package com.dailysmart.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskPriority { LOW, MEDIUM, HIGH }

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val category: String = "General",
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueDateMillis: Long? = null,
    val isCompleted: Boolean = false,
    val completedAtMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)