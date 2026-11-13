package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.focus.FocusScreen
import com.example.ui.habits.HabitsScreen
import com.example.ui.insights.InsightsScreen
import com.example.ui.planner.PlannerScreen
import com.example.ui.pro.ProUpgradeScreen
import com.example.ui.reflect.ReflectScreen
import com.example.ui.sounds.AdMobBannerBar
import com.example.ui.sounds.SoundsScreen
import com.example.ui.theme.VioletLight
import com.example.ui.theme.VioletPrimary
import com.example.viewmodel.ZenithTab
import com.example.viewmodel.ZenithViewModel

data class NavItem(
    val tab: ZenithTab,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
)

@Composable
fun ZenithApp(
    viewModel: ZenithViewModel = viewModel()
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val showReflectSheet by viewModel.showReflectSheet.collectAsState()
    val showProScreen by viewModel.showProScreen.collectAsState()
    val entitlementState by viewModel.entitlementState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val navItems = listOf(
        NavItem(ZenithTab.SOUNDS, Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq, "Sounds"),
        NavItem(ZenithTab.FOCUS, Icons.Filled.Timer, Icons.Outlined.Timer, "Focus"),
        NavItem(ZenithTab.HABITS, Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle, "Habits"),
        NavItem(ZenithTab.PLANNER, Icons.Filled.CalendarToday, Icons.Outlined.CalendarToday, "Planner"),
        NavItem(ZenithTab.INSIGHTS, Icons.Filled.BarChart, Icons.Outlined.BarChart, "Insights")
    )

    if (showProScreen) {
        ProUpgradeScreen(viewModel = viewModel)
        return
    }

    BackHandler(enabled = showReflectSheet || currentTab != ZenithTab.SOUNDS) {
        if (showReflectSheet) {
            viewModel.closeReflectJournal()
        } else {
            viewModel.selectTab(ZenithTab.SOUNDS)
        }
    }

    if (isTablet) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            NavigationRail(
                modifier = Modifier
                    .fillMaxHeight()
                    .testTag("tablet_navigation_rail"),
                containerColor = MaterialTheme.colorScheme.surface,
                header = {
                    Box(modifier = Modifier.padding(vertical = 16.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Spa,
                            contentDescription = "Sukoono Logo",
                            tint = VioletPrimary
                        )
                    }
                }
            ) {
                navItems.forEach { item ->
                    val isSelected = currentTab == item.tab && !showReflectSheet
                    NavigationRailItem(
                        selected = isSelected,
                        onClick = { viewModel.selectTab(item.tab) },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = VioletLight,
                            selectedTextColor = VioletLight,
                            indicatorColor = VioletPrimary.copy(alpha = 0.2f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("nav_${item.tab.name.lowercase()}")
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    MainContentHost(
                        currentTab = currentTab,
                        showReflectSheet = showReflectSheet,
                        viewModel = viewModel
                    )
                }
                AdMobBannerBar(
                    isPro = entitlementState.isPro,
                    isOnline = isOnline
                )
            }
        }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                    AdMobBannerBar(
                        isPro = entitlementState.isPro,
                        isOnline = isOnline
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        modifier = Modifier.testTag("bottom_navigation_bar")
                    ) {
                        navItems.forEach { item ->
                            val isSelected = currentTab == item.tab && !showReflectSheet
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { viewModel.selectTab(item.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = VioletLight,
                                    selectedTextColor = VioletLight,
                                    indicatorColor = VioletPrimary.copy(alpha = 0.2f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_${item.tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                MainContentHost(
                    currentTab = currentTab,
                    showReflectSheet = showReflectSheet,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun MainContentHost(
    currentTab: ZenithTab,
    showReflectSheet: Boolean,
    viewModel: ZenithViewModel,
    modifier: Modifier = Modifier
) {
    if (showReflectSheet) {
        Column(modifier = modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.closeReflectJournal() },
                    modifier = Modifier.testTag("close_reflect_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Insights",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Back to Insights",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ReflectScreen(viewModel = viewModel, modifier = Modifier.weight(1f))
        }
    } else {
        CurrentTabContent(currentTab = currentTab, viewModel = viewModel, modifier = modifier)
    }
}

@Composable
fun CurrentTabContent(
    currentTab: ZenithTab,
    viewModel: ZenithViewModel,
    modifier: Modifier = Modifier
) {
    when (currentTab) {
        ZenithTab.SOUNDS -> SoundsScreen(viewModel = viewModel, modifier = modifier)
        ZenithTab.FOCUS -> FocusScreen(viewModel = viewModel, modifier = modifier)
        ZenithTab.HABITS -> HabitsScreen(viewModel = viewModel, modifier = modifier)
        ZenithTab.PLANNER -> PlannerScreen(viewModel = viewModel, modifier = modifier)
        ZenithTab.INSIGHTS -> InsightsScreen(viewModel = viewModel, modifier = modifier)
    }
}
