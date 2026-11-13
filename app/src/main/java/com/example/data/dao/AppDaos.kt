package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.FocusSession
import com.example.data.model.Habit
import com.example.data.model.HabitCompletion
import com.example.data.model.MindfulReflection
import com.example.data.model.TimeboxTask
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getSessionsSince(sinceTimestamp: Long): Flow<List<FocusSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSession): Long

    @Query("SELECT COALESCE(SUM(durationMinutes), 0) FROM focus_sessions WHERE sessionType = 'POMODORO' OR sessionType = 'FLOW'")
    fun getTotalFocusMinutes(): Flow<Int>
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY id ASC")
    fun getAllHabits(): Flow<List<Habit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: Habit): Long

    @Update
    suspend fun updateHabit(habit: Habit)

    @Delete
    suspend fun deleteHabit(habit: Habit)

    @Query("SELECT * FROM habit_completions WHERE dateString = :dateString")
    fun getCompletionsForDate(dateString: String): Flow<List<HabitCompletion>>

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId ORDER BY dateString DESC, completedAt DESC")
    fun getCompletionsForHabit(habitId: Long): Flow<List<HabitCompletion>>

    @Query("SELECT * FROM habit_completions ORDER BY dateString DESC, completedAt DESC")
    fun getAllCompletions(): Flow<List<HabitCompletion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: HabitCompletion): Long

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND dateString = :dateString")
    suspend fun deleteCompletion(habitId: Long, dateString: String)

    @Query("DELETE FROM habit_completions WHERE id = :completionId")
    suspend fun deleteCompletionById(completionId: Long)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId")
    suspend fun deleteCompletionsForHabit(habitId: Long)
}

@Dao
interface TimeboxDao {
    @Query("SELECT * FROM timebox_tasks WHERE dateString = :dateString ORDER BY startTime ASC")
    fun getTasksForDate(dateString: String): Flow<List<TimeboxTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TimeboxTask): Long

    @Update
    suspend fun updateTask(task: TimeboxTask)

    @Delete
    suspend fun deleteTask(task: TimeboxTask)

    @Query("UPDATE timebox_tasks SET isCompleted = :isCompleted WHERE id = :taskId")
    suspend fun setTaskCompleted(taskId: Long, isCompleted: Boolean)
}

@Dao
interface ReflectionDao {
    @Query("SELECT * FROM mindful_reflections WHERE dateString = :dateString LIMIT 1")
    fun getReflectionForDate(dateString: String): Flow<MindfulReflection?>

    @Query("SELECT * FROM mindful_reflections ORDER BY timestamp DESC")
    fun getAllReflections(): Flow<List<MindfulReflection>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReflection(reflection: MindfulReflection): Long
}
