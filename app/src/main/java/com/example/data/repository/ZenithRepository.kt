package com.example.data.repository

import com.example.data.ZenithDatabase
import com.example.data.model.FocusSession
import com.example.data.model.Habit
import com.example.data.model.HabitCompletion
import com.example.data.model.MindfulReflection
import com.example.data.model.TimeboxTask
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import kotlin.math.roundToLong

data class DayCompletionStatus(
    val dateString: String,
    val dayLabel: String,
    val isCompleted: Boolean
)

data class HabitWithStatus(
    val habit: Habit,
    val isCompletedToday: Boolean,
    val currentStreak: Int,
    val bestStreak: Int,
    val weeklyCompletions: Int,
    val recentDays: List<DayCompletionStatus> = emptyList(),
    val totalCompletions: Int = 0
)

data class HabitHistoryEntry(
    val completion: HabitCompletion,
    val habit: Habit
)

data class DailySummary(
    val dateString: String,
    val focusMinutes: Int,
    val habitsCompleted: Int,
    val totalHabits: Int,
    val tasksCompleted: Int,
    val totalTasks: Int
)

class ZenithRepository(private val database: ZenithDatabase) {
    private val focusDao = database.focusDao()
    private val habitDao = database.habitDao()
    private val timeboxDao = database.timeboxDao()
    private val reflectionDao = database.reflectionDao()

    // Focus
    val allFocusSessions: Flow<List<FocusSession>> = focusDao.getAllSessions()
    val totalFocusMinutes: Flow<Int> = focusDao.getTotalFocusMinutes()

    fun getSessionsSince(timestamp: Long): Flow<List<FocusSession>> {
        return focusDao.getSessionsSince(timestamp)
    }

    suspend fun logFocusSession(session: FocusSession): Long {
        return focusDao.insertSession(session)
    }

    // Habits
    val allHabits: Flow<List<Habit>> = habitDao.getAllHabits()
    val allCompletions: Flow<List<HabitCompletion>> = habitDao.getAllCompletions()

    fun getCompletionsForDate(dateString: String): Flow<List<HabitCompletion>> {
        return habitDao.getCompletionsForDate(dateString)
    }

    fun getCompletionsForHabit(habitId: Long): Flow<List<HabitCompletion>> {
        return habitDao.getCompletionsForHabit(habitId)
    }

    suspend fun addHabit(habit: Habit): Long {
        return habitDao.insertHabit(habit)
    }

    suspend fun deleteHabit(habit: Habit) {
        habitDao.deleteCompletionsForHabit(habit.id)
        habitDao.deleteHabit(habit)
    }

    suspend fun toggleHabitCompletion(habitId: Long, dateString: String, isCurrentlyCompleted: Boolean) {
        if (isCurrentlyCompleted) {
            habitDao.deleteCompletion(habitId, dateString)
        } else {
            habitDao.insertCompletion(HabitCompletion(habitId = habitId, dateString = dateString))
        }
    }

    suspend fun deleteCompletionById(completionId: Long) {
        habitDao.deleteCompletionById(completionId)
    }

    // Timebox
    fun getTasksForDate(dateString: String): Flow<List<TimeboxTask>> {
        return timeboxDao.getTasksForDate(dateString)
    }

    suspend fun addTask(task: TimeboxTask): Long {
        return timeboxDao.insertTask(task)
    }

    suspend fun updateTask(task: TimeboxTask) {
        timeboxDao.updateTask(task)
    }

    suspend fun deleteTask(task: TimeboxTask) {
        timeboxDao.deleteTask(task)
    }

    suspend fun toggleTaskCompletion(taskId: Long, isCompleted: Boolean) {
        timeboxDao.setTaskCompleted(taskId, isCompleted)
    }

    // Reflections
    fun getReflectionForDate(dateString: String): Flow<MindfulReflection?> {
        return reflectionDao.getReflectionForDate(dateString)
    }

    suspend fun saveReflection(reflection: MindfulReflection): Long {
        return reflectionDao.insertReflection(reflection)
    }

    companion object {
        fun getTodayString(): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        }

        fun calculateStreak(habitId: Long, completions: List<HabitCompletion>): Pair<Int, Int> {
            val habitDates = completions
                .filter { it.habitId == habitId }
                .map { it.dateString }
                .toSet()

            if (habitDates.isEmpty()) return Pair(0, 0)

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val calendar = Calendar.getInstance()
            val todayStr = sdf.format(calendar.time)

            // Current streak check starting from today or yesterday
            var currentStreak = 0
            val checkCal = Calendar.getInstance()
            val hasToday = habitDates.contains(todayStr)
            if (!hasToday) {
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
            }

            while (true) {
                val dateStr = sdf.format(checkCal.time)
                if (habitDates.contains(dateStr)) {
                    currentStreak++
                    checkCal.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
            }

            // Calculate best streak across all logged dates
            val sortedDates = habitDates.mapNotNull {
                try { sdf.parse(it) } catch (e: Exception) { null }
            }.sorted()

            var bestStreak = 0
            var tempStreak = 0
            var lastDate: Date? = null

            for (date in sortedDates) {
                if (lastDate == null) {
                    tempStreak = 1
                } else {
                    val diff = ((date.time - lastDate.time) / 86_400_000.0).roundToLong()
                    if (diff == 1L) {
                        tempStreak++
                    } else if (diff > 1L) {
                        tempStreak = 1
                    }
                }
                lastDate = date
                if (tempStreak > bestStreak) {
                    bestStreak = tempStreak
                }
            }

            return Pair(currentStreak, maxOf(bestStreak, currentStreak))
        }
    }
}
