package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.FocusDao
import com.example.data.dao.HabitDao
import com.example.data.dao.ReflectionDao
import com.example.data.dao.TimeboxDao
import com.example.data.model.FocusSession
import com.example.data.model.Habit
import com.example.data.model.HabitCompletion
import com.example.data.model.MindfulReflection
import com.example.data.model.TimeboxTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Database(
    entities = [
        FocusSession::class,
        Habit::class,
        HabitCompletion::class,
        TimeboxTask::class,
        MindfulReflection::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ZenithDatabase : RoomDatabase() {
    abstract fun focusDao(): FocusDao
    abstract fun habitDao(): HabitDao
    abstract fun timeboxDao(): TimeboxDao
    abstract fun reflectionDao(): ReflectionDao

    companion object {
        @Volatile
        private var INSTANCE: ZenithDatabase? = null

        fun getInstance(context: Context): ZenithDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZenithDatabase::class.java,
                    "zenith_database"
                )
                    .addCallback(DatabaseSeedCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseSeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                val database = INSTANCE ?: return@launch
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                // Seed Default Habits
                val habitDao = database.habitDao()
                val habit1 = habitDao.insertHabit(
                    Habit(
                        title = "Morning Sunlight & Hydration",
                        description = "Drink 500ml water and get 10 mins of natural light",
                        category = "Health",
                        colorHex = "#FFB300",
                        iconName = "wb_sunny",
                        targetDaysPerWeek = 7
                    )
                )
                val habit2 = habitDao.insertHabit(
                    Habit(
                        title = "Deep Work Focus Block",
                        description = "Uninterrupted focus with notifications muted",
                        category = "Focus",
                        colorHex = "#7C4DFF",
                        iconName = "psychology",
                        targetDaysPerWeek = 5
                    )
                )
                val habit3 = habitDao.insertHabit(
                    Habit(
                        title = "Mindful Breathwork",
                        description = "5 minutes of box breathing or calm meditation",
                        category = "Mind",
                        colorHex = "#06B6D4",
                        iconName = "self_improvement",
                        targetDaysPerWeek = 7
                    )
                )
                val habit4 = habitDao.insertHabit(
                    Habit(
                        title = "Read 15 Pages",
                        description = "Non-fiction, philosophy, or personal growth",
                        category = "Learning",
                        colorHex = "#10B981",
                        iconName = "menu_book",
                        targetDaysPerWeek = 6
                    )
                )
                val habit5 = habitDao.insertHabit(
                    Habit(
                        title = "Evening Digital Sunset",
                        description = "Screens off 45 mins before bedtime",
                        category = "Health",
                        colorHex = "#F43F5E",
                        iconName = "nightlight",
                        targetDaysPerWeek = 7
                    )
                )

                // Seed starter completions across recent days for immediate history & streak display
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -1)
                val yesterday = sdf.format(cal.time)
                val yesterdayMs = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, -1)
                val twoDaysAgo = sdf.format(cal.time)
                val twoDaysAgoMs = cal.timeInMillis

                habitDao.insertCompletion(HabitCompletion(habitId = habit1, dateString = today))
                habitDao.insertCompletion(HabitCompletion(habitId = habit1, dateString = yesterday, completedAt = yesterdayMs))
                habitDao.insertCompletion(HabitCompletion(habitId = habit1, dateString = twoDaysAgo, completedAt = twoDaysAgoMs))
                habitDao.insertCompletion(HabitCompletion(habitId = habit2, dateString = yesterday, completedAt = yesterdayMs))
                habitDao.insertCompletion(HabitCompletion(habitId = habit3, dateString = today))
                habitDao.insertCompletion(HabitCompletion(habitId = habit3, dateString = yesterday, completedAt = yesterdayMs))
                habitDao.insertCompletion(HabitCompletion(habitId = habit4, dateString = twoDaysAgo, completedAt = twoDaysAgoMs))

                // Seed Timebox Tasks for Today
                val timeboxDao = database.timeboxDao()
                timeboxDao.insertTask(
                    TimeboxTask(
                        dateString = today,
                        startTime = "08:30",
                        durationMinutes = 45,
                        title = "Morning Routine & Goal Alignment",
                        category = "Routine",
                        priority = "MEDIUM",
                        isCompleted = true
                    )
                )
                timeboxDao.insertTask(
                    TimeboxTask(
                        dateString = today,
                        startTime = "09:30",
                        durationMinutes = 90,
                        title = "Deep Focus: Key Project Sprint",
                        category = "Deep Work",
                        priority = "HIGH",
                        isCompleted = false
                    )
                )
                timeboxDao.insertTask(
                    TimeboxTask(
                        dateString = today,
                        startTime = "12:00",
                        durationMinutes = 45,
                        title = "Nutritious Lunch & Outdoor Walk",
                        category = "Wellness",
                        priority = "MEDIUM",
                        isCompleted = false
                    )
                )
                timeboxDao.insertTask(
                    TimeboxTask(
                        dateString = today,
                        startTime = "14:00",
                        durationMinutes = 60,
                        title = "Review Tasks, Sync & Email Clearing",
                        category = "Routine",
                        priority = "LOW",
                        isCompleted = false
                    )
                )

                // Seed a completed focus session
                val focusDao = database.focusDao()
                focusDao.insertSession(
                    FocusSession(
                        timestamp = System.currentTimeMillis() - 7200000,
                        durationMinutes = 25,
                        sessionType = "POMODORO",
                        tag = "Deep Work",
                        notes = "Completed architecture planning"
                    )
                )

                // Seed starter reflection
                val reflectionDao = database.reflectionDao()
                reflectionDao.insertReflection(
                    MindfulReflection(
                        dateString = today,
                        mood = "Calm",
                        gratitude = "Grateful for crisp fresh air and uninterrupted focus time.",
                        intentions = "Execute high-leverage tasks with clarity and presence."
                    )
                )
            }
        }
    }
}
