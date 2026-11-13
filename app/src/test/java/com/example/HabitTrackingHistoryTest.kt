package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ZenithDatabase
import com.example.data.model.Habit
import com.example.data.repository.ZenithRepository
import com.example.ui.habits.HabitsScreen
import com.example.ui.theme.ZenithTheme
import com.example.viewmodel.ZenithViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HabitTrackingHistoryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: ZenithDatabase
    private lateinit var repository: ZenithRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, ZenithDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ZenithRepository(database)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun markHabitComplete_storesAndRemovesHistoryInRoom() = runBlocking {
        val habitId = repository.addHabit(
            Habit(
                title = "Hydrate 2L",
                description = "Drink water consistently",
                category = "Health",
                colorHex = "#06B6D4",
                iconName = "wb_sunny",
                targetDaysPerWeek = 7
            )
        )

        val today = "2026-11-13"
        val yesterday = "2026-11-12"

        // Mark complete for today and yesterday
        repository.toggleHabitCompletion(habitId, yesterday, isCurrentlyCompleted = false)
        repository.toggleHabitCompletion(habitId, today, isCurrentlyCompleted = false)

        val allHistory = repository.allCompletions.first()
        assertEquals(2, allHistory.size)
        assertTrue(allHistory.any { it.habitId == habitId && it.dateString == today })
        assertTrue(allHistory.any { it.habitId == habitId && it.dateString == yesterday })

        // Toggle today off -> history retains yesterday only
        repository.toggleHabitCompletion(habitId, today, isCurrentlyCompleted = true)
        val afterToggleOff = repository.allCompletions.first()
        assertEquals(1, afterToggleOff.size)
        assertFalse(afterToggleOff.any { it.dateString == today })

        // Delete habit -> cleans up completion history
        val storedHabit = repository.allHabits.first().first { it.id == habitId }
        repository.deleteHabit(storedHabit)
        assertTrue(repository.allCompletions.first().isEmpty())
    }

    @Test
    fun habitsScreen_displaysListView_togglesCompletion_andShowsHistoryTab() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ZenithViewModel(app, repository)

        // Add a custom habit via ViewModel so we have a deterministic title to verify
        viewModel.addNewHabit(
            title = "Evening Stretch",
            description = "10 min mobility",
            category = "Fitness",
            colorHex = "#10B981",
            iconName = "fitness_center",
            targetDays = 5
        )

        composeTestRule.setContent {
            ZenithTheme {
                HabitsScreen(viewModel = viewModel)
            }
        }

        // Wait until added habit loads from Room
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            ShadowLooper.idleMainLooper()
            viewModel.habitsWithStatus.value.any { it.habit.title == "Evening Stretch" }
        }
        composeTestRule.waitForIdle()

        val stretchHabit = viewModel.habitsWithStatus.value.first { it.habit.title == "Evening Stretch" }
        assertFalse(stretchHabit.isCompletedToday)

        // Filter by Fitness and toggle completion in the list view
        composeTestRule.onNodeWithTag("habits_lazy_list")
            .assertIsDisplayed()
            .performScrollToNode(hasTestTag("filter_category_fitness"))
        composeTestRule.onNodeWithTag("filter_category_fitness").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("habit_toggle_${stretchHabit.habit.id}")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            ShadowLooper.idleMainLooper()
            viewModel.habitsWithStatus.value
                .firstOrNull { it.habit.id == stretchHabit.habit.id }
                ?.isCompletedToday == true
        }
        composeTestRule.waitForIdle()

        // Switch to History view and confirm Room history card & entry render
        composeTestRule.onNodeWithTag("habits_lazy_list")
            .performScrollToNode(hasTestTag("habits_tab_history"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("habits_tab_history").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("history_summary_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("habits_lazy_list")
            .performScrollToNode(hasText("Evening Stretch"))
        composeTestRule.onNodeWithText("Evening Stretch").assertIsDisplayed()
    }
}
