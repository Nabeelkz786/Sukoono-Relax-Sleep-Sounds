package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMinutes: Int,
    val sessionType: String, // "POMODORO", "SHORT_BREAK", "LONG_BREAK", "FLOW"
    val tag: String,         // "Deep Work", "Coding", "Writing", "Mindfulness", "Design", "Study"
    val notes: String = ""
)

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val category: String,    // "Mind", "Health", "Focus", "Fitness", "Learning"
    val colorHex: String,
    val iconName: String,
    val targetDaysPerWeek: Int = 7,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "habit_completions",
    indices = [Index(value = ["habitId", "dateString"], unique = true)]
)
data class HabitCompletion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val habitId: Long,
    val dateString: String,  // YYYY-MM-DD
    val completedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "timebox_tasks")
data class TimeboxTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateString: String,  // YYYY-MM-DD
    val startTime: String,   // "09:00"
    val durationMinutes: Int = 30,
    val title: String,
    val category: String,    // "Deep Work", "Routine", "Meeting", "Wellness"
    val priority: String = "MEDIUM", // "HIGH", "MEDIUM", "LOW"
    val isCompleted: Boolean = false
)

@Entity(tableName = "mindful_reflections")
data class MindfulReflection(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateString: String,  // YYYY-MM-DD
    val mood: String,        // "Energized", "Calm", "Focused", "Tired", "Reflective"
    val gratitude: String = "",
    val intentions: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
