package com.example.ui.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.HabitHistoryEntry
import com.example.data.repository.HabitWithStatus
import com.example.data.repository.ZenithRepository
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ZenithTopBar
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.VioletLight
import com.example.ui.theme.VioletPrimary
import com.example.viewmodel.ZenithViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class HabitsSubView {
    DAILY_LIST,
    HISTORY_LOG
}

@Composable
fun HabitsScreen(
    viewModel: ZenithViewModel,
    modifier: Modifier = Modifier
) {
    val habits by viewModel.habitsWithStatus.collectAsState()
    val allCompletions by viewModel.allHabitCompletions.collectAsState()
    val historyEntries by viewModel.habitHistoryEntries.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()

    var subView by remember { mutableStateOf(HabitsSubView.DAILY_LIST) }
    var selectedCategory by remember { mutableStateOf("All") }
    var showAddDialog by remember { mutableStateOf(false) }
    var inspectedHabitId by remember { mutableStateOf<Long?>(null) }

    val categories = listOf("All", "Health", "Focus", "Mind", "Fitness", "Learning")
    val filteredHabits = remember(habits, selectedCategory) {
        if (selectedCategory == "All") habits
        else habits.filter { it.habit.category == selectedCategory }
    }

    val completedCount = habits.count { it.isCompletedToday }
    val totalCount = habits.size
    val progressRatio = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f
    val maxStreak = habits.maxOfOrNull { it.currentStreak } ?: 0
    val bestOverallStreak = habits.maxOfOrNull { it.bestStreak } ?: 0

    val todayStr = remember { ZenithRepository.getTodayString() }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 84.dp)
                .testTag("habits_lazy_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ZenithTopBar(
                    title = "Daily Habits",
                    subtitle = "Consistency compounds into mastery",
                    streakCount = maxStreak
                )
            }

            // Segmented Switcher: Daily List | History
            item {
                HabitsViewSwitcher(
                    currentView = subView,
                    historyCount = historyEntries.size,
                    onSelectView = { subView = it },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            if (subView == HabitsSubView.DAILY_LIST) {
                // 7-Day Date Strip
                item {
                    SevenDayDateStrip(
                        selectedDate = selectedDate,
                        todayString = todayStr,
                        onSelectDate = { viewModel.setSelectedDate(it) },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                // Progress Banner
                item {
                    val isToday = selectedDate == todayStr
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .testTag("habits_progress_card")
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (isToday) "TODAY'S RITUALS" else formatDateHeader(selectedDate, todayStr).uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$completedCount of $totalCount completed",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.testTag("habits_completion_summary")
                                    )
                                }

                                Text(
                                    text = "${(progressRatio * 100).toInt()}%",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (progressRatio >= 1f && totalCount > 0) EmeraldSuccess else AmberAccent
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = { progressRatio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (progressRatio >= 1f && totalCount > 0) EmeraldSuccess else VioletPrimary,
                                trackColor = MaterialTheme.colorScheme.surface
                            )
                        }
                    }
                }

                // Category Filter Chips
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) VioletPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedCategory = cat }
                                    .testTag("filter_category_${cat.lowercase()}")
                            ) {
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                if (filteredHabits.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = if (habits.isEmpty()) "No Habits Configured" else "No $selectedCategory Habits",
                            message = if (habits.isEmpty()) {
                                "Build your daily foundation! Tap '+' below to add your first daily habit."
                            } else {
                                "No habits match this category filter. Try selecting All or create a new habit."
                            },
                            actionLabel = if (habits.isEmpty()) "Add Habit" else "Show All",
                            onAction = {
                                if (habits.isEmpty()) showAddDialog = true else selectedCategory = "All"
                            },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                } else {
                    items(filteredHabits, key = { it.habit.id }) { item ->
                        HabitCard(
                            habitWithStatus = item,
                            onToggle = { viewModel.toggleHabit(item) },
                            onInspect = { inspectedHabitId = item.habit.id },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            } else {
                // HISTORY LOG VIEW
                item {
                    HistorySummaryCard(
                        totalCheckIns = historyEntries.size,
                        bestStreak = bestOverallStreak,
                        activeHabits = habits.size,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                if (historyEntries.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Completion History Yet",
                            message = "Mark your daily habits complete in the Daily List tab to start recording your Room history.",
                            actionLabel = "Go to Daily List",
                            onAction = { subView = HabitsSubView.DAILY_LIST },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                } else {
                    val grouped = historyEntries.groupBy { it.completion.dateString }
                    grouped.forEach { (dateStr, entries) ->
                        item(key = "header_$dateStr") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatDateHeader(dateStr, todayStr),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = PlusJakartaSansFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = EmeraldSuccess.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "${entries.size} completed",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = JetBrainsMonoFamily
                                        ),
                                        color = EmeraldSuccess,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        items(entries, key = { "hist_${it.completion.id}" }) { entry ->
                            HabitHistoryRow(
                                entry = entry,
                                onRemove = { viewModel.removeHistoryEntry(entry.completion.id) },
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Add Habit FAB
        FloatingActionButton(
            onClick = { showAddDialog = true },
            shape = CircleShape,
            containerColor = VioletPrimary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .testTag("add_habit_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Habit",
                modifier = Modifier.size(26.dp)
            )
        }

        if (showAddDialog) {
            AddHabitDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { title, desc, cat, color, icon, days ->
                    viewModel.addNewHabit(title, desc, cat, color, icon, days)
                    showAddDialog = false
                }
            )
        }

        val inspectedItem = habits.find { it.habit.id == inspectedHabitId }
        if (inspectedItem != null) {
            val habitHistory = remember(allCompletions, inspectedItem.habit.id) {
                allCompletions.filter { it.habitId == inspectedItem.habit.id }
            }
            HabitDetailHistoryDialog(
                habitWithStatus = inspectedItem,
                habitCompletions = habitHistory,
                onToggleDate = { dateStr, isDone ->
                    viewModel.toggleHabitForDate(inspectedItem.habit.id, dateStr, isDone)
                },
                onDeleteHabit = {
                    viewModel.deleteHabit(inspectedItem.habit)
                    inspectedHabitId = null
                },
                onDismiss = { inspectedHabitId = null }
            )
        }
    }
}

@Composable
private fun HabitsViewSwitcher(
    currentView: HabitsSubView,
    historyCount: Int,
    onSelectView: (HabitsSubView) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SwitcherPill(
                label = "Daily List",
                icon = Icons.Default.FormatListBulleted,
                isSelected = currentView == HabitsSubView.DAILY_LIST,
                onClick = { onSelectView(HabitsSubView.DAILY_LIST) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("habits_tab_daily")
            )
            SwitcherPill(
                label = "History ($historyCount)",
                icon = Icons.Default.History,
                isSelected = currentView == HabitsSubView.HISTORY_LOG,
                onClick = { onSelectView(HabitsSubView.HISTORY_LOG) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("habits_tab_history")
            )
        }
    }
}

@Composable
private fun SwitcherPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) VioletPrimary else Color.Transparent,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SevenDayDateStrip(
    selectedDate: String,
    todayString: String,
    onSelectDate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val days = remember(todayString) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dowFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val domFormat = SimpleDateFormat("d", Locale.getDefault())
        (6 downTo 0).map { offset ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -offset)
            Triple(
                sdf.format(cal.time),
                dowFormat.format(cal.time).uppercase(),
                domFormat.format(cal.time)
            )
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEach { (dateStr, dow, dom) ->
            val isSelected = selectedDate == dateStr
            val isToday = dateStr == todayString

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = when {
                    isSelected -> VioletPrimary
                    isToday -> VioletPrimary.copy(alpha = 0.16f)
                    else -> MaterialTheme.colorScheme.surface
                },
                border = if (isToday && !isSelected) BorderStroke(1.dp, VioletLight) else null,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectDate(dateStr) }
                    .testTag("date_strip_$dateStr")
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isToday) "NOW" else dow.take(3),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isSelected) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dom,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun HabitCard(
    habitWithStatus: HabitWithStatus,
    onToggle: () -> Unit,
    onInspect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val habit = habitWithStatus.habit
    val isCompleted = habitWithStatus.isCompletedToday
    val parsedColor = parseHexColor(habit.colorHex, VioletPrimary)

    val cardBg by animateColorAsState(
        targetValue = if (isCompleted) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(),
        label = "cardBg"
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 0.dp else 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("habit_card_${habit.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onInspect() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Icon
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(parsedColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getHabitIcon(habit.iconName),
                    contentDescription = habit.category,
                    tint = parsedColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Habit Details + Mini 7-Day History Dots
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = PlusJakartaSansFamily,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else null
                    ),
                    color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (habit.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = habit.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Mini 7-Day History Dots
                if (habitWithStatus.recentDays.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        habitWithStatus.recentDays.forEach { day ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (day.isCompleted) parsedColor else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (day.isCompleted) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(9.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = day.dayLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        fontFamily = JetBrainsMonoFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (habitWithStatus.currentStreak > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AmberGlow.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = "Streak",
                                    tint = AmberGlow,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${habitWithStatus.currentStreak}d streak",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = AmberGlow
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = "${habitWithStatus.weeklyCompletions}/${habit.targetDaysPerWeek} this wk",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Accessible 48.dp Completion Toggle IconButton
            IconButton(
                onClick = onToggle,
                modifier = Modifier
                    .size(48.dp)
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .background(
                        if (isCompleted) EmeraldSuccess else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .testTag("habit_toggle_${habit.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = if (isCompleted) "Mark ${habit.title} incomplete" else "Mark ${habit.title} complete",
                    tint = if (isCompleted) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun HistorySummaryCard(
    totalCheckIns: Int,
    bestStreak: Int,
    activeHabits: Int,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("history_summary_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            HistoryStatColumn("CHECK-INS", "$totalCheckIns", EmeraldSuccess)
            HistoryStatColumn("BEST STREAK", "${bestStreak}d", AmberAccent)
            HistoryStatColumn("HABITS", "$activeHabits", VioletLight)
        }
    }
}

@Composable
private fun HistoryStatColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
    }
}

@Composable
private fun HabitHistoryRow(
    entry: HabitHistoryEntry,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val habit = entry.habit
    val parsedColor = parseHexColor(habit.colorHex, VioletPrimary)
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeStr = timeFormatter.format(Date(entry.completion.completedAt))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .testTag("history_entry_${entry.completion.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(parsedColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getHabitIcon(habit.iconName),
                    contentDescription = habit.category,
                    tint = parsedColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = PlusJakartaSansFamily
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = habit.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = parsedColor
                    )
                    Text(
                        text = " • Logged at $timeStr",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("remove_history_${entry.completion.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove completion record for ${habit.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatDateHeader(dateString: String, todayString: String): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val displayFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    val calYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val yesterdayString = sdf.format(calYesterday.time)

    val parsed = try { sdf.parse(dateString) } catch (_: Exception) { null }
    val formatted = if (parsed != null) displayFormat.format(parsed) else dateString

    return when (dateString) {
        todayString -> "Today • $formatted"
        yesterdayString -> "Yesterday • $formatted"
        else -> formatted
    }
}
