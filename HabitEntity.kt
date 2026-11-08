package com.dailysmart.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val iconName: String = "check_circle",
    val colorHex: String = "#5BC0BE",
    val frequencyPerWeek: Int = 7,
    val targetDays: String = "1,2,3,4,5,6,7",
    val createdAtMillis: Long = System.currentTimeMillis()
)