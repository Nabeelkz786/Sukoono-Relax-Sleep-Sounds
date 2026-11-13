package com.example

import com.example.data.model.HabitCompletion
import com.example.data.repository.ZenithRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ZenithStreakTest {

    @Test
    fun testEmptyCompletions_returnsZeroStreak() {
        val (currentStreak, bestStreak) = ZenithRepository.calculateStreak(1L, emptyList())
        assertEquals(0, currentStreak)
        assertEquals(0, bestStreak)
    }

    @Test
    fun testConsecutiveDays_calculatesStreakCorrectly() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val todayStr = sdf.format(cal.time)

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdf.format(cal.time)

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val dayBeforeYesterdayStr = sdf.format(cal.time)

        val completions = listOf(
            HabitCompletion(id = 1, habitId = 1L, dateString = dayBeforeYesterdayStr),
            HabitCompletion(id = 2, habitId = 1L, dateString = yesterdayStr),
            HabitCompletion(id = 3, habitId = 1L, dateString = todayStr)
        )

        val (currentStreak, bestStreak) = ZenithRepository.calculateStreak(1L, completions)
        assertEquals(3, currentStreak)
        assertEquals(3, bestStreak)
    }
}
