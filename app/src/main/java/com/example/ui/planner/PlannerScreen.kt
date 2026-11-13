package com.example.ui.planner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TimeboxTask
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ZenithTopBar
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanFocus
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.RoseWarning
import com.example.ui.theme.VioletLight
import com.example.ui.theme.VioletPrimary
import com.example.viewmodel.ZenithViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun PlannerScreen(
    viewModel: ZenithViewModel,
    modifier: Modifier = Modifier
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val tasks by viewModel.tasksForDate.collectAsState()
    var showAddTaskDialog by remember { mutableStateOf(false) }

    val completedTasks = tasks.count { it.isCompleted }
    val totalTasks = tasks.size

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ZenithTopBar(
                    title = "Daily Timebox",
                    subtitle = "Plan with intent, execute with calm"
                )
            }

            // Date Navigator Bar
            item {
                DateNavigator(
                    currentDate = selectedDate,
                    onDateChanged = { viewModel.setSelectedDate(it) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            // Summary Stats Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SCHEDULED BLOCKS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$completedTasks of $totalTasks blocks finished",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (totalTasks > 0) {
                            val ratio = completedTasks.toFloat() / totalTasks.toFloat()
                            Text(
                                text = "${(ratio * 100).toInt()}%",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (ratio >= 1f) EmeraldSuccess else VioletLight
                            )
                        }
                    }
                }
            }

            if (tasks.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Blocks Scheduled",
                        message = "Timebox your day to protect your deep work. Tap '+' to create your first block.",
                        actionLabel = "Add Timeblock",
                        onAction = { showAddTaskDialog = true },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            } else {
                items(tasks, key = { it.id }) { task ->
                    TimeboxTaskCard(
                        task = task,
                        onToggle = { viewModel.toggleTask(task) },
                        onDelete = { viewModel.deleteTask(task) },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddTaskDialog = true },
            shape = CircleShape,
            containerColor = VioletPrimary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .testTag("add_task_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Timeblock",
                modifier = Modifier.size(26.dp)
            )
        }

        if (showAddTaskDialog) {
            AddTimeboxDialog(
                onDismiss = { showAddTaskDialog = false },
                onAdd = { title, time, duration, cat, prio ->
                    viewModel.addTimeboxTask(title, time, duration, cat, prio)
                    showAddTaskDialog = false
                }
            )
        }
    }
}

@Composable
fun DateNavigator(
    currentDate: String,
    onDateChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val displayFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

    val parsedDate = try { sdf.parse(currentDate) ?: Date() } catch (_: Exception) { Date() }
    val displayString = displayFormat.format(parsedDate)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val cal = Calendar.getInstance().apply { time = parsedDate; add(Calendar.DAY_OF_YEAR, -1) }
                    onDateChanged(sdf.format(cal.time))
                },
                modifier = Modifier.testTag("prev_day_button")
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Day")
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = displayString,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                val isToday = currentDate == SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                if (isToday) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = VioletLight
                    )
                }
            }

            IconButton(
                onClick = {
                    val cal = Calendar.getInstance().apply { time = parsedDate; add(Calendar.DAY_OF_YEAR, 1) }
                    onDateChanged(sdf.format(cal.time))
                },
                modifier = Modifier.testTag("next_day_button")
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next Day")
            }
        }
    }
}

@Composable
fun TimeboxTaskCard(
    task: TimeboxTask,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg by animateColorAsState(
        targetValue = if (task.isCompleted) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "taskCardBg"
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (task.isCompleted) 0.dp else 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time Badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(58.dp)
            ) {
                Text(
                    text = task.startTime,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else VioletLight
                )
                Text(
                    text = "${task.durationMinutes}m",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Task Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = PlusJakartaSansFamily,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                    ),
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Category Tag
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = task.category,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Priority Tag
                    val (prioColor, prioLabel) = when (task.priority) {
                        "HIGH" -> RoseWarning to "High"
                        "MEDIUM" -> AmberAccent to "Med"
                        else -> CyanFocus to "Low"
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = prioColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = prioLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = prioColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Checkbox
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (task.isCompleted) EmeraldSuccess else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onToggle() }
                    .testTag("task_toggle_${task.id}"),
                contentAlignment = Alignment.Center
            ) {
                if (task.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddTimeboxDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, startTime: String, duration: Int, category: String, priority: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("09:00") }
    var durationMinutes by remember { mutableIntStateOf(45) }
    var category by remember { mutableStateOf("Deep Work") }
    var priority by remember { mutableStateOf("HIGH") }

    val categories = listOf("Deep Work", "Routine", "Meeting", "Wellness")
    val durations = listOf(15, 30, 45, 60, 90)
    val priorities = listOf("HIGH", "MEDIUM", "LOW")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Timebox Block",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Block Title (e.g., Code Review & Architecture)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VioletPrimary,
                        cursorColor = VioletPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("timebox_title_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Start Time (HH:MM)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Duration",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            durations.take(3).forEach { d ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (durationMinutes == d) VioletPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { durationMinutes = d }
                                ) {
                                    Text(
                                        text = "${d}m",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (durationMinutes == d) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Category selector
                Column {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { cat ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (category == cat) VioletPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { category = cat }
                            ) {
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (category == cat) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Priority selector
                Column {
                    Text(
                        text = "Priority Level",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        priorities.forEach { prio ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (priority == prio) VioletPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { priority = prio }
                            ) {
                                Text(
                                    text = prio,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (priority == prio) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onAdd(title, startTime, durationMinutes, category, priority)
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                modifier = Modifier.testTag("save_task_button")
            ) {
                Text("Schedule Block", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
